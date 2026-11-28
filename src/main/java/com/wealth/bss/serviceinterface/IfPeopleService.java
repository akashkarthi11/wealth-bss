package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.people.BillSplitDto;
import com.wealth.common.model.dto.people.PeopleOverviewDto;
import com.wealth.common.model.dto.people.CommentCreateRequestDto;
import com.wealth.common.model.dto.people.CommentCreateResultDto;
import com.wealth.common.model.dto.people.EntryNoteRequestDto;
import com.wealth.common.model.dto.people.EntryNoteResultDto;
import com.wealth.common.model.dto.people.RequestDecideRequestDto;
import com.wealth.common.model.dto.people.RequestDecideResultDto;
import com.wealth.common.model.dto.people.LendPaidRequestDto;
import com.wealth.common.model.dto.people.LendPaidResultDto;
import com.wealth.common.model.dto.people.LendRemarkRequestDto;
import com.wealth.common.model.dto.people.LendRemarkResultDto;
import com.wealth.common.model.dto.people.LendViewRequestRowDto;
import com.wealth.common.model.dto.people.PersonMergeRequestDto;
import com.wealth.common.model.dto.people.PersonMergeResultDto;
import com.wealth.common.model.dto.people.LendViewDto;
import com.wealth.common.model.dto.people.LendViewItemDto;
import com.wealth.common.model.dto.people.LendViewRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateResultDto;
import com.wealth.common.model.dto.people.PersonEntryRequestDto;
import com.wealth.common.model.dto.people.PersonEntryResultDto;
import com.wealth.common.model.dto.people.PersonSaveRequestDto;
import com.wealth.common.model.dto.people.PersonSaveResultDto;
import com.wealth.common.model.dto.people.RecordStatusRequestDto;
import com.wealth.common.model.dto.people.RecordStatusResultDto;
import com.wealth.common.model.dto.people.RoundOffRequestDto;
import com.wealth.common.model.dto.people.RoundOffResultDto;
import com.wealth.common.model.dto.people.SharePaidRequestDto;
import com.wealth.common.model.dto.people.SharePaidResultDto;
import com.wealth.common.model.dto.people.SplitCreateRequestDto;

// Money lent and split bills of the signed-in user (user id from the token context).
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

	// Adds money lent to or paid back by one person: links transfers (transactionIds) or enters an amount by hand. A new name adds the person. dryRun returns what would be stored and stores nothing.
	// Refused when a transfer is not the caller's, is not a transfer, or is already linked or split.
	PersonEntryResultDto createEntries(PersonEntryRequestDto request);

	// Marks one person's share of a split bill paid back. Already paid stores nothing; refused when the share is closed by a round-off.
	SharePaidResultDto markSharePaid(SharePaidRequestDto request);

	// Closes everything open with a person into one round-off and carries one new amount forward. Refused when nothing is open or an amount is below 0.
	RoundOffResultDto roundOff(RoundOffRequestDto request);

	// Writes off, undoes or deletes one record: a line (entryId) can be active, written_off or removed; a bill share (splitId + personId) can be active or written_off (cancel the split to undo it).
	// Nothing closed by a round-off can change; the same status stores nothing. A deleted line is hidden and not counted, and its transfer can be linked again.
	RecordStatusResultDto updateRecord(RecordStatusRequestDto request);

	// Adds a person (no personId: a name is needed and must not be taken) or changes one: only the fields sent change. The yearly interest rate is a percentage (12 is 12 %), worked out daily on what is owed.
	// A person can be removed (status removed) only when they have no lines or shares left. dryRun stores nothing.
	PersonSaveResultDto savePerson(PersonSaveRequestDto request);

	// Turns a person's share link on or off. On: a new random secret replaces any old link, only its hash is kept, and the link (/l/<secret>) is returned this once. Off: the link stops working.
	ShareUpdateResultDto updateShare(ShareUpdateRequestDto request);

	// The public page: one person's own summary and lines from their link's secret, with no sign-in. Every call counts against the caller's address; an unknown or turned-off link gets one generic refusal.
	LendViewDto lendView(LendViewRequestDto request);

	// The same person entered twice: every line, share, request, round-off and remark of fromId moves to intoId, fromId's name becomes another name of intoId, and fromId is removed. Nothing is lost.
	// Refused when the two are the same, either is not the caller's, or both are in the same active split bill (cancel and redo that split first).
	PersonMergeResultDto mergePeople(PersonMergeRequestDto request);

	// The person says they paid, from the public page: a message waits for the owner (at most 3 at a time, 10 an hour from one address). Nothing is added to the account until the owner accepts it.
	LendPaidResultDto lendPaid(LendPaidRequestDto request);

	// The person writes a remark on one of their own lines or bill shares, from the public page (20 a day per person, 30 an hour from one address). The owner sees it and can answer.
	LendRemarkResultDto lendRemark(LendRemarkRequestDto request);

	// The owner answers a remark on a line or a bill share; the person sees it on their page.
	CommentCreateResultDto createComment(CommentCreateRequestDto request);

	// The owner's description of a line (empty clears it, 200 characters); shown on the People page and on the person's own page.
	EntryNoteResultDto updateEntryNote(EntryNoteRequestDto request);

	// Accepts or declines a waiting "I've paid" message. Accepting adds the paid-back line in cash (the amount and the day may be changed) and settles the person when it covers what is owed;
	// it never touches a bank account. A message that was decided already is refused.
	RequestDecideResultDto decideRequest(RequestDecideRequestDto request);
}
