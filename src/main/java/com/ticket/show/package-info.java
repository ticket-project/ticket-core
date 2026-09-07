/**
 * Show module(옛 catalog): Show, Performance, Seat, 공연별 예매 가능 시간과 Hold 한도, PerformanceQueuePolicy,
 * QueueMode, QueueLevel, 공연·회차·좌석 조회, 그리고 Show 좋아요(찜)를 소유한다.
 *
 * <p>구현은 하위 package(web/application/domain/infrastructure)에 있고, 이 module root에는 다른 module이 쓰는 공개 계약만
 * 둔다: {@link com.ticket.show.BookingPolicyLookup}/{@link com.ticket.show.BookingPolicySnapshot}
 * (booking의 즉시 판단용 예매 정책·가격 snapshot), {@link com.ticket.show.ShowLookup}/
 * {@link com.ticket.show.ShowSummary}(show 존재 확인·표시값 조회).
 *
 * <p>{@code PerformanceSeat}(회차별 좌석 판매 상태)는 show가 아니라 booking 소유이며 Task 7에서
 * scalar ID 참조로 정리됐다.
 *
 * <p><b>찜(showlike)을 이 module이 소유하는 이유</b> — 찜 개수(예: {@code Show.viewCount}와 같은
 * 성격의 파생 지표)와 찜하기/해제하기는 Show를 설명하는 부가 속성이지 독자적인 업무가 아니다.
 * 원래 별도 module({@code com.ticket.showlike})이었지만, show의 공연 상세 조회가 좋아요 개수를
 * 얻으려면 찜 데이터를 참조해야 하고(show → showlike) showlike의 write 경로는 공연 존재 확인을
 * 위해 show를 참조해야 해서(showlike → show) 두 module 사이에 순환이 생겼다. "내 찜 목록"
 * ({@code /api/v1/members/me/likes})까지 감안하면 회원 관점 조회를 별도 module에 남겨도 결국
 * member와 같은 순환이 재발하므로, 찜에 관한 모든 것(entity·추가·삭제·개수·내 목록)을 이 module
 * 하나로 흡수해 순환의 여지 자체를 없앴다. 그 결과 이 module은 회원 존재 확인을 위해
 * {@link com.ticket.member.MemberLookup}을 참조한다(단방향) — booking이 {@code Order.memberId}를
 * 위해 member를 참조하는 것과 같은 패턴이다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Show", allowedDependencies = {"member"})
package com.ticket.show;
