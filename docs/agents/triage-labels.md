# 트리아지 라벨

스킬은 다섯 개의 표준 트리아지 역할로 말한다. 이 문서는 그 역할을 이 저장소 이슈 트래커가
실제로 쓰는 라벨 문자열에 대응시킨다. `/triage`가 읽는다.

| mattpocock/skills의 역할 | 이 저장소의 라벨 | 뜻 |
| --- | --- | --- |
| `needs-triage` | `needs-triage` | 관리자가 평가해야 한다 |
| `needs-info` | `needs-info` | 보고자의 추가 정보를 기다린다 |
| `ready-for-agent` | `ready-for-agent` | 명세가 끝나 AFK 에이전트에게 넘길 수 있다 |
| `ready-for-human` | `ready-for-human` | 사람이 직접 구현해야 한다 |
| `wontfix` | `wontfix` | 처리하지 않는다 |

기본 이름을 그대로 쓴다 — 항등 매핑이다. 스킬이 역할을 말하면(예: "AFK 준비 라벨을 붙여라")
이 표의 오른쪽 문자열을 그대로 쓴다. 쓰는 어휘가 바뀌면 오른쪽 열만 고친다.

## 라벨이 실재하는지

`gh issue edit --add-label`은 없는 라벨에 실패한다. 확인은 이렇게 한다.

```bash
gh label list -R ticket-project/ticket-core
```

없으면 만든다.

```bash
gh label create needs-triage -R ticket-project/ticket-core \
  --description "관리자가 평가해야 한다" --color d876e3
```

`.github/ISSUE_TEMPLATE/bug_report.yml`은 새 버그 이슈에 `needs-triage`를 자동으로 붙인다.
라벨이 없으면 GitHub이 조용히 버리므로, 트리아지 큐가 비어 보이면 이것부터 의심한다.
