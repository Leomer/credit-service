package com.bank.credit.client;

import com.bank.credit.dto.CustomerResponse;
import com.bank.credit.exception.BusinessException;
import com.bank.credit.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerClient {

    private final WebClient customerWebClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    /** Finds a customer by document number, protected by a circuit breaker. */
    public Mono<CustomerResponse> findByDocument(String documentNumber) {
        log.debug("Calling customer-service for document {}", documentNumber);
        Mono<CustomerResponse> call = customerWebClient.get()
                .uri("/customers/{doc}", documentNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(
                        new BusinessException("Customer not found: " + documentNumber)))
                .bodyToMono(CustomerResponse.class);
        return circuitBreakerFactory.create("customer-service").run(call, this::fallback);
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
