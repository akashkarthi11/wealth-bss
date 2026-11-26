package com.wealth.bss.serviceinterface;

import java.util.List;

import com.wealth.common.model.dto.menu.NavMenuDto;

public interface IfMenusService {

	// The navigation the signed-in user may see, built from the table wealth_menus: active entries only, each top-level menu with its submenus.
	// The admin sees every active entry. Anyone else sees the entries that are not admin only and are in their role's permissions (a role with
	// nothing saved sees every one). A submenu shows only when its menu does.
	List<NavMenuDto> navigation();
}
