/**
 * Favorite BC: 회원이 공연(Show)에 남기는 찜(ShowLike)의 데이터와 불변식(같은 회원이 같은 공연을
 * 두 번 찜할 수 없음)을 소유한다.
 *
 * <p>이 module은 <b>업무 module을 하나도 참조하지 않는 leaf</b>다({@code allowedDependencies = {}}).
 * show 존재 확인과 회원 활성 확인은 이 module의 책임이 아니다 — 호출자(show)가 자기 데이터로
 * 먼저 확인한 뒤에만 이 module의 공개 계약을 부른다. {@code showId}는 값으로만 다루고 show
 * entity를 참조하지 않는다.
 *
 * 공개 계약:
 * - ShowLikeQuery (찜 여부·개수·내 찜 목록 조회)
 * - ShowLikeCommand (찜하기/찜 해제)
 *
 * <p>"내 찜 목록"의 공연 표시값(제목·이미지·공연장 이름)은 이 module이 담지 않는다 — 호출자(show)가
 * {@link com.ticket.favorite.ShowLikeEntry#showId()}로 자기 데이터를 다시 조회해 조립한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Favorite", allowedDependencies = {})
package com.ticket.favorite;
