package com.proxy.gate.interfaces;

import java.util.Optional;

import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;

public interface MatchRequestUseCaseInterface {

 Optional<MatchRequestResponseDto> execute(RequestDto dto);
}
