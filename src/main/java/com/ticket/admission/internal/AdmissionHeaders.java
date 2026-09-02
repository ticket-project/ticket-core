package com.ticket.admission.internal;

/**
 * 예매 API가 읽는 대기열 입장 토큰 헤더 이름이다.
 *
 * <p>이 상수는 admission module 내부 전용이다. 다른 module의 HTTP adapter는 이 class를 import하지
 * 않고 헤더 이름 문자열을 직접 다룬 뒤 그 값을 {@link com.ticket.admission.AdmissionVerifier}에
 * 넘긴다. 헤더 이름 자체는 {@code ticket-queue}와 맞춘 계약이므로 값이 바뀌면 양쪽을 함께 바꾼다.
 */
public final class AdmissionHeaders {

    public static final String ADMISSION_TOKEN = "X-Admission-Token";

    private AdmissionHeaders() {
    }
}
