package com.wealth.bss.common.model.dao;

import java.time.LocalDate;
import java.util.List;

import com.wealth.bss.common.model.entity.WealthApiUsageEntity;

// Saved API usage: one row per day, kind, name and user, with the counts added up.
public interface IfWealthApiUsageDao {

	// Adds the rows to the saved counts, in one transaction, once per batchId. False when that batch was saved before (nothing is added again).
	boolean saveBatch(String batchId, List<WealthApiUsageEntity> rows);

	// True when a batch with this id was saved.
	boolean isBatchSaved(String batchId);

	// The counts per kind and name from that day on (day is empty in the result): what the screen adds the unsaved counts to. userId empty = all users.
	List<WealthApiUsageEntity> findTotalsSince(LocalDate from, String userId);

	// What each user's website calls came to from that day on (userId and calls only); calls made with no user are left out.
	List<WealthApiUsageEntity> findUserCallsSince(LocalDate from);

	// Every user's id and email, to name the users in the usage list (userId and email only).
	List<WealthApiUsageEntity> findUsers();
}
