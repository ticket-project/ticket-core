---
name: verify
description: ticket-core 변경 범위에 맞는 검증을 실행하고 결과를 보고할 때 사용한다.
allowed-tools: Bash(./gradlew:*) PowerShell(.\gradlew.bat:*) Bash(rg:*) Bash(git diff:*)
---

# 검증 실행

1. 변경 범위와 실제 테스트 위치를 확인한다.
2. [testing.md](../../../docs/testing.md#변경별-검증)의 기준으로 필요한 검증을 선택한다.
3. Docker 등 실행 환경과 명령의 테스트 패턴이 실제 테스트를 찾는지 확인한다.
4. 선택한 검증을 실행한다.
5. 실패, 환경 제약, 미검증 범위를 구분한다.
6. 실제 명령·결과·한계를 보고한다.

구조·예매·Redis·이벤트·seed·문서별 진입점과 공통 의무는 [testing.md](../../../docs/testing.md)가 원본이다. 검증 실패를 숨기거나 실행하지 않은 테스트를 통과했다고 보고하지 않는다.
