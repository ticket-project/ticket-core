package com.ticket.booking.admission.exception;

/**
 * admission token이 만료됐다. 만료는 클라이언트가 대기열을 다시 타야 한다는 뜻이라 다른 검증 실패와
 * 구분해 알려준다({@link AdmissionTokenException}과 달리 E8001).
 */
public class AdmissionTokenExpiredException extends AdmissionTokenException {

    private static final String MESSAGE = "대기열 입장 토큰이 만료되었습니다.";

    public AdmissionTokenExpiredException(final String reason, final Throwable cause) {
        super(AdmissionErrorCode.E8001, MESSAGE, reason, cause);
    }
}
