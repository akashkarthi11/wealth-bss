package com.wealth.bss.serviceinterface;

import java.util.Map;

public interface IfFinvuAaService {
	Map<String, Object> configuration();
	Map<String, Object> requestConsent(Map<String, Object> request);
	Map<String, Object> consentStatus(Map<String, Object> request);
	Map<String, Object> requestFinancialInformation(Map<String, Object> request);
	Map<String, Object> financialInformationStatus(Map<String, Object> request);
	Map<String, Object> fetchFinancialInformation(Map<String, Object> request);
}
