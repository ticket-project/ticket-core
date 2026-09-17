/**
 * show의 use case 조립과 트랜잭션 경계, 그리고 밖을 부르는 출력 포트.
 *
 * <p>읽기 모델({@code *Row}·{@code *View}·{@code *Param}·{@code *Criteria})은 {@code
 * com.ticket.show.application.query}에 따로 둔다 — show는 조회 모델 수가 실행 코드를 덮을 만큼 많아, 한 package에 두면 root
 * 목록만으로 무엇이 실행 코드인지 알 수 없다.
 */
@NullMarked
package com.ticket.show.application;

import org.jspecify.annotations.NullMarked;
