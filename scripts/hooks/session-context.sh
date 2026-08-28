#!/usr/bin/env bash
# SessionStart 훅. 세션 시작 시 저장소의 실제 상태를 컨텍스트에 넣는다.
#
# "기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다", "다른 세션 흔적을 먼저 확인한다"를
# 문서에 적어 두는 대신 실제 값을 보여준다. 지시보다 사실이 싸고 정확하다.

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0

git rev-parse --git-dir >/dev/null 2>&1 || exit 0

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
dirty=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')

echo "저장소 상태 (SessionStart 훅)"
echo "- 브랜치: ${branch:-알 수 없음}"

if [ "${dirty:-0}" -gt 0 ]; then
  echo "- 미커밋 변경 ${dirty}건. 사용자 작업으로 보고 되돌리지 않는다:"
  git status --porcelain 2>/dev/null | head -20 | sed 's/^/    /'
  [ "$dirty" -gt 20 ] && echo "    ... 외 $((dirty - 20))건"
else
  echo "- 미커밋 변경 없음"
fi

wt=$(git worktree list 2>/dev/null | wc -l | tr -d ' ')
if [ "${wt:-1}" -gt 1 ]; then
  echo "- 워크트리 ${wt}개. 다른 세션이 붙어 있을 수 있다 (git worktree list)"
fi

exit 0
