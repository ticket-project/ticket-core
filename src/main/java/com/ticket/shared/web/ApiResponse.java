package com.ticket.shared.web;

import org.jspecify.annotations.Nullable;

/**
 * 모든 HTTP 응답을 감싸는 공통 봉투다. 성공이든 실패든 바깥 모양이 같고 알맹이만 {@code data}와 {@code error} 중 한쪽에 들어간다.
 *
 * <p><b>이 클래스는 오류 타입을 알지 않는다.</b> 오류 계약은 같은 shared 모듈의 {@code com.ticket.shared.exception}이 소유하고 그쪽이 봉투를 만들기 위해
 * {@code web}을 참조한다(방향은 {@code exception -> web} 하나다). 봉투가 거꾸로 오류 타입을 참조하면 두 패키지가 서로 참조하게 된다 — 한 모듈 안의 package 순환은
 * Modulith {@code verify()}도 ArchUnit 규칙도 잡지 않으므로 이 방향은 사람이 지킨다. 그래서 {@link #error}는 code/message/data를 완성된 값으로 받기만 한다.
 *
 * <p>component 순서가 JSON 필드 순서(data, error, result)다. 바꾸지 않는다.
 */
public record ApiResponse<T extends @Nullable Object>(
        @Nullable T data, @Nullable ErrorMessage error, ResultType result) {

    public static <S extends @Nullable Object> ApiResponse<S> success() {
        return new ApiResponse<>(null, null, ResultType.SUCCESS);
    }

    public static <S extends @Nullable Object> ApiResponse<S> success(S data) {
        return new ApiResponse<>(data, null, ResultType.SUCCESS);
    }

    public static <S extends @Nullable Object> ApiResponse<S> error(
            final String code, final String message, final @Nullable Object data) {
        return new ApiResponse<>(null, new ErrorMessage(code, message, data), ResultType.ERROR);
    }
}
