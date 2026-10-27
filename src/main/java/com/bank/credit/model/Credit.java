package com.bank.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credits")
public class Credit {

    @Id
    private String id;

    @Indexed(unique = true)
    private String creditNumber;
    private CreditType creditType;
    private String customerDocument;
    private String customerType;
    private BigDecimal creditLimit;
    private BigDecimal balance;
}
