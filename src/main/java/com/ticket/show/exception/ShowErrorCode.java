package com.ticket.show.exception;

import com.ticket.shared.exception.ErrorCode;

/**
 * show module이 소유하는 오류 코드다.
 *
 * <p>E7xxx 대역은 "공연"으로 묶여 있다. 코드 값이 외부 계약이라 module 경계에 맞춰 재번호하지 않는다. E7001(이미 찜한 대상)은 찜 업무가 like module(옛 favorite)로
 * 분리되며 {@code com.ticket.like.exception.LikeErrorCode}로 옮겨갔다.
 */
public enum ShowErrorCode implements ErrorCode {
    E7002("지원하지 않는 정렬 조건입니다.");
    private final String message;

    ShowErrorCode(final String message) {
        this.message = message;
    }

    /** 응답 {@code error.message}로 나가는 공개 문구다. 외부 계약이라 바꾸지 않는다. */
    public String getMessage() {
        return message;
    }
}
