/**
 * show 조회 경계의 읽기 모델이다.
 *
 * <p>조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code ShowCursor}·{@code ShowSort}), 조회 port가
 * 돌려주는 projection({@code *Row}), use case가 응답용으로 조합한 결과({@code *View}·{@code *Info}), 그리고 그 조합에 필요한
 * 값 객체가 여기 모인다. show의 application root에는 use case 조립과 트랜잭션 경계를 갖는 코드만 남긴다 — 읽기 모델이 함께 있으면 root
 * 목록만으로는 무엇이 실행 코드인지 알 수 없다.
 */
@NullMarked
package com.ticket.show.application.query;

import org.jspecify.annotations.NullMarked;
