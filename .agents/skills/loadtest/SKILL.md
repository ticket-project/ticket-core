---
name: loadtest
description: ticket-core 부하 테스트의 준비·실행·분석을 요청받았을 때 사용한다.
allowed-tools: Bash(rg:*) PowerShell(.\gradlew.bat:*)
---

# 부하 테스트 실행

1. 목적과 대상(Core 단독 용량, 좌석 경합, Queue 포함 전체 흐름)을 확인한다. [testing.md](../../../docs/testing.md#core-부하-검증)의 안전·격리·판정 조건을 적용한다.
2. 형제 [gatling-test README](https://github.com/ticket-project/gatling-test/blob/main/README.md)와 실제 시나리오에서 옵션·feeder·리포트 경로를 확인한다.
3. 대상 URL, 사용자 수, 투입 시간, 전용 performanceId를 사용자에게 확인받는다. 운영 환경에는 직접 부하를 주지 않는다.
4. 전용 데이터와 토큰, Redis 분리, 이전 실행 잔재를 확인하고 승인된 시나리오를 실행한다.
5. 실패율·지연·정합성과 [operations.md](../../../docs/operations.md#core-용량-관측)의 DB·Redis·executor 지표를 함께 분석한다.
6. 가정·실측·미정 목표·한계를 구분해 보고하고 원본 결과를 Issue/PR에 연결한다. 비추적 결과 파일은 사용자 확인 없이 삭제·이동하지 않는다.

Queue 방출량 등 운영 설정은 실측과 별도 승인이 필요한 결정이다. 예시값을 검증된 목표로 취급하지 않는다.
