package com.ticket.booking.exception;

import com.ticket.shared.exception.ErrorCode;

/**
 * booking module이 소유하는 오류 코드다.
 *
 * <p>E3xxx(회차 예매 정책)·E4xxx(회차 좌석)·E5xxx(주문)·E6xxx(선점)를 모두 이 module이 갖는다. E3xxx는 회차 판매 정책 판정(예매 가능 여부)의 오류다 — 그 정책 데이터와
 * 판정 모두 {@code booking.salespolicy.domain.PerformanceSalesPolicy}가 소유하므로 booking의 오류다. 코드 값은 외부 계약이라 재번호하지 않는다.
 * E4003~E4005는 던지는 곳이 없어 지웠다 — 다른 뜻으로 다시 쓰지 않는다.
 */
public enum BookingErrorCode implements ErrorCode {
    E3001("과거 공연은 예매할 수 없습니다."),
    E3002("아직 예매가 오픈되지 않았습니다."),
    E3003("이용 가능한 좌석이 없습니다."),
    // 요청 좌석이 그 회차의 좌석이 아니다.
    E4000("요청한 좌석 정보와 일치하지 않습니다."),
    E4001("이미 선택된 좌석입니다."),
    E4002("본인이 선택한 좌석만 해제할 수 있습니다."),
    // 선택한 적 없는 좌석과 남이 선택한 좌석을 구분하지 않는다 — 남의 선택 사실을 응답에 흘리지 않는다.
    E4006("선택한 좌석만 예매할 수 있습니다."),
    E4007("좌석 선택 시간이 지났습니다. 좌석을 다시 선택해 주세요."),
    E5002("결제 대기 주문만 처리할 수 있습니다."),
    E5003("본인 주문만 처리할 수 있습니다."),
    E5004("이미 진행 중인 결제 대기 주문이 있습니다."),
    E6000("좌석이 이미 선점되었습니다."),
    E6001("선점 가능한 좌석 수를 초과하였습니다."),
    E6003("좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요.");
    private final String message;

    BookingErrorCode(final String message) {
        this.message = message;
    }

    /** 응답 {@code error.message}로 나가는 공개 문구다. 외부 계약이라 바꾸지 않는다. */
    public String getMessage() {
        return message;
    }
}
