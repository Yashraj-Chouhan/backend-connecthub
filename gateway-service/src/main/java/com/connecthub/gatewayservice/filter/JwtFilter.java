package com.connecthub.gatewayservice.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.connecthub.gatewayservice.util.JwtUtil;

@Component
/**
 * Rejects protected gateway requests that do not carry a valid Bearer token.
 *
 * <p>Public routes such as login, docs, translation, and the initial WebSocket
 * handshake are intentionally allowed through without JWT validation.</p>
 */
public class JwtFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        token = token.substring(7);

        if (!jwtUtil.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }

        return isPathPublic(path, "/auth")
                || isPathPublic(path, "/ws")
                || isPathPublic(path, "/translate")
                || isPathPublic(path, "/api/translate")
                || isPathPublic(path, "/transcribe")
                || isPathPublic(path, "/api/transcribe")
                || isPathPublic(path, "/fallback")
                || isPathPublic(path, "/actuator/health")
                || isPathPublic(path, "/v3/api-docs")
                || isPathPublic(path, "/swagger-ui")
                || "/swagger-ui.html".equals(path);
    }

    private boolean isPathPublic(String path, String publicRoot) {
        return publicRoot.equals(path) || path.startsWith(publicRoot + "/");
    }
}
