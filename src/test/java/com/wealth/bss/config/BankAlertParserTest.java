package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.wealth.bss.config.BankAlertParser.Alert;

// Made-up alert text in the wording of each known alert; no real mail, name or number is used.
class BankAlertParserTest {

	private static final String HDFC = "alerts@hdfcbank.bank.in";
	private static final String SLICE = "noreply@slice.bank.in";

	@Test
	void anHdfcUpiDebitGivesTheAccountAmountDateAndPayee() {
		Alert alert = BankAlertParser.parse(HDFC, " HDFC BANK | Dear Customer, Greetings from HDFC Bank! Rs.1,00,000.00 is debited from your account ending 1111 towards VPA shop@bank (Mr TEST USER) on 03-11-26. UPI transaction reference no.: 123456789012. If you did not authorize...");
		assertEquals("HDFC", alert.bank());
		assertEquals("expense", alert.kind());
		assertEquals(new BigDecimal("100000.00"), alert.amount());
		assertEquals("1111", alert.lastDigits());
		assertEquals("2026-11-03", alert.date());
		assertEquals("VPA shop@bank (Mr TEST USER)", alert.party());
		assertNull(alert.balance());
	}

	@Test
	void anHdfcCreditCardDebitInBothWordings() {
		Alert older = BankAlertParser.parse(HDFC, "Greetings from HDFC Bank! Rs.489.00 is debited from your HDFC Bank Credit Card ending 2222 towards CAS*Shop on 14 Apr, 2026 at 12:57:21. To know your available balance");
		assertEquals(new BigDecimal("489.00"), older.amount());
		assertEquals("2222", older.lastDigits());
		assertEquals("2026-04-14", older.date());
		assertEquals("CAS*Shop", older.party());

		Alert newer = BankAlertParser.parse(HDFC, "We would like to inform you that Rs. 99.65 has been debited from your HDFC Bank Credit Card ending 2222 towards SOME STORE on 25 Nov, 2026 at 00:24:18. To check your available balance");
		assertEquals(new BigDecimal("99.65"), newer.amount());
		assertEquals("2026-11-25", newer.date());
		assertEquals("SOME STORE", newer.party());
		assertEquals("expense", newer.kind());
	}

	@Test
	void aSliceDebitGivesThePayeeAndAReference() {
		Alert alert = BankAlertParser.parse(SLICE, "| Hi TEST, | | ₹200 debited from your slice account xx3333 via UPI. | | Transaction date | 22-Nov-26 | | To | SOME PAYEE | | RRN | 111111 | Best, Team slice");
		assertEquals("slice", alert.bank());
		assertEquals("expense", alert.kind());
		assertEquals(new BigDecimal("200"), alert.amount());
		assertEquals("3333", alert.lastDigits());
		assertEquals("2026-11-22", alert.date());
		assertEquals("SOME PAYEE", alert.party());
		assertNull(alert.balance());
	}

	@Test
	void aSliceUpiCreditStatesTheBalanceAndANeftCreditDoesNot() {
		Alert upi = BankAlertParser.parse(SLICE, "Hi TEST, You have received ₹300 via UPI in your slice bank account xx3333. Avl. Bal. ₹40,535.49 Transaction date 18-Nov-26 From Ms Someone RRN 222");
		assertEquals("income", upi.kind());
		assertEquals(new BigDecimal("300"), upi.amount());
		assertEquals(new BigDecimal("40535.49"), upi.balance());
		assertEquals("Ms Someone", upi.party());

		Alert neft = BankAlertParser.parse(SLICE, "Hi TEST, You have received ₹1 via NEFT in your slice bank a/c xx3333! Transaction Date 23-Nov-26 Sender Name SOME LIMITED NEFT Ref No. ABC123 Reach out to");
		assertEquals("income", neft.kind());
		assertEquals("2026-11-23", neft.date());
		assertEquals("SOME LIMITED", neft.party());
		assertNull(neft.balance());
	}

	@Test
	void anythingThatIsNotAKnownAlertGivesNull() {
		assertNull(BankAlertParser.parse(HDFC, "Your OTP for the transaction is 123456. Do not share it."));
		assertNull(BankAlertParser.parse(HDFC, "Missed call from your Relationship Manager"));
		assertNull(BankAlertParser.parse(SLICE, "Your slice credit card statement for November is ready"));
		assertNull(BankAlertParser.parse("promo@somebank.example", "Rs.500.00 is debited from your account ending 1111 towards X on 03-11-26. UPI"));
		assertNull(BankAlertParser.parse(HDFC, "Rs.500.00 is debited from your account ending 1111 towards X on 31-31-26. UPI"), "an impossible date is not guessed");
		assertNull(BankAlertParser.parse(HDFC, "Rs.0.00 is debited from your account ending 1111 towards X on 03-11-26. UPI"), "a zero amount is not a transaction");
		assertNull(BankAlertParser.parse(null, "text"));
		assertNull(BankAlertParser.parse(HDFC, null));
	}
}
