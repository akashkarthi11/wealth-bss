package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

// PDFs are made in the test from made-up text (nothing real); a password-protected one is made the way a bank statement is.
public class StatementReaderTest {

	// A one-page PDF with the given lines; userPassword null leaves it open.
	public static byte[] pdf(List<String> lines, String userPassword) throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.setLeading(16);
				content.newLineAtOffset(50, 700);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			if (userPassword != null) {
				StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-only-secret", userPassword, new AccessPermission());
				policy.setEncryptionKeyLength(128);
				document.protect(policy);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

	@Test
	void redactionHidesNameCardNumbersPanAndEmailButKeepsMerchantsDatesAndAmounts() {
		String text = "KARTHIKEYAN'S Credit card statement\nCard 5280123412341234 PAN ABCDE1234F mail karthi@example.com\nGoogle ₹1,149.00 19 Sep '26 • UPI\nTotal 12,345.67";
		String safe = StatementReader.redact(text, "KART");
		assertFalse(safe.contains("KARTHIKEYAN") || safe.contains("5280123412341234") || safe.contains("ABCDE1234F") || safe.contains("karthi@example.com"));
		assertTrue(safe.contains("Google ₹1,149.00 19 Sep '26") && safe.contains("12,345.67"));
		assertEquals(text.replace("KARTHIKEYAN", "[name]").replace("5280123412341234", "[number]").replace("ABCDE1234F", "[pan]").replace("karthi@example.com", "[email]"), safe);
	}

	@Test
	void theAiAnswerIsKeptOnlyWhereEveryFieldIsValidAndEveryRowIsNotSure() {
		String json = "```json\n{\"rows\":[{\"day\":\"2026-09-19\",\"description\":\" Google  Pay \",\"amount\":149,\"direction\":\"debit\"},"
				+ "{\"day\":\"2026-02-30\",\"description\":\"Impossible day\",\"amount\":1,\"direction\":\"debit\"},"
				+ "{\"day\":\"2026-09-19\",\"description\":\"Negative\",\"amount\":-5,\"direction\":\"debit\"},"
				+ "{\"day\":\"2026-09-19\",\"description\":\"Three decimals\",\"amount\":1.234,\"direction\":\"debit\"},"
				+ "{\"day\":\"2026-09-19\",\"description\":\"Odd direction\",\"amount\":5,\"direction\":\"sideways\"},"
				+ "{\"day\":\"2999-01-01\",\"description\":\"Future\",\"amount\":5,\"direction\":\"credit\"},"
				+ "{\"day\":\"2026-09-18\",\"description\":\"Cashback\",\"amount\":\"1,000.50\",\"direction\":\"CREDIT\"}]}\n```";
		var rows = StatementReader.aiLines(json, 500, 300);
		assertEquals(2, rows.size());
		assertEquals("Google Pay", rows.get(0).description());
		assertEquals(new java.math.BigDecimal("149.00"), rows.get(0).amount());
		assertTrue(rows.get(0).debit() && !rows.get(1).debit());
		assertEquals(new java.math.BigDecimal("1000.50"), rows.get(1).amount());
		assertTrue(rows.stream().noneMatch(StatementReader.Line::sure));
		assertEquals(1, StatementReader.aiLines(json, 1, 300).size(), "at most max rows");
		assertTrue(StatementReader.aiLines("not json", 500, 300).isEmpty());
		assertTrue(StatementReader.aiLines("{\"rows\":\"x\"}", 500, 300).isEmpty());
		assertTrue(StatementReader.aiLines(null, 500, 300).isEmpty());
	}

	@Test
	void anOpenPdfIsReadWithoutAPasswordAndALockedOneOnlyWithTheRightOne() throws IOException {
		byte[] open = pdf(List.of("Statement of account", "Closing Balance Rs. 40,000.00 Cr"), null);
		assertTrue(StatementReader.text(open, List.of()).contains("Closing Balance"));

		byte[] locked = pdf(List.of("Statement of account", "Closing Balance Rs. 40,000.00 Cr"), "01011990");
		assertNull(StatementReader.text(locked, List.of()), "no password: stays locked");
		assertNull(StatementReader.text(locked, List.of("ABCDE1234F", "02021991")), "wrong passwords: stays locked");
		assertTrue(StatementReader.text(locked, List.of("ABCDE1234F", "01011990")).contains("40,000.00"), "the second one opens it");
	}

	@Test
	void aFileThatIsNotAPdfOrIsTooBigOrEmptyIsNotOpened() {
		assertNull(StatementReader.text("not a pdf".getBytes(), List.of("01011990")));
		assertNull(StatementReader.text(new byte[0], List.of()));
		assertNull(StatementReader.text(null, List.of()));
		assertNull(StatementReader.text(new byte[StatementReader.MAX_BYTES + 1], List.of()));
	}

	@Test
	void theLabelledFiguresAreReadInLabelOrderWithCreditOrDebit() {
		List<String> figures = StatementReader.figures(
				"Opening Balance Rs. 1,000.00 Dr   Closing Balance : INR 40,535.49 Cr   Total Amount Due ₹ 50,664.94  Minimum Amount Due: Rs.2,533.25  Credit Limit 1,00,000.00");
		assertEquals(List.of("Closing Balance ₹40,535.49 Cr", "Opening Balance ₹1,000.00 Dr", "Total Amount Due ₹50,664.94", "Minimum Amount Due ₹2,533.25", "Credit Limit ₹1,00,000.00"), figures);
	}

	@Test
	void anHdfcCardStatementTableIsReadFromItsRows() {
		String text = "Statement Date 22 Nov, 2026\nPREVIOUS STATEMENT DUES FINANCE CHARGES TOTAL AMOUNT DUE\nRECEIVED (Current Billing Cycle)\n_\nC1,234.56 C1,234.56 + C100.00 + C0.00 = C250.75\n"
				+ "TOTAL CREDIT LIMIT\nAVAILABLE CREDIT LIMIT AVAILABLE CASH LIMIT\n(Including Cash) MINIMUM DUE DUE DATE\nC50.00 12 Dec, 2026\nC30,000 C20,000 C10,000\nPast Dues OVER LIMIT";
		assertEquals(List.of("Total Amount Due ₹250.75", "Minimum Due ₹50.00", "Due Date 12 Dec, 2026", "Total Credit Limit ₹30,000", "Available Credit Limit ₹20,000"), StatementReader.figures(text));
	}

	@Test
	void theFiguresAreAlsoGivenAsLabelToValueInOrder() {
		assertEquals("{Closing Balance=₹40,535.49 Cr, Total Amount Due=₹50,664.94, Minimum Amount Due=₹2,533.25}",
				StatementReader.figureMap("Closing Balance : INR 40,535.49 Cr   Total Amount Due ₹ 50,664.94  Minimum Amount Due: Rs.2,533.25").toString());
		assertEquals("{Total Amount Due=₹250.75, Minimum Due=₹50.00, Due Date=12 Dec, 2026, Total Credit Limit=₹30,000, Available Credit Limit=₹20,000}",
				StatementReader.figureMap("PREVIOUS STATEMENT DUES FINANCE CHARGES TOTAL AMOUNT DUE RECEIVED _ C1.00 C1.00 + C1.00 + C0.00 = C250.75 TOTAL CREDIT LIMIT AVAILABLE CREDIT LIMIT AVAILABLE CASH LIMIT MINIMUM DUE DUE DATE C50.00 12 Dec, 2026 C30,000 C20,000 C10,000").toString());
		assertTrue(StatementReader.figureMap("nothing").isEmpty());
	}

	@Test
	void everyHdfcCardRowIsReadWithItsDirection() {
		String text = "Domestic Transactions\nDATE & TIME TRANSACTION DESCRIPTION REWARDS AMOUNT PI\n23/10/2026| 00:00 5% Swiggy ORNGE CB + C 97.15 l\n"
				+ "24/10/2026| 10:09 BPPY PYMT DP016297100901Ksgi64 (Ref# 938056864672993) + C 6,811.00 l\n15/11/2026| 13:17 SWIGGY E COMBANGALORE C 399.00 l\n"
				+ "20/11/2026| 11:36 PAY*SWIGGY FOODBANGALORE C 1,577.50 l\nSomething else without a date";
		var lines = StatementReader.lines(text);
		assertEquals(4, lines.size());
		assertEquals("2026-10-23", lines.get(0).day());
		assertEquals("00:00", lines.get(0).time());
		assertEquals("10:09", lines.get(1).time(), "the time printed on the row is kept");
		assertEquals("5% Swiggy ORNGE CB", lines.get(0).description());
		assertEquals(new BigDecimal("97.15"), lines.get(0).amount());
		assertTrue(!lines.get(0).debit() && lines.get(0).sure(), "a + row is money in");
		assertEquals(new BigDecimal("6811.00"), lines.get(1).amount());
		assertTrue(lines.get(2).debit(), "a row without + is money out");
		assertEquals("SWIGGY E COMBANGALORE", lines.get(2).description());
		assertEquals(new BigDecimal("1577.50"), lines.get(3).amount());
	}

	@Test
	void anotherLayoutIsReadByTheGeneralRuleAndNotMarkedSure() {
		var lines = StatementReader.lines("Txn Date Description Amount\n05-Nov-2026 UPI/PAYEE ONE/123 250.00 Dr\n06/11/2026 SALARY CREDIT 10,000.00 Cr\n07 Nov, 2026 CARD PURCHASE 99.50\n31-31-2026 BAD DATE 5.00 Dr");
		assertEquals(3, lines.size());
		assertEquals("2026-11-05", lines.get(0).day());
		assertTrue(lines.get(0).debit() && !lines.get(0).sure());
		assertTrue(!lines.get(1).debit() && lines.get(1).amount().compareTo(new BigDecimal("10000.00")) == 0);
		assertTrue(lines.get(2).debit(), "no Cr or Dr means money out, marked not sure");
		assertTrue(StatementReader.lines(null).isEmpty() && StatementReader.lines("nothing").isEmpty());
	}

	@Test
	void theStatementMailSearchIsWellFormedAndNamesEverySender() {
		String query = com.wealth.bss.constants.WealthServiceConstants.GMAIL_STATEMENT_QUERY.replace(com.wealth.bss.constants.WealthServiceConstants.GMAIL_DAYS_TOKEN, "180");
		assertTrue(query.contains("newer_than:180d "), "the days go into the search");
		assertTrue(java.util.regex.Pattern.compile("\\sOR\\sfrom:").matcher(query).find(), "senders are joined with spaces");
		assertTrue(!java.util.regex.Pattern.compile("\\SOR\\s").matcher(query).find(), "no OR is glued to the address before it");
		assertEquals(query.chars().filter(c -> c == '(').count(), query.chars().filter(c -> c == ')').count());
		for (String sender : new String[] { "noreply@slice.bank.in", "roar@unitybank.co.in", "customercommunication@unitybank.co.in", "Emailstatements.cards@hdfcbank.bank.in",
				"noreply-estatement@alerts.bankofindia.bank.in" }) {
			assertTrue(query.contains("from:" + sender + " ") || query.contains("from:" + sender + ")"), sender);
		}
		assertEquals(5, query.split("from:", -1).length - 1, "five senders");
	}

	@Test
	void textWithoutAnyKnownLabelGivesNothing() {
		assertTrue(StatementReader.figures("Nothing here but 1,000.00 and words").isEmpty());
		assertTrue(StatementReader.figures(null).isEmpty());
		assertNotNull(StatementReader.figures(""));
	}
}
