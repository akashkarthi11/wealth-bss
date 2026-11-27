package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;

// Stores bank transactions read from mail alerts or statements for the signed-in user, without duplicates.
public interface IfBankImportService {

	// Each line: already imported before = skipped; the same payment as a transaction that is there (same account or unplaced, same amount and direction, within a day, alike in text) = skipped;
	// it looks like a duplicate (several candidates, or text very different), fake or unsure = sent to review (the workflow table) and not stored; otherwise stored as an accepted
	// transaction at once. source is alert or statement. TRANSACTION_INVALID for a line with no reference, day or amount.
	BankImportResultDto importLines(String source, List<BankLineDto> lines);
}
