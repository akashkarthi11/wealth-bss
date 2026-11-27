package com.wealth.bss.serviceinterface;

import com.wealth.common.model.dto.admin.IndstocksSaveRequestDto;
import com.wealth.common.model.dto.admin.IndstocksStatusDto;

// The signed-in user's own INDstocks token: save, test, status, remove. Every call is for the caller's own token.
public interface IfIndstocksService {

	IndstocksStatusDto status();

	IndstocksStatusDto save(IndstocksSaveRequestDto request);

	IndstocksStatusDto test();

	boolean remove();
}
