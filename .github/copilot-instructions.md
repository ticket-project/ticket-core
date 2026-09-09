# Ticket 저장소 Copilot 지침

이 저장소의 규칙은 루트 [AGENTS.md](../AGENTS.md)와 `docs/`가 단일 원본이다. Copilot Chat, Copilot code
review, Copilot coding agent는 모두 그 기준을 따른다. **이 파일에 규칙을 복제하지 않는다** — 검증 명령이나
모듈 경계처럼 바뀌는 사실은 이 파일이 아니라 `AGENTS.md`와 `docs/`에서 읽는다.

## 규칙을 확인할 곳

| 알아야 하는 것 | 읽을 곳 |
| --- | --- |
| 작업별로 먼저 읽을 문서 | `AGENTS.md` 의 "먼저 읽을 문서" |
| 리뷰 기준 전체 | `AGENTS.md` 의 "코드 리뷰" |
| 모듈 경계와 강제되는 규칙 | `docs/architecture.md` |
| 완료 판정 조건 | `/verify` 스킬 |
| 주문·hold 후처리 흐름 | `docs/core-booking-lifecycle.md` |
| 검증 명령 | `/verify` 스킬 |
| 테스트 관례(작성 방식·명명) | `docs/testing.md` |

## 문서를 열 수 없는 경우에도 적용할 최소 기준

- 모든 리뷰, 제안, 설명은 한국어로 작성한다.
- 패치만 보지 말고 주변 코드, 관련 설정, 관련 테스트, 호출 흐름을 함께 본다.
- 스타일 취향보다 실제 결함 가능성, 회귀 위험, 테스트 공백을 우선 본다.
- 요청 범위를 벗어난 기능 추가, 리팩터링, 추상화는 제안하지 않는다.
- findings first 원칙을 따르고 심각도 높은 순서로 적는다. 각 이슈는 왜 문제인지와 어떤 조건에서
  깨지는지를 짧게 적는다.

## 우선 검토 영역

모듈 경계와 강제되는 세부 규칙은 반드시 `docs/architecture.md`를 따른다. `AGENTS.md`를 읽을 수
없을 때 최소한으로 참고할 실제 모듈은 `booking`, `show`, `venue`, `like`, `member`, `payment`다.
현재 전체 모듈 목록은 `docs/architecture.md`를 따른다 — 위 목록은 문서를 읽을 수 없을 때의 최소
fallback일 뿐 단일 원본이 아니다.
