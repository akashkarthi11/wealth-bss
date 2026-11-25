package com.wealth.bss.common.nexus;

// Embedded replacement for the real GrowX Nexus service's request-header cache
// (growx-nexus-service: RequestHeaderServiceController/IfRequestHeaderService), called locally instead
// of over HTTP via a Feign client - RequestHeaderUtil has no separate network hop to make for this.
public interface NexusCacheService {

	String getCacheData(String key, String userCode, String appId);

	String setCacheData(String key, String data, String userCode, String appId);

	boolean removeCacheData(String key, String userCode, String appId);
}
