package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.people.PeopleOverviewDto;

// Money lent and split bills of the signed-in user (user id from the token context). Read only.
public interface IfPeopleService {

	// Every person (not removed) with what they owe and the due date, their entries and shares, round-offs, the split bills, the pending "I've paid" requests and
	// suggested transfers to link. Read from the nexus cache and refreshed when older than the refresh time.
	PeopleOverviewDto list();

	// The Refresh button: asks for a fresh read at once (at most every few seconds per user) and returns what is cached now; fetchedAt tells when it was read.
	PeopleOverviewDto refresh();
}
