package com.buy01.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RateLimitGatewayFilterTest {

    @Test
    void shouldThrottleAuthRequestsAfterConfiguredLimit() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(2, 60, 5, 60);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        MockServerWebExchange first = exchange(HttpMethod.POST, "/auth/login");
        MockServerWebExchange second = exchange(HttpMethod.POST, "/auth/login");
        MockServerWebExchange third = exchange(HttpMethod.POST, "/auth/login");

        filter.filter(first, markChainInvoked(chainInvoked)).block();
        filter.filter(second, markChainInvoked(chainInvoked)).block();
        chainInvoked.set(false);
        filter.filter(third, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldIgnorePublicProductReads() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(1, 60, 1, 60);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/products");

        filter.filter(exchange, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    private static WebFilterChain markChainInvoked(AtomicBoolean chainInvoked) {
        return exchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };
    }
}
