package com.wealth.bss.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads one bank alert mail (its sender and text) into a transaction. Only the alert wordings below are known (HDFC UPI debit and credit card debit, slice debit,
// slice UPI and NEFT credit); anything else is not an alert and gives null. A balance is returned only when the alert itself states it (slice UPI credit: Avl. Bal.).
public final class BankAlertParser {

	public static final String EXPENSE = "expense";
	public static final String INCOME = "income";

	// kind is expense (money out) or income (money in); lastDigits are the last four of the account or card; date is yyyy-MM-dd; party is the payee, merchant or sender.
	public record Alert(String bank, String kind, BigDecimal amount, String lastDigits, String date, String party, BigDecimal balance) {
	}

	private static final String AMOUNT = "([\\d,]+(?:\\.\\d{1,2})?)";
	private static final int FLAGS = Pattern.CASE_INSENSITIVE;
	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_MON_YEAR = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
	private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH);

	private static final Pattern HDFC_UPI = Pattern.compile("Rs\\.?\\s?" + AMOUNT + " is debited from your account ending (\\d{4}) towards (.+?) on (\\d{2}-\\d{2}-\\d{2})\\b", FLAGS);
	private static final Pattern HDFC_CARD = Pattern.compile("Rs\\.?\\s?" + AMOUNT + " (?:is|has been) debited from your HDFC Bank Credit Card ending (\\d{4}) towards (.+?) on (\\d{1,2} [A-Za-z]{3}, \\d{4})", FLAGS);
	private static final Pattern SLICE_DEBIT = Pattern.compile("₹\\s?" + AMOUNT + " debited from your slice account xx(\\d{4}) via \\w+\\.? Transaction date (\\d{2}-[A-Za-z]{3}-\\d{2}) To (.+?) RRN", FLAGS);
	private static final Pattern SLICE_UPI_CREDIT = Pattern.compile("received ₹\\s?" + AMOUNT + " via UPI in your slice bank account xx(\\d{4})\\. Avl\\. Bal\\. ₹\\s?" + AMOUNT
			+ " Transaction date (\\d{2}-[A-Za-z]{3}-\\d{2}) From (.+?) RRN", FLAGS);
	private static final Pattern SLICE_NEFT_CREDIT = Pattern.compile("received ₹\\s?" + AMOUNT + " via NEFT in your slice bank a/c xx(\\d{4})!? Transaction Date (\\d{2}-[A-Za-z]{3}-\\d{2}) Sender Name (.+?) NEFT Ref", FLAGS);

	private BankAlertParser() {
	}

	// The senders whose mails are read at all, as the part of the From address that names them.
	public static final List<String> SENDERS = List.of("hdfcbank", "slice.bank.in");

	public static Alert parse(String from, String text) {
		if (from == null || text == null) {
			return null;
		}
		String sender = from.toLowerCase(Locale.ROOT);
		String body = text.replace('|', ' ').replaceAll("\\s+", " ");
		if (sender.contains("hdfcbank")) {
			return hdfc(body);
		}
		if (sender.contains("slice.bank.in")) {
			return slice(body);
		}
		return null;
	}

	private static Alert hdfc(String body) {
		Matcher upi = HDFC_UPI.matcher(body);
		if (upi.find()) {
			return build("HDFC", EXPENSE, upi.group(1), upi.group(2), day(upi.group(4), DAY_MONTH_YEAR), upi.group(3), null);
		}
		Matcher card = HDFC_CARD.matcher(body);
		if (card.find()) {
			return build("HDFC", EXPENSE, card.group(1), card.group(2), day(card.group(4), CARD_DATE), card.group(3), null);
		}
		return null;
	}

	private static Alert slice(String body) {
		Matcher debit = SLICE_DEBIT.matcher(body);
		if (debit.find()) {
			return build("slice", EXPENSE, debit.group(1), debit.group(2), day(debit.group(3), DAY_MON_YEAR), debit.group(4), null);
		}
		Matcher upi = SLICE_UPI_CREDIT.matcher(body);
		if (upi.find()) {
			return build("slice", INCOME, upi.group(1), upi.group(2), day(upi.group(4), DAY_MON_YEAR), upi.group(5), money(upi.group(3)));
		}
		Matcher neft = SLICE_NEFT_CREDIT.matcher(body);
		if (neft.find()) {
			return build("slice", INCOME, neft.group(1), neft.group(2), day(neft.group(3), DAY_MON_YEAR), neft.group(4), null);
		}
		return null;
	}

	private static Alert build(String bank, String kind, String amount, String lastDigits, String date, String party, BigDecimal balance) {
		BigDecimal value = money(amount);
		return date == null || value == null || value.signum() <= 0 ? null : new Alert(bank, kind, value, lastDigits, date, party.trim(), balance);
	}

	private static BigDecimal money(String text) {
		try {
			return new BigDecimal(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// yyyy-MM-dd, or null when the date does not read.
	private static String day(String text, DateTimeFormatter format) {
		try {
			return LocalDate.parse(text, format).toString();
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
