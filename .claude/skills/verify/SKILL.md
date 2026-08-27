---
name: verify
description: >
  ticket 저장소에서 변경 범위에 맞는 최소 검증을 골라 실행하고 결과를 정확히 보고한다.
  테스트 실행, 빌드 확인, 커밋 전 검증, "이거 돌려봐 / 검증해줘" 요청을 받았을 때 쓴다.
allowed-tools: Bash(./gradlew:*) PowerShell(.\gradlew.bat:*) Bash(rg:*) Bash(git diff:*)
---

# 검증

**전체를 돌리는 것은 기본값이 아니다.** 바꾼 것에 닿는 검증부터 좁게 실행하고, 전체는 push
직전이나 원인을 모를 때 돌린다.

## 무엇을 돌릴지

| 상황 | 실행 |
| --- | --- |
| 컴파일 여부만 빠르게 보고 싶다 | `./gradlew :core:core-api:compileJava` |
| 엔티티, 값 객체, 도메인 정책, `*Finder`를 고쳤다 | `./gradlew :core:core-domain:test` |
| use case, 트랜잭션 경계, 조립, 조회 view를 고쳤다 | `./gradlew :core:core-app:test` |
| Controller, DTO, security, 설정을 고쳤다 | `./gradlew :core:core-api:test` |
| Querydsl 조회, JWT, 암호화, scheduler를 고쳤다 | `./gradlew :core:core-infra:test` |
| 모듈 경계, 패키지 위치, `build.gradle`을 건드렸다 | 구조 테스트 (아래) |
| Redis adapter, key, TTL, expiration listener를 고쳤다 | `./gradlew :core:core-infra:integrationTest` (Docker 필요) |
| 특정 테스트만 보고 싶다 | `./gradlew :core:core-app:test --tests "com.ticket.core.app.order.*"` |
| 배포 산출물까지 확인한다 | `./gradlew clean :bootstrap:bootJar -x test` |
| push·PR 직전 | `./gradlew test :core:core-infra:integrationTest :bootstrap:bootJar` (CI와 같은 명령) |
| 문서만 바꿨다 | `rg -n "찾을_문구"` 와 `git diff --check` |

Windows PowerShell에서는 `.\gradlew.bat`을 쓴다.

## 구조를 건드렸으면 이것부터

```bash
./gradlew :core:core-api:test --tests "com.ticket.core.CoreLayerArchitectureTest"
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainArchitectureTest"
./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"
./gradlew :core:core-api:test --tests "com.ticket.core.CoreApiArchitectureTest"
```

`CoreLayerArchitectureTest`가 계층 의존 방향을 검사하는 본체다. 새 코드의 위치가 의심스러우면
이것부터 돌린다. 무엇을 막는지는 [architecture.md](../../../docs/architecture.md#아키텍처-규칙).

`CoreDomainModuleStructureTest`는 다른 모듈 파일을 **상대 경로로** 읽는다. IDE에서 작업 디렉터리를
바꿔 단독 실행하면 코드가 옳아도 실패하므로, 이 테스트의 실패는 Gradle로 다시 확인한 뒤 판단한다.

## 통합 테스트

Testcontainers를 쓰므로 **Docker가 실행 중이어야 한다.** Docker가 없으면 실패의 원인이 코드가
아니다. `check`가 `integrationTest`에 의존하므로 `check`를 부르면 Docker 없이 실패한다.

Redis key, TTL, expiration listener, Redisson 변경은 단위 테스트만으로 확인했다고 보지 않는다.

## 결과를 보고할 때

- 통과·실패 수를 그대로 적는다. "대부분 통과" 같은 요약은 쓰지 않는다.
- **돌리지 않은 범위를 밝힌다.** "core-domain 통과"와 "전체 통과"는 다른 말이다.
- 실패가 이번 변경 때문인지 기존 상태인지 구분한다. 애매하면 변경 전 커밋에서 같은 명령을 돌려
  비교한다.
- Docker 미실행, Gradle 캐시, 작업 디렉터리처럼 코드와 무관한 원인이면 그 사실을 먼저 적는다.
  환경 문제를 코드 결함으로 보고하지 않는다.
- **검증에 실패한 상태로 커밋하지 않는다.** 테스트 스킵과 `--no-verify`는 없다.

새 테스트를 쓰는 관례(한국어 메서드 이름, 무엇을 함께 고정할지)는
[testing.md](../../../docs/testing.md#새-테스트를-추가할-때)를 본다.
