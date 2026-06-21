# 부하 테스트 기준

이 문서는 부하 테스트 문서의 진입점이다.

## 목적

로컬 환경에서 예매 오픈 순간의 핵심 위험을 재현한다.

- 대기열 진입 처리량
- 대기열 토큰 발급과 상태 조회 흐름
- 같은 좌석 hold 경합

운영 환경에는 직접 부하 테스트를 실행하지 않는다. 운영과 가까운 처리량은 별도 스테이징 환경에서 같은 시나리오를 재사용해 확인한다.

## 상세 문서

현재 상세 실행 절차는 아래 문서에 있다.

- [load-test/ticket-open-local.md](load-test/ticket-open-local.md)

실제 Gatling 프로젝트는 sibling 저장소 `../ticket-gatling-load-tests/load-tests/gatling`에 있다.

## 기본 실행 흐름

1. Redis와 필요한 로컬 관측성 도구를 실행한다.
2. API 서버를 local 프로파일로 실행한다.
3. 테스트용 회원 JWT 또는 seed 회원 로그인을 준비한다.
4. Gatling Simulation 또는 관련 JUnit 정합성 테스트를 실행한다.
5. 실패율, 응답 시간, 대기열 admitted/waiting 비율, hold 성공 건수를 확인한다.

## 관련 명령

```powershell
.\gradlew.bat :core:core-api:bootRun
.\gradlew.bat :core:core-api:test --tests "com.ticket.core.config.seed.SeedDataLoaderTest"
cd ..\ticket-gatling-load-tests
.\gradlew.bat -p load-tests/gatling test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 주의점

- 운영 환경에 직접 부하를 주지 않는다.
- 로컬 검증은 절대 처리량보다 정합성 오류 발견을 우선한다.
- 테스트 데이터의 `performanceId`, `seatIds`, 회원 토큰은 실제 seed 데이터와 맞아야 한다.
