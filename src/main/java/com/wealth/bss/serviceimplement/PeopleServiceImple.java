package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wealth.bss.common.model.dao.IfWealthPeopleDao;
import com.wealth.bss.common.model.dto.PaginationDto;
import com.wealth.bss.common.model.entity.WealthBillSplitEntity;
import com.wealth.bss.common.model.entity.WealthBillSplitShareEntity;
import com.wealth.bss.common.model.entity.WealthLendCommentEntity;
import com.wealth.bss.common.model.entity.WealthLendRequestEntity;
import com.wealth.bss.common.model.entity.WealthPersonAliasEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntity;
import com.wealth.bss.common.model.entity.WealthPersonEntryEntity;
import com.wealth.bss.common.model.entity.WealthPersonRollupEntity;
import com.wealth.bss.common.model.entity.WealthTransactionEntity;
import com.wealth.bss.common.utils.security.LoginRateLimiter;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.bss.serviceinterface.IfPeopleService;
import com.wealth.common.generic.BaseError;
import com.wealth.common.generic.KarthiException;
import com.wealth.common.logger.KarthiLogger;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.IdRequestDto;
import com.wealth.common.model.dto.people.BillSplitDto;
import com.wealth.common.model.dto.people.LendCommentDto;
import com.wealth.common.model.dto.people.LendRequestDto;
import com.wealth.common.model.dto.people.PeopleOverviewDto;
import com.wealth.common.model.dto.people.CommentCreateRequestDto;
import com.wealth.common.model.dto.people.CommentCreateResultDto;
import com.wealth.common.model.dto.people.EntryNoteRequestDto;
import com.wealth.common.model.dto.people.EntryNoteResultDto;
import com.wealth.common.model.dto.people.RequestDecideRequestDto;
import com.wealth.common.model.dto.people.RequestDecideResultDto;
import com.wealth.common.model.dto.people.LendPaidRequestDto;
import com.wealth.common.model.dto.people.LendPaidResultDto;
import com.wealth.common.model.dto.people.LendRemarkRequestDto;
import com.wealth.common.model.dto.people.LendRemarkResultDto;
import com.wealth.common.model.dto.people.LendViewRequestRowDto;
import com.wealth.common.model.dto.people.LendViewDto;
import com.wealth.common.model.dto.people.LendViewItemDto;
import com.wealth.common.model.dto.people.LendViewRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateRequestDto;
import com.wealth.common.model.dto.people.ShareUpdateResultDto;
import com.wealth.common.model.dto.people.PeopleTotalsDto;
import com.wealth.common.model.dto.people.PersonDto;
import com.wealth.common.model.dto.people.PersonMergeRequestDto;
import com.wealth.common.model.dto.people.PersonMergeResultDto;
import com.wealth.common.model.dto.people.PersonEntryRequestDto;
import com.wealth.common.model.dto.people.PersonEntryResultDto;
import com.wealth.common.model.dto.people.PersonItemDto;
import com.wealth.common.model.dto.people.PersonRollupDto;
import com.wealth.common.model.dto.people.PersonSaveRequestDto;
import com.wealth.common.model.dto.people.PersonSaveResultDto;
import com.wealth.common.model.dto.people.RecordStatusRequestDto;
import com.wealth.common.model.dto.people.RecordStatusResultDto;
import com.wealth.common.model.dto.people.RoundOffRequestDto;
import com.wealth.common.model.dto.people.RoundOffResultDto;
import com.wealth.common.model.dto.people.SharePaidRequestDto;
import com.wealth.common.model.dto.people.SharePaidResultDto;
import com.wealth.common.model.dto.people.SplitCreateRequestDto;
import com.wealth.common.model.dto.people.SplitPersonRequestDto;
import com.wealth.common.model.dto.people.SplitShareDto;
import com.wealth.common.model.dto.people.SuggestionDto;
import com.wealth.common.model.dto.people.UnlinkedDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

// What each person owes: their shares of split bills plus money lent, minus money paid back, plus simple daily interest when a rate is set. Transfers stay
// transfers and bills stay expenses: this only adds them up per person. The rules are those of the old app (api/_loans.js), moved here unchanged.
@Service
public class PeopleServiceImple implements IfPeopleService {

	private static final KarthiLogger LOGGER = new KarthiLogger(PeopleServiceImple.class);
	private static final ZoneId IST = ZoneId.of(WealthServiceConstants.BROKER_ZONE);
	private static final Pattern LEAD_PAYMENT = Pattern.compile("^(upi\\s+)?(transfer|payment|money)\\s+(to|from)\\s+", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEAD_PAID = Pattern.compile("^(paid\\s+to|received\\s+from|sent\\s+to)\\s+", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEAD_TITLE = Pattern.compile("^(mr|mrs|ms|miss|dr)\\.?\\s+", Pattern.CASE_INSENSITIVE);
	private static final Pattern ID_SHAPE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final Pattern RECEIVED = Pattern.compile("received|from", Pattern.CASE_INSENSITIVE);
	private static final Pattern PEER_TO_PEER = Pattern.compile("p2p|liquiloans|lendbox|faircent|lendenclub", Pattern.CASE_INSENSITIVE);

	// What one person's figures add up to.
	record Figures(double lent, double repaid, double interest, double owed, double overpaid, String lastMovement, boolean overdue) {
	}

	// Money or a share, as the figures see it.
	record Move(String on, double amount, String direction) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final IfWealthPeopleDao peopleDao;
	private final IfNexusReadService nexusReadService;
	private final Clock clock;
	private final LoginRateLimiter rateLimiter;

	@Autowired
	public PeopleServiceImple(IfWealthPeopleDao peopleDao, IfNexusReadService nexusReadService, LoginRateLimiter rateLimiter) {
		this(peopleDao, nexusReadService, Clock.systemUTC(), rateLimiter);
	}

	PeopleServiceImple(IfWealthPeopleDao peopleDao, IfNexusReadService nexusReadService, Clock clock) {
		this(peopleDao, nexusReadService, clock, null);
	}

	PeopleServiceImple(IfWealthPeopleDao peopleDao, IfNexusReadService nexusReadService, Clock clock, LoginRateLimiter rateLimiter) {
		this.peopleDao = peopleDao;
		this.nexusReadService = nexusReadService;
		this.clock = clock;
		this.rateLimiter = rateLimiter;
	}

	// Not read from the tables on every call: the nexus returns it from its cache and refreshes it when it is older than the refresh time (rules 10).
	@Override
	public PeopleOverviewDto list() {
		return stamp(nexusReadService.read(WealthServiceConstants.NEXUS_READ_PEOPLE, PeopleOverviewDto.class, this::buildOverview));
	}

	@Override
	public PeopleOverviewDto refresh() {
		return stamp(nexusReadService.hardRefresh(WealthServiceConstants.NEXUS_READ_PEOPLE, PeopleOverviewDto.class, this::buildOverview));
	}

	private PeopleOverviewDto stamp(CachedValueDto<PeopleOverviewDto> cached) {
		PeopleOverviewDto result = cached.getData();
		result.setFetchedAt(cached.getFetchedAt());
		result.setStale(cached.isStale());
		return result;
	}

	// ---- a new split ----

	// Paise spread over weights with largest-remainder rounding, so the parts add up exactly (the old app's spread).
	static long[] spread(long paise, long[] weights) {
		long[] out = new long[weights.length];
		BigInteger sum = BigInteger.valueOf(Arrays.stream(weights).sum());
		if (sum.signum() <= 0) {
			return out;
		}
		BigInteger[] remainder = new BigInteger[weights.length];
		long used = 0;
		for (int i = 0; i < weights.length; i++) {
			BigInteger[] division = BigInteger.valueOf(paise).multiply(BigInteger.valueOf(weights[i])).divideAndRemainder(sum);
			out[i] = division[0].longValue();
			remainder[i] = division[1];
			used += out[i];
		}
		long left = paise - used;
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < weights.length; i++) {
			order.add(i);
		}
		order.sort((a, b) -> remainder[b].compareTo(remainder[a]) != 0 ? remainder[b].compareTo(remainder[a]) : Integer.compare(a, b));
		for (int i = 0; i < order.size() && left > 0; i++, left--) {
			out[order.get(i)]++;
		}
		return out;
	}

	// {base, tax} in paise for the owner (first, when in the split) and then each person. total includes tax. Equal: everyone pays the same. Amounts: each person's amount before tax is given and the
	// owner's part is what is left. Tax is shared in proportion to each part (equally in equal mode).
	static List<long[]> computeShares(long total, long tax, String mode, boolean includeMe, long[] given) {
		if (total <= 0 || tax < 0 || tax > total) {
			throw new KarthiException(BaseError.SPLIT_AMOUNTS_INVALID);
		}
		long sub = total - tax;
		int people = given.length;
		int parts = people + (includeMe ? 1 : 0);
		long[] bases;
		if (WealthServiceConstants.PEOPLE_SPLIT_EQUAL.equals(mode)) {
			long[] ones = new long[parts];
			Arrays.fill(ones, 1);
			bases = spread(sub, ones);
		} else {
			long others = 0;
			for (long base : given) {
				if (base < 0) {
					throw new KarthiException(BaseError.SPLIT_AMOUNTS_INVALID);
				}
				others += base;
			}
			if (others > sub || (!includeMe && others != sub)) {
				throw new KarthiException(BaseError.SPLIT_AMOUNTS_INVALID);
			}
			bases = new long[parts];
			for (int i = 0; i < people; i++) {
				bases[i + (includeMe ? 1 : 0)] = given[i];
			}
			if (includeMe) {
				bases[0] = sub - others;
			}
		}
		long[] taxes;
		if (WealthServiceConstants.PEOPLE_SPLIT_EQUAL.equals(mode)) {
			long[] ones = new long[parts];
			Arrays.fill(ones, 1);
			taxes = spread(tax, ones);
		} else {
			taxes = spread(tax, bases);
		}
		List<long[]> out = new ArrayList<>();
		for (int i = 0; i < parts; i++) {
			out.add(new long[] { bases[i], taxes[i] });
		}
		return out;
	}

	private static BigDecimal rupees(long paise) {
		return BigDecimal.valueOf(paise, 2);
	}

	private static long paise(BigDecimal amount) {
		if (amount.abs().doubleValue() > WealthServiceConstants.PEOPLE_SPLIT_TOTAL_MAX) {
			throw new KarthiException(BaseError.SPLIT_AMOUNTS_INVALID);
		}
		return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private static String cleanName(String value) {
		String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
		return name.length() > WealthServiceConstants.PEOPLE_NAME_MAX_LENGTH ? name.substring(0, WealthServiceConstants.PEOPLE_NAME_MAX_LENGTH) : name;
	}

	@Override
	public BillSplitDto createSplit(SplitCreateRequestDto request) {
		String methodName = "createSplit";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getTransactionId() == null || !ID_SHAPE.matcher(request.getTransactionId()).matches() || request.getPeople() == null || request.getPeople().isEmpty()
				|| request.getPeople().size() > WealthServiceConstants.PEOPLE_SPLIT_MAX_PEOPLE
				|| !(WealthServiceConstants.PEOPLE_SPLIT_EQUAL.equals(request.getMode()) || WealthServiceConstants.PEOPLE_SPLIT_AMOUNTS.equals(request.getMode()))) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthTransactionEntity transaction = peopleDao.findTransactionForSplit(userId, request.getTransactionId());
		// a transfer counts as money sent unless its category says received (the direction rule the People list uses)
		boolean sent = transaction != null && !(transaction.getCategory() != null && RECEIVED.matcher(transaction.getCategory()).find());
		if (transaction == null || !("expense".equals(transaction.getKind()) || "investment".equals(transaction.getKind()) || ("transfer".equals(transaction.getKind()) && sent))
				|| peopleDao.transactionLinkedToPerson(userId, transaction.getId())) {
			throw new KarthiException(BaseError.SPLIT_TRANSACTION_INVALID);
		}
		if (WealthServiceConstants.PEOPLE_STATUS_ACTIVE.equals(transaction.getSplitStatus())) {
			throw new KarthiException(BaseError.SPLIT_ALREADY_EXISTS);
		}
		boolean equal = WealthServiceConstants.PEOPLE_SPLIT_EQUAL.equals(request.getMode());
		boolean includeMe = !Boolean.FALSE.equals(request.getIncludeMe());
		long total = paise(transaction.getAmount());
		long tax = request.getTax() == null ? 0 : paise(request.getTax());
		List<WealthPersonEntity> people = new ArrayList<>();
		long[] given = new long[request.getPeople().size()];
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < given.length; i++) {
			SplitPersonRequestDto one = request.getPeople().get(i);
			WealthPersonEntity person = new WealthPersonEntity();
			String key;
			if (one == null) {
				throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
			} else if (one.getPersonId() != null && !one.getPersonId().isBlank()) {
				if (!ID_SHAPE.matcher(one.getPersonId()).matches()) {
					throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
				}
				person.setId(one.getPersonId());
				key = one.getPersonId().toLowerCase();
			} else {
				person.setName(cleanName(one.getName()));
				if (person.getName().isEmpty()) {
					throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
				}
				key = person.getName().toLowerCase();
			}
			if (!seen.add(key)) {
				throw new KarthiException(BaseError.SPLIT_PERSON_INVALID);
			}
			if (!equal) {
				if (one.getBase() == null) {
					throw new KarthiException(BaseError.SPLIT_AMOUNTS_INVALID);
				}
				given[i] = paise(one.getBase());
			}
			people.add(person);
		}
		List<long[]> parts = computeShares(total, tax, request.getMode(), includeMe, given);
		WealthBillSplitEntity split = new WealthBillSplitEntity();
		split.setTransactionId(transaction.getId());
		String title = cut(transaction.getDescription(), WealthServiceConstants.PEOPLE_TITLE_MAX_LENGTH);
		split.setTitle(title.isEmpty() ? cut(transaction.getCategory(), WealthServiceConstants.PEOPLE_TITLE_MAX_LENGTH) : title);
		split.setOn(transaction.getDay());
		split.setTotal(rupees(total));
		split.setTax(rupees(tax));
		split.setMode(request.getMode());
		String note = cut(request.getNote(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		split.setNote(note.isEmpty() ? null : note);
		WealthBillSplitShareEntity mine = null;
		List<WealthBillSplitShareEntity> shares = new ArrayList<>();
		long othersOwe = 0;
		for (int i = 0; i < parts.size(); i++) {
			long[] part = parts.get(i);
			WealthBillSplitShareEntity share = new WealthBillSplitShareEntity();
			share.setBase(rupees(part[0]));
			share.setTax(rupees(part[1]));
			share.setAmount(rupees(part[0] + part[1]));
			if (includeMe && i == 0) {
				mine = share;
			} else {
				shares.add(share);
				othersOwe += part[0] + part[1];
			}
		}
		String splitId = peopleDao.createSplit(userId, split, people, shares, mine);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		BillSplitDto dto = new BillSplitDto();
		dto.setId(splitId);
		dto.setTransactionId(split.getTransactionId());
		dto.setTitle(split.getTitle());
		dto.setOn(split.getOn());
		dto.setTotal(split.getTotal());
		dto.setTax(split.getTax());
		dto.setMode(split.getMode());
		dto.setMyShare(mine == null ? BigDecimal.ZERO.setScale(2) : mine.getAmount());
		dto.setOthersOwe(rupees(othersOwe));
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return dto;
	}

	@Override
	public Boolean cancelSplit(IdRequestDto request) {
		String methodName = "cancelSplit";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getId() == null || !ID_SHAPE.matcher(request.getId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		boolean changed = peopleDao.cancelSplit(userId, request.getId());
		if (changed) {
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | changed=" + changed);
		return changed;
	}

	// ---- entries, a paid share and a round-off (the rules of the old app, api/_loans.js) ----

	// A day as the old API took it: empty is today (IST); otherwise a real yyyy-MM-dd that is not in the future.
	private String dayOrToday(String value) {
		String today = LocalDate.now(clock.withZone(IST)).toString();
		if (value == null || value.isBlank()) {
			return today;
		}
		String day;
		try {
			day = LocalDate.parse(value.trim()).toString();
		} catch (DateTimeParseException e) {
			throw new KarthiException(BaseError.DATE_INVALID);
		}
		if (day.compareTo(today) > 0) {
			throw new KarthiException(BaseError.DATE_INVALID);
		}
		return day;
	}

	private static boolean amountOk(BigDecimal amount) {
		return amount != null && amount.signum() > 0 && amount.doubleValue() <= WealthServiceConstants.PEOPLE_AMOUNT_MAX;
	}

	// Money lent or paid back. Linking: the transfers must be the caller's, be transfers, and not be linked to a person or split already; sent counts as lent and received as paid back (one transfer may force
	// the direction). By hand: a direction and an amount. The person is added when the name is new, a removed one comes back, a settled one re-opens when lent to, and each transfer's name is kept as another name.
	@Override
	public PersonEntryResultDto createEntries(PersonEntryRequestDto request) {
		String methodName = "createEntries";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		List<String> ids = request.getTransactionIds() == null ? List.of() : request.getTransactionIds();
		if (ids.size() > WealthServiceConstants.PEOPLE_ENTRY_MAX_TRANSACTIONS || new HashSet<>(ids).size() != ids.size() || ids.stream().anyMatch(id -> id == null || !ID_SHAPE.matcher(id).matches())) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		boolean byId = request.getPersonId() != null && !request.getPersonId().isBlank();
		String name = cleanName(request.getName());
		if (byId ? !ID_SHAPE.matcher(request.getPersonId()).matches() : name.isEmpty()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthPersonEntity person = new WealthPersonEntity();
		String ownName = name;
		if (byId) {
			WealthPersonEntity found = peopleDao.findPerson(userId, request.getPersonId());
			if (found == null || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(found.getStatus())) {
				throw new KarthiException(BaseError.PERSON_NOT_FOUND);
			}
			person.setId(found.getId());
			ownName = found.getName();
		} else {
			person.setName(name);
		}
		String note = cut(request.getNote(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		List<WealthPersonEntryEntity> entries = new ArrayList<>();
		List<String> aliases = new ArrayList<>();
		if (!ids.isEmpty()) {
			List<WealthTransactionEntity> transfers = peopleDao.findTransfersForEntries(userId, ids);
			if (transfers.size() != ids.size()) {
				throw new KarthiException(BaseError.ENTRY_TRANSACTION_INVALID);
			}
			boolean forced = ids.size() == 1 && (WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(request.getDirection()) || WealthServiceConstants.PEOPLE_DIRECTION_IN.equals(request.getDirection()));
			String ownKey = nameKey(ownName);
			Set<String> seen = new HashSet<>();
			for (WealthTransactionEntity transfer : transfers) {
				if (!WealthServiceConstants.PEOPLE_KIND_TRANSFER.equals(transfer.getKind())) {
					throw new KarthiException(BaseError.ENTRY_TRANSACTION_INVALID);
				}
				if (transfer.getSplitStatus() != null) {
					throw new KarthiException(BaseError.ENTRY_TRANSACTION_LINKED);
				}
				WealthPersonEntryEntity entry = new WealthPersonEntryEntity();
				entry.setDirection(forced ? request.getDirection() : directionOf(transfer));
				entry.setAmount(transfer.getAmount());
				entry.setOn(transfer.getDay());
				entry.setTransactionId(transfer.getId());
				entry.setNote(note.isEmpty() ? null : note);
				entries.add(entry);
				String alias = personOf(transfer.getDescription());
				if (!alias.isEmpty() && !alias.toLowerCase().equals(ownKey) && seen.add(alias.toLowerCase())) {
					aliases.add(alias);
				}
			}
		} else {
			if (!(WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(request.getDirection()) || WealthServiceConstants.PEOPLE_DIRECTION_IN.equals(request.getDirection())) || !amountOk(request.getAmount())) {
				throw new KarthiException(BaseError.ENTRY_AMOUNT_INVALID);
			}
			WealthPersonEntryEntity entry = new WealthPersonEntryEntity();
			entry.setDirection(request.getDirection());
			entry.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
			entry.setOn(dayOrToday(request.getOccurredOn()));
			entry.setNote(note.isEmpty() ? null : note);
			entries.add(entry);
		}
		PersonEntryResultDto result = new PersonEntryResultDto();
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		result.setPerson(ownName);
		result.setEntries(entries.size());
		if (!result.isDryRun()) {
			result.setPersonId(peopleDao.addEntries(userId, person, entries, aliases));
			result.setStored(true);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | entries=" + entries.size() + " | dryRun=" + result.isDryRun());
		return result;
	}

	// The person paid their share of a split bill: adds one paid-back entry for the share's amount. Already paid is not an error and stores nothing; a share closed by a round-off is refused.
	@Override
	public SharePaidResultDto markSharePaid(SharePaidRequestDto request) {
		String methodName = "markSharePaid";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getSplitId() == null || !ID_SHAPE.matcher(request.getSplitId()).matches() || request.getPersonId() == null || !ID_SHAPE.matcher(request.getPersonId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthBillSplitShareEntity share = peopleDao.findShareToPay(userId, request.getSplitId(), request.getPersonId());
		if (share == null) {
			throw new KarthiException(BaseError.SHARE_NOT_FOUND);
		}
		SharePaidResultDto result = new SharePaidResultDto();
		if (peopleDao.sharePaid(request.getPersonId(), request.getSplitId())) {
			result.setAlready(WealthServiceConstants.PEOPLE_ALREADY_PAID);
			return result;
		}
		if (share.getRollupId() != null || !amountOk(share.getAmount())) {
			throw new KarthiException(BaseError.SHARE_CLOSED);
		}
		String on = dayOrToday(request.getOccurredOn());
		String note = cut(request.getNote(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		result.setAmount(share.getAmount());
		result.setOn(on);
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		if (!result.isDryRun()) {
			result.setStored(peopleDao.addSharePaid(userId, request.getPersonId(), request.getSplitId(), share.getAmount(), on, note.isEmpty() ? null : note));
			if (result.isStored()) {
				nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
			}
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + result.isStored());
		return result;
	}

	// Closes every counted open line with a person into one round-off (what is owed now, what was already back, one new amount carried forward). The figures are worked out here from the person's open lines.
	@Override
	public RoundOffResultDto roundOff(RoundOffRequestDto request) {
		String methodName = "roundOff";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getPersonId() == null || !ID_SHAPE.matcher(request.getPersonId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		PersonDto person = buildOverview().getPeople().stream().filter(p -> request.getPersonId().equals(p.getId())).findFirst().orElseThrow(() -> new KarthiException(BaseError.PERSON_NOT_FOUND));
		List<PersonItemDto> counted = person.getItems().stream().filter(i -> !i.isWrittenOff()).toList();
		if (counted.isEmpty()) {
			throw new KarthiException(BaseError.ROUNDOFF_NOTHING_OPEN);
		}
		BigDecimal total = person.getOwed().setScale(2, RoundingMode.HALF_UP);
		BigDecimal back = request.getAlreadyBack() == null ? BigDecimal.ZERO : request.getAlreadyBack();
		BigDecimal carried = request.getNewAmount() == null ? total.subtract(back).max(BigDecimal.ZERO) : request.getNewAmount();
		if (back.signum() < 0 || back.doubleValue() > WealthServiceConstants.PEOPLE_AMOUNT_MAX || carried.signum() < 0 || carried.doubleValue() > WealthServiceConstants.PEOPLE_AMOUNT_MAX) {
			throw new KarthiException(BaseError.ROUNDOFF_AMOUNT_INVALID);
		}
		back = back.setScale(2, RoundingMode.HALF_UP);
		carried = carried.setScale(2, RoundingMode.HALF_UP);
		String on = dayOrToday(request.getClosedOn());
		RoundOffResultDto result = new RoundOffResultDto();
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		result.setPerson(person.getName());
		result.setClosedItems(counted.size());
		result.setTotalOwed(total);
		result.setAlreadyBack(back);
		result.setNewAmount(carried);
		result.setClosedOn(on);
		result.setRoundedBy(total.subtract(back).subtract(carried));
		if (!result.isDryRun()) {
			String note = cut(request.getNote(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
			WealthPersonRollupEntity rollup = new WealthPersonRollupEntity();
			rollup.setPersonId(person.getId());
			rollup.setOn(on);
			rollup.setTotalOwed(total);
			rollup.setAlreadyBack(back);
			rollup.setNewAmount(carried);
			rollup.setClosedItems(counted.size());
			rollup.setNote(note.isEmpty() ? null : note);
			String entryNote = "Round-off " + on + ": " + counted.size() + (counted.size() == 1 ? " entry" : " entries") + ", owed " + WealthServiceConstants.PEOPLE_RUPEE + plain(total) + ", already back "
					+ WealthServiceConstants.PEOPLE_RUPEE + plain(back);
			result.setClosed(peopleDao.roundOff(userId, rollup, entryNote));
			result.setRollupId(rollup.getId());
			result.setStored(true);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | closedItems=" + counted.size() + " | dryRun=" + result.isDryRun());
		return result;
	}

	// A line or a share written off (kept and shown, no longer counted), undone, or a line deleted (hidden, not counted; its transfer can be linked again). Nothing closed by a round-off can change.
	@Override
	public RecordStatusResultDto updateRecord(RecordStatusRequestDto request) {
		String methodName = "updateRecord";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getStatus() == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String status = request.getStatus();
		boolean known = WealthServiceConstants.PEOPLE_STATUS_ACTIVE.equals(status) || WealthServiceConstants.PEOPLE_STATUS_WRITTEN_OFF.equals(status);
		boolean byEntry = request.getEntryId() != null && !request.getEntryId().isBlank();
		String userId = RequestHeaderUtil.getUserId();
		RecordStatusResultDto result = new RecordStatusResultDto();
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		result.setTo(status);
		if (byEntry) {
			if (!ID_SHAPE.matcher(request.getEntryId()).matches() || !(known || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(status))) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			WealthPersonEntryEntity entry = peopleDao.findEntryForStatus(userId, request.getEntryId());
			if (entry == null || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(entry.getStatus())) {
				throw new KarthiException(BaseError.ENTRY_NOT_FOUND);
			}
			if (entry.getRollupId() != null) {
				throw new KarthiException(BaseError.ENTRY_CLOSED);
			}
			result.setFrom(entry.getStatus());
			if (!status.equals(entry.getStatus()) && !result.isDryRun()) {
				result.setStored(peopleDao.setEntryStatus(userId, entry.getId(), status));
			}
			if (result.isStored()) {
				nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
				nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
			}
		} else {
			if (request.getSplitId() == null || !ID_SHAPE.matcher(request.getSplitId()).matches() || request.getPersonId() == null || !ID_SHAPE.matcher(request.getPersonId()).matches() || !known) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			WealthBillSplitShareEntity share = peopleDao.findShareForStatus(userId, request.getSplitId(), request.getPersonId());
			if (share == null) {
				throw new KarthiException(BaseError.SHARE_NOT_FOUND);
			}
			if (share.getRollupId() != null) {
				throw new KarthiException(BaseError.SHARE_CLOSED);
			}
			result.setFrom(share.getStatus());
			if (!status.equals(share.getStatus()) && !result.isDryRun()) {
				result.setStored(peopleDao.setShareStatus(userId, share.getId(), status));
			}
			if (result.isStored()) {
				nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
			}
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + result.isStored());
		return result;
	}

	// ---- a person added or changed ----

	private static boolean oneOf(String value, String... allowed) {
		return Arrays.asList(allowed).contains(value);
	}

	// The owner's text: control characters become spaces, trimmed, cut to the limit; empty is nothing.
	private static String noteOf(String value) {
		String text = cut(value == null ? "" : value.replaceAll("[\\u0000-\\u001f]", " "), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		return text.isEmpty() ? null : text;
	}

	@Override
	public PersonSaveResultDto savePerson(PersonSaveRequestDto request) {
		String methodName = "savePerson";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		boolean isNew = request.getPersonId() == null || request.getPersonId().isBlank();
		if (!isNew && !ID_SHAPE.matcher(request.getPersonId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		Set<String> changed = new LinkedHashSet<>();
		WealthPersonEntity values = new WealthPersonEntity();
		values.setKind(WealthServiceConstants.PEOPLE_KIND_PERSONAL);
		values.setStatus(WealthServiceConstants.PEOPLE_STATUS_OPEN);
		if (request.getName() != null) {
			String name = cleanName(request.getName());
			if (name.isEmpty()) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			values.setName(name);
			changed.add(WealthServiceConstants.PEOPLE_FIELD_NAME);
		}
		if (request.getKind() != null) {
			if (!oneOf(request.getKind(), WealthServiceConstants.PEOPLE_KIND_PERSONAL, WealthServiceConstants.PEOPLE_KIND_PLATFORM)) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			values.setKind(request.getKind());
			changed.add(WealthServiceConstants.PEOPLE_FIELD_KIND);
		}
		if (request.getDueOn() != null) {
			if (!request.getDueOn().isBlank()) {
				try {
					values.setDueOn(LocalDate.parse(request.getDueOn().trim()).toString());
				} catch (DateTimeParseException e) {
					throw new KarthiException(BaseError.DATE_INVALID);
				}
			}
			changed.add(WealthServiceConstants.PEOPLE_FIELD_DUE_ON);
		}
		if (request.getAnnualRate() != null) {
			if (!request.getAnnualRate().isBlank()) {
				BigDecimal percent;
				try {
					percent = new BigDecimal(request.getAnnualRate().trim());
				} catch (NumberFormatException e) {
					throw new KarthiException(BaseError.RATE_INVALID);
				}
				if (percent.signum() < 0 || percent.doubleValue() > WealthServiceConstants.PEOPLE_RATE_MAX) {
					throw new KarthiException(BaseError.RATE_INVALID);
				}
				values.setAnnualRate(percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
			}
			changed.add(WealthServiceConstants.PEOPLE_FIELD_RATE);
		}
		if (request.getStatus() != null) {
			if (!oneOf(request.getStatus(), WealthServiceConstants.PEOPLE_STATUS_OPEN, WealthServiceConstants.PEOPLE_STATUS_SETTLED, WealthServiceConstants.PEOPLE_STATUS_WRITTEN_OFF,
					WealthServiceConstants.PEOPLE_STATUS_REMOVED)) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			values.setStatus(request.getStatus());
			changed.add(WealthServiceConstants.PEOPLE_FIELD_STATUS);
		}
		if (request.getNote() != null) {
			values.setNote(noteOf(request.getNote()));
			changed.add(WealthServiceConstants.PEOPLE_FIELD_NOTE);
		}
		if (request.getHidden() != null) {
			values.setHidden(request.getHidden());
			changed.add(WealthServiceConstants.PEOPLE_FIELD_HIDDEN);
		}
		String userId = RequestHeaderUtil.getUserId();
		PersonSaveResultDto result = new PersonSaveResultDto();
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		if (isNew) {
			if (values.getName() == null) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			result.getChanged().addAll(changed);
			if (!result.isDryRun()) {
				String id = peopleDao.insertPerson(userId, values);
				if (id == null) {
					throw new KarthiException(BaseError.PERSON_NAME_TAKEN);
				}
				result.setPersonId(id);
				result.setStored(true);
			}
		} else {
			WealthPersonEntity found = peopleDao.findPerson(userId, request.getPersonId());
			if (found == null || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(found.getStatus())) {
				throw new KarthiException(BaseError.PERSON_NOT_FOUND);
			}
			String keepAlias = null;
			if (changed.contains(WealthServiceConstants.PEOPLE_FIELD_NAME)) {
				if (values.getName().equals(found.getName())) {
					changed.remove(WealthServiceConstants.PEOPLE_FIELD_NAME);
				} else {
					keepAlias = personOf(found.getName());
				}
			}
			if (WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(values.getStatus()) && changed.contains(WealthServiceConstants.PEOPLE_FIELD_STATUS)
					&& peopleDao.countPersonRecords(found.getId()) > 0) {
				throw new KarthiException(BaseError.PERSON_HAS_RECORDS);
			}
			if (changed.isEmpty()) {
				throw new KarthiException(BaseError.INVALID_REQUEST);
			}
			result.setPersonId(found.getId());
			result.getChanged().addAll(changed);
			if (!result.isDryRun()) {
				if (!peopleDao.updatePerson(userId, found.getId(), changed, values, keepAlias)) {
					throw new KarthiException(BaseError.PERSON_NOT_FOUND);
				}
				result.setStored(true);
			}
		}
		if (result.isStored()) {
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + result.isStored());
		return result;
	}

	// ---- a person's share link and the public page ----

	// The SHA-256 of a secret as base64url without padding, the way the old app stored it (so links made there keep working).
	static String hashOf(String token) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public ShareUpdateResultDto updateShare(ShareUpdateRequestDto request) {
		String methodName = "updateShare";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getEnabled() == null || request.getPersonId() == null || !ID_SHAPE.matcher(request.getPersonId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthPersonEntity found = peopleDao.findPerson(userId, request.getPersonId());
		if (found == null || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(found.getStatus())) {
			throw new KarthiException(BaseError.PERSON_NOT_FOUND);
		}
		ShareUpdateResultDto result = new ShareUpdateResultDto();
		if (request.getEnabled()) {
			byte[] bytes = new byte[WealthServiceConstants.SHARE_TOKEN_BYTES];
			RANDOM.nextBytes(bytes);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			if (!peopleDao.setShareToken(userId, found.getId(), hashOf(token))) {
				throw new KarthiException(BaseError.PERSON_NOT_FOUND);
			}
			result.setShared(true);
			result.setPath(WealthServiceConstants.SHARE_PATH_PREFIX + token);
		} else if (!peopleDao.clearShareToken(userId, found.getId())) {
			throw new KarthiException(BaseError.PERSON_NOT_FOUND);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | shared=" + result.isShared());
		return result;
	}

	@Override
	public LendViewDto lendView(LendViewRequestDto request) {
		String methodName = "lendView";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (rateLimiter != null && !rateLimiter.attempt(WealthServiceConstants.LEND_VIEW_RATE_KEY + RequestHeaderUtil.getIp(), WealthServiceConstants.LEND_VIEW_IP_LIMIT,
				WealthServiceConstants.LEND_VIEW_WINDOW_SECONDS)) {
			throw new KarthiException(BaseError.LINK_RATE_LIMITED);
		}
		WealthPersonEntity owner = ownerOf(request == null ? null : request.getToken());
		PeopleOverviewDto overview = buildOverview(owner.getUserId());
		PersonDto me = overview.getPeople().stream().filter(p -> owner.getId().equals(p.getId())).findFirst().orElseThrow(() -> new KarthiException(BaseError.SHARE_LINK_INVALID));
		LendViewDto dto = new LendViewDto();
		dto.setName(me.getName());
		dto.setOwed(me.getOwed());
		dto.setLent(me.getLent());
		dto.setRepaid(me.getRepaid());
		dto.setStatus(me.getStatus());
		dto.setAsOf(overview.getAsOf());
		for (PersonItemDto item : me.getItems()) {
			boolean bill = WealthServiceConstants.PEOPLE_ITEM_BILL.equals(item.getType());
			LendViewItemDto line = new LendViewItemDto();
			line.setEntryId(item.getEntryId());
			line.setShareId(item.getShareId());
			line.setOn(item.getOn());
			// a bill's own text can carry card or account digits, so the person sees only that it is a share of a bill
			line.setLabel(bill ? WealthServiceConstants.PEOPLE_LABEL_SHARE_OF + WealthServiceConstants.PEOPLE_LABEL_A_BILL : item.getLabel());
			line.setAmount(item.getAmount());
			line.setDirection(item.getDirection());
			line.setWrittenOff(item.isWrittenOff());
			line.setNote(item.getNote());
			line.setPaid(bill ? item.isPaid() : null);
			line.setComments(item.getComments());
			dto.getItems().add(line);
		}
		for (WealthLendRequestEntity sent : peopleDao.findLendViewRequests(me.getId())) {
			LendViewRequestRowDto row = new LendViewRequestRowDto();
			row.setAmount(sent.getAmount());
			row.setPaidOn(sent.getPaidOn());
			row.setMethod(sent.getMethod());
			row.setStatus(sent.getStatus());
			row.setAcceptedAmount(sent.getAcceptedAmount());
			row.setSentAt(sent.getCreatedAt() == null ? null : sent.getCreatedAt().toInstant().toString());
			dto.getRequests().add(row);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | items=" + dto.getItems().size());
		return dto;
	}

	// The person a share secret belongs to. The secret is 32 to 64 URL-safe characters; a wrong, old or turned-off one gets one generic refusal.
	private WealthPersonEntity ownerOf(String token) {
		if (token == null || !token.matches(WealthServiceConstants.SHARE_TOKEN_PATTERN)) {
			throw new KarthiException(BaseError.SHARE_LINK_INVALID);
		}
		WealthPersonEntity owner = peopleDao.findPersonByShareHash(hashOf(token));
		if (owner == null) {
			throw new KarthiException(BaseError.SHARE_LINK_INVALID);
		}
		return owner;
	}

	// Every public call counts against the caller's address.
	private void limit(String keyPrefix, long limit) {
		if (rateLimiter != null && !rateLimiter.attempt(keyPrefix + RequestHeaderUtil.getIp(), limit, WealthServiceConstants.LEND_VIEW_WINDOW_SECONDS)) {
			throw new KarthiException(BaseError.LINK_RATE_LIMITED);
		}
	}

	// Free text from a person or the owner: control characters become spaces, trimmed, cut to the limit; empty is nothing.
	private static String textOf(String value, int max) {
		String text = cut(value == null ? "" : value.replaceAll("[\\u0000-\\u001f]", " "), max);
		return text.isEmpty() ? null : text;
	}

	@Override
	public LendPaidResultDto lendPaid(LendPaidRequestDto request) {
		String methodName = "lendPaid";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		limit(WealthServiceConstants.LEND_PAID_RATE_KEY, WealthServiceConstants.LEND_PAID_IP_LIMIT);
		WealthPersonEntity owner = ownerOf(request == null ? null : request.getToken());
		if (request.getAmount() == null || request.getAmount().signum() <= 0 || request.getAmount().doubleValue() > WealthServiceConstants.LEND_PAID_AMOUNT_MAX) {
			throw new KarthiException(BaseError.ENTRY_AMOUNT_INVALID);
		}
		String paidOn = dayOrToday(request.getPaidOn());
		if (paidOn.compareTo(WealthServiceConstants.LEND_PAID_MIN_DAY) < 0) {
			throw new KarthiException(BaseError.DATE_INVALID);
		}
		String method = request.getMethod() == null || request.getMethod().isBlank() ? "upi" : request.getMethod();
		if (!oneOf(method, "upi", "bank", "cash", "other")) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		if (peopleDao.countPendingRequests(owner.getId()) >= WealthServiceConstants.LEND_PAID_PENDING_MAX) {
			throw new KarthiException(BaseError.PAID_TOO_MANY_WAITING);
		}
		peopleDao.addLendRequest(owner.getId(), request.getAmount().setScale(2, RoundingMode.HALF_UP), paidOn, method, textOf(request.getNote(), WealthServiceConstants.LEND_PAID_NOTE_MAX));
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, owner.getUserId());
		LendPaidResultDto result = new LendPaidResultDto();
		result.setStored(true);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return result;
	}

	@Override
	public LendRemarkResultDto lendRemark(LendRemarkRequestDto request) {
		String methodName = "lendRemark";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		limit(WealthServiceConstants.LEND_REMARK_RATE_KEY, WealthServiceConstants.LEND_REMARK_IP_LIMIT);
		WealthPersonEntity owner = ownerOf(request == null ? null : request.getToken());
		String body = textOf(request.getBody(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		boolean byEntry = request.getEntryId() != null && !request.getEntryId().isBlank();
		boolean byShare = request.getShareId() != null && !request.getShareId().isBlank();
		if (body == null || byEntry == byShare || !ID_SHAPE.matcher(byEntry ? request.getEntryId() : request.getShareId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		if (byEntry ? !peopleDao.entryOfPerson(request.getEntryId(), owner.getId()) : !peopleDao.shareOfPerson(request.getShareId(), owner.getId())) {
			throw new KarthiException(byEntry ? BaseError.ENTRY_NOT_FOUND : BaseError.SHARE_NOT_FOUND);
		}
		Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofHours(24)));
		if (peopleDao.countPersonRemarksSince(owner.getId(), since) >= WealthServiceConstants.LEND_REMARKS_PER_DAY) {
			throw new KarthiException(BaseError.REMARK_LIMIT);
		}
		peopleDao.addRemark(owner.getId(), byEntry ? request.getEntryId() : null, byEntry ? null : request.getShareId(), WealthServiceConstants.PEOPLE_AUTHOR_PERSON, body, WealthServiceConstants.ACTOR_LEND_LINK);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, owner.getUserId());
		LendRemarkResultDto result = new LendRemarkResultDto();
		result.setStored(true);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return result;
	}

	@Override
	public CommentCreateResultDto createComment(CommentCreateRequestDto request) {
		String methodName = "createComment";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String body = textOf(request.getBody(), WealthServiceConstants.PEOPLE_NOTE_MAX_LENGTH);
		boolean byEntry = request.getEntryId() != null && !request.getEntryId().isBlank();
		boolean byShare = request.getShareId() != null && !request.getShareId().isBlank();
		if (body == null || byEntry == byShare || !ID_SHAPE.matcher(byEntry ? request.getEntryId() : request.getShareId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		String personId = byEntry ? peopleDao.findEntryPersonOfOwner(userId, request.getEntryId()) : peopleDao.findSharePersonOfOwner(userId, request.getShareId());
		if (personId == null) {
			throw new KarthiException(byEntry ? BaseError.ENTRY_NOT_FOUND : BaseError.SHARE_NOT_FOUND);
		}
		CommentCreateResultDto result = new CommentCreateResultDto();
		result.setCommentId(peopleDao.addRemark(personId, byEntry ? request.getEntryId() : null, byEntry ? null : request.getShareId(), WealthServiceConstants.PEOPLE_AUTHOR_OWNER, body,
				WealthServiceConstants.ACTOR_OWNER));
		result.setStored(true);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return result;
	}

	@Override
	public EntryNoteResultDto updateEntryNote(EntryNoteRequestDto request) {
		String methodName = "updateEntryNote";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getEntryId() == null || !ID_SHAPE.matcher(request.getEntryId()).matches()) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		String note = textOf(request.getNote(), WealthServiceConstants.ENTRY_NOTE_MAX_LENGTH);
		if (!peopleDao.setEntryNote(userId, request.getEntryId(), note)) {
			throw new KarthiException(BaseError.ENTRY_NOT_FOUND);
		}
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		EntryNoteResultDto result = new EntryNoteResultDto();
		result.setStored(true);
		result.setNote(note);
		LOGGER.info(WealthServiceConstants.EXITING + methodName);
		return result;
	}

	@Override
	public RequestDecideResultDto decideRequest(RequestDecideRequestDto request) {
		String methodName = "decideRequest";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getRequestId() == null || !ID_SHAPE.matcher(request.getRequestId()).matches()
				|| !oneOf(request.getDecision(), WealthServiceConstants.PEOPLE_DECISION_ACCEPT, WealthServiceConstants.PEOPLE_DECISION_DECLINE)) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthLendRequestEntity found = peopleDao.findRequestOfOwner(userId, request.getRequestId());
		if (found == null) {
			throw new KarthiException(BaseError.REQUEST_NOT_FOUND);
		}
		if (!WealthServiceConstants.PEOPLE_REQUEST_PENDING.equals(found.getStatus())) {
			throw new KarthiException(BaseError.REQUEST_ALREADY_DECIDED);
		}
		RequestDecideResultDto result = new RequestDecideResultDto();
		result.setDecision(request.getDecision());
		if (WealthServiceConstants.PEOPLE_DECISION_DECLINE.equals(request.getDecision())) {
			if (!peopleDao.declineRequest(userId, found.getId())) {
				throw new KarthiException(BaseError.REQUEST_ALREADY_DECIDED);
			}
		} else {
			BigDecimal amount = request.getAmount() == null ? found.getAmount() : request.getAmount();
			if (!amountOk(amount)) {
				throw new KarthiException(BaseError.ENTRY_AMOUNT_INVALID);
			}
			amount = amount.setScale(2, RoundingMode.HALF_UP);
			String paidOn = dayOrToday(request.getPaidOn() == null || request.getPaidOn().isBlank() ? found.getPaidOn() : request.getPaidOn());
			double owedBefore = buildOverview().getPeople().stream().filter(p -> found.getPersonId().equals(p.getId())).mapToDouble(p -> p.getOwed().doubleValue()).findFirst().orElse(0);
			boolean closeLend = request.getCloseLend() == null ? amount.doubleValue() >= owedBefore - WealthServiceConstants.PEOPLE_COVERS_TOLERANCE : request.getCloseLend();
			String note = "Paid back via shared link (" + found.getMethod() + (found.getNote() == null || found.getNote().isBlank() ? "" : ": " + found.getNote()) + ")";
			if (!peopleDao.acceptRequest(userId, found, amount, paidOn, closeLend, note)) {
				throw new KarthiException(BaseError.REQUEST_ALREADY_DECIDED);
			}
			result.setAmount(amount);
			result.setPaidOn(paidOn);
			result.setCloseLend(closeLend);
		}
		result.setStored(true);
		nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | decision=" + request.getDecision());
		return result;
	}

	@Override
	public PersonMergeResultDto mergePeople(PersonMergeRequestDto request) {
		String methodName = "mergePeople";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		if (request == null || request.getFromId() == null || request.getIntoId() == null || !ID_SHAPE.matcher(request.getFromId()).matches() || !ID_SHAPE.matcher(request.getIntoId()).matches()
				|| request.getFromId().equalsIgnoreCase(request.getIntoId())) {
			throw new KarthiException(BaseError.INVALID_REQUEST);
		}
		String userId = RequestHeaderUtil.getUserId();
		WealthPersonEntity from = peopleDao.findPerson(userId, request.getFromId());
		WealthPersonEntity into = peopleDao.findPerson(userId, request.getIntoId());
		if (from == null || into == null || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(from.getStatus()) || WealthServiceConstants.PEOPLE_STATUS_REMOVED.equals(into.getStatus())) {
			throw new KarthiException(BaseError.PERSON_NOT_FOUND);
		}
		if (peopleDao.findMergeClash(into.getId(), from.getId()) != null) {
			throw new KarthiException(BaseError.MERGE_BOTH_IN_SPLIT);
		}
		PersonMergeResultDto result = new PersonMergeResultDto();
		result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
		result.setFrom(from.getName());
		result.setInto(into.getName());
		result.setPersonId(into.getId());
		if (!result.isDryRun()) {
			peopleDao.mergePeople(userId, from.getId(), into.getId(), personOf(from.getName()));
			result.setStored(true);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_PEOPLE, userId);
			nexusReadService.evict(WealthServiceConstants.NEXUS_READ_TRANSACTIONS, userId);
		}
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | stored=" + result.isStored());
		return result;
	}

	private static String cut(String value, int max) {
		String text = value == null ? "" : value.trim();
		return text.length() > max ? text.substring(0, max) : text;
	}

	// ---- names and figures ----

	// "UPI transfer to Arun S", "Mr Arun S . UPI . HDFC statement . ref" give "Arun S".
	static String personOf(String description) {
		String text = description == null ? "" : description.split(Pattern.quote(WealthServiceConstants.PEOPLE_SOURCE_SEPARATOR), -1)[0];
		text = LEAD_PAYMENT.matcher(text).replaceFirst("");
		text = LEAD_PAID.matcher(text).replaceFirst("");
		return LEAD_TITLE.matcher(text).replaceFirst("").replaceAll("\\s+", " ").trim();
	}

	// The key a name is matched on: "Mr Arun S . UPI . ref" and "ARUN S" give "arun s".
	private static String nameKey(String value) {
		return personOf(value).toLowerCase();
	}

	private static BigDecimal r2(double value) {
		return BigDecimal.valueOf(Math.round(value * 100) / 100.0).setScale(2, RoundingMode.HALF_UP);
	}

	private static double days(String from, String to) {
		return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to));
	}

	// Running balance with daily simple interest; repayments reduce interest first, then principal.
	static Figures personFigures(List<Move> moves, BigDecimal annualRate, String dueOn, String status, String today) {
		List<Move> list = new ArrayList<>(moves);
		list.sort(Comparator.comparing(Move::on));
		double principal = 0;
		double interest = 0;
		double lent = 0;
		double repaid = 0;
		String last = null;
		double rate = annualRate == null ? 0 : annualRate.doubleValue();
		for (Move move : list) {
			String day = move.on().substring(0, 10);
			if (rate > 0 && last != null && principal > 0) {
				interest += principal * rate * Math.max(0, days(last, day)) / WealthServiceConstants.PEOPLE_DAYS_PER_YEAR;
			}
			last = day;
			if (WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(move.direction())) {
				lent += move.amount();
				principal += move.amount();
			} else {
				repaid += move.amount();
				double toInterest = Math.min(interest, move.amount());
				interest -= toInterest;
				principal -= move.amount() - toInterest;
			}
		}
		boolean open = WealthServiceConstants.PEOPLE_STATUS_OPEN.equals(status);
		if (open && rate > 0 && last != null && principal > 0) {
			interest += principal * rate * Math.max(0, days(last, today)) / WealthServiceConstants.PEOPLE_DAYS_PER_YEAR;
		}
		double owed = open ? r2(Math.max(0, principal + interest)).doubleValue() : 0;
		boolean overdue = open && dueOn != null && dueOn.substring(0, 10).compareTo(today) < 0 && owed > 0;
		return new Figures(r2(lent).doubleValue(), r2(repaid).doubleValue(), r2(Math.max(0, interest)).doubleValue(), owed,
				principal < -WealthServiceConstants.PEOPLE_OVERPAID_LIMIT ? r2(-principal).doubleValue() : 0, list.isEmpty() ? null : list.get(list.size() - 1).on().substring(0, 10), overdue);
	}

	private static String directionOf(WealthTransactionEntity transfer) {
		return transfer.getCategory() != null && RECEIVED.matcher(transfer.getCategory()).find() ? WealthServiceConstants.PEOPLE_DIRECTION_IN : WealthServiceConstants.PEOPLE_DIRECTION_OUT;
	}

	private static double value(BigDecimal amount) {
		return amount == null ? 0 : amount.doubleValue();
	}

	private static boolean isOut(String direction) {
		return WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(direction);
	}

	// ---- the overview ----

	private PeopleOverviewDto buildOverview() {
		return buildOverview(RequestHeaderUtil.getUserId());
	}

	private PeopleOverviewDto buildOverview(String userId) {
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(userId);
		String today = LocalDate.now(clock.withZone(IST)).toString();
		List<WealthPersonEntity> people = peopleDao.findPeople(paginationDto);
		List<WealthPersonEntryEntity> entries = peopleDao.findEntries(paginationDto);
		List<WealthBillSplitShareEntity> shares = peopleDao.findShares(paginationDto);
		List<WealthBillSplitEntity> splits = peopleDao.findSplits(paginationDto);
		List<WealthPersonRollupEntity> rollups = peopleDao.findRollups(paginationDto);
		List<WealthLendRequestEntity> requests = peopleDao.findPendingRequests(paginationDto);
		List<WealthPersonAliasEntity> aliases = peopleDao.findAliases(paginationDto);
		List<WealthLendCommentEntity> comments = peopleDao.findComments(paginationDto);
		List<WealthTransactionEntity> unlinked = peopleDao.findUnlinkedTransfers(paginationDto);

		// every name a person goes by points to that person
		Map<String, WealthPersonEntity> keyToPerson = new HashMap<>();
		Map<String, WealthPersonEntity> byId = new HashMap<>();
		Map<String, String> nameById = new HashMap<>();
		for (WealthPersonEntity person : people) {
			keyToPerson.put(nameKey(person.getName()), person);
			byId.put(person.getId(), person);
			nameById.put(person.getId(), person.getName());
		}
		for (WealthPersonAliasEntity alias : aliases) {
			WealthPersonEntity person = byId.get(alias.getPersonId());
			if (person != null) {
				keyToPerson.putIfAbsent(nameKey(alias.getAlias()), person);
			}
		}
		Map<String, WealthBillSplitEntity> splitById = new HashMap<>();
		for (WealthBillSplitEntity split : splits) {
			splitById.put(split.getId(), split);
		}
		Map<String, List<LendCommentDto>> commentsByEntry = new HashMap<>();
		Map<String, List<LendCommentDto>> commentsByShare = new HashMap<>();
		for (WealthLendCommentEntity comment : comments) {
			LendCommentDto dto = new LendCommentDto();
			dto.setId(comment.getId());
			dto.setAuthor(comment.getAuthor());
			dto.setBody(comment.getBody());
			dto.setAt(comment.getCreatedAt());
			(comment.getEntryId() != null ? commentsByEntry : commentsByShare).computeIfAbsent(comment.getEntryId() != null ? comment.getEntryId() : comment.getShareId(), k -> new ArrayList<>())
					.add(dto);
		}
		// a share counts as paid when a payment back is linked to it ("Mark paid")
		Map<String, WealthPersonEntryEntity> paidShare = new HashMap<>();
		for (WealthPersonEntryEntity entry : entries) {
			if (entry.getSplitId() != null && WealthServiceConstants.PEOPLE_DIRECTION_IN.equals(entry.getDirection()) && WealthServiceConstants.PEOPLE_STATUS_ACTIVE.equals(entry.getStatus())) {
				paidShare.put(entry.getPersonId() + ":" + entry.getSplitId(), entry);
			}
		}

		List<PersonDto> out = new ArrayList<>();
		for (WealthPersonEntity person : people) {
			out.add(toPerson(person, entries, shares, rollups, requests, aliases, unlinked, keyToPerson, splitById, commentsByEntry, commentsByShare, paidShare, today));
		}
		List<PersonDto> shown = out.stream().filter(p -> !p.isHidden()).toList();
		Set<String> hiddenIds = new HashSet<>();
		for (PersonDto person : out) {
			if (person.isHidden()) {
				hiddenIds.add(person.getId());
			}
		}
		List<PersonDto> active = shown.stream().filter(p -> WealthServiceConstants.PEOPLE_STATUS_OPEN.equals(p.getStatus())).toList();

		PeopleOverviewDto dto = new PeopleOverviewDto();
		dto.setAsOf(today);
		dto.setTotals(totals(shown, active, hiddenIds.size(), splits.size()));
		for (WealthLendRequestEntity request : requests) {
			if (!hiddenIds.contains(request.getPersonId())) {
				dto.getRequests().add(toRequest(request));
			}
		}
		dto.setPeople(out);
		for (WealthBillSplitEntity split : splits) {
			dto.getSplits().add(toSplit(split, shares, nameById, paidShare));
		}
		List<WealthTransactionEntity> unmatched = new ArrayList<>();
		for (WealthTransactionEntity transfer : unlinked) {
			if (!keyToPerson.containsKey(nameKey(transfer.getDescription()))) {
				unmatched.add(transfer);
			}
		}
		dto.setSuggestions(suggestions(unmatched));
		LOGGER.info(WealthServiceConstants.EXITING + methodName + " | people=" + out.size());
		return dto;
	}

	private PersonDto toPerson(WealthPersonEntity person, List<WealthPersonEntryEntity> entries, List<WealthBillSplitShareEntity> shares, List<WealthPersonRollupEntity> rollups,
			List<WealthLendRequestEntity> requests, List<WealthPersonAliasEntity> aliases, List<WealthTransactionEntity> unlinked, Map<String, WealthPersonEntity> keyToPerson,
			Map<String, WealthBillSplitEntity> splitById, Map<String, List<LendCommentDto>> commentsByEntry, Map<String, List<LendCommentDto>> commentsByShare,
			Map<String, WealthPersonEntryEntity> paidShare, String today) {
		List<PersonItemDto> all = new ArrayList<>();
		for (WealthPersonEntryEntity entry : entries) {
			if (person.getId().equals(entry.getPersonId())) {
				all.add(entryItem(entry, splitById, commentsByEntry));
			}
		}
		for (WealthBillSplitShareEntity share : shares) {
			if (person.getId().equals(share.getPersonId()) && splitById.containsKey(share.getSplitId())) {
				all.add(shareItem(share, splitById.get(share.getSplitId()), commentsByShare, paidShare));
			}
		}
		all.sort(Comparator.comparing(PersonItemDto::getOn).reversed());
		List<PersonItemDto> open = all.stream().filter(i -> i.getRollupId() == null).toList();
		List<PersonRollupDto> rolls = new ArrayList<>();
		for (WealthPersonRollupEntity rollup : rollups) {
			if (person.getId().equals(rollup.getPersonId())) {
				PersonRollupDto roll = new PersonRollupDto();
				roll.setId(rollup.getId());
				roll.setOn(rollup.getOn());
				roll.setTotalOwed(rollup.getTotalOwed());
				roll.setAlreadyBack(rollup.getAlreadyBack());
				roll.setNewAmount(rollup.getNewAmount());
				roll.setClosedItems(rollup.getClosedItems());
				roll.setNote(rollup.getNote());
				roll.setItems(all.stream().filter(i -> rollup.getId().equals(i.getRollupId())).toList());
				rolls.add(roll);
			}
		}
		List<WealthTransactionEntity> mine = new ArrayList<>();
		for (WealthTransactionEntity transfer : unlinked) {
			WealthPersonEntity match = keyToPerson.get(nameKey(transfer.getDescription()));
			if (match != null && match.getId().equals(person.getId())) {
				mine.add(transfer);
			}
		}
		List<Move> moves = new ArrayList<>();
		for (PersonItemDto item : open) {
			if (!item.isWrittenOff()) {
				moves.add(new Move(item.getOn(), item.getAmount().doubleValue(), item.getDirection()));
			}
		}
		Figures figures = personFigures(moves, person.getAnnualRate(), person.getDueOn(), person.getStatus(), today);

		PersonDto dto = new PersonDto();
		dto.setId(person.getId());
		dto.setName(person.getName());
		dto.setKind(person.getKind());
		dto.setSharedSince(person.getShareCreatedAt());
		dto.setHidden(person.isHidden());
		for (WealthPersonAliasEntity alias : aliases) {
			if (person.getId().equals(alias.getPersonId())) {
				dto.getAliases().add(alias.getAlias());
			}
		}
		List<WealthTransactionEntity> latest = new ArrayList<>(mine.subList(Math.max(0, mine.size() - WealthServiceConstants.PEOPLE_UNLINKED_PER_PERSON_MAX), mine.size()));
		Collections.reverse(latest);
		for (WealthTransactionEntity transfer : latest) {
			dto.getUnlinked().add(toUnlinked(transfer));
		}
		dto.setUnlinkedCount(mine.size());
		dto.setRequests((int) requests.stream().filter(r -> person.getId().equals(r.getPersonId())).count());
		dto.setDueOn(person.getDueOn());
		dto.setAnnualRate(person.getAnnualRate());
		dto.setStatus(person.getStatus());
		dto.setNote(person.getNote());
		dto.setLent(r2(figures.lent()));
		dto.setRepaid(r2(figures.repaid()));
		dto.setInterest(r2(figures.interest()));
		dto.setOwed(r2(figures.owed()));
		dto.setOverpaid(r2(figures.overpaid()));
		dto.setLastMovement(figures.lastMovement());
		dto.setOverdue(figures.overdue());
		dto.setBills((int) open.stream().filter(i -> WealthServiceConstants.PEOPLE_ITEM_BILL.equals(i.getType())).count());
		dto.setUnpaidShares((int) open.stream().filter(i -> WealthServiceConstants.PEOPLE_ITEM_BILL.equals(i.getType()) && !i.isPaid() && !i.isWrittenOff()).count());
		dto.setWrittenOff(r2(open.stream().filter(i -> i.isWrittenOff() && isOut(i.getDirection())).mapToDouble(i -> i.getAmount().doubleValue()).sum()));
		// owes nothing (settled, written off, or everything paid back): history, shown with 0
		dto.setHistory(!WealthServiceConstants.PEOPLE_STATUS_OPEN.equals(person.getStatus()) || !(figures.owed() > 0));
		dto.setItems(new ArrayList<>(open));
		dto.setRollups(rolls);
		return dto;
	}

	private PersonItemDto entryItem(WealthPersonEntryEntity entry, Map<String, WealthBillSplitEntity> splitById, Map<String, List<LendCommentDto>> commentsByEntry) {
		PersonItemDto item = new PersonItemDto();
		item.setType(entry.getTransactionId() != null ? WealthServiceConstants.PEOPLE_ITEM_TRANSFER : entry.getRollupRef() != null ? WealthServiceConstants.PEOPLE_ITEM_CARRIED
				: WealthServiceConstants.PEOPLE_ITEM_MANUAL);
		item.setDirection(entry.getDirection());
		item.setAmount(entry.getAmount());
		item.setOn(entry.getOn());
		item.setTransactionId(entry.getTransactionId());
		item.setEntryId(entry.getId());
		item.setSplitId(entry.getSplitId());
		item.setRollupId(entry.getRollupId());
		item.setWrittenOff(WealthServiceConstants.PEOPLE_STATUS_WRITTEN_OFF.equals(entry.getStatus()));
		WealthBillSplitEntity split = entry.getSplitId() == null ? null : splitById.get(entry.getSplitId());
		item.setLabel(entry.getRollupRef() != null ? WealthServiceConstants.PEOPLE_LABEL_CARRIED
				: entry.getSplitId() != null ? WealthServiceConstants.PEOPLE_LABEL_PAID_SHARE + (split != null ? split.getTitle() : WealthServiceConstants.PEOPLE_LABEL_A_BILL)
						: isOut(entry.getDirection()) ? WealthServiceConstants.PEOPLE_LABEL_LENT : WealthServiceConstants.PEOPLE_LABEL_PAID_BACK);
		item.setDetail(entry.getTxDescription() == null || entry.getTxDescription().isEmpty() ? null : entry.getTxDescription());
		item.setNote(entry.getNote() == null || entry.getNote().isEmpty() ? null : entry.getNote());
		item.setComments(commentsByEntry.getOrDefault(entry.getId(), new ArrayList<>()));
		item.setAccount(entry.getAccount());
		return item;
	}

	private PersonItemDto shareItem(WealthBillSplitShareEntity share, WealthBillSplitEntity split, Map<String, List<LendCommentDto>> commentsByShare,
			Map<String, WealthPersonEntryEntity> paidShare) {
		WealthPersonEntryEntity paid = paidShare.get(share.getPersonId() + ":" + share.getSplitId());
		PersonItemDto item = new PersonItemDto();
		item.setType(WealthServiceConstants.PEOPLE_ITEM_BILL);
		item.setDirection(WealthServiceConstants.PEOPLE_DIRECTION_OUT);
		item.setAmount(share.getAmount());
		item.setOn(split.getOn());
		item.setSplitId(split.getId());
		item.setShareId(share.getId());
		item.setTransactionId(split.getTransactionId());
		item.setRollupId(share.getRollupId());
		item.setWrittenOff(WealthServiceConstants.PEOPLE_STATUS_WRITTEN_OFF.equals(share.getStatus()));
		item.setLabel(WealthServiceConstants.PEOPLE_LABEL_SHARE_OF + split.getTitle());
		if (share.getTax() != null && share.getTax().signum() != 0) {
			item.setDetail(WealthServiceConstants.PEOPLE_RUPEE + plain(share.getBase()) + " + " + WealthServiceConstants.PEOPLE_RUPEE + plain(share.getTax()) + " GST");
		}
		item.setComments(commentsByShare.getOrDefault(share.getId(), new ArrayList<>()));
		item.setPaid(paid != null);
		item.setPaidOn(paid == null ? null : paid.getOn());
		return item;
	}

	// 100.00 shows as 100 and 12.50 as 12.5, as the old page wrote them.
	private static String plain(BigDecimal amount) {
		return amount == null ? "0" : amount.signum() == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
	}

	private PeopleTotalsDto totals(List<PersonDto> shown, List<PersonDto> active, int hidden, int splits) {
		PeopleTotalsDto totals = new PeopleTotalsDto();
		totals.setOwed(r2(active.stream().mapToDouble(p -> p.getOwed().doubleValue()).sum()));
		totals.setLent(r2(shown.stream().mapToDouble(p -> p.getLent().doubleValue()).sum()));
		totals.setRepaid(r2(shown.stream().mapToDouble(p -> p.getRepaid().doubleValue()).sum()));
		totals.setHidden(hidden);
		totals.setPeople((int) active.stream().filter(p -> p.getOwed().signum() > 0).count());
		totals.setOverdue((int) active.stream().filter(PersonDto::isOverdue).count());
		totals.setSplits(splits);
		return totals;
	}

	private LendRequestDto toRequest(WealthLendRequestEntity request) {
		LendRequestDto dto = new LendRequestDto();
		dto.setId(request.getId());
		dto.setPersonId(request.getPersonId());
		dto.setName(request.getName());
		dto.setAmount(request.getAmount());
		dto.setPaidOn(request.getPaidOn());
		dto.setMethod(request.getMethod());
		dto.setNote(request.getNote());
		dto.setSentAt(request.getCreatedAt());
		return dto;
	}

	private BillSplitDto toSplit(WealthBillSplitEntity split, List<WealthBillSplitShareEntity> shares, Map<String, String> nameById, Map<String, WealthPersonEntryEntity> paidShare) {
		BillSplitDto dto = new BillSplitDto();
		dto.setId(split.getId());
		dto.setTransactionId(split.getTransactionId());
		dto.setTitle(split.getTitle());
		dto.setOn(split.getOn());
		dto.setTotal(split.getTotal());
		dto.setTax(split.getTax());
		dto.setMode(split.getMode());
		double others = 0;
		BigDecimal mine = BigDecimal.ZERO;
		List<SplitShareDto> list = new ArrayList<>();
		for (WealthBillSplitShareEntity share : shares) {
			if (!split.getId().equals(share.getSplitId())) {
				continue;
			}
			SplitShareDto one = new SplitShareDto();
			one.setPersonId(share.getPersonId());
			one.setName(share.getPersonId() == null ? WealthServiceConstants.PEOPLE_NAME_YOU : nameById.getOrDefault(share.getPersonId(), ""));
			one.setBase(share.getBase());
			one.setTax(share.getTax());
			one.setAmount(share.getAmount());
			one.setPaid(share.getPersonId() == null || paidShare.containsKey(share.getPersonId() + ":" + split.getId()));
			list.add(one);
			if (share.getPersonId() == null) {
				mine = share.getAmount();
			} else {
				others += value(share.getAmount());
			}
		}
		list.sort(Comparator.comparingInt((SplitShareDto s) -> s.getPersonId() == null ? 0 : 1).thenComparing(SplitShareDto::getName));
		dto.setMyShare(mine);
		dto.setOthersOwe(r2(others));
		dto.setShares(list);
		return dto;
	}

	private UnlinkedDto toUnlinked(WealthTransactionEntity transfer) {
		UnlinkedDto dto = new UnlinkedDto();
		dto.setId(transfer.getId());
		dto.setDirection(directionOf(transfer));
		dto.setAmount(transfer.getAmount());
		dto.setDay(transfer.getDay());
		dto.setDescription(transfer.getDescription());
		return dto;
	}

	// Names nobody in the list goes by yet, with both sent and received transfers (or sent to a peer-to-peer platform).
	private List<SuggestionDto> suggestions(List<WealthTransactionEntity> transfers) {
		Map<String, SuggestionDto> by = new LinkedHashMap<>();
		Map<String, double[]> sums = new HashMap<>();
		for (WealthTransactionEntity transfer : transfers) {
			String person = personOf(transfer.getDescription());
			String key = person.toLowerCase();
			if (key.isEmpty()) {
				continue;
			}
			SuggestionDto group = by.computeIfAbsent(key, k -> {
				SuggestionDto created = new SuggestionDto();
				created.setPerson(person);
				created.setKey(k);
				created.setFirstDay(transfer.getDay());
				return created;
			});
			double[] sum = sums.computeIfAbsent(key, k -> new double[2]);
			UnlinkedDto one = toUnlinked(transfer);
			sum[WealthServiceConstants.PEOPLE_DIRECTION_OUT.equals(one.getDirection()) ? 0 : 1] += value(transfer.getAmount());
			group.getTransactions().add(one);
			group.setLastDay(transfer.getDay());
		}
		List<SuggestionDto> result = new ArrayList<>();
		for (Map.Entry<String, SuggestionDto> entry : by.entrySet()) {
			SuggestionDto group = entry.getValue();
			double[] sum = sums.get(entry.getKey());
			boolean p2p = PEER_TO_PEER.matcher(entry.getKey()).find();
			group.setSent(r2(sum[0]));
			group.setReceived(r2(sum[1]));
			group.setP2p(p2p);
			if ((sum[0] > 0 && sum[1] > 0) || (p2p && sum[0] > 0)) {
				result.add(group);
			}
		}
		result.sort(Comparator.comparingDouble((SuggestionDto s) -> s.getSent().doubleValue() - s.getReceived().doubleValue()).reversed());
		return new ArrayList<>(result.subList(0, Math.min(result.size(), WealthServiceConstants.PEOPLE_SUGGESTION_MAX)));
	}
}
