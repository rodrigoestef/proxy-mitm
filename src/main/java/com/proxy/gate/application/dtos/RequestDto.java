package com.proxy.gate.application.dtos;

import java.util.Map;
import com.proxy.gate.enums.Methods;

import java.net.URI;

public record RequestDto(Methods method, URI url, String body, Map<String,String> headers) {
}
