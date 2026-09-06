package com.ticket.booking.internal.application.admission;

/**
 * 대기열 입장 자격을 검증하는 booking의 출력 포트다(원래 별도 admission module의 공개 계약이었다).
 *
 * <p>호출자는 회차가 대기열 입장을 요구하는지 스스로 판단한 뒤에만 이 method를 호출한다. 토큰이
 * 어떤 형식이고 어떻게 서명·해석되는지는 {@code internal.infrastructure.admission} 구현만 안다.
 */
public interface AdmissionVerifier {

    /**
     * 입장 자격을 검증한다. 유효하면 검증 결과를 반환하고, 그렇지 않으면 admission이 소유한 예외를
     * 던진다 — 토큰이 없으면 {@code AdmissionTokenRequiredException}(E8000), 만료면
     * {@code AdmissionTokenExpiredException}(E8001), 그 밖의 검증 실패는
     * {@code AdmissionTokenException}(E8002)이다. 셋 다 HTTP 403이다.
     *
     * @param performanceId 예매하려는 회차 id
     * @param memberId 요청한 회원 id
     * @param admissionToken 요청이 전달한 입장 토큰. null 또는 빈 문자열일 수 있다
     */
    AdmissionVerification verify(long performanceId, long memberId, String admissionToken);
}
