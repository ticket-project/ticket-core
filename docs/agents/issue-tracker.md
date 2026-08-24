# 이슈 트래커: GitHub

이 저장소의 이슈와 스펙은 GitHub 이슈로 관리한다. 모든 조작은 `gh` CLI로 한다.

원격은 `ticket-project/ticket-core`다. 로컬 디렉터리 이름(`ticket`)과 저장소 이름(`ticket-core`)이
다르므로 주의한다. 클론 안에서 실행하면 `gh`가 `git remote -v`로 저장소를 스스로 알아내지만,
클론 밖이나 형제 저장소에서 실행할 때는 `-R ticket-project/ticket-core`를 명시한다.

형제 저장소(`ticket-fe`, `ticket-queue`, `gatling-test`)의 이슈는 각 저장소 Issues에 따로 있다.
교차 참조할 때는 `-R ticket-project/<저장소>`로 대상을 명시한다.

## 규약

- **이슈 생성**: `gh issue create --title "..." --body "..."`. 본문이 여러 줄이면 heredoc을 쓴다.
- **이슈 읽기**: `gh issue view <번호> --comments`. 라벨까지 함께 받아야 하면 `--json`으로 조회하고
  `jq`로 코멘트를 걸러낸다.
- **이슈 목록**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`.
  필요에 따라 `--label`, `--state` 필터를 붙인다.
- **코멘트**: `gh issue comment <번호> --body "..."`
- **라벨 부여/제거**: `gh issue edit <번호> --add-label "..."` / `--remove-label "..."`
- **닫기**: `gh issue close <번호> --comment "..."`

이슈 제목과 본문은 저장소 규칙에 따라 한국어로 쓴다.

## PR을 트리아지 대상으로 볼지

**PR을 요청 유입 경로로 취급: 아니오.** _(외부 PR을 기능 요청으로 다루는 저장소라면 `예`로 바꾼다.
`/triage`가 이 플래그를 읽는다.)_

`예`로 바꾸면 PR도 이슈와 같은 라벨과 상태를 거치며, `gh pr` 대응 명령을 쓴다.

- **PR 읽기**: `gh pr view <번호> --comments`, 변경은 `gh pr diff <번호>`.
- **트리아지 대상 외부 PR 목록**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`
  후 `authorAssociation`이 `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, `NONE`인 것만 남긴다
  (`OWNER`/`MEMBER`/`COLLABORATOR`는 버린다).
- **코멘트/라벨/닫기**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub은 이슈와 PR이 번호 공간을 공유하므로 `#42`만 보면 어느 쪽인지 알 수 없다.
`gh pr view 42`를 먼저 시도하고 실패하면 `gh issue view 42`로 넘어간다.

## 스킬이 "이슈 트래커에 게시"라고 할 때

GitHub 이슈를 만든다.

## 스킬이 "해당 티켓을 가져와라"라고 할 때

`gh issue view <번호> --comments`를 실행한다.

## Wayfinding 조작

`/wayfinder`가 쓴다. **맵**은 이슈 하나이고, **자식** 이슈가 티켓이 된다.

- **맵**: `wayfinder:map` 라벨이 붙은 이슈 하나. 본문에 Notes / Decisions-so-far / Fog를 둔다.
  `gh issue create --label wayfinder:map`.
- **자식 티켓**: 맵에 GitHub sub-issue로 연결된 이슈(sub-issues 엔드포인트에 `gh api`).
  sub-issues를 쓸 수 없으면 맵 본문 task list에 자식을 추가하고, 자식 본문 맨 위에
  `Part of #<맵번호>`를 적는다. 라벨은 `wayfinder:<타입>`(`research`/`prototype`/`grilling`/`task`).
  티켓을 claim하면 진행하는 사람에게 assign한다.
- **블로킹**: GitHub **네이티브 issue dependencies**를 정식 표현으로 쓴다. 간선 추가는
  `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`이고,
  `<blocker-db-id>`는 블로커의 숫자 **database id**다(`gh api repos/<owner>/<repo>/issues/<n> --jq .id`.
  `#number`나 `node_id`가 아니다). GitHub은 열린 블로커만 `issue_dependencies_summary.blocked_by`로
  보고하며 이것이 실제 게이트다. dependencies를 쓸 수 없으면 자식 본문 맨 위에
  `Blocked by: #<n>, #<n>` 줄로 대체한다. 블로커가 모두 닫히면 티켓이 풀린다.
- **Frontier 조회**: 맵의 열린 자식을 나열하고(`gh issue list --state open`, 맵의 sub-issue나 task list
  범위로 제한), 열린 블로커가 있거나(`issue_dependencies_summary.blocked_by > 0`, 또는 `Blocked by`
  줄에 열린 이슈) assignee가 있는 것을 뺀다. 남은 것 중 맵 순서상 첫 번째를 고른다.
- **Claim**: `gh issue edit <n> --add-assignee @me`. 세션의 첫 쓰기 작업이다.
- **해소**: `gh issue comment <n> --body "<답>"` → `gh issue close <n>` → 맵의 Decisions-so-far에
  맥락 포인터(gist + 링크)를 덧붙인다.
