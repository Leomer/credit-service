package com.bank.credit.client;

import com.bank.credit.dto.CustomerResponse;
import com.bank.credit.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerClient {

    private final WebClient customerWebClient;

    /** Finds a customer by document number or fails with BusinessException. */
    public Mono<CustomerResponse> findByDocument(String documentNumber) {
        log.debug("Calling customer-service for document {}", documentNumber);
        return customerWebClient.get()
                .uri("/customers/{doc}", documentNumber)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(
                        new BusinessException("Customer not found: " + documentNumber)))
                .bodyToMono(CustomerResponse.class);
    }
}
