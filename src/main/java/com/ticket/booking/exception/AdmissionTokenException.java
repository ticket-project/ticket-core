package com.ticket.booking.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/**
 * admission token 검증 실패다. 없으면 E8000(대기열 진입), 만료면 E8001(대기열 재진입), 그 밖은 E8002(재발급)다 — 클라이언트의 다음 행동이 달라 코드를 나눈다.
 *
 * <p><b>{@code reason}은 응답에 노출되지 않는다.</b> 서명 불일치·audience 불일치·subject 파싱 실패처럼 검증이 어디서 깨졌는지는 공격자에게 알려줄 정보가 아니므로, 공개 메시지는
 * 사유와 무관하게 코드마다 하나로 고정하고 진단 문구는 handler의 로그로만 나간다.
 */
public final class AdmissionTokenException extends TicketException {
    private final String reason;

    public AdmissionTokenException(final AdmissionErrorCode errorCode, final String reason) {
        this(errorCode, reason, null);
    }

    public AdmissionTokenException(
            final AdmissionErrorCode errorCode, final String reason, final @Nullable Throwable cause) {
        super(errorCode, errorCode.getMessage(), null, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
