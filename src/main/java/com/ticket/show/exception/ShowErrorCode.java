package com.ticket.show.exception;

import com.ticket.error.ErrorCode;

/**
 * show module이 소유하는 오류 코드다.
 *
 * <p>E7xxx 대역은 "공연"으로 묶여 있다. 코드 값이 외부 계약이라 module 경계에 맞춰
 * 재번호하지 않는다. E7001(이미 찜한 공연)은 찜 업무가 favorite module로 분리되며
 * {@code com.ticket.favorite.exception.FavoriteErrorCode}로 옮겨갔다.
 */
public enum ShowErrorCode implements ErrorCode {

    E7002("미지원 공연 정렬");

    private final String description;

    ShowErrorCode(final String description) {
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
