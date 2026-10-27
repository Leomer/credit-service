package com.bank.credit.controller;

import com.bank.credit.dto.BalanceUpdateRequest;
import com.bank.credit.dto.CreditDto;
import com.bank.credit.service.CreditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<CreditDto> create(@Valid @RequestBody CreditDto dto) {
        return creditService.create(dto);
    }

    @GetMapping
    public Flowable<CreditDto> findAll() {
        return creditService.findAll();
    }

    @GetMapping("/{creditNumber}")
    public Single<CreditDto> findByCreditNumber(@PathVariable String creditNumber) {
        return creditService.findByCreditNumber(creditNumber);
    }

    @GetMapping("/customer/{customerDocument}")
    public Flowable<CreditDto> findByCustomer(@PathVariable String customerDocument) {
        return creditService.findByCustomer(customerDocument);
    }

    @PutMapping("/{creditNumber}")
    public Single<CreditDto> update(@PathVariable String creditNumber,
                                    @Valid @RequestBody CreditDto dto) {
        return creditService.update(creditNumber, dto);
    }

    @PatchMapping("/{creditNumber}/balance")
    public Single<CreditDto> updateBalance(@PathVariable String creditNumber,
                                           @Valid @RequestBody BalanceUpdateRequest request) {
        return creditService.updateBalance(creditNumber, request.getAmount());
    }

    @DeleteMapping("/{creditNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String creditNumber) {
        return creditService.delete(creditNumber);
    }
}
