package com.ticket.shared.exception;

import org.jspecify.annotations.Nullable;

/**
 * 요청한 데이터가 없다. 어느 module에서든 뜻이 같아 여기 있다.
 *
 * <p>"어떤" 데이터가 없는지가 업무적으로 구분돼야 하면 그 module이 <b>이 예외를 상속한</b> 자기 예외를 갖는다({@code
 * PerformanceNotFoundException} 등). 상속을 여는 이유는 오류 코드({@link CommonErrorCode#E404})와 HTTP 404 매핑을
 * 그대로 물려주기 위해서다 — module base 예외({@code ShowException} 등)를 상속하면 그 module handler의 상태와 E-code로
 * 응답이 바뀌어 외부 계약이 깨진다.
 *
 * <p>상속한 예외는 자기 상세 문구를 스스로 만든다. 부르는 쪽이 매번 문자열을 조립하지 않게 하려는 것이라, 하위 타입에
 * 문구를 받는 생성자를 두지 않는다.
 *
 * <p>HTTP 404 매핑은 {@code com.ticket.shared.exception.handler.GlobalExceptionHandler}가 안다 — 하위 타입도
 * 같은 handler가 잡는다.
 */
public class NotFoundException extends TicketException {
    private static final String MESSAGE = "요청하신 정보를 찾을 수 없습니다.";

    public NotFoundException() {
        this(null);
    }

    /** @param detail 무엇을 찾지 못했는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로 나가고 고정 {@code message}를 덮지 않는다. */
    public NotFoundException(final @Nullable String detail) {
        super(CommonErrorCode.E404, MESSAGE, detail);
    }
}
