package com.bank.credit.dto;

import lombok.Data;

@Data
public class CustomerResponse {
    private String documentNumber;
    private String type;
    private String profile;
}
