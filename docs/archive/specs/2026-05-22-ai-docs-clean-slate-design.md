> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-05-23. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# AI Docs Clean Slate Design

## 목표

현재 프로젝트 문서와 AI 설정을 "현재 쓰는 기준만 남기는 구조"로 재설계한다.

- 루트에는 진입점과 공통 AI 작업 규칙만 둔다.
- 오래된 초안과 중복 README 문서는 필요한 내용만 흡수한 뒤 삭제한다.
- AI 도구별 자동 로딩 파일은 유지 여부를 기능 기준으로 판단한다.
- 사람과 AI 모두 같은 문서 흐름을 보게 한다.

## 핵심 결정

기존 루트 보조 README 파일을 보존해야 한다는 전제를 버린다. Git 히스토리가 있으므로 현재 기준으로 쓰지 않는 초안 문서는 repository 안에 계속 둘 필요가 없다.

최종 목표 구조는 아래와 같다.

```text
README.md
AGENTS.md

docs/
  development.md
  architecture.md
  operations.md
  load-test.md
  decisions/

.github/
  copilot-instructions.md
  instructions/
    java-review.instructions.md
    code-review.instructions.md
  workflows/

.codex/
  config.toml
  AGENTS.md
  agents/
```

## 삭제 또는 흡수 대상

### 기존 개발 기준 README

현재 코드 기준 개발 문서로 유효한 내용이 많다. 파일 자체는 삭제하되, 필요한 내용은 `docs/development.md`로 흡수한다.

흡수 대상:

- 현재 구현 상태
- 인증, 쇼/회차/좌석, selection, hold, order 흐름
- 미구현 범위
- 주요 도메인 모델

삭제 후 참조 갱신 대상:

- `README.md`
- `AGENTS.md`
- `.codex/AGENTS.md`
- `.github/copilot-instructions.md`
- `.github/ISSUE_TEMPLATE/*.yml`
- `.codex/agents/*.toml`

### 기존 아키텍처 README

구조 문서로 유효하다. 파일 자체는 삭제하되, 필요한 내용은 `docs/architecture.md`로 흡수한다.

흡수 대상:

- Gradle 모듈 책임
- 모듈 의존 방향
- 패키지 경계
- 구조 테스트 기준
- 다음 구조 정리 방향

삭제 후 참조 갱신 대상:

- `README.md`
- `AGENTS.md`
- `.codex/AGENTS.md`
- `.github/copilot-instructions.md`
- `.github/ISSUE_TEMPLATE/*.yml`
- `.github/workflows/claude-code-review.yml`
- `.codex/agents/*.toml`

### 기존 제품 README

내용 일부만 유지한다. 별도 제품 문서가 반드시 필요하지 않으면 `README.md`의 프로젝트 소개와 `docs/development.md`의 현재 구현 범위로 흡수한 뒤 삭제한다.

흡수 대상:

- 프로젝트가 만드는 것
- 현재 사용자 흐름
- 구현 완료와 계획 중 범위

### 기존 AI 초안 README

삭제한다.

이 문서는 과거 AI 초안이며 현재 개발 기준이 아니다. 필요한 정보는 이미 다른 현재 문서에 반영되어 있거나, 반영되지 않은 내용은 현재 기준으로 검증되지 않았다. 보관은 Git 히스토리로 충분하다.

### 이전 README 정리 설계/계획 문서

이전 보존형 README 정리 설계/계획 문서는 이번 clean slate 설계로 대체된다.

구현 단계에서 삭제한다.

## 유지 대상

### `README.md`

루트 진입점으로 유지한다. 길이는 짧게 유지한다.

포함할 내용:

- 프로젝트 한 줄 설명
- 빠른 실행 명령
- 문서 지도
- AI 작업 기준 위치

포함하지 않을 내용:

- 상세 요구사항
- 도메인별 상세 구현
- 긴 아키텍처 설명
- 오래된 TODO 목록

### `AGENTS.md`

AI 공통 작업 규칙의 단일 기준으로 유지한다.

포함할 내용:

- 한국어 응답
- UTF-8 BOM 없음
- 작업 범위 최소화
- 모듈 경계
- 검증 명령
- 커밋 규칙
- 문서 우선순위

문서 우선순위는 새 구조에 맞춰 갱신한다.

1. `README.md`
2. `docs/development.md`
3. `docs/architecture.md`
4. `docs/operations.md`
5. 관련 모듈 `build.gradle`, 테스트 코드

### `.codex/config.toml`

Codex 실행 설정이므로 유지한다. 다만 문서 참조가 있으면 새 경로로 갱신한다.

### `.codex/AGENTS.md`

Codex 전용 보조 지침으로 유지한다. 루트 `AGENTS.md`와 중복되는 일반 규칙은 줄이고, Codex 리뷰 기준만 남긴다.

### `.github/copilot-instructions.md`

Copilot 자동 로딩용으로 유지한다. 내용은 짧은 어댑터로 축소한다.

원칙:

- 공통 규칙은 `AGENTS.md`를 따르라고 연결한다.
- 상세 개발 기준은 `docs/development.md`, 구조 기준은 `docs/architecture.md`를 연결한다.
- Copilot 전용 차이만 남긴다.

### `.coderabbit.yaml`, `.pr_agent.toml`

실제 PR 리뷰 자동화에 쓰는 설정이면 유지한다. 문서 경로 또는 지침 중 오래된 README 참조가 있으면 새 문서 경로로 갱신한다.

## 새 문서 책임

### `docs/development.md`

개발자가 현재 코드 기준으로 알아야 할 내용을 담는다.

- 현재 구현 상태
- 주요 API 흐름
- 주요 도메인 모델
- 미구현 범위
- 개발 시 주의점

### `docs/architecture.md`

구조 기준만 담는다.

- Gradle 모듈 책임
- 모듈 의존 방향
- 패키지 경계
- 인프라 구현 위치
- 구조 테스트 기준

### `docs/operations.md`

실행과 검증 기준을 담는다.

- 로컬 실행
- Redis 실행
- 주요 Gradle 명령
- 테스트/빌드 검증
- 환경변수와 프로파일
- 배포 workflow 기준

### `docs/load-test.md`

기존 `docs/load-test/ticket-open-local.md`의 역할을 대표 문서로 연결하거나, 필요하면 내용을 흡수한다.

1차 구현에서는 기존 부하 테스트 문서를 바로 삭제하지 않고, `docs/load-test.md`에서 연결하는 방식이 안전하다.

## 참조 갱신 원칙

파일 삭제 전에 전체 참조를 먼저 검색한다.

대상 키워드:

- 삭제한 루트 보조 README 파일명
- 대체된 보존형 README 정리 문서명

참조가 남아 있으면 삭제하지 않는다. 모든 참조를 새 문서 경로로 갱신한 뒤 삭제한다.

## 검증 전략

문서 재구성이므로 Java 빌드 검증은 필수 대상이 아니다. 대신 아래 정적 검증을 수행한다.

- 삭제한 루트 보조 README 파일명과 대체된 보존형 README 정리 문서명이 남지 않았는지 확인한다.
- `README.md`, `AGENTS.md`, `.codex/AGENTS.md`, `.github/copilot-instructions.md`가 새 문서 경로를 가리키는지 확인한다.
- 삭제된 파일이 `git status`에 의도한 삭제로만 나타나는지 확인한다.
- 새 문서와 수정 문서가 UTF-8 BOM 없이 저장되었는지 확인한다.
- 자동화 설정 파일 `.codex/config.toml`, `.coderabbit.yaml`, `.pr_agent.toml`, `.github/workflows/*.yml`은 삭제하지 않았는지 확인한다.

## 성공 기준

- 루트에는 `README.md`, `AGENTS.md`만 남는다.
- 루트에 `README_*.md` 파일이 남지 않는다.
- 기존 AI 초안 README는 삭제된다.
- 현재 기준 문서는 `docs/development.md`, `docs/architecture.md`, `docs/operations.md`로 재편된다.
- AI 도구별 지침은 공통 규칙을 중복하지 않고 새 문서 구조를 참조한다.
- 오래된 README 파일명 참조가 repository 안에 남지 않는다.

## 리스크와 대응

- GitHub 이슈 템플릿의 외부 링크가 깨질 수 있다.
  - 대응: 기존 개발 기준 README와 기존 아키텍처 README 링크를 각각 `docs/development.md`, `docs/architecture.md`로 갱신한다.
- Codex/Copilot 지침이 새 문서 경로를 못 찾으면 AI 응답 품질이 낮아질 수 있다.
  - 대응: `AGENTS.md`, `.codex/AGENTS.md`, `.github/copilot-instructions.md`를 같은 문서 순서로 맞춘다.
- `.agents/skills`는 범용 스킬이 많지만 즉시 삭제하면 도구 동작 영향이 클 수 있다.
  - 대응: 이번 clean slate의 1차 범위에서는 `.agents/skills` 삭제를 제외하고, 별도 점검 대상으로 남긴다.
