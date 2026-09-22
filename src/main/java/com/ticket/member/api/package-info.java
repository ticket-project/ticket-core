/**
 * member가 다른 module에 공개하는 계약이다.
 *
 * <p>행위 계약 둘: {@link com.ticket.member.api.MemberAccountApi}(등록·자격 증명 확인·활성 확인·소셜 계정 해석·탈퇴)와
 * {@link com.ticket.member.api.MemberLookupApi}(entity 대신 쓰는 회원 조회·활성 검증). 나머지는 그 계약이 주고받는 값이다.
 *
 * <p><b>비밀번호 해시는 이 계약을 통과하지 않는다.</b> 오가는 것은 {@link com.ticket.member.api.RawPassword}뿐이고 해싱과 일치 확인은 member 안에서 끝난다.
 * {@link com.ticket.member.api.AuthenticatedMember}는 다른 module의 controller가 parameter로 받는 인증 주체이며, 형제 저장소
 * {@code ticket-queue}의 동명 타입과 데이터 형태를 맞춘 계약이라 이름을 바꾸지 않는다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.member.api;

import org.jspecify.annotations.NullMarked;
