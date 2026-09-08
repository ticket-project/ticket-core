/**
 * member의 회원 command use case(부하 테스트 회원 시드, 탈퇴)다.
 *
 * <p>{@code @NamedInterface("seed")}는 {@code com.ticket.seed.SeedDataLoader}가
 * {@link com.ticket.member.application.member.command.SeedLoadTestMembersUseCase}를
 * 호출해 부하 테스트 회원을 만들 수 있게 연다({@code com.ticket.seed}의 package-info 참고).
 * 이 package에는 {@code WithdrawCurrentMemberUseCase} 등 member 전용 구현도 함께 있어
 * NamedInterface가 그것까지 노출한다.
 */
@org.springframework.modulith.NamedInterface("seed")
package com.ticket.member.application.member.command;
