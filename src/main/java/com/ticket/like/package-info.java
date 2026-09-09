/**
 * Like BC: 회원이 어떤 대상(현재는 공연(Show))에 남기는 찜(Like)의 데이터와 불변식(같은 회원이
 * 같은 대상을 두 번 찜할 수 없음)을 소유한다. 찜하기·찜 해제·찜 상태 조회의 HTTP endpoint와
 * use case도 이 module이 소유한다.
 *
 * <p>대상 종류는 {@link com.ticket.like.LikeType}으로 값화돼 있다. 지금은 {@code SHOW} 하나뿐이지만
 * 공연장·출연자 찜이 생겨도 이 module과 테이블(LIKES)을 늘리지 않고 값만 추가한다. 다만 대상별
 * 표시값 조립(예: "내 찜 목록"에 보여줄 공연 제목·이미지)은 여전히 그 대상을 아는 module의
 * 책임이다 — 이 module은 줄여주지 않는다.
 *
 * <p><b>이 module은 다른 BC의 entity 존재 여부를 자기 invariant로 잡지 않는다.</b>
 * {@code targetId}는 값으로만 다루고, 존재하지 않는 targetId를 찜해도 막지 않는다 —
 * {@code show -> like}(공연 상세의 찜 개수 조회)는 지울 수 없는 의존이라, 이 module이 대상
 * 존재를 확인하려면 {@code like -> show}가 생겨 순환이 된다(ADR 0006 §2가 없앤 순환).
 * 회원 활성 확인은 다르다 — JWT 인증만으로는 탈퇴 회원을 걸러낼 수 없어({@code member} BC의
 * 인증 필터는 서명·만료만 검사하고 탈퇴 후에도 access token은 만료 전까지 유효하다) 이
 * module이 {@link com.ticket.member.MemberLookup}을 직접 불러 다시 확인한다.
 * {@code member}는 아무 업무 module도 참조하지 않는 leaf라 이 방향은 순환을 만들지 않는다.
 *
 * 공개 계약:
 * - LikeQuery (찜 여부·개수·내 찜 목록 조회)
 * - LikeCommand (찜하기/찜 해제)
 * - LikeType (찜 대상 종류)
 *
 * <p>"내 찜 목록"(showId 목록에 제목·이미지·공연장 이름을 붙여 보여주는 것)은 이 module이 아니라
 * show의 {@code GetMyShowLikesUseCase}가 한다 — 대상 표시값 조립은 그 대상을 아는 module의
 * 책임이라는 원칙 때문이다. 여러 BC를 넘나드는 "내 정보" 조합을 전담하는 module(가칭 mypage)이
 * 생기면 그때 옮길 후보다(아직 만들지 않는다 — 지금은 이 하나의 화면 때문에 새 BC를 만들
 * 근거가 부족하다).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Like", allowedDependencies = {"member"})
package com.ticket.like;
