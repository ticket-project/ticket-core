/**
 * Like BC: 회원이 어떤 대상(현재는 공연(Show))에 남기는 찜(Like)의 데이터와 불변식(같은 회원이
 * 같은 대상을 두 번 찜할 수 없음)을 소유한다.
 *
 * <p>대상 종류는 {@link com.ticket.like.LikeType}으로 값화돼 있다. 지금은 {@code SHOW} 하나뿐이지만
 * 공연장·출연자 찜이 생겨도 이 module과 테이블(LIKES)을 늘리지 않고 값만 추가한다. 다만 대상별
 * 표시값 조립과 대상 존재 확인은 여전히 그 대상을 아는 module의 책임이다 — 이 module은 줄여주지
 * 않는다.
 *
 * <p>이 module은 <b>업무 module을 하나도 참조하지 않는 leaf</b>다({@code allowedDependencies = {}}).
 * 대상 존재 확인과 회원 활성 확인은 이 module의 책임이 아니다 — 호출자(show)가 자기 데이터로
 * 먼저 확인한 뒤에만 이 module의 공개 계약을 부른다. {@code targetId}는 값으로만 다루고 대상
 * entity를 참조하지 않는다.
 *
 * 공개 계약:
 * - LikeQuery (찜 여부·개수·내 찜 목록 조회)
 * - LikeCommand (찜하기/찜 해제)
 * - LikeType (찜 대상 종류)
 *
 * <p>"내 찜 목록"의 대상 표시값(공연이면 제목·이미지·공연장 이름)은 이 module이 담지 않는다 —
 * 호출자(show)가 {@link com.ticket.like.LikeEntry#targetId()}로 자기 데이터를 다시 조회해
 * 조립한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Like", allowedDependencies = {})
package com.ticket.like;
