package com.ticket.shared.web;

import org.jspecify.annotations.Nullable;

/**
 * HTTP 오류 응답 본문이다. 클라이언트는 message가 아니라 code로 분기한다.
 *
 * <p>{@code message}는 오류마다 정해진 공개 문구이고, {@code data}는 그 오류를 좁히는 부가 정보다 (검증 실패한 필드 목록, 인증 실패 사유 등). 둘을 바꿔 담으면 외부 계약이
 * 깨진다.
 *
 * <p>{@link ApiResponse}와 같은 이유로 오류 타입을 알지 않는다 — 완성된 문자열만 받는다.
 */
public record ErrorMessage(
        String code, String message, @Nullable Object data) {}
