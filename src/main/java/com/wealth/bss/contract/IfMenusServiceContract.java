package com.wealth.bss.contract;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.model.dto.menu.NavMenuDto;

// Signed-in users: each gets only the entries their role may see.
@RequestMapping(WealthServiceConstants.MENUS_BASE)
public interface IfMenusServiceContract {

	@PostMapping(WealthServiceConstants.MENUS_LIST)
	ResponseEntity<ApiResponse<List<NavMenuDto>, Void>> list() throws KarthiException;
}
