package com.wealth.bss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.wealth.bss.contract.IfMenusServiceContract;
import com.wealth.bss.serviceinterface.IfMenusService;
import com.wealth.common.generic.ApiResponse;
import com.wealth.common.generic.ApiResponseTemplate;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.menu.NavMenuDto;

@RestController
public class MenusServiceController implements IfMenusServiceContract {

	private static final KarthiLogger LOGGER = new KarthiLogger(MenusServiceController.class);

	private final IfMenusService menusService;
	private final ApiResponseTemplate responseTemplate;

	public MenusServiceController(IfMenusService menusService, ApiResponseTemplate responseTemplate) {
		this.menusService = menusService;
		this.responseTemplate = responseTemplate;
	}

	@Override
	public ResponseEntity<ApiResponse<List<NavMenuDto>, Void>> list() throws KarthiException {
		LOGGER.info("Entering list");
		return responseTemplate.apires(menusService.navigation(), BaseError.Success);
	}
}
