# Ticket 저장소 Copilot 지침

이 저장소의 규칙은 루트 [AGENTS.md](../AGENTS.md)와 `docs/`가 단일 원본이다. Copilot Chat, Copilot code
review, Copilot coding agent는 모두 그 기준을 따른다. **이 파일에 규칙을 복제하지 않는다** — 검증 명령이나
모듈 경계처럼 바뀌는 사실은 이 파일이 아니라 `AGENTS.md`와 `docs/`에서 읽는다.

## 규칙을 확인할 곳

| 알아야 하는 것 | 읽을 곳 |
| --- | --- |
| 작업별로 먼저 읽을 문서 | `AGENTS.md` 의 "작업별로 먼저 읽을 문서" |
| 리뷰 기준 전체 | `AGENTS.md` 의 "코드 리뷰" |
| 모듈 경계와 강제되는 규칙 | `docs/architecture.md` |
| 구현 절차와 완료 조건 | `docs/development.md` |
| 주문·hold 후처리 흐름 | `docs/core-booking-lifecycle.md` |
| 검증 명령과 테스트 관례 | `docs/testing.md` |

## 문서를 열 수 없는 경우에도 적용할 최소 기준

- 모든 리뷰, 제안, 설명은 한국어로 작성한다.
- 패치만 보지 말고 주변 코드, 관련 설정, 관련 테스트, 호출 흐름을 함께 본다.
- 스타일 취향보다 실제 결함 가능성, 회귀 위험, 테스트 공백을 우선 본다.
- 요청 범위를 벗어난 기능 추가, 리팩터링, 추상화는 제안하지 않는다.
- findings first 원칙을 따르고 심각도 높은 순서로 적는다. 각 이슈는 왜 문제인지와 어떤 조건에서
  깨지는지를 짧게 적는다.

## 우선 검토 영역

- `auth`: JWT, refresh token, OAuth2, security filter chain, 공개 API 노출
- `hold`, `order`: 주문 시작·만료·취소와 hold 해제의 일관성, 부분 상태 전이
- `performanceseat`: 좌석 상태 계산, selection/hold 충돌, 실시간 브로드캐스트
- `queue`: admission token 검증과 회차별 진입 정책
- `core-api`는 진입점과 설정만, `core-domain`은 업무 규칙, `core-infra`는 기술 구현이다. 경계 위반을 먼저 본다.
- Redis key naming, TTL, expiration listener, scheduler, 분산락 범위
- **DB 트랜잭션 안에서 Redis나 WebSocket을 호출하는 코드는 항상 지적한다.**
- `core-domain`에 `@Scheduled`, `@TransactionalEventListener`, Redisson, Spring Data Redis, Swagger가
  들어오면 구조 위반이다.
