#!/usr/bin/env bash
# 에이전트 문서 구조를 검증한다. CI와 로컬에서 같은 명령으로 돌린다.
#
#   bash scripts/check-docs.sh
#
# 검사 항목
#   1. AGENTS.md 줄 수 상한 (진입점이 다시 불어나는 것을 막는다)
#   2. 문서가 가리키는 다른 문서(.md)가 실재하는지 (없는 파일을 읽으라는 지시를 막는다)
#   3. docs/archive 각 파일에 "현행 아님" 배너가 있는지
#   4. 스킬 SKILL.md 프론트매터에 name과 description이 있는지
#   5. UTF-8 BOM이 섞이지 않았는지
#   6. 형제 저장소와 커밋 type 표가 어긋나지 않았는지 (나란히 있을 때만)

set -uo pipefail
cd "$(dirname "$0")/.."

AGENTS_MAX=200
# 조건부·미래 참조라 없어도 정상인 경로
ALLOW_MISSING="CONTEXT-MAP.md"

fail=0
err() { printf 'FAIL  %s\n' "$*"; fail=1; }
ok()  { printf 'ok    %s\n' "$*"; }

# 1 ─ AGENTS.md 줄 수
for f in $(git ls-files '*AGENTS.md'); do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$AGENTS_MAX" ]; then
    err "$f 가 ${n}줄로 상한 ${AGENTS_MAX}줄을 넘었다. 절차는 .claude/skills/ 로 옮긴다"
  else
    ok "$f ${n}줄 (<= ${AGENTS_MAX})"
  fi
done

# 2 ─ 문서 포인터
broken=0
for f in $(git ls-files '*.md') $(find .claude/skills -name '*.md' 2>/dev/null); do
  [ -f "$f" ] || continue
  case "$f" in docs/archive/*) continue ;; esac
  d=$(dirname "$f")
  grep -oE '`[A-Za-z0-9_./-]+\.md`|\]\([A-Za-z0-9_./#-]+\.md[^)]*\)' "$f" 2>/dev/null \
    | sed 's/^`//; s/`$//; s/^](//; s/)$//; s/#.*$//' | sort -u | while read -r p; do
        [ -n "$p" ] || continue
        case " $ALLOW_MISSING " in *" $p "*) continue ;; esac
        [ -e "$d/$p" ] || [ -e "$p" ] || printf '%s\t%s\n' "$f" "$p"
      done
done > /tmp/_broken_ptr.$$ 2>/dev/null
if [ -s /tmp/_broken_ptr.$$ ]; then
  while IFS=$'\t' read -r f p; do err "$f 가 없는 경로를 가리킨다 -> $p"; done < /tmp/_broken_ptr.$$
else
  ok "문서 포인터 전부 실재"
fi
rm -f /tmp/_broken_ptr.$$

# 3 ─ 아카이브 배너
if [ -d docs/archive ]; then
  missing=0
  for f in $(git ls-files 'docs/archive/*.md'); do
    case "$f" in */README.md) continue ;; esac
    head -1 "$f" | grep -q '완료·폐기된 기록' || { err "$f 에 현행 아님 배너가 없다"; missing=1; }
  done
  [ "$missing" -eq 0 ] && ok "docs/archive 배너 전부 존재"
fi

# 4 ─ 스킬 프론트매터
if [ -d .claude/skills ]; then
  for f in $(find .claude/skills -name 'SKILL.md'); do
    head -1 "$f" | grep -q '^---$' || { err "$f 에 YAML 프론트매터가 없다"; continue; }
    fm=$(sed -n '2,/^---$/p' "$f")
    echo "$fm" | grep -q '^name:' || err "$f 프론트매터에 name이 없다"
    echo "$fm" | grep -q '^description:' || err "$f 프론트매터에 description이 없다"
  done
  ok "스킬 프론트매터 검사 완료 ($(find .claude/skills -name 'SKILL.md' | wc -l)개)"
fi

# 5 ─ BOM
bom=0
for f in $(git ls-files '*.md') $(find .claude/skills -name '*.md' 2>/dev/null); do
  [ -f "$f" ] || continue
  if head -c3 "$f" | od -An -tx1 2>/dev/null | grep -q 'ef bb bf'; then
    err "$f 에 UTF-8 BOM이 있다"; bom=1
  fi
done
[ "$bom" -eq 0 ] && ok "BOM 없음"

# 6 ─ 형제 저장소와 공통 컨벤션 동기화 (워크스페이스에서 나란히 볼 때만)
SIBLINGS="../ticket ../ticket-queue ../gatling-test"
CONV=".claude/skills/commit-pr/references/conventions.md"
if [ -f "$CONV" ]; then
  mine=$(sed -n '/^| type | 사용 기준 |/,/^| `revert`/p' "$CONV" | md5sum | cut -d' ' -f1)
  checked=0; drift=0
  for sib in $SIBLINGS; do
    [ -f "$sib/$CONV" ] || continue
    [ "$(cd "$sib" 2>/dev/null && pwd)" = "$(pwd)" ] && continue
    theirs=$(sed -n '/^| type | 사용 기준 |/,/^| `revert`/p' "$sib/$CONV" | md5sum | cut -d' ' -f1)
    checked=$((checked+1))
    if [ "$mine" != "$theirs" ]; then
      err "$sib 와 type 표가 어긋났다. 세 저장소는 같은 문구를 쓴다"
      drift=1
    fi
  done
  if [ "$checked" -eq 0 ]; then
    ok "형제 저장소 없음 — 동기화 검사 건너뜀"
  elif [ "$drift" -eq 0 ]; then
    ok "형제 저장소 ${checked}곳과 type 표 일치"
  fi
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "문서 검사 실패"
  exit 1
fi
echo "문서 검사 통과"
