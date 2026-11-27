package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthTransactionWorkflowEntity;

public interface IfWealthTransactionWorkflowDao {

	// entity.userId is the owner. Stores the line in doubt; returns false when that statement line is already there (nothing is stored again).
	boolean insert(WealthTransactionWorkflowEntity entity);

	// Whether the user has that statement line in the workflow, in any state.
	boolean exists(String userId, String sourceRef);

	// The user's lines still waiting, newest first.
	List<WealthTransactionWorkflowEntity> findOpen(String userId);

	// One of the user's lines, any state; null when it is not theirs.
	WealthTransactionWorkflowEntity find(String userId, String id);

	// Marks an open line as merged, accepted or dismissed by the user, once. Returns false when it was not open any more.
	boolean decide(String userId, String id, String status);
}
