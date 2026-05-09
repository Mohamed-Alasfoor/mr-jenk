package com.buy01.gatewayservice.filter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitGatewayFilter implements WebFilter, Ordered {

    private final Map<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();
    private final int authMaxRequests;
    private final Duration authWindow;
    private final int mediaWriteMaxRequests;
    private final Duration mediaWriteWindow;

    public RateLimitGatewayFilter(
            @Value("${app.rate-limit.auth.max-requests}") int authMaxRequests,
            @Value("${app.rate-limit.auth.window-seconds}") long authWindowSeconds,
            @Value("${app.rate-limit.media-write.max-requests}") int mediaWriteMaxRequests,
            @Value("${app.rate-limit.media-write.window-seconds}") long mediaWriteWindowSeconds
    ) {
        this.authMaxRequests = authMaxRequests;
        this.authWindow = Duration.ofSeconds(authWindowSeconds);
        this.mediaWriteMaxRequests = mediaWriteMaxRequests;
        this.mediaWriteWindow = Duration.ofSeconds(mediaWriteWindowSeconds);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (CorsUtils.isPreFlightRequest(request)) {
            return chain.filter(exchange);
        }

        RateLimitPolicy policy = resolvePolicy(request);
        if (policy == null) {
            return chain.filter(exchange);
        }

        String key = policy.name + ":" + resolveClientKey(exchange);
        FixedWindowCounter counter = counters.computeIfAbsent(key, ignored -> new FixedWindowCounter());
        if (counter.tryAcquire(policy.maxRequests, policy.window)) {
            return chain.filter(exchange);
        }

        return writeTooManyRequests(exchange, policy.name);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private RateLimitPolicy resolvePolicy(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (method == HttpMethod.POST && ("/auth/register".equals(path) || "/auth/login".equals(path))) {
            return new RateLimitPolicy("auth", authMaxRequests, authWindow);
        }

        if ((method == HttpMethod.POST && "/media/images".equals(path))
                || (method == HttpMethod.DELETE && path.matches("^/media/images/[^/]+$"))) {
            return new RateLimitPolicy("media-write", mediaWriteMaxRequests, mediaWriteWindow);
        }

        return null;
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.trim();
        }

        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange, String policyName) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"message\":\"Too many requests for " + policyName + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private record RateLimitPolicy(String name, int maxRequests, Duration window) {
    }

    private static final class FixedWindowCounter {
        private long windowStartMillis = System.currentTimeMillis();
        private int requestCount;

        synchronized boolean tryAcquire(int maxRequests, Duration window) {
            long now = System.currentTimeMillis();
            long windowMillis = window.toMillis();
            if (now - windowStartMillis >= windowMillis) {
                windowStartMillis = now;
                requestCount = 0;
            }

            if (requestCount >= maxRequests) {
                return false;
            }

            requestCount++;
            return true;
        }
    }
}
