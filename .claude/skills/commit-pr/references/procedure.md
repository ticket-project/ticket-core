# 커밋과 PR 절차 (상세)

기본 흐름은 **작업 브랜치 → 작업 단위 커밋들 → PR → squash 또는 rebase 반영**이다.
기준 브랜치는 `master`(= `origin/HEAD`)다. 이 저장소는 선형 이력을 유지하므로 merge commit으로
반영하지 않는다. 브랜치 최신화도 merge가 아니라 rebase로 한다.

## master push는 곧 배포다

`.github/workflows/deploy.yml`이 `master` push에 붙어 있다. 반영하면 다음이 자동으로 일어난다.

1. `ci.yml`이 `./gradlew test :core:core-infra:integrationTest :bootstrap:integrationTest :bootstrap:bootJar`를 실행하고 jar를 올린다.
2. 검증된 jar로 Docker 이미지를 빌드해 `ticket-be:<commit SHA>`로 push한다.
3. 운영 서버에 SSH로 들어가 `docker compose up -d`와 `docker restart ticket-nginx`를 실행한다.

자동 롤백은 없다. PR을 반영할 때 운영에 나간다는 사실을 먼저 알린다. DB 구조 변경이 포함되면
Flyway가 애플리케이션 기동 중 실행된다는 점을 함께 확인한다
([operations.md](../../../../docs/operations.md#db-마이그레이션)).

## 순서

1. **준비물 확인** — `gh auth status`(PR 단계 전), `git --version` 2.38 이상(충돌 검증에 필요).
2. **현황 파악** — `git status`와 `git diff`로 워킹트리 전체를 본다. untracked 파일도 내용을
   확인한다. 기존 미커밋 변경은 사용자의 작업으로 보고 되돌리지 않는다.
3. **범위별 검증** — 커밋이 건드리는 범위의 검증을 통과해야 커밋한다
   ([testing.md](../../../../docs/testing.md#무엇을-돌릴지)). 실패하면 커밋하지 않고 실패 내용을
   그대로 보고한다. 테스트 스킵과 `--no-verify`는 쓰지 않는다.
4. **작업 단위로 쪼개기** — 한 커밋은 하나의 의도이며 독립적으로 revert할 수 있는 덩어리다.
   성격이 다른 변경을 같은 커밋에 넣지 않는다. 하나의 PR에 여러 작업 단위 커밋이 담길 수 있고,
   그때는 PR 본문에 단위별로 정리한다.
5. **명시적 스테이징** — 파일 경로를 지정해 `git add`한다. `git add -A`와 `git add .`는 쓰지 않는다.
   `build/`, `.gradle/`, `docker-compose.yml`, 로컬 설정 파일은 스테이징하지 않는다.
6. **작업 브랜치** — `master`에 있으면 먼저 브랜치를 딴다.

   ```bash
   git fetch origin
   git checkout -b <prefix>/<주제> origin/HEAD
   ```

   `<prefix>`는 변경 성격(`feat`, `fix`, `refactor`, `chore`, `docs`, `ci`)이나 작업 주체
   (`claude`, `codex`, `agent`)를 쓰고 주제는 영문 소문자와 하이픈으로 짧게 적는다. 기준 브랜치보다
   뒤처져 있으면 `git rebase origin/HEAD`로 정렬한다. `amend`는 요청받았을 때만 하고,
   force push는 자기 작업 브랜치에 한해 `--force-with-lease`로 한다.
7. **충돌 검증** — 커밋할 때마다 확인한다.

   ```bash
   git fetch origin
   git status -sb
   git merge-tree --write-tree origin/HEAD HEAD
   ```

   exit 0이면 통과다. exit 1이면 임의로 해결하지 않고 멈춘 뒤, 충돌 파일마다 ① 내 브랜치가 바꾼
   내용 ② `master`가 바꾼 내용을 각각 한 줄 한국어로 정리해 "내 변경 유지 / master 쪽 유지 /
   둘을 합침" 중에서 선택을 받는다. merge-tree 원문 덤프를 그대로 붙이지 않는다.
8. **PR 생성과 반영** — 제목은 [conventions.md](conventions.md)의 형식을 쓰고, 본문은
   `.github/pull_request_template.md`의 항목을 채운다. 반영은 `gh pr merge --squash` 또는
   `--rebase`를 사용하고 `--merge`는 쓰지 않는다.
9. **뒷정리와 보고** — 커밋 해시, 변경 통계, 검증 결과, 충돌 검증 결과, PR URL과 반영 여부를
   보고하고, 남은 unstaged 또는 untracked 파일이 있으면 반드시 알린다. 이번 작업과 무관한 미커밋
   변경이 있으면 정렬을 강행하지 않고 미룬 사실만 보고한다. stash나 reset으로 다른 작업을
   건드리지 않는다.

## 하지 않을 것

- 명시적 요청 없이 커밋 절차를 시작하기
- `master`에 직접 커밋하거나 push하기
- merge commit으로 PR 반영하기, 브랜치 최신화를 merge로 하기
- 검증 미실행이나 실패 상태로 커밋하기
- 세션 작업 전체를 거대한 커밋 하나로 뭉치기
- 충돌 검증 실패 상태에서 반영 강행하기
- `git add -A`로 뭉텅이 스테이징하기, 확인하지 않은 파일 커밋하기
- 영어 커밋 메시지, "Update files" 류의 무의미한 제목
- `--no-verify`, 훅과 서명 우회

## 멀티라인 메시지

bash heredoc으로 작성한다. PowerShell에서 heredoc 문법을 흉내 내지 않는다.

```bash
git commit -m "$(cat <<'MSG'
perf(order): 주문 생성 트랜잭션 범위 축소

Redis 좌석 선점 중 DB 커넥션을 점유하지 않도록
주문과 선점 이력 저장 구간만 트랜잭션으로 분리한다.

Co-Authored-By: <작업한 에이전트 표기>
MSG
)"
```
