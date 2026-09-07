/**
 * Favorite module: 회원이 공연(Show)에 남기는 찜(ShowLike)의 데이터와 불변식(같은 회원이 같은 공연을
 * 두 번 찜할 수 없음)을 소유한다.
 *
 * <p>이 module은 <b>업무 module을 하나도 참조하지 않는 leaf</b>다({@code allowedDependencies = {}}).
 * show 존재 확인과 회원 활성 확인은 이 module의 책임이 아니다 — 호출자(show)가 자기 데이터로
 * 먼저 확인한 뒤에만 이 module의 공개 계약을 부른다. 그래서 {@code showId}는 값으로만 다루고
 * show entity를 참조하지 않는다(원래는 {@code @ManyToOne Show}였다가 scalar {@code showId}
 * column으로 바뀌었다).
 *
 * <p>모듈 root에는 다른 module이 쓰는 공개 계약만 둔다: {@link com.ticket.favorite.ShowLikeQuery}
 * (찜 여부·개수·내 찜 목록 조회), {@link com.ticket.favorite.ShowLikeCommand}(찜하기/찜 해제).
 * "내 찜 목록"의 공연 표시값(제목·이미지·공연장 이름)은 이 module이 담지 않는다 — 호출자(show)가
 * {@link com.ticket.favorite.ShowLikeEntry#showId()}로 자기 데이터를 다시 조회해 조립한다.
 *
 * <p>원래는 별도 module({@code com.ticket.showlike})이었다가 순환(show → showlike의 좋아요 개수
 * 조회, showlike → show의 공연 존재 확인·내 찜 목록 표시값 조회) 때문에 show module로 흡수됐었다
 * (ADR 0003 §11). 이번에 그 흡수를 되돌리면서, show → showlike 방향은 그대로 두고(찜 개수 조회)
 * showlike → show 방향(존재 확인, 표시값 조회)만 없애 순환을 해소했다 — show가 자기 데이터로
 * 공연 존재를 확인하고, 찜 목록의 표시값도 show가 직접 조립한다. 자세한 배경은
 * {@code docs/adr/0006-bounded-context-module-boundaries.md}를 본다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Favorite", allowedDependencies = {})
package com.ticket.favorite;
