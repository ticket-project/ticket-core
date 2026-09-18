/**
 * show의 use case 조립과 트랜잭션 경계다.
 *
 * <p>공개 계약({@code show.api})의 구현과 요청 단위 use case가 함께 있다. 응답으로 내보내는 조합 결과({@code *View})는 바로 아래
 * {@code show.usecase.view}에, 조회 projection과 조회 파라미터({@code *Row}·{@code *Param}·{@code
 * *Criteria})는 {@code show.query}에 따로 둔다 — show는 이 타입 수가 실행 코드를 덮을 만큼 많아, 한 package에 두면 목록만으로 무엇이
 * 실행 코드인지 알 수 없다.
 */
@NullMarked
package com.ticket.show.usecase;

import org.jspecify.annotations.NullMarked;
