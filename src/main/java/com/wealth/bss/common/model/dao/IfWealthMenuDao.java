package com.wealth.bss.common.model.dao;

import java.util.List;

import com.wealth.bss.common.model.entity.WealthMenuEntity;

public interface IfWealthMenuDao {

	// Every menu and submenu, in display order (sort order, then key).
	List<WealthMenuEntity> findAll();
}
