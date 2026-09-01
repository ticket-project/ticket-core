package com.ticket.web;

import com.ticket.shared.BusinessException;
import com.ticket.shared.BusinessProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Locale;

/**
 * {@link BusinessException}을 RFC 7807 {@link ProblemDetail}로 직렬화하는 전역 오류 계약이다.
 *
 * <p>구체 업무 module의 오류 enum을 import하지 않는다. {@link BusinessException#problem()}이 돌려주는
 * {@link BusinessProblem} 계약만 읽어 응답을 만든다.
 *
 * <p>기존 {@code com.ticket.support.error.CoreException} 기반 오류는 아직 이 handler가 다루지 않는다.
 * 업무 로직이 이 module로 이동하기 전까지 {@code GlobalExceptionHandler}가 그 경로의 안전망을 유지한다.
 * 이 handler에 예상하지 못한 예외(non-{@link BusinessException})를 위한 포괄 handler를 두지 않는 것도
 * 같은 이유다 — 두 {@code @RestControllerAdvice}가 같은 {@code Exception.class}를 각각 선언하면 어느
 * bean이 실제로 응답을 만드는지 Spring의 advice 선택 순서에 좌우되어, 기존 {@code CoreException} 처리를
 * 조용히 가로챌 위험이 있다.
 */
@RestControllerAdvice
public class GlobalProblemDetailHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(final BusinessException exception) {
        final BusinessProblem problem = exception.problem();

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(problem.status()), problem.detail());
        problemDetail.setType(URI.create(
                "urn:ticket:problem:%s:%s".formatted(problem.module(), problem.code())));
        problemDetail.setTitle(problem.title());
        problemDetail.setProperty("code", businessCode(problem));

        return problemDetail;
    }

    private String businessCode(final BusinessProblem problem) {
        return "%s_%s".formatted(
                problem.module().toUpperCase(Locale.ROOT),
                problem.code().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
