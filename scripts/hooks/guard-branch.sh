#!/usr/bin/env bash
# PreToolUse(Bash|PowerShell) 훅. 기본 브랜치에서 이력을 바꾸는 명령을 차단한다.
#
# master push는 곧 운영 배포다(.github/workflows/deploy.yml, 자동 롤백 없음).
# 이 규칙을 문서로 반복해 적는 대신 여기서 강제한다.
#
# stdin: Claude Code가 주는 훅 입력 JSON
# exit 2: 도구 호출을 막고 stderr를 에이전트에게 보여준다
#
# 사람이 직접 실행하는 경로(`!git ...`)는 막지 않는다. 에이전트 경로만 막는다.

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0

# 테스트용으로만 덮어쓴다. 평소에는 기본값을 쓴다.
PROTECTED="${TICKET_PROTECTED_BRANCHES:-master main}"

payload=$(cat)

# JSON 파서 의존성을 두지 않는다. 원문에서 위험 명령의 흔적만 찾는다.
# 오탐이 나더라도 보호 브랜치에 있을 때뿐이고, 차단 사유를 그대로 알려준다.
echo "$payload" | grep -qE 'git[[:space:]]+(commit|push|merge|rebase)' || exit 0

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || exit 0

for p in $PROTECTED; do
  if [ "$branch" = "$p" ]; then
    cat >&2 <<MSG
차단: 현재 브랜치가 '$branch'다. 이 저장소는 기본 브랜치에 직접 커밋하거나 push하지 않는다.
'$branch' push는 곧 운영 배포이고(.github/workflows/deploy.yml) 자동 롤백이 없다.

작업 브랜치를 만들고 다시 시도한다:
  git switch -c <type>/<주제>

이 판단이 틀렸다고 보면 사용자에게 먼저 알리고, 사용자가 직접 실행하게 한다.
MSG
    exit 2
  fi
done

exit 0
