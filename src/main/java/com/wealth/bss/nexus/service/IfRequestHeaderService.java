package com.wealth.bss.nexus.service;

// In-process form of growx-nexus-service IfRequestHeaderService.
public interface IfRequestHeaderService {

	String getCacheData(String key);

	// Returns the full namespaced key that was written.
	String setCacheData(String key, String data);

	boolean removeCacheData(String key);
}
