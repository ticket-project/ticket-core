> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-20. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Show Image Thumbnail Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공연 목록 계열 응답이 원본 대신 목록용 썸네일 경로를 내려주도록 바꾼다.

**Architecture:** 정적 리소스에 목록용 썸네일 파일을 추가하고, 백엔드 응답 조립 단계에서 `show` 원본 경로를 썸네일 경로로 치환한다. 상세 응답은 기존 원본을 유지한다.

**Tech Stack:** Spring Boot, Querydsl, static resources

---

### Task 1: 썸네일 파일 준비

**Files:**
- Create: `core/core-api/src/main/resources/static/api/images/shows/card/*.jpg`

- [ ] 원본 `shows` 이미지에서 목록용 썸네일을 생성한다.

### Task 2: 목록 응답 경로 변환

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/show/image/ShowImagePathResolver.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/query/ShowListQueryRepository.java`

- [ ] 목록 응답용 이미지 경로 변환 클래스를 추가한다.
- [ ] 메인 목록, 최신, 검색, 오픈예정 응답의 `image`가 썸네일 경로를 사용하게 바꾼다.

### Task 3: 검증

**Files:**
- Verify only

- [ ] `:core:core-api:compileJava`로 컴파일 검증한다.
