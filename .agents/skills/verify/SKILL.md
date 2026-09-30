---
name: verify
description: ticket-core 변경 범위에 맞는 검증을 선택·실행하고 결과를 보고할 때 사용한다. 부하 실행은 loadtest를 따른다.
---

# 검증 실행

## Read Order

1. [AGENTS.md](../../../AGENTS.md)와 현재 diff로 브랜치·변경 범위를 확인한다.
2. [testing.md](../../../docs/testing.md#테스트를-돌리는-시점)와 [변경별 검증](../../../docs/testing.md#변경별-검증)을 읽는다.
3. 실제 관련 source/tests와 필요한 Gradle task·[CI](../../../.github/workflows/ci.yml)만 확인한다.

## Checks

- 변경에 닿는 검증부터 작업 중 실행하고 위험에 따라 넓힌다. 실행 시점·검증 의무는 testing.md가 원본이다.
- 단일 Gradle 프로젝트이므로 실제 테스트 위치를 찾아 `--tests`로 좁힌다. PowerShell은 `.\gradlew.bat`을 사용한다.
- 테스트 패턴이 실제 클래스를 찾는지와 실행 건수를 확인한다. Docker 등 환경 실패와 코드 결함을 구분한다.
- 부하 실행을 요청받으면 [loadtest](../loadtest/SKILL.md)로 연결한다.

## Verification

필요한 검증이 통과하고 최종 diff가 검증 범위와 일치한 뒤 커밋한다. 실제 명령·결과·실패 원인·미실행 범위를 구분해 보고한다. 문서만 변경했다면 testing.md의 문서 검사로 끝낸다.
