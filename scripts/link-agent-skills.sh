#!/usr/bin/env bash
# 스킬 본문은 .agents/skills/에 있다. .claude/skills는 거기를 가리키는 로컬 링크이며
# git에 커밋되지 않는다(Windows에서 symlink 복원이 기본적으로 안 되기 때문).
#
# 클론 직후, 또는 스킬 업데이트 CLI가 .claude/skills를 실체 디렉터리로 되돌려 놓았을 때
# 한 번 실행한다.
#
#   bash scripts/link-agent-skills.sh

set -uo pipefail
cd "$(dirname "$0")/.."

if [ -e .claude/skills ] && [ ! -L .claude/skills ]; then
  echo "경고: .claude/skills가 실체 디렉터리다. 내용을 확인하고 지운 뒤 다시 실행한다." >&2
  echo "  (스킬 업데이트 CLI가 링크를 덮어썼을 수 있다. .agents/skills와 diff로 비교한다)" >&2
  exit 1
fi

rm -f .claude/skills
ln -s ../.agents/skills .claude/skills

echo "완료: .claude/skills -> .agents/skills"
