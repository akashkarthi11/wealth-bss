package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthAccountBalanceDao;
import com.wealth.bss.common.model.dao.IfWealthAccountDao;
import com.wealth.bss.common.model.dao.IfWealthAccountSplitDao;
import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthAccountBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitBalanceEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitEntity;
import com.wealth.bss.common.model.entity.WealthAccountSplitMoveEntity;
import com.wealth.bss.common.model.entity.WealthAccountSubtypeEntity;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfAccountsService;
import com.wealth.bss.serviceinterface.IfRequestContextService;
import com.wealth.common.assembler.GenericAssembler;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.AccountIdRequestDto;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.account.AccountBalanceDto;
import com.wealth.common.model.dto.account.AccountCreateRequestDto;
import com.wealth.common.model.dto.account.BalanceRecordRequestDto;
import com.wealth.common.model.dto.account.BalanceRecordResultDto;
import com.wealth.common.model.dto.account.FdTermsRequestDto;
import com.wealth.common.model.dto.account.AccountDetailDto;
import com.wealth.common.model.dto.account.AccountDto;
import com.wealth.common.model.dto.account.AccountSplitDto;
import com.wealth.common.model.dto.account.AccountSubtypeDto;
import com.wealth.common.model.dto.account.AccountTotalsDto;
import com.wealth.common.model.dto.account.AccountsOverviewDto;
import com.wealth.common.model.dto.account.BankDto;
import com.wealth.common.model.dto.account.FdTermsDto;
import com.wealth.common.model.dto.account.SplitMoveDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

@Service
public class AccountsServiceImple implements IfAccountsService {

	private static final KarthiLogger LOGGER = new KarthiLogger(AccountsServiceImple.class);

	private static final Pattern ID_SHAPE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private final IfWealthAccountDao wealthAccountDao;
	private final IfWealthAccountBalanceDao wealthAccountBalanceDao;
	private final IfWealthAccountSplitDao wealthAccountSplitDao;
	private final IfWealthInvestmentDao wealthInvestmentDao;
	private final IfNexusReadService nexusReadService;
	private final IfRequestContextService requestContextService;

	public AccountsServiceImple(IfWealthAccountDao wealthAccountDao, IfWealthAccountBalanceDao wealthAccountBalanceDao,
			IfWealthAccountSplitDao wealthAccountSplitDao, IfWealthInvestmentDao wealthInvestmentDao, IfNexusReadService nexusReadService,
			IfRequestContextService requestContextService) {
		this.nexusReadService = nexusReadService;
		this.requestContextService = requestContextService;
		this.wealthAccountDao = wealthAccountDao;
		this.wealthAccountBalanceDao = wealthAccountBalanceDao;
		this.wealthAccountSplitDao = wealthAccountSplitDao;
		this.wealthInvestmentDao = wealthInvestmentDao;
	}

	// The overview is not computed on every call: the nexus returns it from its cache and refreshes it when it is older than the refresh time (rules 10).
	@Override
	public AccountsOverviewDto overview() {
		return stamp(nexusReadService.read(WealthServiceConstants.NEXUS_READ_ACCOUNTS, AccountsOverviewDto.class, this::buildOverview));
	}

	@Override
	public AccountsOverviewDto refresh() {
		return stamp(nexusReadService.hardRefresh(WealthServiceConstants.NEXUS_READ_ACCOUNTS, AccountsOverviewDto.class, this::buildOverview));
	}

	private AccountsOverviewDto stamp(CachedValueDto<AccountsOverviewDto> cached) {
		AccountsOverviewDto overview = cached.getData();
		overview.setFetchedAt(cached.getFetchedAt());
		overview.setStale(cached.isStale());
		return overview;
	}

	private AccountsOverviewDto buildOverview() {
		String methodName = "overview";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = caller();
		Map<String, WealthAccountSubtypeEntity> subtypes = subtypesByCode();
		Map<String, WealthAccountBalanceEntity> latest = new HashMap<>();
		for (WealthAccountBalanceEntity balance : wealthAccountBalanceDao.findLatestByUser(paginationDto)) {
			latest.put(balance.getAccountId(), balance);
		}
		Map<String, WealthAccountBalanceEntity> held = holdingValues(paginationDto);
		Map<String, BankDto> banks = new LinkedHashMap<>();
		AccountTotalsDto totals = emptyTotals();
		List<AccountDto> investmentAccounts = new ArrayList<>();
		for (WealthAccountEntity entity : wealthAccountDao.findByUser(paginationDto)) {
			if (!WealthServiceConstants.ACCOUNT_ACTIVE.equals(entity.getStatus())) {
				continue;
			}
			WealthAccountSubtypeEntity subtype = subtypes.get(entity.getSubtypeCode());
			AccountDto account = toAccount(entity, subtype, valued(subtype, held.get(entity.getId()), latest.get(entity.getId())));
			add(totals, account);
			// A broker account valued by its holdings (demat) is an investment, not a bank account: it counts in the totals and shows on Investments.
			if (subtype != null && WealthServiceConstants.VALUATION_HOLDINGS.equals(subtype.getValuation())) {
				investmentAccounts.add(account);
				continue;
			}
			BankDto bank = banks.computeIfAbsent(entity.getBankName(), name -> newBank(name));
			bank.getAccounts().add(account);
			add(bank.getTotals(), account);
		}
		for (BankDto bank : banks.values()) {
			close(bank.getTotals());
		}
		close(totals);
		AccountsOverviewDto overview = new AccountsOverviewDto();
		overview.setTotals(totals);
		overview.setBanks(new ArrayList<>(banks.values()));
		overview.setInvestmentAccounts(investmentAccounts);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | banks=" + banks.size());
		return overview;
	}

	// A bank has no table of its own: it is the institution text of its accounts, so it appears with its first account. A new account starts with an unknown balance.
	@Override
	public AccountDto create(AccountCreateRequestDto request) {
		String methodName = "create";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		WealthAccountSubtypeEntity subtype = request.getSubtype() == null ? null : subtypesByCode().get(request.getSubtype().trim());
		if (subtype == null) {
			throw new KarthiException(BaseError.ACCOUNT_TYPE_INVALID);
		}
		String institution = clean(request.getInstitution(), WealthServiceConstants.ACCOUNT_TEXT_MAX_LENGTH);
		String name = clean(request.getName(), WealthServiceConstants.ACCOUNT_TEXT_MAX_LENGTH);
		String masked = clean(request.getMaskedIdentifier(), WealthServiceConstants.ACCOUNT_MASK_MAX_LENGTH);
		if (institution.isEmpty() || name.isEmpty() || (request.getMaskedIdentifier() != null && request.getMaskedIdentifier().trim().length() > WealthServiceConstants.ACCOUNT_MASK_MAX_LENGTH)
				|| (request.getInstitution() != null && request.getInstitution().trim().length() > WealthServiceConstants.ACCOUNT_TEXT_MAX_LENGTH)
				|| (request.getName() != null && request.getName().trim().length() > WealthServiceConstants.ACCOUNT_TEXT_MAX_LENGTH)) {
			throw new KarthiException(BaseError.ACCOUNT_DETAILS_INVALID);
		}
		String currency = request.getCurrency() == null || request.getCurrency().isBlank()
				? (WealthServiceConstants.SUBTYPE_US_BROKERAGE.equals(subtype.getCode()) ? WealthServiceConstants.CURRENCY_USD : WealthServiceConstants.CURRENCY_INR)
				: request.getCurrency().trim().toUpperCase();
		if (!WealthServiceConstants.CURRENCY_INR.equals(currency) && !WealthServiceConstants.CURRENCY_USD.equals(currency)) {
			throw new KarthiException(BaseError.ACCOUNT_CURRENCY_INVALID);
		}
		FdTermsDto terms = request.getFdTerms();
		if (terms != null && (!WealthServiceConstants.SUBTYPE_FIXED_DEPOSIT.equals(subtype.getCode()) || !validTerms(terms))) {
			throw new KarthiException(BaseError.FD_TERMS_INVALID);
		}
		WealthAccountEntity entity = new WealthAccountEntity();
		entity.setUserId(RequestHeaderUtil.getUserId());
		entity.setAccountKey(accountKey(institution, subtype.getCode(), name, masked));
		entity.setBankName(institution);
		entity.setAccountName(name);
		entity.setSubtypeCode(subtype.getCode());
		entity.setMaskedIdentifier(masked.isEmpty() ? null : masked);
		entity.setCurrency(currency);
		if (terms != null) {
			entity.setFdPrincipal(terms.getPrincipal());
			entity.setFdBookedOn(terms.getBookedOn());
			entity.setFdMaturityOn(terms.getMaturityOn());
			entity.setFdPayout(terms.getPayout());
			entity.setFdCompounding(terms.getCompounding());
		}
		WealthAccountEntity saved = wealthAccountDao.insert(entity);
		if (saved == null) {
			throw new KarthiException(BaseError.ACCOUNT_EXISTS);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, entity.getUserId());
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_INVESTMENTS, entity.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return toAccount(saved, subtype, null);
	}

	@Override
	public BalanceRecordResultDto recordBalance(BalanceRecordRequestDto request) {
		String methodName = "recordBalance";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || !isId(request.getAccountId())) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		PaginationDto paginationDto = caller();
		paginationDto.setId(request.getAccountId());
		WealthAccountEntity account = wealthAccountDao.findById(paginationDto);
		if (account == null) {
			throw new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND);
		}
		WealthAccountSubtypeEntity subtype = subtypesByCode().get(account.getSubtypeCode());
		if (!WealthServiceConstants.ACCOUNT_ACTIVE.equals(account.getStatus()) || subtype == null || !WealthServiceConstants.VALUATION_BALANCE.equals(subtype.getValuation())) {
			throw new KarthiException(BaseError.BALANCE_NOT_ALLOWED);
		}
		String status = request.getStatus() == null || request.getStatus().isBlank() ? WealthServiceConstants.BALANCE_VERIFIED : request.getStatus().trim();
		long now = System.currentTimeMillis();
		Timestamp asOf = request.getAsOf() == null ? new Timestamp(now) : request.getAsOf();
		if (request.getBalance() == null || request.getBalance().abs().doubleValue() > WealthServiceConstants.BALANCE_ABS_MAX || asOf.getTime() > now
				|| !(WealthServiceConstants.BALANCE_VERIFIED.equals(status) || WealthServiceConstants.BALANCE_REPORTED.equals(status))) {
			throw new KarthiException(BaseError.BALANCE_INVALID);
		}
		String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim().substring(0, Math.min(request.getNote().trim().length(), WealthServiceConstants.BALANCE_NOTE_MAX_LENGTH));
		WealthAccountBalanceEntity entity = new WealthAccountBalanceEntity();
		entity.setUserId(paginationDto.getUserId());
		entity.setAccountId(account.getId());
		entity.setAsOf(asOf);
		entity.setBalance(request.getBalance());
		entity.setStatus(status);
		entity.setNote(note);
		entity.setEnteredBy(WealthServiceConstants.ACTOR_OWNER);
		BalanceRecordResultDto result = new BalanceRecordResultDto();
		if (request.isDryRun()) {
			result.setBalance(GenericAssembler.toDTO(entity, AccountBalanceDto.class));
			return result;
		}
		WealthAccountBalanceEntity saved = wealthAccountBalanceDao.insert(entity);
		if (saved == null) {
			result.setReason(WealthServiceConstants.BALANCE_ALREADY_STORED);
			return result;
		}
		if (WealthServiceConstants.SUBTYPES_WITH_ACCOUNT_COPY.contains(account.getSubtypeCode()) && WealthServiceConstants.BALANCE_VERIFIED.equals(status)) {
			wealthAccountBalanceDao.updateAccountCopy(entity);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, entity.getUserId());
		result.setStored(true);
		result.setBalance(GenericAssembler.toDTO(saved, AccountBalanceDto.class));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return result;
	}

	@Override
	public AccountDto updateFdTerms(FdTermsRequestDto request) {
		String methodName = "updateFdTerms";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		requestContextService.requireAdmin();
		if (request == null || !isId(request.getAccountId()) || request.getFdTerms() == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		PaginationDto paginationDto = caller();
		paginationDto.setId(request.getAccountId());
		WealthAccountEntity account = wealthAccountDao.findById(paginationDto);
		if (account == null) {
			throw new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND);
		}
		FdTermsDto terms = request.getFdTerms();
		if (!WealthServiceConstants.SUBTYPE_FIXED_DEPOSIT.equals(account.getSubtypeCode()) || !validTerms(terms)) {
			throw new KarthiException(BaseError.FD_TERMS_INVALID);
		}
		WealthAccountEntity change = new WealthAccountEntity();
		change.setUserId(paginationDto.getUserId());
		change.setId(account.getId());
		change.setFdPrincipal(terms.getPrincipal());
		change.setFdBookedOn(terms.getBookedOn());
		change.setFdMaturityOn(terms.getMaturityOn());
		change.setFdPayout(terms.getPayout());
		change.setFdCompounding(terms.getCompounding());
		WealthAccountEntity saved = wealthAccountDao.updateFdTerms(change);
		if (saved == null) {
			throw new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_ACCOUNTS, change.getUserId());
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return toAccount(saved, subtypesByCode().get(saved.getSubtypeCode()), null);
	}

	private static String clean(String value, int max) {
		return value == null ? "" : value.trim().length() > max ? value.trim().substring(0, max) : value.trim();
	}

	// The key that makes the same institution, type, name and number one account: lower-case words joined with dashes, as the old app wrote it.
	private static String accountKey(String institution, String subtype, String name, String masked) {
		return java.util.stream.Stream.of(slug(institution), subtype, slug(name), slug(masked)).filter(part -> !part.isEmpty()).collect(java.util.stream.Collectors.joining("-"));
	}

	private static String slug(String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	// Principal above 0, maturity after booking, and only the allowed payout and compounding words.
	private static boolean validTerms(FdTermsDto terms) {
		return (terms.getPrincipal() == null || terms.getPrincipal().signum() > 0)
				&& (terms.getBookedOn() == null || terms.getMaturityOn() == null || terms.getMaturityOn().after(terms.getBookedOn()))
				&& (terms.getPayout() == null || WealthServiceConstants.FD_PAYOUTS.contains(terms.getPayout()))
				&& (terms.getCompounding() == null || WealthServiceConstants.FD_COMPOUNDING.contains(terms.getCompounding()));
	}

	@Override
	public AccountDetailDto detail(IdRequestDto request) {
		String methodName = "detail";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = caller();
		paginationDto.setId(request == null ? null : request.getId());
		if (!isId(paginationDto.getId())) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		WealthAccountEntity entity = wealthAccountDao.findById(paginationDto);
		if (entity == null) {
			throw new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND);
		}
		paginationDto.setPageSize(WealthServiceConstants.BALANCE_HISTORY_ROWS);
		List<WealthAccountBalanceEntity> history = wealthAccountBalanceDao.findHistory(paginationDto);
		AccountDetailDto detail = new AccountDetailDto();
		WealthAccountSubtypeEntity subtype = subtypesByCode().get(entity.getSubtypeCode());
		detail.setAccount(toAccount(entity, subtype, valued(subtype, holdingValues(paginationDto).get(entity.getId()), history.isEmpty() ? null : history.get(0))));
		detail.setBalances(GenericAssembler.toDTOList(history, AccountBalanceDto.class));
		detail.setSplits(buildSplits(paginationDto));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | balances=" + history.size());
		return detail;
	}

	@Override
	public List<AccountSubtypeDto> subtypes() {
		String methodName = "subtypes";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		List<AccountSubtypeDto> result = GenericAssembler.toDTOList(wealthAccountDao.findAllSubtypes(), AccountSubtypeDto.class);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + result.size());
		return result;
	}

	@Override
	public List<AccountSplitDto> splits(AccountIdRequestDto request) {
		String methodName = "splits";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = caller();
		paginationDto.setId(request == null ? null : request.getAccountId());
		if (!isId(paginationDto.getId())) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		if (wealthAccountDao.findById(paginationDto) == null) {
			throw new KarthiException(BaseError.WEALTH_ACCOUNT_NOT_FOUND);
		}
		List<AccountSplitDto> result = buildSplits(paginationDto);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | rows=" + result.size());
		return result;
	}

	// A malformed id is refused here; the database would otherwise fail the UUID cast.
	private boolean isId(String id) {
		return id != null && ID_SHAPE.matcher(id).matches();
	}

	private PaginationDto caller() {
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
		return paginationDto;
	}

	private Map<String, WealthAccountSubtypeEntity> subtypesByCode() {
		Map<String, WealthAccountSubtypeEntity> byCode = new HashMap<>();
		for (WealthAccountSubtypeEntity subtype : wealthAccountDao.findAllSubtypes()) {
			byCode.put(subtype.getCode(), subtype);
		}
		return byCode;
	}

	// The market value of the holdings (quantity x latest price) by account; shown as an estimate because prices move.
	private Map<String, WealthAccountBalanceEntity> holdingValues(PaginationDto paginationDto) {
		Map<String, WealthAccountBalanceEntity> held = new HashMap<>();
		for (WealthAccountBalanceEntity value : wealthInvestmentDao.findHoldingValuesByUser(paginationDto)) {
			value.setStatus(WealthServiceConstants.BALANCE_ESTIMATED);
			held.put(value.getAccountId(), value);
		}
		return held;
	}

	// An account valued by holdings (demat, funds, gold) shows its holdings value, or none while it has no priced holding; its own balance rows are not its value (Angel One's cash is
	// kept there). Every other account shows its latest balance.
	private WealthAccountBalanceEntity valued(WealthAccountSubtypeEntity subtype, WealthAccountBalanceEntity held, WealthAccountBalanceEntity balance) {
		return subtype != null && WealthServiceConstants.VALUATION_HOLDINGS.equals(subtype.getValuation()) ? held : balance;
	}

	private AccountDto toAccount(WealthAccountEntity entity, WealthAccountSubtypeEntity subtype, WealthAccountBalanceEntity balance) {
		AccountDto dto = GenericAssembler.toDTO(entity, AccountDto.class);
		dto.setCategory(subtype == null ? null : subtype.getCategory());
		if (balance != null) {
			dto.setBalance(balance.getBalance());
			dto.setBalanceAsOf(balance.getAsOf());
			dto.setBalanceStatus(balance.getStatus());
		}
		return dto;
	}

	private BankDto newBank(String name) {
		BankDto bank = new BankDto();
		bank.setName(name);
		bank.setTotals(emptyTotals());
		bank.setAccounts(new ArrayList<>());
		return bank;
	}

	private AccountTotalsDto emptyTotals() {
		AccountTotalsDto totals = new AccountTotalsDto();
		totals.setCasa(BigDecimal.ZERO);
		totals.setInvestment(BigDecimal.ZERO);
		totals.setLiability(BigDecimal.ZERO);
		totals.setNetWorth(BigDecimal.ZERO);
		return totals;
	}

	// Rupee balances only; a balance waiting for reconciliation is left out; a holdings account counts at the market value of its holdings.
	private void add(AccountTotalsDto totals, AccountDto account) {
		if (account.getBalance() == null || !WealthServiceConstants.CURRENCY_INR.equals(account.getCurrency())
				|| WealthServiceConstants.BALANCE_PENDING_RECONCILIATION.equals(account.getBalanceStatus())) {
			return;
		}
		if (WealthServiceConstants.CATEGORY_CASA.equals(account.getCategory())) {
			totals.setCasa(totals.getCasa().add(account.getBalance()));
		} else if (WealthServiceConstants.CATEGORY_INVESTMENT.equals(account.getCategory())) {
			totals.setInvestment(totals.getInvestment().add(account.getBalance()));
		} else if (WealthServiceConstants.CATEGORY_LIABILITY.equals(account.getCategory())) {
			totals.setLiability(totals.getLiability().add(account.getBalance()));
		}
	}

	private void close(AccountTotalsDto totals) {
		totals.setNetWorth(totals.getCasa().add(totals.getInvestment()).subtract(totals.getLiability()));
	}

	// Estimated balance = the latest saved split balance plus the moves made after it; interest shares come with the interest module.
	private List<AccountSplitDto> buildSplits(PaginationDto paginationDto) {
		List<WealthAccountSplitEntity> splits = wealthAccountSplitDao.findByAccount(paginationDto);
		if (splits.isEmpty()) {
			return new ArrayList<>();
		}
		Map<String, WealthAccountSplitBalanceEntity> base = new HashMap<>();
		for (WealthAccountSplitBalanceEntity balance : wealthAccountSplitDao.findLatestBalancesByAccount(paginationDto)) {
			base.put(balance.getSplitId(), balance);
		}
		Map<String, List<WealthAccountSplitMoveEntity>> moves = new HashMap<>();
		for (WealthAccountSplitMoveEntity move : wealthAccountSplitDao.findMovesByAccount(paginationDto)) {
			moves.computeIfAbsent(move.getSplitId(), id -> new ArrayList<>()).add(move);
		}
		List<AccountSplitDto> result = new ArrayList<>();
		for (WealthAccountSplitEntity split : splits) {
			AccountSplitDto dto = GenericAssembler.toDTO(split, AccountSplitDto.class);
			WealthAccountSplitBalanceEntity saved = base.get(split.getId());
			List<WealthAccountSplitMoveEntity> mine = moves.getOrDefault(split.getId(), List.of());
			BigDecimal moved = BigDecimal.ZERO;
			boolean movedAfter = false;
			for (WealthAccountSplitMoveEntity move : mine) {
				if (saved == null || saved.getAsOf() == null || move.getOccurredAt().after(saved.getAsOf())) {
					moved = moved.add(move.getAmount());
					movedAfter = true;
				}
			}
			if (saved != null || movedAfter) {
				BigDecimal start = saved == null ? BigDecimal.ZERO : saved.getBalance();
				dto.setEstimatedBalance(start.add(moved));
			}
			dto.setBalanceStatus(movedAfter ? WealthServiceConstants.BALANCE_ESTIMATED : saved == null ? null : saved.getStatus());
			dto.setBalanceAsOf(saved == null ? null : saved.getAsOf());
			dto.setMoves(GenericAssembler.toDTOList(mine, SplitMoveDto.class));
			result.add(dto);
		}
		return result;
	}
}
