package com.example.smart_fuel_management_system;

import lombok.AllArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@Component
@AllArgsConstructor
public class CustomRateLimiterFilter_TokenBucket implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final int CAPACITY = 10;
    private static final double REFILL_RATE = 1.0;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String ip = extractIp(exchange);
        String key = "token_bucket:" + ip;

        long now = System.currentTimeMillis();

        return redisTemplate.opsForHash()
                .entries(key)
                .collectMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                )
                .flatMap(data -> {

                    // 1. Load state
                    double tokens = data.containsKey("tokens")
                            ? Double.parseDouble(data.get("tokens"))
                            : CAPACITY;

                    long lastRefill = data.containsKey("lastRefill")
                            ? Long.parseLong(data.get("lastRefill"))
                            : now;

                    // 2. Calculate elapsed time
                    double elapsedSeconds = (now - lastRefill) / 1000.0;

                    // 3. Refill tokens
                    tokens = Math.min(CAPACITY, tokens + elapsedSeconds * REFILL_RATE);

                    // 4. Block if no tokens
                    if (tokens < 1) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    // 5. Consume token
                    tokens -= 1;

                    double finalTokens = tokens;

                    // 6. Save state
                    return redisTemplate.opsForHash()
                            .put(key, "tokens", String.valueOf(finalTokens))
                            .then(redisTemplate.opsForHash()
                                    .put(key, "lastRefill", String.valueOf(now)))
                            .then(redisTemplate.expire(key, Duration.ofMinutes(5)))
                            .then(chain.filter(exchange));
                });
    }

    private String extractIp(ServerWebExchange exchange) {

        String ip = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

        if (ip == null || ip.isEmpty()) {
            ip = Objects.requireNonNull(exchange.getRequest()
                            .getRemoteAddress())
                    .getAddress()
                    .getHostAddress();
        }

        return ip.split(",")[0].trim();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
