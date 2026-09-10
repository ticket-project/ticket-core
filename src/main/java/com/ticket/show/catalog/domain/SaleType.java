package com.ticket.show.catalog.domain;

import lombok.Getter;

@Getter
public enum SaleType {
    GENERAL("일반판매"),
    EXCLUSIVE("단독판매");

    private final String description;

    SaleType(String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

}
