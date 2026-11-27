package com.wealth.bss.serviceimplement;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealth.bss.common.model.dao.IfWealthCommonConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthConnectionDao;
import com.wealth.bss.common.model.dao.IfWealthStatementFigureDao;
import com.wealth.bss.common.model.dao.IfWealthTransactionDao;
import com.wealth.bss.common.model.dao.IfWealthUserConnectionDao;
import com.wealth.bss.common.model.entity.WealthCommonConnectionEntity;
import com.wealth.bss.common.model.entity.WealthConnectionEntity;
import com.wealth.bss.common.model.entity.WealthStatementFigureEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.serviceinterface.IfBankImportService;
import com.wealth.common.model.dto.transaction.BankImportResultDto;
import com.wealth.common.model.dto.transaction.BankLineDto;
import com.wealth.bss.common.model.entity.WealthUserConnectionEntity;
import com.wealth.bss.config.BankAlertParser;
import com.wealth.bss.config.GeminiClient;
import com.wealth.bss.config.GmailClient;
import com.wealth.bss.config.SettingsCipher;
import com.wealth.bss.config.StatementReader;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfGmailService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.admin.GmailAppSaveRequestDto;
import com.wealth.common.model.dto.admin.GmailConnectRequestDto;
import com.wealth.common.model.dto.admin.GmailConnectUrlDto;
import com.wealth.common.model.dto.admin.GmailFetchResultDto;
import com.wealth.common.model.dto.admin.GmailFinishRequestDto;
import com.wealth.common.model.dto.admin.GmailPendingItemDto;
import com.wealth.common.model.dto.admin.GmailReviewRequestDto;
import com.wealth.common.model.dto.admin.GmailStatementResultDto;
import com.wealth.common.model.dto.admin.GmailStatementRowDto;
import com.wealth.common.model.dto.admin.GmailStatementSettingsRequestDto;
import com.wealth.common.model.dto.admin.GmailStatusDto;
import com.wealth.common.model.dto.admin.StatementAcceptRequestDto;
import com.wealth.common.model.dto.admin.StatementHistoryDto;
import com.wealth.common.model.dto.admin.StatementDownloadRequestDto;
import com.wealth.common.model.dto.admin.StatementFileDto;
import com.wealth.common.model.dto.admin.StatementRetryRequestDto;
import com.wealth.common.model.dto.admin.StatementUploadRequestDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class GmailServiceImple implements IfGmailService {

	private static final KarthiLogger LOGGER = new KarthiLogger(GmailServiceImple.class);
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");
	private static final Pattern PAN_SHAPE = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
	private static final Pattern DOB_SHAPE = Pattern.compile("^[0-9]{8}$");
	private static final String KEY_PAN = "pan";
	private static final String KEY_DOB = "dob";
	private static final String KEY_NAME = "name4";
	// passwords the user typed and chose to keep, one per line
	private static final String KEY_EXTRA = "extraPasswords";
	private static final int EXTRA_PASSWORDS_MAX = 5;
	private static final int UPLOAD_MAX_BYTES = 5_000_000;
	private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F' };
	private static final int STATEMENT_SOURCE_MAX = 120;
	private static final Pattern NAME_SHAPE = Pattern.compile("^[A-Z]{4}$");
	// what the last read found, kept in the same encrypted block: lines joined by a line break, and the time in milliseconds
	private static final String KEY_BALANCES = "lastBalances";
	private static final String KEY_BALANCES_AT = "lastBalancesAt";
	private static final String KEY_FIGURES = "lastFigures";
	private static final String KEY_FIGURES_AT = "lastFiguresAt";
	private static final String KEY_OPENED = "lastOpened";
	private static final String KEY_LOCKED = "lastLocked";
	private static final int KEPT_LINES_MAX = 40;
	private static final int KEPT_LINE_LENGTH = 200;
	private static final Pattern ID_SHAPE =Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final String OK = "ok";
	private static final String FAILED = "failed";
	private static final String GMAIL_LABEL = "Gmail (bank alerts)";
	private static final String GMAIL_ADDRESS = "https://gmail.googleapis.com";
	private static final String STATE_SEPARATOR = "|";
	private static final String KEY_STATEMENT_DAYS = "statementDays";
	private static final String KEY_STATEMENT_MAX = "statementMax";
	private static final String KEY_ALERT_DAYS = "alertDays";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	// The Google client as saved: both values, the API address from the main row, and how far back and how many mails a read looks at.
	private record App(String clientId, String clientSecret, String baseUrl, int statementDays, int statementMax, int alertDays) {
	}

	private final IfRequestContextService requestContextService;
	private final IfWealthConnectionDao connectionDao;
	private final IfWealthCommonConnectionDao commonDao;
	private final IfWealthUserConnectionDao userDao;
	private final IfWealthTransactionDao transactionDao;
	private final IfNexusReadService nexusReadService;
	private final SettingsCipher cipher;
	private final GmailClient client;
	private final IfBankImportService importService;
	private final IfWealthStatementFigureDao statementDao;
	private final GeminiClient gemini;

	public GmailServiceImple(IfRequestContextService requestContextService, IfWealthConnectionDao connectionDao, IfWealthCommonConnectionDao commonDao,
			IfWealthUserConnectionDao userDao, IfWealthTransactionDao transactionDao, IfNexusReadService nexusReadService, SettingsCipher cipher, GmailClient client, IfBankImportService importService,
			IfWealthStatementFigureDao statementDao, GeminiClient gemini) {
		this.statementDao = statementDao;
		this.gemini = gemini;
		this.importService = importService;
		this.requestContextService = requestContextService;
		this.connectionDao = connectionDao;
		this.commonDao = commonDao;
		this.userDao = userDao;
		this.transactionDao = transactionDao;
		this.nexusReadService = nexusReadService;
		this.cipher = cipher;
		this.client = client;
	}

	@Override
	public GmailStatusDto statementSave(GmailStatementSettingsRequestDto request) {
		String methodName = "statementSave";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		String pan = request == null || request.getPan() == null ? "" : request.getPan().trim().toUpperCase(Locale.ROOT);
		String dob = request == null || request.getDob() == null ? "" : request.getDob().trim();
		String name = request == null || request.getName4() == null ? "" : request.getName4().trim().toUpperCase(Locale.ROOT);
		if ((!pan.isEmpty() && !PAN_SHAPE.matcher(pan).matches()) || (!dob.isEmpty() && !validBirthDate(dob)) || (!name.isEmpty() && !NAME_SHAPE.matcher(name).matches())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		Map<String, String> values = unpack(row.getCredentialEnc());
		if (!name.isEmpty()) {
			values.put(KEY_NAME, name);
		}
		if (!pan.isEmpty()) {
			values.put(KEY_PAN, pan);
		}
		if (!dob.isEmpty()) {
			values.put(KEY_DOB, dob);
		}
		if (blank(values.get(KEY_PAN)) && blank(values.get(KEY_DOB))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		userDao.saveCredential(userId, WealthServiceConstants.GMAIL_USER, cipher.encrypt(pack(values)));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return statusOf(userId);
	}

	@Override
	public GmailStatementSettingsRequestDto statementReveal() {
		WealthUserConnectionEntity row = userDao.find(currentUser(), WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		Map<String, String> values = unpack(row.getCredentialEnc());
		GmailStatementSettingsRequestDto shown = new GmailStatementSettingsRequestDto();
		shown.setPan(values.getOrDefault(KEY_PAN, ""));
		shown.setDob(values.getOrDefault(KEY_DOB, ""));
		shown.setName4(values.getOrDefault(KEY_NAME, ""));
		return shown;
	}

	@Override
	public GmailStatusDto statementClear() {
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		Map<String, String> values = unpack(row.getCredentialEnc());
		values.remove(KEY_PAN);
		values.remove(KEY_DOB);
		values.remove(KEY_NAME);
		userDao.saveCredential(userId, WealthServiceConstants.GMAIL_USER, cipher.encrypt(pack(values)));
		return statusOf(userId);
	}

	@Override
	public GmailStatementResultDto statements() {
		String methodName = "statements";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		App app = requireApp();
		Map<String, String> values = unpack(row.getCredentialEnc());
		List<String> passwords = passwordsFor(values);
		if (passwords.isEmpty() || blank(values.get("refreshToken"))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		GmailStatementResultDto result = new GmailStatementResultDto();
		List<String> figures = new ArrayList<>();
		AiBudget ai = new AiBudget(values.get(KEY_NAME));
		try {
			String access = client.accessToken(app.clientId(), app.clientSecret(), values.get("refreshToken"));
			for (String id : client.messageIds(app.baseUrl(), access, WealthServiceConstants.GMAIL_STATEMENT_QUERY.replace(WealthServiceConstants.GMAIL_DAYS_TOKEN, String.valueOf(app.statementDays())), app.statementMax())) {
				result.setRead(result.getRead() + 1);
				for (GmailClient.Attachment pdf : client.pdfAttachments(app.baseUrl(), access, id)) {
					openStatement(userId, id, pdf.name(), WealthServiceConstants.STATEMENT_ORIGIN_GMAIL, client.attachment(app.baseUrl(), access, id, pdf.id()), passwords, result, figures, ai);
				}
			}
		} catch (IllegalStateException e) {
			LOGGER.warn("Gmail statement read failed: " + e.getMessage());
			throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
		}
		result.setFigures(figures);
		Map<String, String> kept = new LinkedHashMap<>();
		kept.put(KEY_FIGURES, lines(figures));
		kept.put(KEY_FIGURES_AT, String.valueOf(System.currentTimeMillis()));
		kept.put(KEY_OPENED, String.valueOf(result.getOpened()));
		kept.put(KEY_LOCKED, String.valueOf(result.getLocked()));
		remember(userId, kept);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | opened=" + result.getOpened() + " locked=" + result.getLocked());
		return result;
	}

	// The transaction rows of an opened statement as the JSON kept on its row (date, time, description, amount, direction, sure). Nothing goes to Transactions until the user accepts the statement.
	private String rowsJson(List<StatementReader.Line> rows) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (StatementReader.Line row : rows) {
			Map<String, Object> one = new LinkedHashMap<>();
			one.put("day", row.day());
			one.put("time", row.time());
			one.put("description", row.description());
			one.put("amount", row.amount().toPlainString());
			one.put("debit", row.debit());
			one.put("sure", row.sure());
			list.add(one);
		}
		try {
			return MAPPER.writeValueAsString(list);
		} catch (IOException e) {
			throw new IllegalStateException("The statement rows could not be kept");
		}
	}

	@Override
	public BankImportResultDto statementAccept(StatementAcceptRequestDto request) {
		String methodName = "statementAccept";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthStatementFigureEntity statement = statementDao.find(userId, request.getId());
		if (statement == null || !WealthServiceConstants.STATEMENT_OPENED.equals(statement.getStatus())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		List<Map<String, Object>> rows;
		try {
			rows = MAPPER.readValue(statement.getLines() == null ? "[]" : statement.getLines(), new TypeReference<List<Map<String, Object>>>() {
			});
		} catch (IOException e) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		// Each row is checked on its own by the import: a new one is stored as a transaction, one already there is skipped, one with a problem goes to the Data quality review as its own item.
		// Rows go in by date and time, oldest first, so the list shows them in the order they happened. The reference is made from the statement and the row, so accepting again repeats nothing.
		List<BankLineDto> lines = new ArrayList<>();
		List<String> order = new ArrayList<>();
		int index = 0;
		for (Map<String, Object> row : rows) {
			String day = String.valueOf(row.get("day"));
			String time = row.get("time") == null ? "" : String.valueOf(row.get("time"));
			String description = String.valueOf(row.get("description"));
			BigDecimal amount = new BigDecimal(String.valueOf(row.get("amount")));
			BankLineDto line = new BankLineDto();
			line.setRef(WealthServiceConstants.STATEMENT_REF_PREFIX + digest(statement.getMailRef() + "|" + statement.getSource() + "|" + index + "|" + day + "|" + time + "|" + amount.toPlainString() + "|" + description));
			line.setDay(day);
			line.setDescription(description.length() > WealthServiceConstants.STATEMENT_DESCRIPTION_MAX_LENGTH ? description.substring(0, WealthServiceConstants.STATEMENT_DESCRIPTION_MAX_LENGTH) : description);
			line.setAmount(amount);
			line.setDebit(Boolean.TRUE.equals(row.get("debit")));
			line.setSure(Boolean.TRUE.equals(row.get("sure")));
			line.setSuspicious(java.time.LocalDate.parse(day).isAfter(java.time.LocalDate.now().plusDays(1)));
			lines.add(line);
			order.add(day + " " + time + " " + String.format("%06d", index));
			index++;
		}
		List<Integer> sorted = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			sorted.add(i);
		}
		sorted.sort((a, b) -> order.get(a).compareTo(order.get(b)));
		List<BankLineDto> inOrder = new ArrayList<>();
		sorted.forEach(i -> inOrder.add(lines.get(i)));
		BankImportResultDto imported = inOrder.isEmpty() ? new BankImportResultDto() : importService.importLines(WealthServiceConstants.WORKFLOW_SOURCE_STATEMENT, inOrder);
		statementDao.markAccepted(userId, request.getId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + inOrder.size());
		return imported;
	}

	private static String digest(String text) {
		return digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String digest(byte[] bytes) {
		try {
			byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 12; i++) {
				hex.append(String.format("%02x", hash[i]));
			}
			return hex.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("No SHA-256");
		}
	}

	// A statement's figures as one JSON object (label to value), replacing what was saved for the same mail and file. A statement with no figure is not saved.
	private void saveFigures(String userId, String mailId, String source, String origin, String status, String fileName, Map<String, String> figures, String linesJson) {
		WealthStatementFigureEntity entity = new WealthStatementFigureEntity();
		entity.setUserId(userId);
		entity.setMailRef(mailId);
		entity.setSource(sourceOf(source));
		entity.setOrigin(origin);
		entity.setStatus(status);
		entity.setFileName(fileName == null || fileName.length() > WealthServiceConstants.STATEMENT_FILE_NAME_MAX ? null : fileName);
		entity.setFigures(pack(figures));
		entity.setLines(linesJson);
		statementDao.upsert(entity);
	}

	private static String sourceOf(String source) {
		return source.length() > STATEMENT_SOURCE_MAX ? source.substring(0, STATEMENT_SOURCE_MAX) : source;
	}

	// The file name without .pdf and with long digit runs hidden, as shown on screen.
	private static String shownName(String fileName) {
		String name = fileName.replaceAll("(?i)\\.pdf$", "").replaceAll("\\d{6,}", "…");
		return name.isBlank() ? "statement" : name;
	}

	// Opens one PDF with the passwords. Opened: its figures are kept and its rows imported. Locked: kept as a history row with no figures. Returns whether it opened.
	// What the AI reading may use in one request: the saved first letters of the user's name (hidden before sending) and how many statements it may still read.
	private static final class AiBudget {
		private final String namePrefix;
		private int left = WealthServiceConstants.AI_STATEMENTS_PER_READ;

		private AiBudget(String namePrefix) {
			this.namePrefix = namePrefix;
		}
	}

	// A statement with no row the fixed readers know goes to the AI provider (Gemini, when it is switched on in Connections with a key), with personal details hidden first.
	// The rows come back checked and marked not sure, and wait for the user's Accept like any other. Never fails the read: no provider, no key, too long, a failed call or an answer with no valid row means no rows.
	private List<StatementReader.Line> aiRows(String text, AiBudget ai, GmailStatementResultDto result) {
		if (ai == null || ai.left <= 0 || text.length() > WealthServiceConstants.AI_TEXT_MAX) {
			return new ArrayList<>();
		}
		WealthConnectionEntity provider = connectionDao.findAll().stream().filter(row -> WealthServiceConstants.GEMINI_PROVIDER.equals(row.getProvider()) && row.isEnabled() && !blank(row.getCredentialEnc())
				&& !blank(row.getBaseUrl())).findFirst().orElse(null);
		if (provider == null) {
			return new ArrayList<>();
		}
		ai.left--;
		try {
			String key = cipher.decrypt(provider.getCredentialEnc());
			String answer = gemini.generateJson(provider.getBaseUrl(), key, WealthServiceConstants.GEMINI_MODEL, StatementReader.aiPrompt(StatementReader.redact(text, ai.namePrefix)));
			List<StatementReader.Line> rows = StatementReader.aiLines(answer, WealthServiceConstants.AI_ROWS_MAX, WealthServiceConstants.STATEMENT_DESCRIPTION_MAX_LENGTH);
			if (!rows.isEmpty()) {
				result.setAiStatements(result.getAiStatements() + 1);
			}
			return rows;
		} catch (RuntimeException e) {
			LOGGER.warn("The AI reading of a statement failed (" + e.getClass().getSimpleName() + ")");
			return new ArrayList<>();
		}
	}

	private boolean openStatement(String userId, String mailId, String fileName, String origin, byte[] pdf, List<String> passwords, GmailStatementResultDto result, List<String> figures, AiBudget ai) {
		String name = shownName(fileName);
		String text = StatementReader.text(pdf, passwords);
		if (text == null) {
			result.setLocked(result.getLocked() + 1);
			if (!result.getLockedNames().contains(name)) {
				result.getLockedNames().add(name);
			}
			saveFigures(userId, mailId, name, origin, WealthServiceConstants.STATEMENT_LOCKED, fileName, new LinkedHashMap<>(), "[]");
			return false;
		}
		result.setOpened(result.getOpened() + 1);
		for (String figure : StatementReader.figures(text)) {
			figures.add(name + ": " + figure);
		}
		List<StatementReader.Line> rows = StatementReader.lines(text);
		if (rows.isEmpty()) {
			rows = aiRows(text, ai, result);
		}
		saveFigures(userId, mailId, name, origin, WealthServiceConstants.STATEMENT_OPENED, fileName, StatementReader.figureMap(text), rowsJson(rows));
		result.setRows(result.getRows() + rows.size());
		return true;
	}

	@Override
	public List<StatementHistoryDto> statementHistory() {
		return statementDao.findHistory(currentUser()).stream().map(this::historyOf).toList();
	}

	private StatementHistoryDto historyOf(WealthStatementFigureEntity row) {
		StatementHistoryDto dto = new StatementHistoryDto();
		dto.setId(row.getId());
		dto.setName(row.getSource());
		dto.setFileName(row.getFileName());
		dto.setOrigin(row.getOrigin());
		dto.setStatus(row.getStatus());
		dto.setReceivedAt(row.getReceivedAt());
		dto.setReadAt(row.getReadAt());
		dto.setFigures(readJson(row.getFigures()));
		dto.setRowCount(row.getRowCount());
		dto.setAcceptedAt(row.getAcceptedAt());
		return dto;
	}

	@Override
	public GmailStatementResultDto statementUpload(StatementUploadRequestDto request) {
		String methodName = "statementUpload";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		String fileName = request == null || request.getFileName() == null ? "" : request.getFileName().trim();
		String typed = request == null || request.getPassword() == null ? "" : request.getPassword();
		if (fileName.isEmpty() || fileName.length() > WealthServiceConstants.STATEMENT_FILE_NAME_MAX || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") || fileName.matches(".*\\p{Cntrl}.*")
				|| typed.length() > WealthServiceConstants.STATEMENT_PASSWORD_MAX || blank(request.getData())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		byte[] pdf;
		try {
			pdf = java.util.Base64.getDecoder().decode(request.getData().trim());
		} catch (IllegalArgumentException e) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		if (pdf.length < PDF_MAGIC.length || pdf.length > UPLOAD_MAX_BYTES || !java.util.Arrays.equals(java.util.Arrays.copyOf(pdf, PDF_MAGIC.length), PDF_MAGIC)) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		Map<String, String> values = row == null ? new LinkedHashMap<>() : unpack(row.getCredentialEnc());
		List<String> passwords = passwordsFor(values);
		if (!typed.isEmpty()) {
			passwords.add(0, typed);
		}
		GmailStatementResultDto result = new GmailStatementResultDto();
		result.setRead(1);
		List<String> figures = new ArrayList<>();
		boolean opened = openStatement(userId, WealthServiceConstants.STATEMENT_UPLOAD_REF + digest(pdf), fileName, WealthServiceConstants.STATEMENT_ORIGIN_UPLOAD, pdf, passwords, result, figures,
				new AiBudget(values.get(KEY_NAME)));
		result.setFigures(figures);
		if (opened && request.isKeep() && !typed.isEmpty() && row != null) {
			keepPassword(userId, typed);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | opened=" + result.getOpened() + " locked=" + result.getLocked());
		return result;
	}

	@Override
	public GmailStatementResultDto statementRetry(StatementRetryRequestDto request) {
		String methodName = "statementRetry";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		String typed = request == null || request.getPassword() == null ? "" : request.getPassword();
		if (request == null || blank(request.getId()) || !ID_SHAPE.matcher(request.getId()).matches() || typed.isEmpty() || typed.length() > WealthServiceConstants.STATEMENT_PASSWORD_MAX) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthStatementFigureEntity statement = statementDao.find(userId, request.getId());
		if (statement == null || !WealthServiceConstants.STATEMENT_LOCKED.equals(statement.getStatus()) || !WealthServiceConstants.STATEMENT_ORIGIN_GMAIL.equals(statement.getOrigin())
				|| blank(statement.getFileName())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		App app = requireApp();
		Map<String, String> values = unpack(row.getCredentialEnc());
		if (blank(values.get("refreshToken"))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		List<String> passwords = passwordsFor(values);
		passwords.add(0, typed);
		GmailStatementResultDto result = new GmailStatementResultDto();
		result.setRead(1);
		List<String> figures = new ArrayList<>();
		try {
			String access = client.accessToken(app.clientId(), app.clientSecret(), values.get("refreshToken"));
			for (GmailClient.Attachment pdf : client.pdfAttachments(app.baseUrl(), access, statement.getMailRef())) {
				if (pdf.name().equals(statement.getFileName())) {
					boolean opened = openStatement(userId, statement.getMailRef(), pdf.name(), WealthServiceConstants.STATEMENT_ORIGIN_GMAIL, client.attachment(app.baseUrl(), access, statement.getMailRef(), pdf.id()),
							passwords, result, figures, new AiBudget(values.get(KEY_NAME)));
					if (opened && request.isKeep()) {
						keepPassword(userId, typed);
					}
				}
			}
		} catch (IllegalStateException e) {
			LOGGER.warn("Gmail statement retry failed: " + e.getMessage());
			throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
		}
		result.setFigures(figures);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | opened=" + result.getOpened());
		return result;
	}

	@Override
	public StatementFileDto statementDownload(StatementDownloadRequestDto request) {
		String methodName = "statementDownload";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		if (request == null || blank(request.getId()) || !ID_SHAPE.matcher(request.getId()).matches()) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthStatementFigureEntity statement = statementDao.find(userId, request.getId());
		if (statement == null || !WealthServiceConstants.STATEMENT_ORIGIN_GMAIL.equals(statement.getOrigin()) || blank(statement.getFileName()) || blank(statement.getMailRef())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		App app = requireApp();
		Map<String, String> values = unpack(row.getCredentialEnc());
		if (blank(values.get("refreshToken"))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		try {
			String access = client.accessToken(app.clientId(), app.clientSecret(), values.get("refreshToken"));
			for (GmailClient.Attachment pdf : client.pdfAttachments(app.baseUrl(), access, statement.getMailRef())) {
				if (pdf.name().equals(statement.getFileName())) {
					byte[] bytes = client.attachment(app.baseUrl(), access, statement.getMailRef(), pdf.id());
					if (bytes == null || bytes.length == 0 || bytes.length > StatementReader.MAX_BYTES) {
						throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
					}
					StatementFileDto file = new StatementFileDto();
					file.setFileName(pdf.name());
					file.setData(java.util.Base64.getEncoder().encodeToString(bytes));
					LOGGER.info(WealthServiceConstants.EXITING + methodName);
					return file;
				}
			}
		} catch (IllegalStateException e) {
			LOGGER.warn("Gmail statement download failed: " + e.getMessage());
			throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
		}
		throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
	}

	// A typed password that opened a statement is added to the saved ones (the newest first, at most five), in the same encrypted block.
	private void keepPassword(String userId, String password) {
		try {
			WealthUserConnectionEntity current = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
			if (current == null) {
				return;
			}
			Map<String, String> values = unpack(current.getCredentialEnc());
			List<String> extra = new ArrayList<>(unlines(values.get(KEY_EXTRA)));
			extra.remove(password);
			extra.add(0, password);
			values.put(KEY_EXTRA, String.join("\n", extra.subList(0, Math.min(extra.size(), EXTRA_PASSWORDS_MAX))));
			userDao.saveCredential(userId, WealthServiceConstants.GMAIL_USER, cipher.encrypt(pack(values)));
		} catch (RuntimeException e) {
			LOGGER.warn("A typed password could not be kept (" + e.getClass().getSimpleName() + ")");
		}
	}

	@Override
	public List<GmailStatementRowDto> statementList() {
		return statementDao.findByUser(currentUser()).stream().filter(row -> !"{}".equals(row.getFigures())).map(row -> {
			GmailStatementRowDto dto = new GmailStatementRowDto();
			dto.setId(row.getId());
			dto.setSource(row.getSource());
			dto.setReadAt(row.getReadAt());
			dto.setFigures(readJson(row.getFigures()));
			dto.setRowCount(row.getRowCount());
			dto.setAcceptedAt(row.getAcceptedAt());
			return dto;
		}).toList();
	}

	// The passwords banks lock statements with, made from what the user saved (nothing is guessed from anywhere else): the date of birth as DDMMYYYY, DDMMYY or DDMM; the PAN in capitals
	// or small letters; and the first four letters of the name (capitals or small) with the day and month of birth (HDFC, ICICI, Axis and others use this). Each is only tried to open the
	// user's own statement.
	private static List<String> passwordsFor(Map<String, String> values) {
		List<String> all = basePasswords(values);
		for (String extra : unlines(values.get(KEY_EXTRA))) {
			if (!extra.isBlank() && !all.contains(extra)) {
				all.add(extra);
			}
		}
		return all;
	}

	private static List<String> basePasswords(Map<String, String> values) {
		java.util.Set<String> passwords = new java.util.LinkedHashSet<>();
		String dob = values.get(KEY_DOB);
		String pan = values.get(KEY_PAN);
		String name = values.get(KEY_NAME);
		boolean dobSet = !blank(dob) && dob.length() == 8;
		if (dobSet) {
			passwords.add(dob);
			passwords.add(dob.substring(0, 4) + dob.substring(6));
			passwords.add(dob.substring(0, 4));
		}
		if (!blank(pan)) {
			passwords.add(pan);
			passwords.add(pan.toLowerCase(Locale.ROOT));
		}
		if (!blank(name) && dobSet) {
			passwords.add(name + dob.substring(0, 4));
			passwords.add(name.toLowerCase(Locale.ROOT) + dob.substring(0, 4));
		}
		return new ArrayList<>(passwords);
	}

	// DDMMYYYY that is a real date in the past.
	private static boolean validBirthDate(String dob) {
		try {
			return DOB_SHAPE.matcher(dob).matches() && java.time.LocalDate.parse(dob, java.time.format.DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
					.isBefore(java.time.LocalDate.now());
		} catch (java.time.format.DateTimeParseException e) {
			return false;
		}
	}

	@Override
	public GmailFetchResultDto fetch() {
		String methodName = "fetch";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		App app = requireApp();
		String refresh = unpack(row.getCredentialEnc()).get("refreshToken");
		GmailFetchResultDto result = new GmailFetchResultDto();
		Map<String, String> balances = new LinkedHashMap<>();
		List<BankLineDto> lines = new ArrayList<>();
		try {
			if (blank(refresh)) {
				throw new IllegalStateException("No saved Gmail token");
			}
			String access = client.accessToken(app.clientId(), app.clientSecret(), refresh);
			for (String id : client.messageIds(app.baseUrl(), access, WealthServiceConstants.GMAIL_ALERT_QUERY.replace(WealthServiceConstants.GMAIL_DAYS_TOKEN, String.valueOf(app.alertDays())), WealthServiceConstants.GMAIL_FETCH_MAX)) {
				GmailClient.Message mail = client.message(app.baseUrl(), access, id);
				result.setRead(result.getRead() + 1);
				BankAlertParser.Alert alert = BankAlertParser.parse(mail.from(), mail.text());
				if (alert == null) {
					result.setSkipped(result.getSkipped() + 1);
					continue;
				}
				lines.add(lineOf(id, alert));
				if (alert.balance() != null && !balances.containsKey(alert.bank() + alert.lastDigits())) {
					balances.put(alert.bank() + alert.lastDigits(), alert.bank() + " ••" + alert.lastDigits() + ": ₹" + alert.balance().toPlainString() + " on " + alert.date());
				}
			}
		} catch (IllegalStateException e) {
			LOGGER.warn("Gmail read failed: " + e.getMessage());
			userDao.saveCheck(userId, WealthServiceConstants.GMAIL_USER, FAILED);
			throw new KarthiException(BaseError.GMAIL_FETCH_FAILED);
		}
		userDao.saveCheck(userId, WealthServiceConstants.GMAIL_USER, OK);
		// No doubt = stored at once; a repeat = skipped; a duplicate-looking or fake-looking alert = the Data quality screen.
		BankImportResultDto imported = importService.importLines(WealthServiceConstants.WORKFLOW_SOURCE_ALERT, lines);
		result.setAdded(imported.getStored());
		result.setDuplicates(imported.getSame() + imported.getRepeated());
		result.setReview(imported.getReview());
		result.setBalancesSeen(new ArrayList<>(balances.values()));
		Map<String, String> kept = new LinkedHashMap<>();
		kept.put(KEY_BALANCES, lines(result.getBalancesSeen()));
		kept.put(KEY_BALANCES_AT, String.valueOf(System.currentTimeMillis()));
		remember(userId, kept);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | read=" + result.getRead() + " added=" + result.getAdded() + " review=" + result.getReview());
		return result;
	}

	@Override
	public List<GmailPendingItemDto> pending() {
		return transactionDao.findPendingFromGmail(currentUser()).stream().map(row -> {
			GmailPendingItemDto dto = new GmailPendingItemDto();
			dto.setId(row.getId());
			dto.setKind(row.getKind());
			dto.setAmount(row.getAmount());
			dto.setDate(row.getOccurredOn());
			dto.setDescription(row.getDescription());
			dto.setAccount(WealthServiceConstants.TRANSACTION_UNASSIGNED.equals(row.getAccount()) ? "" : row.getAccount());
			return dto;
		}).toList();
	}

	@Override
	public boolean review(GmailReviewRequestDto request) {
		String userId = currentUser();
		String decision = request == null ? "" : String.valueOf(request.getDecision());
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()
				|| !(WealthServiceConstants.GMAIL_ACCEPT.equals(decision) || WealthServiceConstants.GMAIL_DISMISS.equals(decision))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		boolean changed = transactionDao.setGmailReviewStatus(userId, request.getId(), WealthServiceConstants.GMAIL_ACCEPT.equals(decision) ? "accepted" : "dismissed");
		if (!changed) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		return true;
	}

	// One alert as a bank line for the import: the mail id is its reference. An alert dated more than a day ahead looks fake, and one without the last digits cannot be trusted; the import decides
	// the rest (a repeat, a duplicate of a transaction that is already there, or no doubt at all).
	private BankLineDto lineOf(String messageId, BankAlertParser.Alert alert) {
		BankLineDto line = new BankLineDto();
		line.setRef(WealthServiceConstants.GMAIL_REF_PREFIX + messageId);
		line.setBank(alert.bank());
		line.setAccountDigits(alert.lastDigits());
		line.setDay(alert.date());
		line.setDescription(alert.party());
		line.setCategory(WealthServiceConstants.GMAIL_CATEGORY);
		line.setKind(alert.kind());
		line.setAmount(alert.amount());
		line.setDebit(!"income".equals(alert.kind()));
		line.setSure(!blank(alert.lastDigits()));
		line.setSuspicious(java.time.LocalDate.parse(alert.date()).isAfter(java.time.LocalDate.now().plusDays(1)));
		return line;
	}

	@Override
	public GmailStatusDto status() {
		return statusOf(currentUser());
	}

	@Override
	public GmailConnectUrlDto connectUrl(GmailConnectRequestDto request) {
		String methodName = "connectUrl";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		App app = requireApp();
		String redirect = redirect(request == null ? null : request.getRedirectUri());
		long expires = System.currentTimeMillis() + Duration.ofMinutes(WealthServiceConstants.GMAIL_STATE_MINUTES).toMillis();
		GmailConnectUrlDto dto = new GmailConnectUrlDto();
		dto.setUrl(client.consentUrl(app.clientId(), redirect, cipher.encrypt(userId + STATE_SEPARATOR + expires)));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return dto;
	}

	@Override
	public GmailStatusDto connectFinish(GmailFinishRequestDto request) {
		String methodName = "connectFinish";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		if (request == null || blank(request.getCode()) || blank(request.getState()) || request.getCode().length() > WealthServiceConstants.GMAIL_CODE_MAX_LENGTH
				|| request.getState().length() > WealthServiceConstants.GMAIL_CODE_MAX_LENGTH) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		String redirect = redirect(request.getRedirectUri());
		App app = requireApp();
		if (!stateIsFor(userId, request.getState())) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		GmailClient.Tokens tokens;
		String address;
		try {
			tokens = client.exchange(app.clientId(), app.clientSecret(), redirect, request.getCode());
			address = client.mailboxAddress(app.baseUrl(), tokens.accessToken());
		} catch (IllegalStateException e) {
			LOGGER.warn("Gmail connect failed: " + e.getMessage());
			throw new KarthiException(BaseError.GMAIL_CONNECT_FAILED);
		}
		// Reconnecting keeps the saved statement passwords.
		WealthUserConnectionEntity before = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		Map<String, String> kept = before == null ? new LinkedHashMap<>() : unpack(before.getCredentialEnc());
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : List.of(KEY_PAN, KEY_DOB, KEY_NAME)) {
			if (!blank(kept.get(key))) {
				values.put(key, kept.get(key));
			}
		}
		values.put("refreshToken", tokens.refreshToken());
		values.put("email", address);
		WealthUserConnectionEntity entity = new WealthUserConnectionEntity();
		entity.setUserId(userId);
		entity.setProvider(WealthServiceConstants.GMAIL_USER);
		entity.setCredentialEnc(cipher.encrypt(pack(values)));
		entity.setEnabled(true);
		userDao.upsert(entity);
		userDao.saveCheck(userId, WealthServiceConstants.GMAIL_USER, OK);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return statusOf(userId);
	}

	@Override
	public GmailStatusDto test() {
		String methodName = "test";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONNECTED);
		}
		App app = requireApp();
		String refresh = unpack(row.getCredentialEnc()).get("refreshToken");
		boolean worked = false;
		if (refresh != null && !refresh.isEmpty()) {
			try {
				client.mailboxAddress(app.baseUrl(), client.accessToken(app.clientId(), app.clientSecret(), refresh));
				worked = true;
			} catch (IllegalStateException e) {
				LOGGER.warn("Gmail test failed: " + e.getMessage());
			}
		}
		userDao.saveCheck(userId, WealthServiceConstants.GMAIL_USER, worked ? OK : FAILED);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | " + (worked ? OK : FAILED));
		return statusOf(userId);
	}

	@Override
	public boolean disconnect() {
		String methodName = "disconnect";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		String userId = currentUser();
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row == null) {
			return true;
		}
		Map<String, String> values = unpack(row.getCredentialEnc());
		String refresh = values.get("refreshToken");
		if (refresh != null && !refresh.isEmpty()) {
			client.revoke(refresh);
		}
		// Disconnecting takes Gmail away, not the saved statement passwords: they stay (encrypted) for the next connection. With none saved the row is removed as before.
		Map<String, String> passwords = new LinkedHashMap<>();
		for (String key : List.of(KEY_PAN, KEY_DOB, KEY_NAME)) {
			if (!blank(values.get(key))) {
				passwords.put(key, values.get(key));
			}
		}
		if (passwords.isEmpty()) {
			userDao.delete(userId, WealthServiceConstants.GMAIL_USER);
		} else {
			WealthUserConnectionEntity kept = new WealthUserConnectionEntity();
			kept.setUserId(userId);
			kept.setProvider(WealthServiceConstants.GMAIL_USER);
			kept.setCredentialEnc(cipher.encrypt(pack(passwords)));
			kept.setEnabled(false);
			userDao.upsert(kept);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return true;
	}

	@Override
	public GmailStatusDto appSave(GmailAppSaveRequestDto request) {
		String methodName = "appSave";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		String clientId = request.getClientId() == null ? "" : request.getClientId().trim();
		String secret = request.getClientSecret() == null ? "" : request.getClientSecret().trim();
		if (tooLongOrControl(clientId) || (!secret.isEmpty() && tooLongOrControl(secret))) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		WealthCommonConnectionEntity saved = commonDao.find(WealthServiceConstants.GMAIL_APP);
		Map<String, String> before = saved == null ? new LinkedHashMap<>() : unpack(saved.getCredentialEnc());
		if (clientId.isEmpty()) {
			clientId = before.getOrDefault("clientId", "");
		}
		if (secret.isEmpty()) {
			secret = before.getOrDefault("clientSecret", "");
		}
		if (clientId.isEmpty() || secret.isEmpty()) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		Map<String, String> values = new LinkedHashMap<>();
		values.put("clientId", clientId);
		values.put("clientSecret", secret);
		values.put(KEY_STATEMENT_DAYS, limited(request.getStatementDays(), before.get(KEY_STATEMENT_DAYS), WealthServiceConstants.GMAIL_DAYS_MIN, WealthServiceConstants.GMAIL_DAYS_MAX));
		values.put(KEY_STATEMENT_MAX, limited(request.getStatementMax(), before.get(KEY_STATEMENT_MAX), WealthServiceConstants.GMAIL_MAILS_MIN, WealthServiceConstants.GMAIL_MAILS_MAX));
		values.put(KEY_ALERT_DAYS, limited(request.getAlertDays(), before.get(KEY_ALERT_DAYS), WealthServiceConstants.GMAIL_DAYS_MIN, WealthServiceConstants.GMAIL_DAYS_MAX));
		WealthCommonConnectionEntity entity = new WealthCommonConnectionEntity();
		entity.setProvider(WealthServiceConstants.GMAIL_APP);
		entity.setCredentialEnc(cipher.encrypt(pack(values)));
		entity.setKeyHint(clientId.length() > WealthServiceConstants.KEY_HINT_LENGTH ? clientId.substring(clientId.length() - WealthServiceConstants.KEY_HINT_LENGTH) : "");
		commonDao.upsert(entity);
		switchService(true);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return statusOf(currentUser());
	}

	@Override
	public boolean appRemove() {
		String methodName = "appRemove";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		commonDao.delete(WealthServiceConstants.GMAIL_APP);
		switchService(false);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return true;
	}

	// The main row (the API config) is the on/off switch for the whole service; it is created if the migration's row is missing.
	private void switchService(boolean on) {
		WealthConnectionEntity row = mainRow();
		if (row == null) {
			row = new WealthConnectionEntity();
			row.setProvider(WealthServiceConstants.GMAIL_APP);
			row.setLabel(GMAIL_LABEL);
			row.setBaseUrl(GMAIL_ADDRESS);
			row.setPriority(1);
		}
		row.setEnabled(on);
		row.setCredentialEnc(null);
		row.setKeyHint(null);
		row.setCredentialExtraEnc(null);
		connectionDao.upsert(row);
	}

	private WealthConnectionEntity mainRow() {
		return connectionDao.findAll().stream().filter(row -> WealthServiceConstants.GMAIL_APP.equals(row.getProvider())).findFirst().orElse(null);
	}

	// The Google client when the service is on and both values can be read; otherwise null.
	private App app() {
		WealthConnectionEntity main = mainRow();
		WealthCommonConnectionEntity common = commonDao.find(WealthServiceConstants.GMAIL_APP);
		if (main == null || !main.isEnabled() || common == null) {
			return null;
		}
		Map<String, String> values = unpack(common.getCredentialEnc());
		String id = values.get("clientId");
		String secret = values.get("clientSecret");
		return blank(id) || blank(secret) ? null : new App(id, secret, main.getBaseUrl(), number(values.get(KEY_STATEMENT_DAYS), WealthServiceConstants.GMAIL_STATEMENT_DAYS_DEFAULT,
				WealthServiceConstants.GMAIL_DAYS_MIN, WealthServiceConstants.GMAIL_DAYS_MAX),
				number(values.get(KEY_STATEMENT_MAX), WealthServiceConstants.GMAIL_STATEMENT_MAX_DEFAULT, WealthServiceConstants.GMAIL_MAILS_MIN, WealthServiceConstants.GMAIL_MAILS_MAX),
				number(values.get(KEY_ALERT_DAYS), WealthServiceConstants.GMAIL_ALERT_DAYS_DEFAULT, WealthServiceConstants.GMAIL_DAYS_MIN, WealthServiceConstants.GMAIL_DAYS_MAX));
	}

	// The number sent, else the one already saved, as text; outside the limits is refused.
	private static String limited(Integer sent, String saved, int min, int max) {
		if (sent == null) {
			return saved == null ? "" : saved;
		}
		if (sent < min || sent > max) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		return String.valueOf(sent);
	}

	// A saved number inside its limits; missing, unreadable or outside gives the default.
	private static int number(String saved, int fallback, int min, int max) {
		try {
			int value = Integer.parseInt(saved == null ? "" : saved.trim());
			return value < min || value > max ? fallback : value;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private App requireApp() {
		App app = app();
		if (app == null) {
			throw new KarthiException(BaseError.GMAIL_NOT_CONFIGURED);
		}
		return app;
	}

	private GmailStatusDto statusOf(String userId) {
		GmailStatusDto dto = new GmailStatusDto();
		App configured = app();
		dto.setAppConfigured(configured != null);
		if (configured != null) {
			dto.setStatementDays(configured.statementDays());
			dto.setStatementMax(configured.statementMax());
			dto.setAlertDays(configured.alertDays());
		}
		WealthUserConnectionEntity row = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
		if (row != null && row.isEnabled()) {
			dto.setConnected(true);
			Map<String, String> values = unpack(row.getCredentialEnc());
			dto.setEmail(mask(values.get("email")));
			dto.setStatementPasswordsSet(!blank(values.get(KEY_PAN)) || !blank(values.get(KEY_DOB)));
			dto.setPanSaved(!blank(values.get(KEY_PAN)));
			dto.setDobSaved(!blank(values.get(KEY_DOB)));
			dto.setNameSaved(!blank(values.get(KEY_NAME)));
			dto.setLastBalances(unlines(values.get(KEY_BALANCES)));
			dto.setLastBalancesAt(at(values.get(KEY_BALANCES_AT)));
			dto.setLastFigures(unlines(values.get(KEY_FIGURES)));
			dto.setLastFiguresAt(at(values.get(KEY_FIGURES_AT)));
			dto.setLastStatementsOpened(count(values.get(KEY_OPENED)));
			dto.setLastStatementsLocked(count(values.get(KEY_LOCKED)));
			dto.setStatus(row.getLastStatus());
			dto.setCheckedAt(row.getLastCheckedAt());
		}
		return dto;
	}

	// The state carries the user and an expiry, encrypted, so it cannot be made or changed outside the server. It is only valid for the user it was made for.
	private boolean stateIsFor(String userId, String state) {
		String plain = cipher.decrypt(state);
		if (plain == null) {
			return false;
		}
		int at = plain.lastIndexOf(STATE_SEPARATOR);
		if (at < 1 || !plain.substring(0, at).equals(userId)) {
			return false;
		}
		try {
			return System.currentTimeMillis() <= Long.parseLong(plain.substring(at + 1));
		} catch (NumberFormatException e) {
			return false;
		}
	}

	// https, or http on this machine; the UI callback path, with no query, fragment or login part. Google itself only accepts an address registered on the client.
	private String redirect(String value) {
		String uri = value == null ? "" : value.trim();
		if (uri.isEmpty() || uri.length() > WealthServiceConstants.GMAIL_REDIRECT_MAX_LENGTH || CONTROL.matcher(uri).find()) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		try {
			URI parsed = new URI(uri);
			String host = parsed.getHost();
			boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
			boolean secure = "https".equals(parsed.getScheme()) || ("http".equals(parsed.getScheme()) && local);
			if (!secure || host == null || parsed.getRawQuery() != null || parsed.getRawFragment() != null || parsed.getRawUserInfo() != null
					|| !WealthServiceConstants.GMAIL_CALLBACK_PATH.equals(parsed.getPath())) {
				throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
			}
		} catch (URISyntaxException e) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		return uri;
	}

	private String currentUser() {
		String userId = RequestHeaderUtil.getUserId();
		if (blank(userId)) {
			throw new KarthiException(BaseError.GMAIL_REQUEST_INVALID);
		}
		return userId;
	}

	// What a read found replaces what the last one kept, in the user's own connection row (the same encrypted block as the token; no table of its own). Read again just before
	// the write so a change made meanwhile (a saved PAN) is not lost. A failure here never fails the read.
	private void remember(String userId, Map<String, String> kept) {
		try {
			WealthUserConnectionEntity current = userDao.find(userId, WealthServiceConstants.GMAIL_USER);
			if (current == null) {
				return;
			}
			Map<String, String> values = unpack(current.getCredentialEnc());
			values.putAll(kept);
			userDao.saveCredential(userId, WealthServiceConstants.GMAIL_USER, cipher.encrypt(pack(values)));
		} catch (RuntimeException e) {
			LOGGER.warn("What was read could not be kept (" + e.getClass().getSimpleName() + ")");
		}
	}

	// At most 40 lines of at most 200 characters, one per line.
	private static String lines(List<String> list) {
		List<String> kept = new ArrayList<>();
		for (String line : list == null ? List.<String>of() : list) {
			if (kept.size() < KEPT_LINES_MAX && line != null && !line.isBlank()) {
				kept.add(line.replace('\n', ' ').length() > KEPT_LINE_LENGTH ? line.replace('\n', ' ').substring(0, KEPT_LINE_LENGTH) : line.replace('\n', ' '));
			}
		}
		return String.join("\n", kept);
	}

	private static List<String> unlines(String text) {
		return blank(text) ? new ArrayList<>() : new ArrayList<>(List.of(text.split("\n")));
	}

	private static java.sql.Timestamp at(String millis) {
		try {
			return blank(millis) ? null : new java.sql.Timestamp(Long.parseLong(millis));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int count(String text) {
		try {
			return blank(text) ? 0 : Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// The values kept together as one encrypted block (JSON); empty when there is none or it cannot be read.
	private Map<String, String> unpack(String stored) {
		return readJson(cipher.decrypt(stored));
	}

	// A JSON object of text to text; empty when there is none or it cannot be read.
	private Map<String, String> readJson(String json) {
		if (blank(json)) {
			return new LinkedHashMap<>();
		}
		try {
			return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		} catch (IOException e) {
			return new LinkedHashMap<>();
		}
	}

	private String pack(Map<String, String> values) {
		try {
			return MAPPER.writeValueAsString(values);
		} catch (IOException e) {
			throw new IllegalStateException("The connection could not be saved");
		}
	}

	// k***@gmail.com: enough to recognise the mailbox, never the whole address.
	private static String mask(String email) {
		int at = email == null ? -1 : email.indexOf('@');
		return at < 1 ? "" : email.charAt(0) + "***" + email.substring(at);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean tooLongOrControl(String value) {
		return value.length() > WealthServiceConstants.GMAIL_VALUE_MAX_LENGTH || CONTROL.matcher(value).find();
	}
}
