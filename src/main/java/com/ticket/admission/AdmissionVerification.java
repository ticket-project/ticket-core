package com.ticket.admission;

/**
 * {@link AdmissionVerifier#verify(long, long, String)}가 성공했을 때 돌려주는 불변 결과다.
 *
 * <p>검증에 쓰인 회차·회원 식별자만 공개하고, JWT claim이나 토큰 원문·만료 시각 같은 기술 상세는
 * 담지 않는다. 검증 실패는 이 record가 아니라 {@code CoreException}으로 전달된다.
 */
public record AdmissionVerification(long performanceId, long memberId) {
}
