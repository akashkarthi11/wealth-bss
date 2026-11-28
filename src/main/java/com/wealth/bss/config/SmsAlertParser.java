package com.wealth.bss.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads one bank SMS (its sender name and text) into a transaction. Banks are told apart by the sender name; the wording is read by general rules (amount, debit or credit word, last four
// digits, date, payee), not by one fixed text. Anything that is not clearly a money movement (an OTP, a reminder, a mandate, an offer) gives null. A message that is a transaction but is missing a
// detail (no last digits, no date in the text, both debit and credit words, no payee) comes back with sure = false, so the import sends it to review instead of saving it at once.
public final class SmsAlertParser {

	public record Alert(String bank, String kind, BigDecimal amount, String lastDigits, String date, String party, boolean sure) {
	}

	// The sender name (VM-HDFCBK-S, AX-SLICEB-S, HDFCBK ...) holds one of these letters; the right side is the bank name used on accounts.
	private static final List<String[]> BANKS = List.of(new String[] { "HDFC", "HDFC" }, new String[] { "SLICE", "slice" });

	private static final Pattern NOT_A_TRANSACTION = Pattern.compile("\\botp\\b|one[- ]time|verification|will be debited|has been requested|requested|e-?mandate|auto-?pay|reminder|payment due|is due|due (?:on|by|date)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern AMOUNT = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CREDIT = Pattern.compile("credited|received|deposited|refund", Pattern.CASE_INSENSITIVE);
	private static final Pattern DEBIT = Pattern.compile("debited|spent|\\bsent\\b|withdrawn|\\bpaid\\b|purchase", Pattern.CASE_INSENSITIVE);
	private static final Pattern DIGITS = Pattern.compile("(?:a/c|acct|account|card)(?:\\s*(?:no\\.?|number))?(?:\\s*ending(?:\\s*(?:with|in))?)?\\s*[:\\-]?\\s*(?:[xX*•]*)(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern DAY_FIRST = Pattern.compile("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4}|\\d{2})\\b");
	private static final Pattern DAY_MONTH_NAME = Pattern.compile("\\b(\\d{1,2})[- ]([A-Za-z]{3})[- ,]*(\\d{4}|\\d{2})\\b");
	private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
	private static final String STOP = "(?=\\s+(?:on|ref|upi|via|using|txn|avl|bal|not you|if not|call|info)\\b|\\s*[.(\\n]|\\s+\\d{1,2}[-/]|$)";
	private static final Pattern PARTY_DEBIT = Pattern.compile("\\b(?:to|at|towards)\\s+(?:VPA\\s+)?(.{2,80}?)" + STOP, Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_CREDIT = Pattern.compile("\\b(?:from|by)\\s+(?:VPA\\s+)?(.{2,80}?)" + STOP, Pattern.CASE_INSENSITIVE);
	private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

	private SmsAlertParser() {
	}

	// The bank name for a sender, or null when the sender is not one we read.
	public static String bankOf(String sender) {
		if (sender == null) {
			return null;
		}
		String upper = sender.toUpperCase(Locale.ROOT);
		for (String[] bank : BANKS) {
			if (upper.contains(bank[0])) {
				return bank[1];
			}
		}
		return null;
	}

	// received is the day the SMS arrived: the date to use when the text has none, and what a date in the text is checked against.
	public static Alert parse(String sender, String text, LocalDate received) {
		String bank = bankOf(sender);
		if (bank == null || text == null || received == null) {
			return null;
		}
		String body = text.replace('|', ' ').replaceAll("\\s+", " ").trim();
		if (NOT_A_TRANSACTION.matcher(body).find()) {
			return null;
		}
		Matcher amount = AMOUNT.matcher(body);
		boolean credit = CREDIT.matcher(body).find();
		boolean debit = DEBIT.matcher(body).find();
		if (!amount.find() || (!credit && !debit)) {
			return null;
		}
		BigDecimal value = money(amount.group(1));
		if (value == null || value.signum() <= 0) {
			return null;
		}
		boolean both = credit && debit;
		boolean income = credit && (!debit || indexOf(CREDIT, body) < indexOf(DEBIT, body));
		Matcher digits = DIGITS.matcher(body);
		String last = digits.find() ? digits.group(1) : "";
		String day = dayIn(body, received);
		String party = party(body, income);
		boolean sure = !both && !last.isEmpty() && day != null && party != null;
		return new Alert(bank, income ? BankAlertParser.INCOME : BankAlertParser.EXPENSE, value, last, day == null ? received.toString() : day, party == null ? "" : party, sure);
	}

	private static int indexOf(Pattern pattern, String body) {
		Matcher matcher = pattern.matcher(body);
		return matcher.find() ? matcher.start() : Integer.MAX_VALUE;
	}

	private static String party(String body, boolean income) {
		Matcher matcher = (income ? PARTY_CREDIT : PARTY_DEBIT).matcher(body);
		if (!matcher.find()) {
			return null;
		}
		String name = matcher.group(1).trim();
		return name.isEmpty() ? null : name;
	}

	// The first date in the text that is a real day no more than a year before the SMS arrived and not after it; null when there is none.
	private static String dayIn(String body, LocalDate received) {
		Matcher iso = ISO.matcher(body);
		if (iso.find()) {
			String day = valid(iso.group(1), iso.group(2), iso.group(3), received);
			if (day != null) {
				return day;
			}
		}
		Matcher first = DAY_FIRST.matcher(body);
		while (first.find()) {
			String day = valid(year(first.group(3)), first.group(2), first.group(1), received);
			if (day != null) {
				return day;
			}
		}
		Matcher name = DAY_MONTH_NAME.matcher(body);
		while (name.find()) {
			try {
				String text = Integer.parseInt(name.group(1)) + " " + capital(name.group(2)) + " " + year(name.group(3));
				String day = valid(LocalDate.parse(text, MONTH_NAME), received);
				if (day != null) {
					return day;
				}
			} catch (DateTimeParseException | NumberFormatException e) {
				// not a date: look at the next one
			}
		}
		return null;
	}

	private static String year(String text) {
		return text.length() == 2 ? "20" + text : text;
	}

	private static String capital(String word) {
		return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String valid(String year, String month, String day, LocalDate received) {
		try {
			return valid(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)), received);
		} catch (java.time.DateTimeException | NumberFormatException e) {
			return null;
		}
	}

	private static String valid(LocalDate date, LocalDate received) {
		return date.isAfter(received) || date.isBefore(received.minusYears(1)) ? null : date.toString();
	}

	private static BigDecimal money(String text) {
		try {
			return new BigDecimal(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
