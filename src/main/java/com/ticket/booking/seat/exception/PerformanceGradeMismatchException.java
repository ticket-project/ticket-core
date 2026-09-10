package com.ticket.booking.seat.exception;



import com.ticket.booking.support.exception.BookingErrorCode;
import com.ticket.booking.support.exception.BookingException;
import lombok.Getter;
/**
 * 판매 좌석 편성 요청의 PerformanceGrade가 그 회차에 속하지 않는다.
 *
 * <p>{@code performanceGradeId}는 요청이 배정한 값이라 null일 수 있다. 그래서 {@code Long}으로
 * 그대로 받는다 — 진단 값을 unboxing하다가 이 업무 예외가 NPE로 바뀌면 안 된다. 두 값 모두
 * 진단 정보이고 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class PerformanceGradeMismatchException extends BookingException {

    private static final String MESSAGE = "요청한 등급이 이 회차에 속하지 않습니다.";

    private final Long performanceId;
    private final Long performanceGradeId;

    public PerformanceGradeMismatchException(final Long performanceId, final Long performanceGradeId) {
        super(BookingErrorCode.E4004, MESSAGE, null);
        this.performanceId = performanceId;
        this.performanceGradeId = performanceGradeId;
    }
}
