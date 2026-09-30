# AI 작업 안내

모든 agent는 먼저 [AGENTS.md](../../AGENTS.md)를 읽는다. 이 문서는 AI 자산을 관리하거나 작업별 skill을 찾을 때만 읽는 짧은 2차 router다.

| 원본 | 역할 |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | 공통 agent source of truth / 1차 bootloader와 작업 라우팅 |
| [README.md](../../README.md) | repo/product overview와 로컬 실행 |
| [architecture.md](../architecture.md), [ADR](../adr/README.md) | 현재 architecture와 승인된 결정; ADR의 채택·대체·미적용 상태 확인 |
| [coding-guidelines.md](../coding-guidelines.md) | 코드 작성 기준 |
| [testing.md](../testing.md) | 검증 선택·실행·보고 기준 |
| [core-booking-lifecycle.md](../core-booking-lifecycle.md) | booking 정책과 현재 수명주기 |
| [.agents/skills/](../../.agents/skills/) | 특정 작업에서만 읽는 repo-local 작업 매뉴얼 |
| [.codex/](../../.codex/), [.claude/](../../.claude/) | tool-specific 설정; 공통 규칙은 복사하지 않고 AGENTS와 원본 문서로 연결 |

## 작업별 skill

| 작업 | skill |
| --- | --- |
| 검증 선택·실행·보고 | [verify](../../.agents/skills/verify/SKILL.md) |
| 명시적으로 요청한 부하 준비·실행·분석 | [loadtest](../../.agents/skills/loadtest/SKILL.md) |
| 모듈 의존·공개면·코드 배치 | [modulith-boundaries](../../.agents/skills/modulith-boundaries/SKILL.md) |
| HTTP·오류·모듈 공개 계약 변경 | [api-contract-change](../../.agents/skills/api-contract-change/SKILL.md) |
| schema·JPA 매핑·migration 변경 | [database-change](../../.agents/skills/database-change/SKILL.md) |
| 예매 상태·좌석 점유·판매 정책·동시성 변경 | [booking-change](../../.agents/skills/booking-change/SKILL.md) |
| 조회 성능 검토 | [query-performance-review](../../.agents/skills/query-performance-review/SKILL.md) |

AI 문서는 backend encyclopedia가 되지 않게 작게 유지한다. 읽는 순서는 `AGENTS → 가장 관련 있는 문서 → 가장 작은 관련 source/tests`이며, 실제 repo 문서·코드·테스트를 generic skill보다 우선한다. 불일치는 근거와 함께 보고하고 정책을 임의로 바꾸지 않는다.

[skills-lock.json](../../skills-lock.json)은 모든 repo-local skill의 출처와 skill 본문 파일 바이트의 SHA-256을 기록하는 provenance/integrity manifest다. Codex/Claude가 skill을 자동 실행시키는 설정이나 외부 registry lockfile이 아니다. Skill 추가·수정·삭제 시 목록과 실제 파일에서 계산한 hash를 함께 갱신한다. [.gitattributes](../../.gitattributes)는 skill 파일의 LF를 고정해 체크아웃 줄바꿈 변환에 따른 hash 차이를 막는다.
