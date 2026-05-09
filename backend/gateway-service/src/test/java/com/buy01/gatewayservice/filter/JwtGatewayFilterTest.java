package com.buy01.gatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.buy01.gatewayservice.security.AuthenticatedUser;
import com.buy01.gatewayservice.service.JwtService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class JwtGatewayFilterTest {

    @Test
    void shouldAllowUnknownPathWithoutTokenToReachRoutingLayer() {
        JwtService jwtService = mock(JwtService.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(jwtService);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/does-not-exist");

        filter.filter(exchange, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldAllowUnsupportedMethodToReachDownstreamHandler() {
        JwtService jwtService = mock(JwtService.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(jwtService);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/auth/register");

        filter.filter(exchange, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() {
        JwtService jwtService = mock(JwtService.class);
        JwtGatewayFilter filter = new JwtGatewayFilter(jwtService);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/products");

        filter.filter(exchange, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldAuthenticateProtectedRouteWithValidToken() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.parseToken("valid-token"))
                .thenReturn(new AuthenticatedUser("user-1", "seller@example.com", "SELLER"));

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtService);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build()
        );

        filter.filter(exchange, markChainInvoked(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat((String) exchange.getAttribute("authenticatedUserId")).isEqualTo("user-1");
        assertThat((String) exchange.getAttribute("authenticatedUserEmail")).isEqualTo("seller@example.com");
        assertThat((String) exchange.getAttribute("authenticatedUserRole")).isEqualTo("SELLER");
        verify(jwtService).parseToken("valid-token");
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
