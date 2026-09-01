package com.ticket.core.domain;

/**
 * 이 클래스가 검증하던 module 경계(별도 Gradle 서브프로젝트의 build.gradle·settings.gradle
 * 상대 경로)는 Spring Modulith 전환 Task 2(단일 Gradle 프로젝트 통합)에서 사라졌다.
 * {@code docs/superpowers/plans/2026-09-01-ticket-core-spring-modulith.md} Task 2 참고.
 *
 * 동일한 경계 검증은 이후 Task(Application Module 분리, {@code @ApplicationModule} 및
 * Spring Modulith의 {@code ApplicationModules.verify()} 기반 구조 테스트)에서 다시 만든다.
 * 그 전까지 이 파일은 의도적으로 비워 둔다 — 삭제 권한이 없어 내용만 비웠다.
 */
class CoreDomainModuleStructureTest {
}
