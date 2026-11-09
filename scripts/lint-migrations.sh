#!/usr/bin/env bash
# 바뀐 Flyway migration만 squawk로 검사한다(스케일 아웃 결정 D4-1, 규칙은 .squawk.toml).
# 이미 적용된 migration은 고치지 않으므로 새로 추가·수정한 파일만 본다.
#
# 사용: bash scripts/lint-migrations.sh [기준 커밋]
#   기준 커밋이 없거나 찾을 수 없으면 origin/master와의 merge-base를 쓴다. 에이전트는 커밋 전에 인자 없이 돌린다.
set -euo pipefail

SQUAWK_VERSION="2.68.0"
base="${1:-}"
if [ -z "$base" ] || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  base="$(git merge-base HEAD origin/master 2>/dev/null || true)"
fi
if [ -z "$base" ]; then
  echo "기준 커밋을 찾지 못해 migration 검사를 건너뛴다."
  exit 0
fi

files=()
while IFS= read -r file; do
  files+=("$file")
done < <(
  {
    git diff --name-only --diff-filter=AM "$base" HEAD -- ':(glob)src/main/resources/db/migration-vendor/**/*.sql'
    git diff --name-only --diff-filter=AM HEAD -- ':(glob)src/main/resources/db/migration-vendor/**/*.sql'
    git ls-files --others --exclude-standard -- ':(glob)src/main/resources/db/migration-vendor/**/*.sql'
  } | sort -u
)

if [ "${#files[@]}" -eq 0 ]; then
  echo "바뀐 migration이 없다."
  exit 0
fi

printf '검사: %s\n' "${files[@]}"
npx --yes "squawk-cli@${SQUAWK_VERSION}" "${files[@]}"
