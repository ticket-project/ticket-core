package com.ticket.booking.exception;

import com.ticket.shared.exception.TicketException;

/**
 * admission token이 유효하지 않다. admission 검증 예외의 base이며 그 자체로 E8002 계약을 갖는다.
 *
 * <p><b>{@code reason}은 응답에 노출되지 않는다.</b> 서명 불일치·audience 불일치·subject 파싱 실패처럼
 * 검증이 어디서 깨졌는지는 공격자에게 알려줄 정보가 아니므로, 공개 메시지는 사유와 무관하게 하나로
 * 고정하고 진단 문구는 handler의 로그로만 나간다. 토큰 검증 실패를 세분화해 응답에 담지 않는 것이
 * 기존 동작이기도 하다.
 */
public class AdmissionTokenException extends TicketException {

    private static final String MESSAGE = "대기열 입장 토큰이 올바르지 않습니다.";

    private final String reason;

    public AdmissionTokenException(final String reason) {
        this(reason, null);
    }

    public AdmissionTokenException(final String reason, final Throwable cause) {
        this(AdmissionErrorCode.E8002, MESSAGE, reason, cause);
    }

    protected AdmissionTokenException(
            final AdmissionErrorCode errorCode,
            final String message,
            final String reason,
            final Throwable cause
    ) {
        super(errorCode, message, null, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
