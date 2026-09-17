/**
 * show의 use case 조립과 트랜잭션 경계다.
 *
 * <p>공개 계약({@code show.api})의 구현과 요청 단위 use case가 함께 있다. 읽기 모델과 조회 계약({@code *QueryPort}·{@code
 * *Row}·{@code *View}·{@code *Param})은 {@code show.query}에 따로 둔다 — show는 조회 모델 수가 실행 코드를 덮을 만큼 많아,
 * 한 package에 두면 목록만으로 무엇이 실행 코드인지 알 수 없다.
 */
@NullMarked
package com.ticket.show.usecase;

import org.jspecify.annotations.NullMarked;
