package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.people.BillSplitDto;
import com.wealth.common.model.dto.people.PeopleOverviewDto;
import com.wealth.common.model.dto.people.SplitCreateRequestDto;

// Money lent and split bills of the signed-in user (user id from the token context). Read only.
public interface IfPeopleService {

	// Every person (not removed) with what they owe and the due date, their entries and shares, round-offs, the split bills, the pending "I've paid" requests and
	// suggested transfers to link. Read from the nexus cache and refreshed when older than the refresh time.
	PeopleOverviewDto list();

	// The Refresh button: asks for a fresh read at once (at most every few seconds per user) and returns what is cached now; fetchedAt tells when it was read.
	PeopleOverviewDto refresh();

	// Splits one of the caller's own bills (an expense, an investment or a transfer sent) with other people, so the transaction and the split are linked. The total is the transaction's amount;
	// tax is the GST inside it. Returns the split as stored (shares left empty). Refused when the bill is not theirs, is linked to a person, is already split, or the amounts do not add up.
	BillSplitDto createSplit(SplitCreateRequestDto request);

	// Cancels one of the caller's splits: it is kept for the record and no longer counted, and its bill can be split again. False when nothing changed (not theirs, or already cancelled).
	Boolean cancelSplit(IdRequestDto request);
}
