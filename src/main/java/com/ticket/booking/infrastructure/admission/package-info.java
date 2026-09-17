/**
 * 예매 입장 토큰의 설정과 검증 구현이다.
 *
 * <p>토큰 형식(JWT)과 서명 키 설정은 여기서만 안다. application은 {@link
 * com.ticket.booking.application.port.AdmissionVerifier} 계약으로만 부른다.
 */
@NullMarked
package com.ticket.booking.infrastructure.admission;

import org.jspecify.annotations.NullMarked;
