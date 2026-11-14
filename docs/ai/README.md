# AI 작업 안내

모든 agent는 먼저 [AGENTS.md](../../AGENTS.md)를 읽는다. 이 문서는 AI 자산을 관리하거나 작업별 skill을 찾을 때만 읽는 짧은 2차 router다.

| 원본 | 역할 |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | 공통 agent source of truth / 1차 bootloader와 작업 라우팅 |
| [README.md](../../README.md) | repo/product overview와 로컬 실행 |
| [architecture.md](../architecture.md), [ADR](../adr/README.md) | 현재 architecture와 승인된 결정; ADR의 제안·채택·대체 상태 확인 |
| [coding-guidelines.md](../coding-guidelines.md) | 코드 작성 기준 |
| [testing.md](../testing.md) | 검증 선택·실행·보고 기준 |
| [core-booking-lifecycle.md](../core-booking-lifecycle.md) | booking 정책과 현재 수명주기 |
| [.agents/skills/](../../.agents/skills/) | 특정 작업에서만 읽는 repo-local 작업 매뉴얼. Claude Code가 읽는 `.claude/skills`는 이 폴더를 가리키는 로컬 링크라 커밋하지 않는다. 클론 뒤 한 번 `scripts\link-agent-skills.cmd`(Windows 정션) 또는 `bash scripts/link-agent-skills.sh`(그 밖)를 실행한다 |
| [.codex/](../../.codex/), [.claude/](../../.claude/) | tool-specific 설정; 공통 규칙은 복사하지 않고 AGENTS와 원본 문서로 연결 |

## 작업별 skill

| 작업 | skill |
| --- | --- |
| 모듈 의존·공개면·코드 배치 | [modulith-boundaries](../../.agents/skills/modulith-boundaries/SKILL.md) |
| HTTP·오류·모듈 공개 계약 변경 | [api-contract-change](../../.agents/skills/api-contract-change/SKILL.md) |
| schema·JPA 매핑·migration 변경 | [database-change](../../.agents/skills/database-change/SKILL.md) |
| 예매 상태·좌석 점유·판매 정책·동시성 변경 | [booking-change](../../.agents/skills/booking-change/SKILL.md) |
| 조회 성능 검토 | [query-performance-review](../../.agents/skills/query-performance-review/SKILL.md) |
| Core 수용량 측정 실행·병목 보고(사용자가 `/core-capacity`로 부를 때만) | [core-capacity](../../.agents/skills/core-capacity/SKILL.md) |
| 세션 작업을 `blog-drafts/` 블로그 초안으로 정리(사용자가 `/blog`로 부를 때만) | [blog](../../.agents/skills/blog/SKILL.md) |

## 규칙 hook

Claude Code는 [.claude/settings.json](../../.claude/settings.json)의 hook으로 팀 규칙을 필요한 순간에만 다시 알려 준다. 정규식은 후보만 좁히고 고칠지는 agent가 판단하며, 강제는 원본 테스트가 맡는다. 실행에 Python이 필요하고 Codex 등 다른 도구에는 적용되지 않는다. 발동 기록은 `.claude/hooks/fired.log`(커밋하지 않음)에 남겨 오탐·미탐 조정에 쓴다.

| hook | 시점 | 원본 규칙 |
| --- | --- | --- |
| [usecase-persistence-check](../../.claude/hooks/usecase-persistence-check.py) | 파일 작성 직후, 끝내기 직전 | `ArchitectureRulesTest`의 usecase·event persistence 구현 참조 금지 |
| [module-boundary-check](../../.claude/hooks/module-boundary-check.py) | 파일 작성 직후 | `ModularityTests`; 허용 목록은 쓰는 쪽 module `package-info.java`의 `allowedDependencies` |

AI 문서는 backend encyclopedia가 되지 않게 작게 유지한다. 읽는 순서는 `AGENTS → 가장 관련 있는 문서 → 가장 작은 관련 source/tests`이며, 실제 repo 문서·코드·테스트를 generic skill보다 우선한다. 불일치는 근거와 함께 보고하고 정책을 임의로 바꾸지 않는다.

[skills-lock.json](../../skills-lock.json)은 모든 repo-local skill의 출처와 skill 본문 파일 바이트의 SHA-256을 기록하는 provenance/integrity manifest다. Codex/Claude가 skill을 자동 실행시키는 설정이나 외부 registry lockfile이 아니다. Skill 추가·수정·삭제 시 목록과 실제 파일에서 계산한 hash를 함께 갱신한다. [.gitattributes](../../.gitattributes)는 skill 파일의 LF를 고정해 체크아웃 줄바꿈 변환에 따른 hash 차이를 막는다.
