package com.ticket.catalog.exception;

import org.springframework.http.HttpStatus;

/**
 * 지원하지 않는 공연 정렬 조건이다. 어떤 값이 들어왔는지는 {@code data}에 실린다.
 */
public class UnsupportedShowSortException extends CatalogException {

    public UnsupportedShowSortException(final Object data) {
        super(HttpStatus.BAD_REQUEST, CatalogErrorCode.E7002, "지원하지 않는 정렬 조건입니다.", data);
    }
}
