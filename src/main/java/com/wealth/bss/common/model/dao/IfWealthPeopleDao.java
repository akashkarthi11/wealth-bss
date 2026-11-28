package com.wealth.bss.common.model.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthLendCommentEntity;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonAliasEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;

// Reads for the People list. userId (in the PaginationDto) is the caller; every method returns that user's own rows only.
public interface IfWealthPeopleDao {

	// People that are not removed, by name.
	List<WealthPersonEntity> findPeople(PaginationDto paginationDto);

	// Entries that are not removed, newest first, each with its linked transfer's description and account.
	List<WealthPersonEntryEntity> findEntries(PaginationDto paginationDto);

	// Shares of active splits (a share with no person is the owner's own part).
	List<WealthBillSplitShareEntity> findShares(PaginationDto paginationDto);

	// Active splits, newest first.
	List<WealthBillSplitEntity> findSplits(PaginationDto paginationDto);

	// Round-offs, newest first.
	List<WealthPersonRollupEntity> findRollups(PaginationDto paginationDto);

	// Pending "I've paid" requests, newest first, at most 50.
	List<WealthLendRequestEntity> findPendingRequests(PaginationDto paginationDto);

	// Other names people go by.
	List<WealthPersonAliasEntity> findAliases(PaginationDto paginationDto);

	// Remarks on entries and shares, oldest first.
	List<WealthLendCommentEntity> findComments(PaginationDto paginationDto);

	// Transfers sent or received that no entry is linked to yet, oldest first.
	List<WealthTransactionEntity> findUnlinkedTransfers(PaginationDto paginationDto);

	// The caller's transaction with what a split needs (kind, category, description, amount, day); splitStatus is "active" when it already has a split. Null when it is not theirs.
	WealthTransactionEntity findTransactionForSplit(String userId, String transactionId);

	// True when a person's entry is linked to the transaction.
	boolean transactionLinkedToPerson(String userId, String transactionId);

	// Marks the caller's active split cancelled (kept for the record, no longer counted); false when it is not theirs or already cancelled.
	boolean cancelSplit(String userId, String splitId);

	// In one transaction: finds or adds each person (a removed one is brought back), adds the split and its shares, and re-opens settled people. shares[i] belongs to people[i];
	// mine is the owner's own part (null when the owner is not in the split). Returns the new split id. Throws when a person is not the caller's, is listed twice, or the transaction is already split.
	String createSplit(String userId, WealthBillSplitEntity split, List<WealthPersonEntity> people, List<WealthBillSplitShareEntity> shares, WealthBillSplitShareEntity mine);

	// The caller's transactions by id with what an entry needs (kind, category, description, amount, day). splitStatus is "linked" when an entry already uses the transaction, "active" when it is split, else null.
	List<WealthTransactionEntity> findTransfersForEntries(String userId, List<String> transactionIds);

	// One of the caller's people by id, or null when it is not theirs.
	WealthPersonEntity findPerson(String userId, String personId);

	// In one transaction: finds or adds the person (a removed one comes back), stores the entries, re-opens a settled person who is lent to again and keeps each alias as another name.
	// Throws when an entry's transaction is already linked. Returns the person's id.
	String addEntries(String userId, WealthPersonEntity person, List<WealthPersonEntryEntity> entries, List<String> aliases);

	// One person's share of one of the caller's active splits, or null.
	WealthBillSplitShareEntity findShareToPay(String userId, String splitId, String personId);

	// True when the person already has a paid-back entry for that split.
	boolean sharePaid(String personId, String splitId);

	// Adds the paid-back entry for a share; false when it was stored by someone else a moment ago.
	boolean addSharePaid(String userId, String personId, String splitId, BigDecimal amount, String on, String note);

	// Closes every counted open line of the caller's person into the round-off record and carries newAmount forward, in one transaction. Sets the new record's id on rollup and returns how many lines it closed.
	// Throws when the person is not the caller's.
	int roundOff(String userId, WealthPersonRollupEntity rollup, String entryNote);

	// One of the caller's lines (id, status, rollupId), or null when it is not theirs.
	WealthPersonEntryEntity findEntryForStatus(String userId, String entryId);

	// Sets the status of one of the caller's lines (active, written_off or removed); false when it is not theirs.
	boolean setEntryStatus(String userId, String entryId, String status);

	// One person's share of one of the caller's active splits (id, status, rollupId), or null.
	WealthBillSplitShareEntity findShareForStatus(String userId, String splitId, String personId);

	// Sets the status of one of the caller's shares (active or written_off); false when it is not theirs.
	boolean setShareStatus(String userId, String shareId, String status);

	// Adds a person (kind, due day, yearly rate as a fraction, status, note, hidden); null when the caller already has a person by that name (ignoring case).
	String insertPerson(String userId, WealthPersonEntity person);

	// Changes the named fields of one of the caller's people (the field names are the PEOPLE_FIELD_ constants; the others keep their value) and, when keepAlias is set, keeps it as another name.
	// False when the person is not theirs; throws when the new name is taken.
	boolean updatePerson(String userId, String personId, java.util.Set<String> changed, WealthPersonEntity values, String keepAlias);

	// How many lines (not deleted) and shares of active splits a person has.
	int countPersonRecords(String personId);

	// Keeps the hash of a new share secret for one of the caller's people (it replaces the old link); false when the person is not theirs.
	boolean setShareToken(String userId, String personId, String hash);

	// Removes the share link of one of the caller's people; false when the person is not theirs.
	boolean clearShareToken(String userId, String personId);

	// The person whose share secret has this hash (id, userId, name, status), or null. Used by the public page, so it carries the owner's id.
	WealthPersonEntity findPersonByShareHash(String hash);

	// The title of an active split bill that both people are in, or null when they share none.
	String findMergeClash(String intoId, String fromId);

	// In one transaction: moves every line, share, request, round-off, remark and other name of fromId to intoId, keeps keepAlias (the merged-away name) as another name of intoId, removes fromId
	// (its share link stops working) and re-opens intoId when settled. Throws when either person is not the caller's.
	void mergePeople(String userId, String fromId, String intoId, String keepAlias);

	// ---- public page (no caller: everything is scoped by the person the link belongs to) ----

	// How many "I've paid" messages of the person wait for the owner.
	int countPendingRequests(String personId);

	// Stores a waiting "I've paid" message, recorded by the share link.
	void addLendRequest(String personId, BigDecimal amount, String paidOn, String method, String note);

	// The person's ten latest "I've paid" messages with their state, newest first.
	List<WealthLendRequestEntity> findLendViewRequests(String personId);

	// True when the line (not deleted) belongs to the person.
	boolean entryOfPerson(String entryId, String personId);

	// True when the bill share (of an active split) belongs to the person.
	boolean shareOfPerson(String shareId, String personId);

	// How many remarks the person wrote since the given time.
	int countPersonRemarksSince(String personId, Timestamp since);

	// Stores a remark on a line or a share by the author (owner or person) with the writer (owner or lend-link) for the audit log; returns its id.
	String addRemark(String personId, String entryId, String shareId, String author, String body, String actor);

	// ---- owner ----

	// The person of one of the caller's lines (not deleted), or null.
	String findEntryPersonOfOwner(String userId, String entryId);

	// The person of a bill share of the caller's, or null.
	String findSharePersonOfOwner(String userId, String shareId);

	// Sets the description of one of the caller's lines (null clears it); false when it is not theirs.
	boolean setEntryNote(String userId, String entryId, String note);

	// One "I've paid" message of one of the caller's people (id, personId, name, amount, paidOn, method, note, status), or null.
	WealthLendRequestEntity findRequestOfOwner(String userId, String requestId);

	// Marks a waiting message declined; false when it was already decided or is not theirs.
	boolean declineRequest(String userId, String requestId);

	// In one transaction: marks the waiting message accepted, adds the paid-back line (cash, no account or transaction), and settles the person when closeLend. False when it was already decided.
	boolean acceptRequest(String userId, WealthLendRequestEntity request, BigDecimal amount, String paidOn, boolean closeLend, String note);
}
