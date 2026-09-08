---
name: spec-review
description: >
  변경분을 Standards(저장소 표준과 코드 스멜)와 Spec(원래 이슈대로 구현했는가) 두 축으로
  나눠 병렬 리뷰하고 나란히 보고한다. 고정점(커밋·브랜치·태그·merge-base)을 받아 diff를
  잡는다. 브랜치나 PR 리뷰, "X 이후 바뀐 것 리뷰해줘" 요청에 쓴다.
  Claude Code 내장 /code-review와는 다른 스킬이다.
---

# Spec Review

**이 스킬은 이름표일 뿐이다.** 절차 원본은 `.agents/skills/code-review/SKILL.md`에 있다.
그 파일을 읽고 거기 적힌 절차를 그대로 따른다. 여기에 절차를 복사하지 않는다 —
복사하는 순간 상류 업데이트와 어긋난다.

## 왜 별칭이 필요한가

원본 스킬의 이름은 `code-review`인데 Claude Code 내장 `code-review`와 겹쳐 가려진다.
원본을 개명하면 상류(`mattpocock/skills`)와 `skills-lock.json`의 키가 어긋나고, 다음
업데이트가 `code-review`를 다시 받아오면 두 벌이 된다. 그래서 원본은 건드리지 않고
도달 가능한 이름을 하나 더 둔다.

Codex에는 가리는 내장 스킬이 없다. Codex는 `code-review`를 직접 쓰면 되므로 이 별칭은
`allow_implicit_invocation: false`로 두어 중복 선택되지 않게 한다.

## 이 저장소에서 특히 쓸모 있는 이유

Spec 축이 커밋 메시지의 이슈 번호를 `docs/agents/issue-tracker.md`의 절차로 가져와 원
이슈와 대조한다. 이 저장소의 에이전트 설정 중 경로로 직접 읽히는 유일한 곳이다.
