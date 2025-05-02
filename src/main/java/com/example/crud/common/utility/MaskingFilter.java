package com.example.crud.common.utility;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

public class MaskingFilter {
    public static ExchangeFilterFunction auth() {
        return ExchangeFilterFunction.ofRequestProcessor(req ->
                Mono.just(ClientRequest.from(req)
                        .headers(h -> h.set("Authorization", "***"))
                        .build()));
    }
}
