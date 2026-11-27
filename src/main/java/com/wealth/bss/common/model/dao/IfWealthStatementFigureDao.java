package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthStatementFigureEntity;

public interface IfWealthStatementFigureDao {

	// Saves one statement's figures (entity.userId, mailRef, source, figures as JSON text); the same statement read again replaces its figures and read time.
	void upsert(WealthStatementFigureEntity entity);

	// Records that the user accepted the statement's rows (the time is now).
	void markAccepted(String userId, String id);

	// Every statement the user received or uploaded, opened or not, newest first (at most 200); mailRef is the mail id or the upload reference.
	List<WealthStatementFigureEntity> findHistory(String userId);

	// One of the user's statements; null when it is not theirs.
	WealthStatementFigureEntity find(String userId, String id);

	// The user's statements that opened, most recently read first (at most 100).
	List<WealthStatementFigureEntity> findByUser(String userId);
}
