/**
 * member의 회원 command use case(부하 테스트 회원 시드, 탈퇴)다.
 *
 * <p>{@code @NamedInterface("seed")}는 {@code com.ticket.seed.internal.SeedDataLoader}가
 * {@link com.ticket.member.internal.application.member.command.SeedLoadTestMembersUseCase}를
 * 호출해 부하 테스트 회원을 만들 수 있게 연다({@code com.ticket.seed}의 package-info 참고).
 * 이 package에는 {@code WithdrawCurrentMemberUseCase} 등 member 전용 구현도 함께 있어
 * NamedInterface가 그것까지 노출한다 — {@code seed}가 실제로 쓰는 건 시드 use case 하나뿐이지만,
 * 이 하나만 더 좁게 떼어내는 재구성은 하지 않았다(과한 조정으로 판단, member 소유 코드를 이
 * task에서 재배치하는 것은 범위 밖).
 */
@NamedInterface("seed")
package com.ticket.member.internal.application.member.command;

import org.springframework.modulith.NamedInterface;
