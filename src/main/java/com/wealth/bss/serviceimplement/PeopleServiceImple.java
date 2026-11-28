package com.wealth.bss.serviceimplement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.wealth.common.model.dto.people.PeopleTotalsDto;
import com.wealth.common.model.dto.people.PersonDto;
import com.wealth.common.model.dto.people.PersonItemDto;
import com.wealth.common.model.dto.people.PersonRollupDto;
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

	private final IfWealthPeopleDao peopleDao;
	private final IfNexusReadService nexusReadService;
	private final Clock clock;

	@Autowired
	public PeopleServiceImple(IfWealthPeopleDao peopleDao, IfNexusReadService nexusReadService) {
		this(peopleDao, nexusReadService, Clock.systemUTC());
	}

	PeopleServiceImple(IfWealthPeopleDao peopleDao, IfNexusReadService nexusReadService, Clock clock) {
		this.peopleDao = peopleDao;
		this.nexusReadService = nexusReadService;
		this.clock = clock;
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
		String methodName = "list";
		LOGGER.info(WealthServiceConstants.ENTERING + methodName);
		PaginationDto paginationDto = new PaginationDto();
		paginationDto.setUserId(RequestHeaderUtil.getUserId());
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
