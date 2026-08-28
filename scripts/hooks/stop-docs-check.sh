#!/usr/bin/env bash
# Stop 훅. 미커밋 .md 변경이 있으면 문서 검사를 돌려 실패만 보고한다.
#
# scripts/check-docs.sh는 CI 잡이다. 여기서 한 번 더 돌려 PR까지 가기 전에 잡는다.
#
# stdin: 훅 입력 JSON. stop_hook_active가 true면 이미 이 훅 때문에 한 번 더 돈 것이므로
#        다시 막지 않는다(무한 루프 방지).
# exit 2: 종료를 막고 stderr를 에이전트에게 보여준다

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0

payload=$(cat)
echo "$payload" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && exit 0

git rev-parse --git-dir >/dev/null 2>&1 || exit 0
git status --porcelain 2>/dev/null | grep -qE '\.md$' || exit 0

out=$(bash scripts/check-docs.sh --changed 2>&1)
if [ $? -ne 0 ]; then
  {
    echo "문서 검사 실패. 커밋 전에 고친다."
    echo "$out" | grep '^FAIL'
    echo "전체 검사: bash scripts/check-docs.sh"
  } >&2
  exit 2
fi

exit 0
