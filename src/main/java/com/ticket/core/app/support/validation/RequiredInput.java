package com.ticket.core.app.support.validation;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.support.error.CoreException;

/**
 * {@code UseCase.Input}의 필수 component 계약을 한곳에서 판정한다.
 *
 * <p>HTTP가 아닌 adapter(배치, 시드, 다른 진입점)에서 호출해도 지켜져야 하는 조건만 다룬다.
 * Bean Validation은 core-api가 소유하므로 이 모듈은 jakarta.validation에 의존하지 않는다.
 * 판정 실패는 기존 계약대로 {@link ApplicationErrorType#INVALID_INPUT}이다.
 *
 * <p>여기서 검사하는 것은 사용자가 전달한 값의 오류다. {@code execute(null)}처럼 호출부가
 * Input 자체를 넘기지 않은 것은 프로그래머 오류이며 이 클래스가 다루지 않는다.
 */
public final class RequiredInput {

    private RequiredInput() {
    }

    /**
     * 식별자는 null이 아니고 양수여야 한다.
     */
    public static Long positiveId(final Long value, final String name) {
        if (value == null) {
            throw invalid(name + "는 필수입니다.");
        }
        if (value <= 0) {
            throw invalid(name + "는 양수여야 합니다.");
        }
        return value;
    }

    public static String notBlank(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + "는 필수입니다.");
        }
        return value;
    }

    public static <T> T notNull(final T value, final String name) {
        if (value == null) {
            throw invalid(name + "는 필수입니다.");
        }
        return value;
    }

    /**
     * 상한이 없는 페이지 크기다. 상한은 제품 결정이 있는 유스케이스만 갖는다.
     */
    public static int positiveSize(final int value, final String name) {
        if (value <= 0) {
            throw invalid(name + "는 1 이상이어야 합니다.");
        }
        return value;
    }

    /**
     * 상한이 정해진 페이지 크기다. 상한 값은 유스케이스가 소유한다.
     */
    public static int sizeWithin(final int value, final int max, final String name) {
        if (value <= 0 || value > max) {
            throw invalid(name + "는 1 이상 " + max + " 이하여야 합니다.");
        }
        return value;
    }

    private static CoreException invalid(final String message) {
        return new CoreException(ApplicationErrorType.INVALID_INPUT, message);
    }
}
