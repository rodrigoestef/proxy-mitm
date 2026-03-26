package com.proxy.gate.application.dtos;

import com.proxy.gate.enums.Methods;
import java.net.URI;

public record RequestDto(Methods method, URI url) {
}
