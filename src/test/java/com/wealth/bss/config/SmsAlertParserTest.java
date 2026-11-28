package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// The general SMS reader: the wording below is typical bank SMS wording, written from general knowledge. Real messages from the user's phone may differ; whatever the reader is not sure of goes to review.
class SmsAlertParserTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 28);

	@Test
	void upiDebitIsReadAndSure() {
		SmsAlertParser.Alert alert = SmsAlertParser.parse("VM-HDFCBK-S", "Sent Rs.250.00\nFrom HDFC Bank A/c *1234\nTo SWIGGY\nOn 28/11/26\nRef 633245678901\nNot You? Call 18002586161", DAY);
		assertNotNull(alert);
		assertEquals("HDFC", alert.bank());
		assertEquals(BankAlertParser.EXPENSE, alert.kind());
		assertEquals(new BigDecimal("250.00"), alert.amount());
		assertEquals("1234", alert.lastDigits());
		assertEquals("2026-11-28", alert.date());
		assertEquals("SWIGGY", alert.party());
		assertTrue(alert.sure());
	}

	@Test
	void upiCreditIsIncomeWithTheSender() {
		SmsAlertParser.Alert alert = SmsAlertParser.parse("AX-HDFCBK-S", "Credit Alert! Rs.5,000.00 credited to HDFC Bank A/c XX1234 on 27-11-26 from VPA friend@oksbi (UPI 633245678902)", DAY);
		assertNotNull(alert);
		assertEquals(BankAlertParser.INCOME, alert.kind());
		assertEquals(new BigDecimal("5000.00"), alert.amount());
		assertEquals("1234", alert.lastDigits());
		assertEquals("2026-11-27", alert.date());
		assertEquals("friend@oksbi", alert.party());
		assertTrue(alert.sure());
	}

	@Test
	void cardSpendWithAnIsoDate() {
		SmsAlertParser.Alert alert = SmsAlertParser.parse("HDFCBK", "Rs.1,200.00 spent on HDFC Bank Card x4321 at AMAZON on 2026-11-28:10:15:20. Avl Lmt Rs.98,000.00", DAY);
		assertNotNull(alert);
		assertEquals(BankAlertParser.EXPENSE, alert.kind());
		assertEquals(new BigDecimal("1200.00"), alert.amount());
		assertEquals("4321", alert.lastDigits());
		assertEquals("2026-11-28", alert.date());
		assertEquals("AMAZON", alert.party());
		assertTrue(alert.sure());
	}

	@Test
	void sliceWithAMonthNameDate() {
		SmsAlertParser.Alert alert = SmsAlertParser.parse("AX-SLICEB-S", "You spent ₹450 at ZOMATO using your slice card xx1234 on 28-Nov-26.", DAY);
		assertNotNull(alert);
		assertEquals("slice", alert.bank());
		assertEquals(new BigDecimal("450"), alert.amount());
		assertEquals("1234", alert.lastDigits());
		assertEquals("2026-11-28", alert.date());
		assertEquals("ZOMATO", alert.party());
	}

	@Test
	void aMissingDetailMakesItUnsureNotLost() {
		// no last digits and no date in the text: still a transaction, dated the day it arrived, but held for review
		SmsAlertParser.Alert alert = SmsAlertParser.parse("VM-HDFCBK-S", "Rs.99.00 debited via UPI to TEA STALL", DAY);
		assertNotNull(alert);
		assertEquals("", alert.lastDigits());
		assertEquals("2026-11-28", alert.date());
		assertFalse(alert.sure());
	}

	@Test
	void debitAndCreditWordsTogetherAreUnsure() {
		SmsAlertParser.Alert alert = SmsAlertParser.parse("VM-HDFCBK-S", "Rs.500.00 debited from A/c XX1234 on 28-11-26 and credited to A/c XX9999 to RAVI", DAY);
		assertNotNull(alert);
		assertFalse(alert.sure());
	}

	@Test
	void notTransactionsAreDropped() {
		assertNull(SmsAlertParser.parse("VM-HDFCBK-S", "123456 is your OTP for Rs.500 txn at AMAZON. Do not share it.", DAY));
		assertNull(SmsAlertParser.parse("VM-HDFCBK-S", "Rs.500.00 will be debited from your A/c XX1234 on 29-11-26 for e-mandate NETFLIX", DAY));
		assertNull(SmsAlertParser.parse("VM-HDFCBK-S", "Total payment due Rs.12,000 is due on 05-12-26. Pay now", DAY));
		assertNull(SmsAlertParser.parse("VM-HDFCBK-S", "Get a pre-approved loan of Rs.5,00,000 today", DAY));
	}

	@Test
	void otherSendersAreNotRead() {
		assertNull(SmsAlertParser.parse("VK-AMAZON", "Rs.500.00 debited from A/c XX1234 on 28-11-26 to SWIGGY", DAY));
		assertNull(SmsAlertParser.parse(null, "Rs.500.00 debited", DAY));
	}

	@Test
	void aDateOutsideTheLastYearIsNotUsed() {
		SmsAlertParser.Alert future = SmsAlertParser.parse("VM-HDFCBK-S", "Sent Rs.10.00 From HDFC Bank A/c *1234 To SHOP On 30/12/26", DAY);
		assertNotNull(future);
		assertEquals("2026-11-28", future.date());
		assertFalse(future.sure());
	}
}
