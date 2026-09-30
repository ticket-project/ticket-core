---
name: loadtest
description: ticket-core 부하 테스트의 준비·실행·분석을 명시적으로 요청받았을 때 사용한다. 일반 검증이나 조회 코드 검토만으로 부하를 실행하지 않는다.
---

# Core 부하 검증

## Read Order

1. [AGENTS.md](../../../AGENTS.md)의 Core·Queue·gatling-test 소유권을 확인한다.
2. [Core 부하 검증](../../../docs/testing.md#core-부하-검증)과 [Core 용량 관측](../../../docs/operations.md#core-용량-관측)을 읽는다.
3. 형제 `../gatling-test`의 [README](https://github.com/ticket-project/gatling-test/blob/master/README.md)와 관련 시나리오·feeder·실행 옵션을 확인한다.

## Checks

- Core 단독 용량·좌석 경합·Queue 포함 흐름 중 목적을 정하고 testing.md의 승인·격리·잔재 확인 조건을 적용한다. 이미 승인된 범위는 다시 묻지 않는다.
- 시나리오·옵션·리포트의 원본은 gatling-test이며 실행도 그 저장소에서 한다. Core에 복사하지 않는다.
- 운영 환경 직접 부하 금지와 대상 URL·사용자 수·투입 시간·전용 performanceId·판정 기준의 승인은 testing.md를 따른다.
- 승인된 설정과 실제 결과를 구분한다. 비추적 결과 파일의 삭제·이동과 운영 설정 변경은 이번 부하 실행에 포함하지 않는다.

## Verification

원본 리포트의 실패율·지연을 hold/order 정합성·admission 경로 및 서버 지표와 함께 판정한다. 실제 명령·대상·설정·실측·한계를 보고하고, Issue/PR 기록은 사용자가 요청한 범위에서만 한다.
