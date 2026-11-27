package com.wealth.bss.serviceimplement;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.config.IndstocksClient;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfIndstocksService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.IndstocksSaveRequestDto;
import com.wealth.common.model.dto.admin.IndstocksStatusDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// The user's own INDstocks API token: kept encrypted in wealth_user_connections (provider indstocks), never returned or logged. Common (wealth_connections) holds only the address and the on/off switch.
@Service
public class IndstocksServiceImple implements IfIndstocksService {

	private static final KarthiLogger LOGGER = new KarthiLogger(IndstocksServiceImple.class);
	private static final String BEARER = "bearer ";

	private final IfWealthConnectionDao connectionDao;
	private final IfWealthUserConnectionDao userDao;
	private final SettingsCipher cipher;
	private final IndstocksClient client;

	public IndstocksServiceImple(IfWealthConnectionDao connectionDao, IfWealthUserConnectionDao userDao, SettingsCipher cipher, IndstocksClient client) {
		this.connectionDao = connectionDao;
		this.userDao = userDao;
		this.cipher = cipher;
		this.client = client;
	}

	@Override
	public IndstocksStatusDto status() {
		String userId = currentUser();
		WealthConnectionEntity provider = providerRow();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.INDSTOCKS);
		IndstocksStatusDto dto = new IndstocksStatusDto();
		dto.setProviderOn(provider != null && provider.isEnabled() && filled(provider.getBaseUrl()));
		dto.setTokenSet(row != null && filled(cipher.decrypt(row.getCredentialEnc())));
		dto.setStatus(row == null ? null : row.getLastStatus());
		dto.setCheckedAt(row == null ? null : row.getLastCheckedAt());
		return dto;
	}

	@Override
	public IndstocksStatusDto save(IndstocksSaveRequestDto request) {
		String methodName = "save";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		if (request == null || providerRow() == null) {
			throw new KarthiException(request == null ? BaseError.PROVIDER_INVALID : BaseError.PROVIDER_NOT_FOUND);
		}
		String token = request.getToken() == null ? "" : request.getToken().trim();
		if (token.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
			token = token.substring(BEARER.length()).trim();
		}
		if (token.isEmpty() || token.length() > WealthServiceConstants.INDSTOCKS_TOKEN_MAX || token.chars().anyMatch(c -> c <= ' ' || c == 127)) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		WealthUserConnectionEntity own = new WealthUserConnectionEntity();
		own.setUserId(userId);
		own.setProvider(WealthServiceConstants.INDSTOCKS);
		own.setCredentialEnc(cipher.encrypt(token));
		own.setKeyHint(token.length() > WealthServiceConstants.KEY_HINT_LENGTH ? token.substring(token.length() - WealthServiceConstants.KEY_HINT_LENGTH) : null);
		own.setEnabled(true);
		userDao.upsert(own);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return status();
	}

	// A 2xx marks the token working, 401 or 403 marks it refused; a network failure, 429 or a server error says nothing about the token, so the saved result is kept and the call is reported as failed.
	@Override
	public IndstocksStatusDto test() {
		String methodName = "test";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthConnectionEntity provider = providerRow();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.INDSTOCKS);
		String token = row == null ? null : cipher.decrypt(row.getCredentialEnc());
		if (provider == null || !provider.isEnabled() || !filled(provider.getBaseUrl())) {
			throw new KarthiException(BaseError.PROVIDER_NOT_FOUND);
		}
		if (!filled(token)) {
			throw new KarthiException(BaseError.CREDENTIALS_INCOMPLETE);
		}
		int code;
		try {
			code = client.probe(provider.getBaseUrl(), token);
		} catch (IllegalStateException e) {
			LOGGER.warn("INDstocks test could not run: " + e.getMessage());
			throw new KarthiException(BaseError.BROKER_FETCH_FAILED);
		}
		if (code >= 200 && code < 300) {
			userDao.saveCheck(userId, WealthServiceConstants.INDSTOCKS, "ok");
		} else if (code == 401 || code == 403) {
			userDao.saveCheck(userId, WealthServiceConstants.INDSTOCKS, "failed");
		} else {
			throw new KarthiException(BaseError.BROKER_FETCH_FAILED);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return status();
	}

	@Override
	public boolean remove() {
		return userDao.delete(currentUser(), WealthServiceConstants.INDSTOCKS);
	}

	private WealthConnectionEntity providerRow() {
		return connectionDao.findAll().stream().filter(r -> WealthServiceConstants.INDSTOCKS.equals(r.getProvider())).findFirst().orElse(null);
	}

	private String currentUser() {
		String userId = RequestHeaderUtil.getUserId();
		if (userId == null || userId.isBlank()) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		return userId;
	}

	private static boolean filled(String value) {
		return value != null && !value.isBlank();
	}
}
