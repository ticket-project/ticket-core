package com.ticket.shared;

import java.util.Objects;

/**
 * {@link BusinessProblem} 하나를 운반하는 업무 예외다.
 *
 * <p>구체 업무 오류 타입은 각 module이 소유하고, 이 예외는 그 값을 {@link BusinessProblem} 계약으로만
 * 운반한다. 전역 web adapter는 이 클래스와 {@link BusinessProblem}만 알면 된다.
 */
public class BusinessException extends RuntimeException {

    private final BusinessProblem problem;

    public BusinessException(final BusinessProblem problem) {
        super(Objects.requireNonNull(problem, "problem must not be null").detail());
        this.problem = problem;
    }

    public BusinessProblem problem() {
        return problem;
    }
}
