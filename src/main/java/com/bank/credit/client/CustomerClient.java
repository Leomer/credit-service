package com.bank.credit.client;

import com.bank.credit.dto.CustomerResponse;
import com.bank.credit.exception.BusinessException;
import com.bank.credit.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class CustomerClient {

    private static final String KEY_PREFIX = "customer:";

    private final WebClient customerWebClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ReactiveStringRedisTemplate redis;
    private final Duration ttl;

    public CustomerClient(WebClient customerWebClient,
                          ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory,
                          ReactiveStringRedisTemplate redis,
                          @Value("${cache.customer-ttl-minutes}") long ttlMinutes) {
        this.customerWebClient = customerWebClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Finds a customer by document number, protected by a circuit breaker. */
    public Mono<CustomerResponse> findByDocument(String documentNumber) {
        log.debug("Calling customer-service for document {}", documentNumber);
        String key = KEY_PREFIX + documentNumber;
        return redis.opsForValue().get(key)
                .onErrorResume(e -> {
                    log.warn("Redis read failed, using customer-service: {}", e.toString());
                    return Mono.empty();
                })
                .map(this::fromCache)
                .doOnNext(c -> log.debug("Customer {} found in cache", documentNumber))
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(documentNumber, key)));
    }

    /** Calls customer-service and stores the result in Redis. */
    private Mono<CustomerResponse> fetchAndCache(String documentNumber, String key) {
        log.debug("Customer {} not in cache, calling customer-service", documentNumber);
        Mono<CustomerResponse> call = customerWebClient.get()
                .uri("/customers/{doc}", documentNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(
                        new BusinessException("Customer not found: " + documentNumber)))
                .bodyToMono(CustomerResponse.class);
        return circuitBreakerFactory.create("customer-service").run(call, this::fallback)
                .flatMap(customer -> redis.opsForValue()
                        .set(key, toCache(customer), ttl)
                        .onErrorResume(e -> {
                            log.warn("Redis write failed: {}", e.toString());
                            return Mono.just(false);
                        })
                        .thenReturn(customer));
    }

    /** Format: documentNumber|type|profile (empty text when null). */
    private String toCache(CustomerResponse c) {
        return String.join("|", nullToEmpty(c.getDocumentNumber()),
                nullToEmpty(c.getType()), nullToEmpty(c.getProfile()));
    }

    private CustomerResponse fromCache(String value) {
        String[] parts = value.split("\\|", -1);
        CustomerResponse c = new CustomerResponse();
        c.setDocumentNumber(emptyToNull(parts[0]));
        c.setType(emptyToNull(parts[1]));
        c.setProfile(emptyToNull(parts[2]));
        return c;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    /** Business errors pass through; anything else becomes a 503. */
    private Mono<CustomerResponse> fallback(Throwable ex) {
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        log.warn("customer-service fallback triggered: {}", ex.toString());
        return Mono.error(new ServiceUnavailableException(
                "customer-service is unavailable, try again later"));
    }
}
