package com.wealth.bss.serviceimplement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.config.AngelOneClient;
import com.wealth.bss.config.ConnectionTester;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.bss.serviceinterface.IfThirdPartyService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.ProviderRequestDto;
import com.wealth.common.model.dto.admin.ThirdPartyDto;
import com.wealth.common.model.dto.admin.ThirdPartySaveRequestDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class ThirdPartyServiceImple implements IfThirdPartyService {

	private static final KarthiLogger LOGGER = new KarthiLogger(ThirdPartyServiceImple.class);
	private static final Pattern PROVIDER = Pattern.compile("^[a-z0-9-]{2,40}$");
	private static final Pattern ADDRESS = Pattern.compile("^https?://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)(?::\\d{1,5})?(?:/[A-Za-z0-9._~/-]*)?$");
	private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
	private static final String OK = "ok";
	private static final String FAILED = "failed";

	private final IfRequestContextService requestContextService;
	private final IfWealthConnectionDao connectionDao;
	private final SettingsCipher cipher;
	private final ConnectionTester tester;
	private final AngelOneClient angelOne;

	public ThirdPartyServiceImple(IfRequestContextService requestContextService, IfWealthConnectionDao connectionDao, SettingsCipher cipher,
			ConnectionTester tester, AngelOneClient angelOne) {
		this.requestContextService = requestContextService;
		this.connectionDao = connectionDao;
		this.cipher = cipher;
		this.tester = tester;
		this.angelOne = angelOne;
	}

	@Override
	public List<ThirdPartyDto> list() {
		requestContextService.requireAdmin();
		return connectionDao.findAll().stream().map(this::toDto).toList();
	}

	@Override
	public ThirdPartyDto save(ThirdPartySaveRequestDto request) {
		String methodName = "save";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || request.getProvider() == null || !PROVIDER.matcher(request.getProvider()).matches()) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		String label = request.getLabel() == null ? "" : request.getLabel().trim();
		String address = request.getBaseUrl() == null ? "" : request.getBaseUrl().trim();
		if (label.isEmpty() || label.length() > WealthServiceConstants.PROVIDER_LABEL_MAX_LENGTH || !validAddress(address)
				|| request.getPriority() < 1 || request.getPriority() > WealthServiceConstants.PROVIDER_PRIORITY_MAX) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		WealthConnectionEntity entity = new WealthConnectionEntity();
		entity.setProvider(request.getProvider());
		entity.setLabel(label);
		entity.setBaseUrl(address);
		entity.setEnabled(request.isEnabled());
		entity.setPriority(request.getPriority());
		String key = request.getApiKey() == null ? "" : request.getApiKey();
		if (!key.isEmpty()) {
			if (key.length() > WealthServiceConstants.PROVIDER_KEY_MAX_LENGTH || CONTROL.matcher(key).find()) {
				throw new KarthiException(BaseError.ENVIRONMENT_CREDENTIALS_INVALID);
			}
			entity.setCredentialEnc(cipher.encrypt(key));
			entity.setKeyHint(key.length() > WealthServiceConstants.KEY_HINT_LENGTH ? key.substring(key.length() - WealthServiceConstants.KEY_HINT_LENGTH) : "");
		}
		boolean broker = WealthServiceConstants.BROKER_ANGEL_ONE.equals(request.getProvider());
		Map<String, String> extra = request.getExtra() == null ? Map.of() : request.getExtra();
		if (!broker && (!extra.isEmpty() || request.isAutoFetch())) {
			throw new KarthiException(BaseError.PROVIDER_INVALID);
		}
		if (broker) {
			entity.setAutoFetch(request.isAutoFetch());
			entity.setOwnerUserId(RequestHeaderUtil.getUserId());
			entity.setCredentialExtraEnc(mergedExtra(request.getProvider(), extra));
		}
		WealthConnectionEntity saved = connectionDao.upsert(entity);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return toDto(saved);
	}

	@Override
	public ThirdPartyDto test(ProviderRequestDto request) {
		String methodName = "test";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		WealthConnectionEntity row = find(request);
		String key = cipher.decrypt(row.getCredentialEnc());
		boolean worked;
		AngelOneClient.Credentials login = WealthServiceConstants.BROKER_ANGEL_ONE.equals(row.getProvider())
				? angelOne.credentials(key, angelOne.readExtra(cipher.decrypt(row.getCredentialExtraEnc()))) : null;
		if (login != null && login.complete()) {
			worked = signsIn(row.getBaseUrl(), login);
		} else {
			worked = tester.test(row.getProvider(), row.getBaseUrl(), key);
		}
		WealthConnectionEntity result = new WealthConnectionEntity();
		result.setProvider(row.getProvider());
		result.setLastStatus(worked ? OK : FAILED);
		WealthConnectionEntity saved = connectionDao.saveTestResult(result);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | " + (worked ? OK : FAILED));
		return toDto(saved == null ? row : saved);
	}

	@Override
	public void remove(ProviderRequestDto request) {
		requestContextService.requireAdmin();
		WealthConnectionEntity row = find(request);
		connectionDao.delete(row.getProvider());
	}

	// A real sign-in, so a wrong PIN or TOTP secret shows up on Test; the reason is logged without any secret.
	private boolean signsIn(String address, AngelOneClient.Credentials login) {
		try {
			angelOne.login(address, login);
			return true;
		} catch (IllegalStateException e) {
			LOGGER.warn("Angel One sign-in test failed: " + e.getMessage());
			return false;
		}
	}

	// The saved login values with the entered ones on top (an empty entry keeps the saved one), encrypted; null when nothing was entered, so the row keeps what it has.
	private String mergedExtra(String provider, Map<String, String> entered) {
		Map<String, String> values = new LinkedHashMap<>();
		Map<String, String> clean = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : entered.entrySet()) {
			String value = entry.getValue() == null ? "" : entry.getValue().trim();
			if (!AngelOneClient.EXTRA_KEYS.contains(entry.getKey()) || value.length() > WealthServiceConstants.EXTRA_VALUE_MAX_LENGTH || CONTROL.matcher(value).find()) {
				throw new KarthiException(BaseError.ENVIRONMENT_CREDENTIALS_INVALID);
			}
			if (!value.isEmpty()) {
				clean.put(entry.getKey(), value);
			}
		}
		if (clean.isEmpty()) {
			return null;
		}
		connectionDao.findAll().stream().filter(row -> row.getProvider().equals(provider)).findFirst()
				.ifPresent(row -> values.putAll(angelOne.readExtra(cipher.decrypt(row.getCredentialExtraEnc()))));
		values.putAll(clean);
		return cipher.encrypt(angelOne.writeExtra(values));
	}

	private WealthConnectionEntity find(ProviderRequestDto request) {
		String provider = request == null ? null : request.getProvider();
		return connectionDao.findAll().stream().filter(row -> row.getProvider().equals(provider)).findFirst()
				.orElseThrow(() -> new KarthiException(BaseError.PROVIDER_NOT_FOUND));
	}

	// http(s)://host[:port][/path]; never a link-local address (cloud metadata).
	private boolean validAddress(String address) {
		var matcher = ADDRESS.matcher(address);
		return address.length() <= WealthServiceConstants.ORIGIN_MAX_LENGTH && matcher.matches() && !matcher.group(1).startsWith(WealthServiceConstants.LINK_LOCAL_PREFIX);
	}

	ThirdPartyDto toDto(WealthConnectionEntity row) {
		ThirdPartyDto dto = new ThirdPartyDto();
		dto.setProvider(row.getProvider());
		dto.setLabel(row.getLabel());
		dto.setBaseUrl(row.getBaseUrl());
		dto.setEnabled(row.isEnabled());
		dto.setPriority(row.getPriority());
		dto.setKeySet(row.getCredentialEnc() != null);
		dto.setKeyHint(row.getKeyHint());
		dto.setStatus(row.getLastStatus());
		dto.setCheckedAt(row.getLastCheckedAt());
		dto.setExtraSet(angelOne.readExtra(cipher.decrypt(row.getCredentialExtraEnc())).keySet().stream().filter(AngelOneClient.EXTRA_KEYS::contains).toList());
		dto.setAutoFetch(row.isAutoFetch());
		dto.setLastFetchAt(row.getLastFetchAt());
		dto.setLastFetchNote(row.getLastFetchNote());
		return dto;
	}
}
