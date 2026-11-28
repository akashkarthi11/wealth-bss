package com.wealth.bss.serviceimplement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthSmsDeviceDao;
import com.wealth.bss.common.model.entity.WealthSmsDeviceEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.config.BankAlertParser;
import com.wealth.bss.config.SmsAlertParser;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.serviceinterface.IfBankImportService;
import com.wealth.bss.serviceinterface.IfSmsService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceCreateRequestDto;
import com.wealth.common.model.dto.sms.SmsDeviceDto;
import com.wealth.common.model.dto.sms.SmsMessageDto;
import com.wealth.common.model.dto.sms.SmsPushRequestDto;
import com.wealth.common.model.dto.sms.SmsPushResultDto;
import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class SmsServiceImple implements IfSmsService {

	private static final KarthiLogger LOGGER = new KarthiLogger(SmsServiceImple.class);
	private static final Pattern ID_SHAPE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final IfWealthSmsDeviceDao deviceDao;
	private final IfBankImportService importService;
	private final LoginRateLimiter rateLimiter;
	private final Clock clock;

	@Autowired
	public SmsServiceImple(IfWealthSmsDeviceDao deviceDao, IfBankImportService importService, LoginRateLimiter rateLimiter) {
		this(deviceDao, importService, rateLimiter, Clock.system(ZONE));
	}

	SmsServiceImple(IfWealthSmsDeviceDao deviceDao, IfBankImportService importService, LoginRateLimiter rateLimiter, Clock clock) {
		this.deviceDao = deviceDao;
		this.importService = importService;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	@Override
	public SmsDeviceDto createDevice(SmsDeviceCreateRequestDto request) {
		String methodName = "createDevice";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = RequestHeaderUtil.getUserId();
		String label = request == null || request.getLabel() == null ? "" : request.getLabel().replaceAll("[\\u0000-\\u001f]", " ").trim();
		if (label.isEmpty() || label.length() > WealthServiceConstants.SMS_LABEL_MAX) {
			throw new KarthiException(BaseError.SMS_LABEL_INVALID);
		}
		if (deviceDao.countActive(userId) >= WealthServiceConstants.SMS_DEVICES_MAX) {
			throw new KarthiException(BaseError.SMS_DEVICE_LIMIT);
		}
		byte[] bytes = new byte[WealthServiceConstants.SMS_TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		SmsDeviceDto dto = new SmsDeviceDto();
		dto.setId(deviceDao.insert(userId, label, hashOf(token)));
		dto.setLabel(label);
		dto.setToken(token);
		dto.setCreatedAt(Instant.now(clock).toString());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return dto;
	}

	@Override
	public List<SmsDeviceDto> listDevices() {
		return deviceDao.findActive(RequestHeaderUtil.getUserId()).stream().map(row -> {
			SmsDeviceDto dto = new SmsDeviceDto();
			dto.setId(row.getId());
			dto.setLabel(row.getLabel());
			dto.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().toInstant().toString());
			dto.setLastSeenAt(row.getLastSeenAt() == null ? null : row.getLastSeenAt().toInstant().toString());
			dto.setLastRead(row.getLastRead());
			dto.setLastSaved(row.getLastSaved());
			return dto;
		}).toList();
	}

	@Override
	public Boolean revokeDevice(IdRequestDto request) {
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()
				|| !deviceDao.revoke(RequestHeaderUtil.getUserId(), request.getId())) {
			throw new KarthiException(BaseError.SMS_DEVICE_NOT_FOUND);
		}
		return Boolean.TRUE;
	}

	@Override
	public SmsPushResultDto push(SmsPushRequestDto request) {
		String methodName = "push";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		attempt(WealthServiceConstants.SMS_PUSH_RATE_KEY, WealthServiceConstants.SMS_PUSH_IP_LIMIT);
		WealthSmsDeviceEntity device = deviceOf(request == null ? null : request.getToken());
		List<SmsMessageDto> messages = request.getMessages();
		if (messages == null || messages.isEmpty() || messages.size() > WealthServiceConstants.SMS_PUSH_MAX) {
			throw new KarthiException(BaseError.SMS_PUSH_INVALID);
		}
		SmsPushResultDto result = new SmsPushResultDto();
		result.setRead(messages.size());
		List<BankLineDto> lines = new ArrayList<>();
		LocalDate today = LocalDate.now(clock);
		for (SmsMessageDto message : messages) {
			BankLineDto line = lineOf(message, today);
			if (line == null) {
				result.setSkipped(result.getSkipped() + 1);
			} else {
				lines.add(line);
			}
		}
		// The import reads the signed-in user from the request; this request has none, so the phone's owner is set for it.
		RequestHeaderUtil.setUserId(device.getUserId());
		try {
			BankImportResultDto imported = lines.isEmpty() ? new BankImportResultDto() : importService.importLines(WealthServiceConstants.WORKFLOW_SOURCE_ALERT, lines);
			result.setSaved(imported.getStored());
			result.setDuplicates(imported.getSame() + imported.getRepeated());
			result.setReview(imported.getReview());
			deviceDao.touch(device.getId(), result.getRead(), result.getSaved());
		} finally {
			RequestHeaderUtil.setUserId(null);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | read=" + result.getRead() + " saved=" + result.getSaved() + " review=" + result.getReview() + " skipped=" + result.getSkipped());
		return result;
	}

	// The phone a token belongs to. The token is checked for its shape first; a wrong, unknown or removed one gets one generic refusal, and repeated wrong ones from one address are stopped.
	private WealthSmsDeviceEntity deviceOf(String token) {
		WealthSmsDeviceEntity device = token != null && token.matches(WealthServiceConstants.SMS_TOKEN_PATTERN) ? deviceDao.findByTokenHash(hashOf(token)) : null;
		if (device == null) {
			attempt(WealthServiceConstants.SMS_BAD_TOKEN_RATE_KEY, WealthServiceConstants.SMS_BAD_TOKEN_IP_LIMIT);
			throw new KarthiException(BaseError.SMS_TOKEN_INVALID);
		}
		return device;
	}

	private void attempt(String keyPrefix, long limit) {
		if (rateLimiter != null && !rateLimiter.attempt(keyPrefix + RequestHeaderUtil.getIp(), limit, WealthServiceConstants.LEND_VIEW_WINDOW_SECONDS)) {
			throw new KarthiException(BaseError.SMS_RATE_LIMITED);
		}
	}

	// One SMS as a bank line, or null when it is not a bank transaction (or its fields are not usable). The reference is made from the sender, the arrival time and the text, so the same SMS
	// sent twice (a retry, or an app that was reinstalled) is the same line. A line the reader is not sure of is held for review.
	private BankLineDto lineOf(SmsMessageDto message, LocalDate today) {
		if (message == null || message.getSender() == null || message.getBody() == null || message.getSender().length() > WealthServiceConstants.SMS_SENDER_MAX
				|| message.getBody().length() > WealthServiceConstants.SMS_BODY_MAX || message.getBody().isBlank()) {
			return null;
		}
		LocalDate received = message.getAt() <= 0 ? today : LocalDate.ofInstant(Instant.ofEpochMilli(message.getAt()), ZONE);
		if (received.isAfter(today.plusDays(1))) {
			return null;
		}
		SmsAlertParser.Alert alert = SmsAlertParser.parse(message.getSender(), message.getBody(), received);
		if (alert == null) {
			return null;
		}
		BankLineDto line = new BankLineDto();
		line.setRef(WealthServiceConstants.SMS_REF_PREFIX + digest(message.getSender() + "|" + message.getAt() + "|" + message.getBody()));
		line.setBank(alert.bank());
		line.setAccountDigits(alert.lastDigits());
		line.setDay(alert.date());
		line.setDescription(alert.party().isEmpty() ? "Bank SMS" : alert.party());
		line.setCategory(WealthServiceConstants.SMS_CATEGORY);
		line.setKind(alert.kind());
		line.setAmount(alert.amount());
		line.setDebit(!BankAlertParser.INCOME.equals(alert.kind()));
		line.setSure(alert.sure());
		line.setSuspicious(LocalDate.parse(alert.date()).isAfter(today.plusDays(1)));
		return line;
	}

	private static String digest(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), 0, 12);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// The SHA-256 of the token as base64url without padding; only this is stored.
	static String hashOf(String token) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
