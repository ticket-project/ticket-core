package com.ticket.like.exception;

import com.ticket.shared.exception.ErrorCode;

/**
 * like module이 소유하는 오류 코드다.
 *
 * <p>E7xxx 대역은 옛 catalog(현 show)가 "공연"으로 묶어 쓰던 대역이다. E7001은 찜 업무가 favorite(현 like)로 분리되며 이 module로 옮겨왔다. 코드 값이 외부 계약이라
 * module 경계에 맞춰 재번호하지 않는다. E7002(미지원 공연 정렬)는 show가 소유한다.
 */
public enum LikeErrorCode implements ErrorCode {
    E7001("이미 찜한 대상", "이미 찜한 대상입니다.");
    private final String description;
    private final String message;

    LikeErrorCode(final String description, final String message) {
        this.description = description;
        this.message = message;
    }

    /** 응답 {@code error.message}로 나가는 공개 문구다. 외부 계약이라 바꾸지 않는다. */
    public String getMessage() {
        return message;
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
