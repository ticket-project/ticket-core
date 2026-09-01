package com.ticket.core.api;

/**
 * 예매 API가 읽는 대기열 입장 토큰 헤더다. 헤더 이름은 HTTP 계약이므로 core-api가 소유하고,
 * 토큰 검증 구현은 core-infra의 어댑터가 맡는다.
 */
public final class AdmissionHeaders {

    public static final String ADMISSION_TOKEN = "X-Admission-Token";

    private AdmissionHeaders() {
    }
}
