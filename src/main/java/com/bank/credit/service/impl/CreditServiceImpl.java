package com.bank.credit.service.impl;

import com.bank.credit.client.CustomerClient;
import com.bank.credit.dto.CreditDto;
import com.bank.credit.exception.BusinessException;
import com.bank.credit.exception.NotFoundException;
import com.bank.credit.mapper.CreditMapper;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditType;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.service.CreditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditRepository repository;
    private final CustomerClient customerClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final Clock clock;

    @Value("${business.default-due-days}")
    private int defaultDueDays;

    @Override
    public Single<CreditDto> create(CreditDto dto) {
        log.info("Creating {} credit for customer {}", dto.getCreditType(), dto.getCustomerDocument());
        return Single.fromPublisher(customerClient.findByDocument(dto.getCustomerDocument()))
                .flatMap(customer -> hasOverdueDebt(dto.getCustomerDocument())
                        .flatMap(overdue -> overdue
                                ? Single.<CreditDto>error(new BusinessException(
                                "Customer has overdue debt and cannot acquire new products"))
                                : Single.just(dto))
                        .flatMap(ok -> {
                            dto.setCustomerType(customer.getType());
                            return validate(dto);
                        }))
                .map(valid -> {
                    Credit entity = CreditMapper.toEntity(valid);
                    entity.setId(null);
                    entity.setCreditNumber(generateCreditNumber());
                    entity.setBalance(BigDecimal.ZERO);
                    if (entity.getDueDate() == null) {
                        entity.setDueDate(LocalDate.now(clock).plusDays(defaultDueDays));
                    }
                    return entity;
                })
                .flatMap(entity -> Single.fromPublisher(repository.save(entity)))
                .map(CreditMapper::toDto);
    }

    @Override
    public Flowable<CreditDto> findAll() {
        return Flowable.fromPublisher(repository.findAll()).map(CreditMapper::toDto);
    }

    @Override
    public Single<CreditDto> findByCreditNumber(String creditNumber) {
        return findEntity(creditNumber).map(CreditMapper::toDto);
    }

    @Override
    public Flowable<CreditDto> findByCustomer(String customerDocument) {
        return Flowable.fromPublisher(repository.findByCustomerDocument(customerDocument))
                .map(CreditMapper::toDto);
    }

    @Override
    public Single<CreditDto> update(String creditNumber, CreditDto dto) {
        log.info("Updating credit {}", creditNumber);
        return findEntity(creditNumber)
                .flatMap(existing -> {
                    // Only the limit can be modified directly; balance changes via transactions
                    existing.setCreditLimit(dto.getCreditLimit());
                    if (existing.getBalance().compareTo(existing.getCreditLimit()) > 0) {
                        return Single.<Credit>error(new BusinessException(
                                "New limit cannot be lower than the current balance"));
                    }
                    return Single.fromPublisher(repository.save(existing));
                })
                .map(CreditMapper::toDto);
    }

    @Override
    public Single<CreditDto> updateBalance(String creditNumber, BigDecimal amount) {
        log.info("Updating balance of credit {} by {}", creditNumber, amount);
        return findEntity(creditNumber)
                .flatMap(credit -> {
                    Query query = new Query(Criteria.where("creditNumber").is(creditNumber));
                    String errorMessage;
                    if (amount.signum() > 0) {
                        BigDecimal maxCurrentBalance = credit.getCreditLimit().subtract(amount);
                        if (maxCurrentBalance.signum() < 0) {
                            return Single.<Credit>error(new BusinessException("Credit limit exceeded"));
                        }
                        query.addCriteria(Criteria.where("balance").lte(maxCurrentBalance));
                        errorMessage = "Credit limit exceeded";
                    } else {
                        query.addCriteria(Criteria.where("balance").gte(amount.negate()));
                        errorMessage = "Payment exceeds the outstanding debt";
                    }
                    return Maybe.fromPublisher(mongoTemplate.findAndModify(
                                    query, new Update().inc("balance", amount),
                                    FindAndModifyOptions.options().returnNew(true), Credit.class))
                            .switchIfEmpty(Single.<Credit>error(new BusinessException(errorMessage)));
                })
                .map(CreditMapper::toDto);
    }

    @Override
    public Completable delete(String creditNumber) {
        log.info("Deleting credit {}", creditNumber);
        return findEntity(creditNumber)
                .flatMapCompletable(existing -> Completable.fromPublisher(repository.delete(existing)));
    }

    @Override
    public Single<Boolean> hasOverdueDebt(String customerDocument) {
        LocalDate today = LocalDate.now(clock);
        return Flowable.fromPublisher(repository.findByCustomerDocument(customerDocument))
                .any(c -> c.getDueDate() != null
                        && c.getDueDate().isBefore(today)
                        && c.getBalance().signum() > 0);
    }

    /** Validates business rules by customer type and credit type. */
    private Single<CreditDto> validate(CreditDto dto) {
        String customerType = dto.getCustomerType();
        CreditType type = dto.getCreditType();

        if (type == CreditType.PERSONAL && !"PERSONAL".equals(customerType)) {
            return Single.error(new BusinessException("Personal credit requires a personal customer"));
        }
        if (type == CreditType.BUSINESS && !"BUSINESS".equals(customerType)) {
            return Single.error(new BusinessException("Business credit requires a business customer"));
        }
        if (type == CreditType.PERSONAL) {
            return Single.fromPublisher(repository.countByCustomerDocumentAndCreditType(
                            dto.getCustomerDocument(), CreditType.PERSONAL))
                    .flatMap(count -> count > 0
                            ? Single.<CreditDto>error(new BusinessException(
                            "Personal customers can have only one personal credit"))
                            : Single.just(dto));
        }
        return Single.just(dto);
    }

    private String generateCreditNumber() {
        return "CR" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Single<Credit> findEntity(String creditNumber) {
        return Maybe.fromPublisher(repository.findByCreditNumber(creditNumber))
                .switchIfEmpty(Single.error(
                        new NotFoundException("Credit not found: " + creditNumber)));
    }
}
