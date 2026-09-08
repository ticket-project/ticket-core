---
name: commit-pr
description: >
  ticket 저장소의 커밋과 PR을 만든다. Conventional Commits 기반
  `<type>(<scope>): <한국어 설명>` 형식, 작업 브랜치 → 커밋 → PR → squash/rebase 절차,
  충돌 검증을 적용한다. 커밋·PR 생성·브랜치 정리·커밋 메시지 작성 요청을 받았을 때 쓴다.
allowed-tools: Bash(git status:*) Bash(git diff:*) Bash(git log:*) Bash(git fetch:*) Bash(git merge-tree:*) Bash(git branch:*) Bash(gh auth status) Bash(gh pr view:*)
---

# 커밋과 PR

## 시작하기 전

- **커밋은 사용자가 명시적으로 요청할 때만 시작한다.** "진행해 / 좋아"는 작업을 계속하라는
  뜻이지 커밋 트리거가 아니다.
- **`master`에 직접 커밋하거나 push하지 않는다.** `master` push는 곧 운영 배포다
  (자동 롤백 없음, 상세는 `references/procedure.md`).
- 기존 미커밋 변경은 사용자의 작업이다. 되돌리거나 같이 커밋하지 않는다.

## 절차

1. **현황** — `git status`, `git diff`로 워킹트리 전체를 본다. untracked도 내용을 확인한다.
2. **검증** — 커밋이 건드리는 범위의 검증을 통과해야 커밋한다 → `/verify`.
   실패하면 커밋하지 않고 실패 내용을 그대로 보고한다. 스킵과 `--no-verify`는 없다.
3. **브랜치** — `master`에 있으면 먼저 딴다.
   `git fetch origin && git checkout -b <prefix>/<주제> origin/HEAD`
4. **쪼개기** — 한 커밋은 하나의 의도이며 독립적으로 revert할 수 있어야 한다.
5. **스테이징** — 파일 경로를 지정해 `git add`한다. `git add -A`와 `git add .`는 쓰지 않는다.
6. **충돌 검증** — `git fetch origin && git merge-tree --write-tree origin/HEAD HEAD`.
   exit 1이면 임의로 해결하지 않고 멈춘다.
7. **PR** — 제목은 커밋과 같은 형식, 본문은 `.github/pull_request_template.md`.
   반영은 `gh pr merge --squash` 또는 `--rebase`. `--merge`는 쓰지 않는다.
8. **보고** — 커밋 해시, 변경 통계, 검증 결과, 충돌 검증 결과, PR URL, 남은 미커밋 파일.

## 메시지 형식

```text
<type>(<scope>): <한국어 설명>
```

`type`은 **변경 파일의 종류가 아니라 변경 목적**으로 고른다. `scope`는 선택이며 가장 작은
적절한 범위를 쓴다. 한국어, 마침표 없음, 하나의 커밋에 하나의 목적.

## 상세를 열어야 할 때

| 상황 | 열 파일 |
| --- | --- |
| type 선택이 갈릴 때, scope 후보를 고를 때, BREAKING CHANGE, PR 본문 규칙 | `references/conventions.md` |
| 9단계 전체 절차, 충돌 처리 방법, 하지 않을 것 목록, 멀티라인 heredoc | `references/procedure.md` |

멀티라인 메시지는 bash heredoc으로 쓴다. PowerShell에서 heredoc 문법을 흉내 내지 않는다.
