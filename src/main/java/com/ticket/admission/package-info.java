/**
 * Admission module: admission token 설정, claim decode와 검증, 만료·위조 실패 해석, Booking에
 * 공개하는 Admission 검증 API를 소유한다.
 *
 * <p>공개 API는 {@link com.ticket.admission.AdmissionVerifier}와
 * {@link com.ticket.admission.AdmissionVerification}이고, 구현은 {@code internal} 아래에 있다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Admission", allowedDependencies = {})
package com.ticket.admission;
