package com.wealth.bss.serviceimplement;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.common.model.dao.IfWealthUserDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthUserEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfRequestHeaderService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.ThreadLocalDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// Cache first, database second; a Redis failure falls back to the database, which is the source of truth.
@Service
public class RequestContextServiceImple implements IfRequestContextService {

	private static final KarthiLogger LOGGER = new KarthiLogger(RequestContextServiceImple.class);

	private final IfRequestHeaderService requestHeaderService;
	private final IfWealthUserDao wealthUserDao;
	private final ObjectMapper mapper = new ObjectMapper();

	public RequestContextServiceImple(IfRequestHeaderService requestHeaderService, IfWealthUserDao wealthUserDao) {
		this.requestHeaderService = requestHeaderService;
		this.wealthUserDao = wealthUserDao;
	}

	@Override
	public ThreadLocalDto getRequestHeaderCache(String userId) {
		String methodName = "getRequestHeaderCache";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String id = parse(userId);
		if (id == null) {
			LOGGER.warn("Subject is not a user id | " + methodName);
			return null;
		}
		String key = WealthServiceConstants.REQUEST_CONTEXT_KEY_PREFIX + id;
		ThreadLocalDto cached = readCache(key);
		if (cached != null) {
			LOGGER.info(WealthServiceConstants.EXITING + methodName + " | cache hit");
			return cached;
		}
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setId(id);
		WealthUserEntity user = wealthUserDao.findById(paginationDto);
		if (user == null) {
			LOGGER.info(WealthServiceConstants.EXITING + methodName + " | user not found");
			return null;
		}
		ThreadLocalDto dto = toDto(user);
		writeCache(key, dto);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | loaded from database");
		return dto;
	}

	@Override
	public void evict(String userId) {
		String id = parse(userId);
		if (id == null) {
			return;
		}
		try {
			requestHeaderService.removeCacheData(WealthServiceConstants.REQUEST_CONTEXT_KEY_PREFIX + id);
		} catch (RuntimeException e) {
			LOGGER.warn("Request context cache evict failed: " + e.getClass().getSimpleName());
		}
	}

	@Override
	public void requireAdmin() {
		ThreadLocalDto caller = RequestHeaderUtil.getThreadLocalDto();
		if (caller == null || !WealthServiceConstants.ROLE_ADMIN.equals(caller.getRole())) {
			throw new KarthiException(BaseError.ADMIN_ONLY_ACTION);
		}
	}

	// Canonical UUID text, so only a valid id ever reaches the cache key or the query.
	private static String parse(String userId) {
		try {
			return userId == null ? null : UUID.fromString(userId.trim()).toString();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static ThreadLocalDto toDto(WealthUserEntity user) {
		ThreadLocalDto dto = new ThreadLocalDto();
		dto.setUserId(user.getId());
		dto.setRole(user.getRole());
		dto.setSessionVersion(user.getSessionVersion());
		return dto;
	}

	private ThreadLocalDto readCache(String key) {
		try {
			String json = requestHeaderService.getCacheData(key);
			return json == null ? null : mapper.readValue(json, ThreadLocalDto.class);
		} catch (Exception e) {
			LOGGER.warn("Request context cache read failed: " + e.getClass().getSimpleName());
			return null;
		}
	}

	private void writeCache(String key, ThreadLocalDto dto) {
		try {
			requestHeaderService.setCacheData(key, mapper.writeValueAsString(dto));
		} catch (Exception e) {
			LOGGER.warn("Request context cache write failed: " + e.getClass().getSimpleName());
		}
	}
}
