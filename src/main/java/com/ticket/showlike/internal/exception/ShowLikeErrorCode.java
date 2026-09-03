package com.ticket.showlike.internal.exception;

import com.ticket.error.ErrorCode;

/**
 * showlike module이 소유하는 오류 코드다.
 *
 * <p>E7xxx 대역은 "공연"으로 묶여 있어 showlike(E7001)와 catalog(E7002)가 나눠 갖는다. 코드 값이
 * 외부 계약이라 module 경계에 맞춰 재번호하지 않은 결과다.
 */
public enum ShowLikeErrorCode implements ErrorCode {

    E7001("이미 찜한 공연");

    private final String description;

    ShowLikeErrorCode(final String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
