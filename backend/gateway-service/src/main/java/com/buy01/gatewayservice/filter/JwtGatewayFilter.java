package com.buy01.gatewayservice.filter;

import com.buy01.gatewayservice.security.AuthenticatedUser;
import com.buy01.gatewayservice.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements WebFilter {

    private final JwtService jwtService;

    public JwtGatewayFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (CorsUtils.isPreFlightRequest(request)) {
            return chain.filter(exchange);
        }

        AccessType accessType = resolveAccessType(request);
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            if (accessType != AccessType.PROTECTED) {
                return chain.filter(exchange);
            }
            return writeUnauthorized(exchange, "Unauthorized");
        }

        if (accessType == AccessType.UNKNOWN) {
            return chain.filter(exchange);
        }

        try {
            AuthenticatedUser user = jwtService.parseToken(authorization.substring(7));
            exchange.getAttributes().put("authenticatedUserId", user.userId());
            exchange.getAttributes().put("authenticatedUserEmail", user.email());
            exchange.getAttributes().put("authenticatedUserRole", user.role());
            return chain.filter(exchange);
        } catch (JwtException exception) {
            return writeUnauthorized(exchange, "Invalid or expired token");
        }
    }

    private AccessType resolveAccessType(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (path.equals("/actuator/health") || path.equals("/actuator/info")) {
            return AccessType.PUBLIC;
        }

        if (method == HttpMethod.POST && (path.equals("/auth/register") || path.equals("/auth/login"))) {
            return AccessType.PUBLIC;
        }

        if ((method == HttpMethod.GET || method == HttpMethod.PUT) && path.equals("/me")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.GET && path.equals("/products")) {
            return AccessType.PUBLIC;
        }

        if (method == HttpMethod.GET && path.equals("/products/me")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.POST && path.equals("/products")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.GET && path.matches("^/products/[^/]+$") && !path.equals("/products/me")) {
            return AccessType.PUBLIC;
        }

        if ((method == HttpMethod.PUT || method == HttpMethod.DELETE) && path.matches("^/products/[^/]+$")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.GET && path.equals("/media/images")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.POST && path.equals("/media/images")) {
            return AccessType.PROTECTED;
        }

        if (method == HttpMethod.GET && path.matches("^/media/images/[^/]+$")) {
            return AccessType.PUBLIC;
        }

        if (method == HttpMethod.DELETE && path.matches("^/media/images/[^/]+$")) {
            return AccessType.PROTECTED;
        }

        return AccessType.UNKNOWN;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(body)));
    }

    private enum AccessType {
        PUBLIC,
        PROTECTED,
        UNKNOWN
    }
}
