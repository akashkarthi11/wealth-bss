package com.wealth.bss.serviceimplement;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.wealth.bss.config.FinvuFiuClient;
import com.wealth.bss.serviceinterface.IfFinvuAaService;

@Service
public class FinvuAaServiceImple implements IfFinvuAaService {
	private final FinvuFiuClient client;
	
	public FinvuAaServiceImple(FinvuFiuClient client) {
		this.client = client;
	}

	@Override public Map<String, Object> configuration() {
		return client.configuration();
	}

	@Override public Map<String, Object> requestConsent(Map<String, Object> request) {
		return client.requestConsent(required(request, "custId"), optional(request, "templateName", "ONETIME_DEV_TEMPLATE"),
				optional(request, "consentDescription", "Wealth personal finance aggregation"));
	}

	@Override public Map<String, Object> consentStatus(Map<String, Object> request) {
		return client.consentStatus(required(request, "custId"), required(request, "consentHandleId"));
	}

	@Override public Map<String, Object> requestFinancialInformation(Map<String, Object> request) {
		return client.requestFinancialInformation(required(request, "custId"), required(request, "consentHandleId"),
				required(request, "consentId"), required(request, "dateTimeRangeFrom"), required(request, "dateTimeRangeTo"));
	}

	@Override public Map<String, Object> financialInformationStatus(Map<String, Object> request) {
		return client.financialInformationStatus(required(request, "custId"), required(request, "consentHandleId"),
				required(request, "consentId"), required(request, "sessionId"));
	}

	@Override public Map<String, Object> fetchFinancialInformation(Map<String, Object> request) {
		return client.fetchFinancialInformation(required(request, "custId"), required(request, "consentId"), required(request, "sessionId"));
	}

	private String required(Map<String, Object> request, String key) {
		Object value = request == null ? null : request.get(key);
		String text = value == null ? "" : value.toString().trim();
		if (text.isBlank() || text.length() > 200) throw new IllegalArgumentException("Required field is missing or invalid: " + key);
		return text;
	}
	private String optional(Map<String, Object> request, String key, String fallback) {
		Object value = request == null ? null : request.get(key);
		String text = value == null ? "" : value.toString().trim();
		return text.isBlank() ? fallback : text;
	}
}
