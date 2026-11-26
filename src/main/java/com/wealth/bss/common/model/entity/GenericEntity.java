package com.wealth.bss.common.model.entity;

import java.io.Serializable;
import java.sql.Timestamp;

import lombok.Getter;
import lombok.Setter;

// Base of every entity. The audit fields use the GrowX GenericEntity names; the record state, tenant and IP columns are not part of Wealth.
// Columns (migration 0054): crtd_dt, updt_dt, crtd_by, updt_by. Tables that already had created_at and updated_at keep them, and a query reads
// them as CREATED_AT AS CRTD_DT and UPDATED_AT AS UPDT_DT. crtd_by and updt_by are the writer's user number; 1 is the system (cron and jobs).
@Getter
@Setter
public abstract class GenericEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	private Timestamp crtdDt;
	private Integer crtdBy;
	private Timestamp updtDt;
	private Integer updtBy;

	// Not a column: who is writing (owner, agent, cron). A write DaoImple stores it in wealth.actor for the audit log and the balance guard.
	private String actor;

	public abstract String tableName();

	public abstract String primaryKey();
}
