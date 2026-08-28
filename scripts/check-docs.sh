#!/usr/bin/env bash
# 에이전트 문서 구조를 검증한다. CI와 로컬에서 같은 명령으로 돌린다.
#
#   bash scripts/check-docs.sh             전체 (CI)
#   bash scripts/check-docs.sh --changed   미커밋 .md만, 저장소 전체 검사는 건너뜀 (Stop 훅)
#
# 검사 항목
#   1. AGENTS.md 줄 수 상한 (진입점이 다시 불어나는 것을 막는다)
#   2. 문서가 가리키는 다른 문서(.md)가 실재하는지 (없는 파일을 읽으라는 지시를 막는다)
#   3. docs/archive 각 파일에 "현행 아님" 배너가 있는지
#   4. 스킬 SKILL.md 프론트매터에 name과 description이 있는지
#   5. UTF-8 BOM이 섞이지 않았는지
#   6. 형제 저장소와 커밋 type 표가 어긋나지 않았는지 (나란히 있을 때만)
#   7. 문서의 [관측 날짜] 태그가 observed-failures.md의 항목과 짝이 맞는지
#   8. 오래 손대지 않은 문서 보고 (실패시키지 않음)
#
# 성능 주의: Windows(Git Bash)에서는 프로세스 생성이 압도적으로 비싸다. 문서 69개 기준으로
# 파일마다 grep/head/od/git log를 부르면 90초가 넘는다. Stop 훅이 매 턴 이 스크립트를 돌리므로
# 파일별 루프 대신 목록을 한 번에 넘기는 방식을 쓴다.

set -uo pipefail
cd "$(dirname "$0")/.."

AGENTS_MAX=200
# 조건부·미래 참조라 없어도 정상인 경로
ALLOW_MISSING="CONTEXT-MAP.md"
OBS="docs/agents/observed-failures.md"

fail=0
err() { printf 'FAIL  %s\n' "$*"; fail=1; }
ok()  { printf 'ok    %s\n' "$*"; }

# --changed: 미커밋 .md만 본다. 저장소 전체·git 이력을 훑는 검사 3·6·8은 건너뛴다.
# Stop 훅이 매 턴 부르므로 빠른 경로가 필요하다. CI는 인자 없이 전체를 돌린다.
SCOPE="all"
[ "${1:-}" = "--changed" ] && SCOPE="changed"

# 검사 대상 문서 목록. docs/archive는 완료된 기록이라 제외한다.
if [ "$SCOPE" = "changed" ]; then
  DOCS=$(git status --porcelain -- '*.md' | sed 's/^...//' | grep -v '^docs/archive/' | sort -u)
  if [ -z "$DOCS" ]; then
    echo "ok    바뀐 문서 없음"
    exit 0
  fi
else
  DOCS=$( { git ls-files '*.md'; find .claude/skills -name '*.md' 2>/dev/null; } \
          | grep -v '^docs/archive/' | sort -u )
fi

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
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  f=${hit%%:*}
  p=${hit#*:}
  p=${p#\`}; p=${p%\`}
  p=${p#](}; p=${p%)}
  p=${p%%#*}
  [ -n "$p" ] || continue
  case " $ALLOW_MISSING " in *" $p "*) continue ;; esac
  # dirname을 부르지 않는다. 히트 102개 기준으로 프로세스 생성만 22초가 든다.
  if [ "${f#*/}" = "$f" ]; then d="."; else d="${f%/*}"; fi
  if [ ! -e "$d/$p" ] && [ ! -e "$p" ]; then
    err "$f 가 없는 경로를 가리킨다 -> $p"
    broken=1
  fi
done < <(printf '%s\n' "$DOCS" \
         | xargs grep -oHE '`[A-Za-z0-9_./-]+\.md`|\]\([A-Za-z0-9_./#-]+\.md[^)]*\)' 2>/dev/null \
         | sort -u)
[ "$broken" -eq 0 ] && ok "문서 포인터 전부 실재"

# 3 ─ 아카이브 배너 (저장소 전체 검사)
if [ "$SCOPE" = "all" ] && [ -d docs/archive ]; then
  archive_files=$(git ls-files 'docs/archive/*.md' | grep -v '/README.md$')
  if [ -n "$archive_files" ]; then
    nobanner=$(printf '%s\n' "$archive_files" \
               | xargs awk 'FNR==1 && $0 !~ /완료·폐기된 기록/ { print FILENAME }' 2>/dev/null)
    if [ -n "$nobanner" ]; then
      while IFS= read -r f; do err "$f 에 현행 아님 배너가 없다"; done <<< "$nobanner"
    else
      ok "docs/archive 배너 전부 존재"
    fi
  fi
fi

# 4 ─ 스킬 프론트매터
if [ -d .claude/skills ]; then
  skills=$(find .claude/skills -name 'SKILL.md')
  skillcount=$(printf '%s\n' "$skills" | grep -c . )
  # 파일마다 head/sed/grep을 부르는 대신 awk 한 번으로 프론트매터를 본다.
  fmbad=$(printf '%s\n' "$skills" | xargs awk '
      FNR==1 { infm=0; hasname=0; hasdesc=0; opened=($0=="---") }
      FNR==1 && !opened { print FILENAME "\t프론트매터가 없다"; nextfile }
      FNR>1 && $0=="---" && !infm { infm=1 }
      FNR>1 && !infm && /^name:/ { hasname=1 }
      FNR>1 && !infm && /^description:/ { hasdesc=1 }
      ENDFILE {
        if (opened && !hasname) print FILENAME "\t프론트매터에 name이 없다"
        if (opened && !hasdesc) print FILENAME "\t프론트매터에 description이 없다"
      }
    ' 2>/dev/null)
  if [ -n "$fmbad" ]; then
    while IFS=$'\t' read -r f msg; do err "$f $msg"; done <<< "$fmbad"
  else
    ok "스킬 프론트매터 검사 완료 (${skillcount}개)"
  fi
fi

# 5 ─ BOM
# awk 문자열의 8진 이스케이프로 EF BB BF를 비교한다. gawk/mawk 모두에서 동작한다.
bomlist=$(printf '%s\n' "$DOCS" \
          | xargs awk 'FNR==1 && substr($0,1,3)=="\357\273\277" { print FILENAME }' 2>/dev/null)
if [ -n "$bomlist" ]; then
  while IFS= read -r f; do err "$f 에 UTF-8 BOM이 있다"; done <<< "$bomlist"
else
  ok "BOM 없음"
fi

# 6 ─ 형제 저장소와 공통 컨벤션 동기화 (워크스페이스에서 나란히 볼 때만)
SIBLINGS="../ticket ../ticket-queue ../gatling-test"
CONV=".claude/skills/commit-pr/references/conventions.md"
if [ "$SCOPE" = "all" ] && [ -f "$CONV" ]; then
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

# 7 ─ 관측 태그와 실패 기록 대조
# 근거 없는 규칙이 다시 쌓이는 것을 막는다. 문서에 규칙을 남기려면 실제 관측이 있어야 한다.
if [ ! -f "$OBS" ]; then
  err "$OBS 가 없다. 관측된 실패를 적는 곳이 있어야 규칙을 지울 수 있다"
else
  tagfail=0
  while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    f=${hit%%:*}
    [ "$f" = "$OBS" ] && continue
    d=${hit##*관측 }
    d=${d%\]}
    grep -qE "^## $d" "$OBS" || {
      err "$f 의 [관측 $d] 태그에 대응하는 항목이 $OBS 에 없다"
      tagfail=1
    }
  done < <(printf '%s\n' "$DOCS" \
           | xargs grep -oHE '\[관측 [0-9]{4}-[0-9]{2}-[0-9]{2}\]' 2>/dev/null | sort -u)
  [ "$tagfail" -eq 0 ] && ok "관측 태그와 $OBS 항목이 일치"
fi

if [ "$SCOPE" = "changed" ]; then
  if [ "$fail" -ne 0 ]; then echo "문서 검사 실패"; exit 1; fi
  echo "문서 검사 통과 (바뀜 문서만)"
  exit 0
fi

echo
# 8 ─ 신선도 보고 (실패시키지 않는다)
# 손으로 적는 "기준일" 프론트매터는 반드시 실제와 어긋난다. git 이력을 신선도 신호로 쓴다.
# git log는 최신순이므로 파일을 처음 만난 시점이 그 파일의 최신 커밋이다.
STALE_DAYS=${STALE_DAYS:-120}
now=$(date +%s)

# git log는 삭제된 파일의 과거 경로도 준다. 현재 추적 중인 문서만 남기려고 DOCS로 거른다.
stale_list=$(
  { printf '%s\n' "$DOCS" | sed 's/^/KEEP /'
    git log --format='C %ct' --name-only -- '*.md' 2>/dev/null; } \
  | awk -v now="$now" -v limit="$STALE_DAYS" '
      /^KEEP / { keep[substr($0, 6)] = 1; next }
      /^C /    { ts = $2; next }
      NF == 0  { next }
      !($0 in keep) { next }
      $0 ~ /ISSUE_TEMPLATE/ { next }
      seen[$0]++ { next }
      {
        days = int((now - ts) / 86400)
        if (days > limit) printf "  %d일  %s\n", days, $0
      }
    ' \
  | sort -rn
)

if [ -n "$stale_list" ]; then
  printf 'note  %s일 넘게 손대지 않은 문서 (실패 아님, 사실 확인 대상)\n' "$STALE_DAYS"
  printf '%s\n' "$stale_list"
else
  ok "모든 문서가 ${STALE_DAYS}일 안에 갱신됨"
fi

if [ "$fail" -ne 0 ]; then
  echo "문서 검사 실패"
  exit 1
fi
echo "문서 검사 통과"
