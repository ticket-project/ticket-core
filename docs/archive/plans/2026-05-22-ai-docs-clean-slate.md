> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-05-23. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# AI Docs Clean Slate Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 루트 README 계열 중복 문서를 삭제하고, 현재 기준 문서를 `docs/`로 재편한다.

**Architecture:** 루트에는 `README.md`와 `AGENTS.md`만 남긴다. 기존 루트 보조 README 문서의 현재성 있는 내용은 `docs/development.md`, `docs/architecture.md`, `docs/operations.md`, `docs/load-test.md`에 흡수하고, 과거 초안과 대체된 정리 문서는 삭제한다.

**Tech Stack:** Markdown, PowerShell, ripgrep, Git

---

## File Map

- Create: `docs/development.md`
- Create: `docs/architecture.md`
- Create: `docs/operations.md`
- Create: `docs/load-test.md`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `.codex/AGENTS.md`
- Modify: `.github/copilot-instructions.md`
- Modify: `.github/workflows/claude-code-review.yml`
- Modify: `.github/ISSUE_TEMPLATE/config.yml`
- Modify: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Modify: `.github/ISSUE_TEMPLATE/refactor.yml`
- Modify: `.codex/agents/architect.toml`
- Modify: `.codex/agents/doc-updater.toml`
- Delete: 기존 루트 보조 README 문서들
- Delete: 대체된 보존형 README 정리 설계/계획 문서

## Chunk 1: Canonical Docs

### Task 1: Create new docs

- [x] **Step 1:** Create `docs/development.md` from the current implementation and product overview.
- [x] **Step 2:** Create `docs/architecture.md` from the current module structure.
- [x] **Step 3:** Create `docs/operations.md` from local run, verification, profile, deployment, and Datadog notes.
- [x] **Step 4:** Create `docs/load-test.md` as the representative entry for existing load-test documentation.

### Task 2: Rewrite root entry points

- [x] **Step 1:** Rewrite `README.md` as a short project entry and document map.
- [x] **Step 2:** Rewrite `AGENTS.md` to point to the new document priority and keep AI work rules concise.

## Chunk 2: Tool Adapters and Reference Updates

### Task 3: Update AI tool adapters

- [x] **Step 1:** Update `.codex/AGENTS.md` to reference `AGENTS.md`, `docs/development.md`, and `docs/architecture.md`.
- [x] **Step 2:** Update `.github/copilot-instructions.md` to act as a thin Copilot adapter.
- [x] **Step 3:** Update `.codex/agents/architect.toml` and `.codex/agents/doc-updater.toml`.

### Task 4: Update GitHub templates and workflows

- [x] **Step 1:** Update `.github/workflows/claude-code-review.yml`.
- [x] **Step 2:** Update `.github/ISSUE_TEMPLATE/config.yml`.
- [x] **Step 3:** Update `.github/ISSUE_TEMPLATE/feature_request.yml` and `.github/ISSUE_TEMPLATE/refactor.yml`.

## Chunk 3: Delete Replaced Docs and Verify

### Task 5: Delete obsolete docs

- [x] **Step 1:** Delete the old root supplemental README files.
- [x] **Step 2:** Delete the superseded README cleanup spec and plan.

### Task 6: Static verification

- [x] **Step 1:** Run reference search for obsolete names.
- [x] **Step 2:** Verify UTF-8 BOM absence on changed Markdown files.
- [x] **Step 3:** Verify only intended documentation and AI adapter files are staged.
- [x] **Step 4:** Commit the documentation cleanup.
