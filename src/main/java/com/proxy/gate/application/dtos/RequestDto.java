package com.proxy.gate.application.dtos;

import com.proxy.gate.enums.Methods;

public record RequestDto(Methods method, String url) {
}
