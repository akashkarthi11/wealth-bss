package com.wealth.bss.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.common.logger.KarthiLogger;

// Opens a bank statement PDF in memory (nothing is written to disk) and reads the labelled amounts out of its text. The passwords and the text are never logged.
public final class StatementReader {

	private static final KarthiLogger LOGGER = new KarthiLogger(StatementReader.class);
	public static final int MAX_BYTES = 8_000_000;

	// The labels looked for, in the order shown. The first amount that follows a label is taken; Cr or Dr after it is kept.
	private static final List<String> LABELS = List.of("Closing Balance", "Opening Balance", "Total Amount Due", "Total Dues", "Minimum Amount Due", "Minimum Due",
			"Credit Limit", "Available Credit Limit", "Statement Balance");

	private StatementReader() {
	}

	// The text of the PDF, trying no password first and then each given one; null when none opens it (or the file is not a readable PDF).
	public static String text(byte[] pdf, List<String> passwords) {
		if (pdf == null || pdf.length == 0 || pdf.length > MAX_BYTES) {
			return null;
		}
		List<String> tries = new ArrayList<>();
		tries.add("");
		tries.addAll(passwords);
		for (String password : tries) {
			try (PDDocument document = Loader.loadPDF(pdf, password)) {
				PDFTextStripper stripper = new PDFTextStripper();
				stripper.setSortByPosition(true);
				return stripper.getText(document);
			} catch (IOException e) {
				LOGGER.info("A statement did not open with one password (" + e.getClass().getSimpleName() + ")");
			}
		}
		return null;
	}

	// HDFC credit card statement: the headings come first and the amounts follow in rows, with the rupee sign read as "C". Total amount due after "= ", then minimum due, due
	// date and the total, available and cash limits after "DUE DATE".
	private static final Pattern HDFC_TOTAL_DUE = Pattern.compile("TOTAL AMOUNT DUE.{0,120}?= C([\\d,]+\\.\\d{2})");
	private static final Pattern HDFC_MIN_AND_LIMITS = Pattern.compile("DUE DATE C([\\d,]+(?:\\.\\d{2})?) (\\d{1,2} [A-Za-z]{3}, \\d{4}) C([\\d,]+(?:\\.\\d{2})?) C([\\d,]+(?:\\.\\d{2})?) C([\\d,]+(?:\\.\\d{2})?)");

	private static List<String> hdfcCard(String body) {
		List<String> found = new ArrayList<>();
		Matcher due = HDFC_TOTAL_DUE.matcher(body);
		if (due.find()) {
			found.add("Total Amount Due ₹" + due.group(1));
		}
		Matcher rest = HDFC_MIN_AND_LIMITS.matcher(body);
		if (rest.find()) {
			found.add("Minimum Due ₹" + rest.group(1));
			found.add("Due Date " + rest.group(2));
			found.add("Total Credit Limit ₹" + rest.group(3));
			found.add("Available Credit Limit ₹" + rest.group(4));
		}
		return found;
	}

	private static final ObjectMapper AI_MAPPER = new ObjectMapper();
	private static final Pattern AI_PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
	private static final Pattern AI_EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern AI_LONG_DIGITS = Pattern.compile("(?<![\\d.,])\\d{9,}(?![\\d]|[.,]\\d)");
	private static final Pattern AI_ISO_DAY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	// The statement text made safer to send out: the customer's name (words that start with the saved first letters), PAN, e-mail addresses and long numbers (card, account, phone) are replaced.
	// The merchants, dates and amounts stay, because they are what is read.
	public static String redact(String text, String namePrefix) {
		String safe = AI_EMAIL.matcher(text).replaceAll("[email]");
		safe = AI_PAN.matcher(safe).replaceAll("[pan]");
		safe = AI_LONG_DIGITS.matcher(safe).replaceAll("[number]");
		if (namePrefix != null && namePrefix.length() >= 3) {
			safe = Pattern.compile("(?i)\\b" + Pattern.quote(namePrefix) + "[A-Za-z]*").matcher(safe).replaceAll("[name]");
		}
		return safe;
	}

	// The question for the model: only the individual transactions, as strict JSON.
	public static String aiPrompt(String statementText) {
		return "You read the text of a bank or credit card statement. List every individual transaction in it (a purchase, payment, refund, fee, interest or cashback), one entry each. "
				+ "Do not list summary figures such as totals, limits, balances, minimum due or due dates. "
				+ "Answer with JSON only, in this shape: {\"rows\":[{\"day\":\"YYYY-MM-DD\",\"description\":\"merchant or narration\",\"amount\":123.45,\"direction\":\"debit\"}]}. "
				+ "direction is debit for money spent or paid out, credit for money received (refund, cashback, a payment made to the card). amount is a positive number. "
				+ "Use the statement's own dates, adding the year of the statement when a date has none. Never invent a row; when there is none answer {\"rows\":[]}.\n\nStatement text:\n" + statementText;
	}

	// The model's answer as rows. Anything that is not a valid row is dropped (a date that is not real or is in the future, an amount that is not a positive number of at most two decimals,
	// a direction other than debit or credit); at most AI_ROWS_MAX rows. Every row is marked not sure, so accepting it sends it to the Data quality review instead of saving it directly.
	public static List<Line> aiLines(String json, int max, int descriptionMax) {
		List<Line> rows = new ArrayList<>();
		if (json == null || json.isBlank()) {
			return rows;
		}
		JsonNode root;
		try {
			root = AI_MAPPER.readTree(json.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
		} catch (IOException e) {
			return rows;
		}
		JsonNode list = root == null ? null : (root.isArray() ? root : root.path("rows"));
		if (list == null || !list.isArray()) {
			return rows;
		}
		for (JsonNode item : list) {
			if (rows.size() >= max) {
				break;
			}
			String day = item.path("day").asText("");
			String description = item.path("description").asText("").replaceAll("\\s+", " ").trim();
			String direction = item.path("direction").asText("").toLowerCase(Locale.ROOT);
			BigDecimal amount = aiAmount(item.path("amount"));
			if (!AI_ISO_DAY.matcher(day).matches() || !validDay(day) || LocalDate.parse(day).isAfter(LocalDate.now().plusDays(1)) || description.isEmpty() || amount == null
					|| !("debit".equals(direction) || "credit".equals(direction))) {
				continue;
			}
			rows.add(new Line(day, null, description.length() > descriptionMax ? description.substring(0, descriptionMax) : description, amount, "debit".equals(direction), false));
		}
		return rows;
	}

	private static BigDecimal aiAmount(JsonNode node) {
		try {
			BigDecimal amount = new BigDecimal(node.asText("").replace(",", "").trim());
			if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
				return null;
			}
			return amount.setScale(2, RoundingMode.UNNECESSARY);
		} catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	// One transaction row of a statement. day is yyyy-MM-dd; debit is money out; sure is false when the row was read by the general rule (a bank layout that is not known).
	public record Line(String day, String time, String description, BigDecimal amount, boolean debit, boolean sure) {
	}

	// HDFC credit card row: "23/10/2026| 00:00 5% Swiggy ORNGE CB + C 97.15 l": the date and time, the description, "+" for money in (payments, cashback), the rupee sign read as "C".
	private static final Pattern HDFC_ROW = Pattern.compile("^\\s*(\\d{2})/(\\d{2})/(\\d{4})\\|\\s*(\\d{2}:\\d{2})\\s+(.+?)\\s+(\\+\\s+)?C\\s*([\\d,]+\\.\\d{2})(?:\\s+l)?\\s*$");
	// The general rule: a row that starts with a date and ends with an amount (with Cr or Dr when the bank prints it).
	private static final Pattern GENERAL_ROW = Pattern.compile("^\\s*(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{2}-[A-Za-z]{3}-\\d{2,4}|\\d{1,2} [A-Za-z]{3},? \\d{4})\\s+(.+?)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.\\d{2})\\s*(Cr|Dr|CR|DR)?\\s*$");
	private static final List<DateTimeFormatter> GENERAL_DATES = List.of(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH), DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH), DateTimeFormatter.ofPattern("dd-MMM-uu", Locale.ENGLISH), DateTimeFormatter.ofPattern("d MMM, uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH));

	// Every transaction row of the statement, in order. The HDFC card layout is known (sure); any other row that looks like a dated amount is read by the general rule (not sure).
	public static List<Line> lines(String text) {
		List<Line> found = new ArrayList<>();
		if (text == null) {
			return found;
		}
		for (String raw : text.split("\\R")) {
			Matcher card = HDFC_ROW.matcher(raw);
			if (card.matches()) {
				BigDecimal amount = number(card.group(7));
				String day = card.group(3) + "-" + card.group(2) + "-" + card.group(1);
				if (amount != null && amount.signum() > 0 && validDay(day)) {
					found.add(new Line(day, card.group(4), card.group(5).trim(), amount, card.group(6) == null, true));
				}
				continue;
			}
			Matcher general = GENERAL_ROW.matcher(raw);
			if (general.matches()) {
				BigDecimal amount = number(general.group(3));
				String day = generalDay(general.group(1));
				String sign = general.group(4);
				if (amount != null && amount.signum() > 0 && day != null) {
					found.add(new Line(day, "", general.group(2).trim(), amount, sign == null || sign.equalsIgnoreCase("Dr"), false));
				}
			}
		}
		return found;
	}

	private static BigDecimal number(String text) {
		try {
			return new BigDecimal(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean validDay(String day) {
		try {
			LocalDate.parse(day);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private static String generalDay(String text) {
		for (DateTimeFormatter format : GENERAL_DATES) {
			try {
				return LocalDate.parse(text, format).toString();
			} catch (DateTimeParseException e) {
				// try the next layout
			}
		}
		return null;
	}

	private static final Pattern LABEL_AND_VALUE =Pattern.compile("^(.+?) (₹[\\d,]+(?:\\.\\d+)?(?: (?:Cr|Dr))?|\\d{1,2} [A-Za-z]{3}, \\d{4})$");

	// The same figures as label to value ("Closing Balance" to "₹40,000.00 Cr"), in the same order: what is saved for a statement, whatever labels its bank uses.
	public static Map<String, String> figureMap(String text) {
		Map<String, String> map = new LinkedHashMap<>();
		for (String figure : figures(text)) {
			Matcher match = LABEL_AND_VALUE.matcher(figure);
			if (match.matches()) {
				map.put(match.group(1), match.group(2));
			}
		}
		return map;
	}

	// "Closing Balance ₹40,000.00 Cr" per label found, in label order; empty when the text has none of them.
	public static List<String> figures(String text) {
		List<String> found = new ArrayList<>();
		if (text == null) {
			return found;
		}
		String body = text.replaceAll("\\s+", " ");
		List<String> card = hdfcCard(body);
		if (!card.isEmpty()) {
			return card;
		}
		for (String label : LABELS) {
			Matcher match = Pattern.compile(Pattern.quote(label) + "[^0-9₹]{0,40}?(?:Rs\\.?|INR|₹)?\\s*([\\d,]+\\.\\d{2})(?:\\s*(Cr|Dr)\\b)?", Pattern.CASE_INSENSITIVE).matcher(body);
			if (match.find()) {
				found.add(label + " ₹" + match.group(1) + (match.group(2) == null ? "" : " " + match.group(2).substring(0, 1).toUpperCase(Locale.ROOT) + match.group(2).substring(1).toLowerCase(Locale.ROOT)));
			}
		}
		return found;
	}
}
