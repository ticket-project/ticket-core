package com.ticket.web;

/**
 * 모든 HTTP 응답을 감싸는 공통 봉투다. 성공이든 실패든 바깥 모양이 같고 알맹이만
 * {@code data}와 {@code error} 중 한쪽에 들어간다.
 *
 * <p><b>이 클래스는 오류 타입을 알지 않는다.</b> 오류 계약은 {@code com.ticket.error} module이 소유하고
 * 그 module이 봉투를 만들기 위해 {@code web}을 참조한다. 봉투가 거꾸로 오류 타입을 참조하면
 * {@code error <-> web} 순환이 되어 {@code ModularityTests}가 실패한다. 그래서
 * {@link #error} 는 code/message/data를 완성된 값으로 받기만 한다.
 */
public class ApiResponse<T> {

    private final ResultType result;
    private final T data;
    private final ErrorMessage error;

    private ApiResponse(final ResultType result, final T data, final ErrorMessage error) {
        this.result = result;
        this.data = data;
        this.error = error;
    }

    public static <S> ApiResponse<S> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <S> ApiResponse<S> success(S data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null);
    }

    public static <S> ApiResponse<S> error(final String code, final String message) {
        return error(code, message, null);
    }

    public static <S> ApiResponse<S> error(final String code, final String message, final Object data) {
        return new ApiResponse<>(ResultType.ERROR, null, new ErrorMessage(code, message, data));
    }

    public ResultType getResult() {
        return result;
    }

    public T getData() {
        return data;
    }

    public ErrorMessage getError() {
        return error;
    }
}
