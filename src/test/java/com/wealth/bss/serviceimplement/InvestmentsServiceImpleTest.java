package com.wealth.bss.serviceimplement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.common.model.dao.IfWealthInvestmentDao;
import com.wealth.bss.common.model.entity.WealthHoldingDetailEntity;
import com.wealth.bss.nexus.service.IfNexusReadService;
import com.wealth.common.model.dto.CachedValueDto;
import com.wealth.common.model.dto.invest.HoldingDto;
import com.wealth.common.model.dto.invest.InvestmentsDto;
import com.wealth.common.utils.generic.RequestHeaderUtil;

class InvestmentsServiceImpleTest {

	private IfWealthInvestmentDao dao;
	private IfNexusReadService nexus;
	private InvestmentsServiceImple service;

	@BeforeEach
	void setUp() {
		dao = mock(IfWealthInvestmentDao.class);
		nexus = mock(IfNexusReadService.class);
		nexusPassesThrough(nexus);
		service = new InvestmentsServiceImple(dao, nexus);
		RequestHeaderUtil.setUserId("2a7014a8-9cf0-4d39-bed2-c83a83eddf0c");
	}

	@AfterEach
	void tearDown() {
		RequestHeaderUtil.clear();
	}

	// The nexus is not under test here: it hands the loader's result straight back, stamped with a read time.
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void nexusPassesThrough(IfNexusReadService nexus) {
		org.mockito.stubbing.Answer<Object> answer = invocation -> {
			CachedValueDto cached = new CachedValueDto();
			cached.setData(((java.util.function.Supplier) invocation.getArgument(2)).get());
			cached.setFetchedAt(java.sql.Timestamp.valueOf("2026-11-27 10:00:00"));
			return cached;
		};
		when(nexus.read(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
		when(nexus.hardRefresh(any(), any(Class.class), any(java.util.function.Supplier.class))).thenAnswer(answer);
	}

	@Test
	void theListComesThroughTheNexusStampedAndTheRefreshButtonAsksForAHardRefresh() {
		when(dao.findHoldingDetailsByUser(any())).thenReturn(List.of());

		InvestmentsDto result = service.list();
		service.refresh();

		verify(nexus).read(org.mockito.ArgumentMatchers.eq("investments"), org.mockito.ArgumentMatchers.eq(InvestmentsDto.class), any());
		verify(nexus).hardRefresh(org.mockito.ArgumentMatchers.eq("investments"), org.mockito.ArgumentMatchers.eq(InvestmentsDto.class), any());
		assertEquals(Timestamp.valueOf("2026-11-27 10:00:00"), result.getFetchedAt());
		assertEquals(false, result.isStale());
	}

	private WealthHoldingDetailEntity row(String symbol, String currency, String quantity, String invested, String price) {
		WealthHoldingDetailEntity e = new WealthHoldingDetailEntity();
		e.setAccountId("acc");
		e.setBankName("Angel One");
		e.setAccountName("Demat");
		e.setAccountCurrency("INR");
		e.setSymbol(symbol);
		e.setName(symbol);
		e.setCurrency(currency);
		e.setUnit("share");
		e.setAssetClass("indian_equity");
		e.setQuantity(new BigDecimal(quantity));
		e.setInvestedValue(invested == null ? null : new BigDecimal(invested));
		e.setPrice(price == null ? null : new BigDecimal(price));
		e.setPriceAsOf(Timestamp.valueOf("2026-11-26 18:30:00"));
		e.setStatus("active");
		return e;
	}

	@Test
	void eachHoldingGetsAnAveragePriceValueAndGainAndTheTotalsCoverPricedRupeeHoldings() {
		when(dao.findHoldingDetailsByUser(any())).thenReturn(List.of(row("AAA", "INR", "10", "1000", "120"), row("BBB", "INR", "4", "400", "90"), row("CCC", "INR", "5", "250", null)));

		InvestmentsDto result = service.list();

		HoldingDto a = result.getHoldings().get(0);
		assertEquals(0, new BigDecimal("100.0000").compareTo(a.getAveragePrice()));
		assertEquals(0, new BigDecimal("1200.00").compareTo(a.getCurrentValue()));
		assertEquals(0, new BigDecimal("200.00").compareTo(a.getGain()));
		assertEquals(0, new BigDecimal("20.00").compareTo(a.getGainPercent()));
		assertEquals(0, new BigDecimal("-40.00").compareTo(result.getHoldings().get(1).getGain()));
		HoldingDto c = result.getHoldings().get(2);
		assertNull(c.getCurrentValue());
		assertNull(c.getGain());
		assertEquals(3, result.getHoldingCount());
		assertEquals(1, result.getUnpricedCount());
		assertEquals(0, new BigDecimal("1400").compareTo(result.getInvested()));
		assertEquals(0, new BigDecimal("1560.00").compareTo(result.getCurrentValue()));
		assertEquals(0, new BigDecimal("160.00").compareTo(result.getGain()));
		assertEquals(0, new BigDecimal("11.43").compareTo(result.getGainPercent()));
	}

	@Test
	void aForeignHoldingShowsNoGainAndIsValuedInRupeesInTheTotalValueOnly() {
		WealthHoldingDetailEntity usd = row("NVDA", "USD", "2", "500", "150");
		usd.setFxToInr(new BigDecimal("80"));
		when(dao.findHoldingDetailsByUser(any())).thenReturn(List.of(usd));

		InvestmentsDto result = service.list();

		assertEquals(0, new BigDecimal("300.00").compareTo(result.getHoldings().get(0).getCurrentValue()));
		assertNull(result.getHoldings().get(0).getGain());
		assertEquals(0, new BigDecimal("24000.00").compareTo(result.getCurrentValue()));
		assertEquals(0, BigDecimal.ZERO.compareTo(result.getInvested()));
		assertNull(result.getGainPercent());
	}

	@Test
	void aPricedHoldingWithoutAnInvestedAmountCountsInTheValueButNotInTheGain() {
		when(dao.findHoldingDetailsByUser(any())).thenReturn(List.of(row("AAA", "INR", "10", "1000", "120"), row("DDD", "INR", "3", null, "100")));

		InvestmentsDto result = service.list();

		assertEquals(0, new BigDecimal("1500.00").compareTo(result.getCurrentValue()));
		assertEquals(0, new BigDecimal("1000").compareTo(result.getInvested()));
		assertEquals(0, new BigDecimal("200.00").compareTo(result.getGain()));
	}

	@Test
	void noHoldingsGiveEmptyTotals() {
		when(dao.findHoldingDetailsByUser(any())).thenReturn(List.of());

		InvestmentsDto result = service.list();

		assertEquals(0, result.getHoldingCount());
		assertEquals(0, BigDecimal.ZERO.compareTo(result.getInvested()));
		assertNull(result.getGainPercent());
	}
}
