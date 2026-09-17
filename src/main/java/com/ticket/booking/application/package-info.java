/**
 * booking의 use case 조립과 트랜잭션 경계.
 *
 * <p>root에는 실행 코드(조율자·서비스·listener)와 그것들이 주고받는 값만 둔다. 역할이 분명한 셋은 따로 나눈다 — 밖을 부르는 추상은 {@code port},
 * 분산락 계약은 {@code concurrency}, 조회 읽기 모델은 {@code query}, 요청 단위 진입점은 {@code usecase}다.
 */
@NullMarked
package com.ticket.booking.application;

import org.jspecify.annotations.NullMarked;
