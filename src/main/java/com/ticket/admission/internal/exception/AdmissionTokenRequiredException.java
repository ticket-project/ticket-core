package com.ticket.admission.internal.exception;

/**
 * 대기열이 필요한 회차인데 admission token이 아예 오지 않았다.
 *
 * <p>토큰이 잘못된 것과 없는 것을 구분하는 이유는 클라이언트의 다음 행동이 다르기 때문이다 —
 * 없으면 대기열에 진입해야 하고, 잘못됐으면 다시 발급받아야 한다.
 */
public class AdmissionTokenRequiredException extends AdmissionTokenException {

    private static final String MESSAGE = "대기열 입장 토큰이 필요합니다.";

    public AdmissionTokenRequiredException() {
        super(AdmissionErrorCode.E8000, MESSAGE, "admission token missing", null);
    }
}
