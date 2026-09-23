#!/usr/bin/env bash
# 에이전트 문서 구조를 검증한다. CI와 로컬에서 같은 명령으로 돌린다.
#
#   bash scripts/check-docs.sh             전체 (CI)
#   bash scripts/check-docs.sh --changed   미커밋 .md만 본다 (Stop 훅)
#
# 검사 항목
#   1. AGENTS.md 줄 수 상한 (진입점이 다시 불어나는 것을 막는다)
#   2. 문서가 가리키는 다른 문서(.md)가 실재하는지 (없는 파일을 읽으라는 지시를 막는다)
#   3. 스킬 SKILL.md 프론트매터에 name과 description이 있는지
#   4. UTF-8 BOM이 섞이지 않았는지
#   5. 문서의 [관측 날짜] 태그가 observed-failures.md의 항목과 짝이 맞는지
#   6. 문서가 backtick으로 가리키는 package 경로가 src/main/java에 실재하는지
#
# 성능 주의: Windows(Git Bash)에서는 프로세스 생성이 압도적으로 비싸다. 문서 69개 기준으로
# 파일마다 grep/head/od/git log를 부르면 90초가 넘는다. Stop 훅이 매 턴 이 스크립트를 돌리므로
# 파일별 루프 대신 목록을 한 번에 넘기는 방식을 쓴다.

set -uo pipefail
cd "$(dirname "$0")/.."

AGENTS_MAX=80
# 조건부·미래 참조라 없어도 정상인 경로
ALLOW_MISSING="CONTEXT-MAP.md"
# 일부러 "이렇게 하지 말라"고 적은 package 이름과, DB에 박혀 바꿀 수 없는 호환성 식별자.
ALLOW_DEAD_PKG="booking.common booking.order.persistence.jpa.repository.adapter booking.application"
OBS="docs/agents/observed-failures.md"

fail=0
err() { printf 'FAIL  %s\n' "$*"; fail=1; }
ok()  { printf 'ok    %s\n' "$*"; }

# --changed: 미커밋 .md만 본다.
# Stop 훅이 매 턴 부르므로 빠른 경로가 필요하다. CI는 인자 없이 전체를 돌린다.
SCOPE="all"
[ "${1:-}" = "--changed" ] && SCOPE="changed"

# 검사 대상 문서 목록.
if [ "$SCOPE" = "changed" ]; then
  DOCS=$(git status --porcelain -- '*.md' | sed 's/^...//' | sort -u)
  if [ -z "$DOCS" ]; then
    echo "ok    바뀐 문서 없음"
    exit 0
  fi
else
  DOCS=$( { git ls-files '*.md'; find -L .agents/skills -name '*.md' 2>/dev/null; } \
          | sort -u )
fi

# 1 ─ AGENTS.md 줄 수
for f in $(git ls-files '*AGENTS.md'); do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$AGENTS_MAX" ]; then
    err "$f 가 ${n}줄로 상한 ${AGENTS_MAX}줄을 넘었다. 절차는 .agents/skills/ 로 옮긴다"
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
  # 형제 저장소 문서는 통합 workspace에서는 확인할 수 있지만, 이 저장소만 checkout하는 CI에는 없다.
  case "$p" in
    ../gatling-test/*|../../gatling-test/*|../../../gatling-test/*) continue ;;
    ../ticket-queue/*|../../ticket-queue/*|../../../ticket-queue/*) continue ;;
  esac
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

# 3 ─ 스킬 프론트매터
if [ -d .agents/skills ]; then
  skills=$(find -L .agents/skills -name 'SKILL.md')
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

# 4 ─ BOM
# awk 문자열의 8진 이스케이프로 EF BB BF를 비교한다. gawk/mawk 모두에서 동작한다.
bomlist=$(printf '%s\n' "$DOCS" \
          | xargs awk 'FNR==1 && substr($0,1,3)=="\357\273\277" { print FILENAME }' 2>/dev/null)
if [ -n "$bomlist" ]; then
  while IFS= read -r f; do err "$f 에 UTF-8 BOM이 있다"; done <<< "$bomlist"
else
  ok "BOM 없음"
fi


# 5 ─ 관측 태그와 실패 기록 대조
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

# 6 ─ 문서가 가리키는 package 경로
# 계층 이름이 바뀌는 리팩터링(application -> usecase 등)에서 문서만 옛 이름으로 남는 것을 막는다.
# 검사 2가 .md 링크를 지켜주듯 이쪽은 산문 속 package 경로를 지킨다.
# ADR은 결정 당시의 기록이라 옛 경로가 정상이므로 제외한다 -- 어긋난 부분은 갱신 배너로 덮는다.
# ponytail: package 경로만 본다. 타입 이름은 stdlib/enum 값/역할 이름 오탐이 많아 allowlist 관리 비용이
# 검사 가치를 넘는다. 필요해지면 그때 넓힌다.
deadpkg=0
PKG_DOCS=$(printf '%s\n' "$DOCS" | grep -v '^docs/adr/')
if [ -n "$PKG_DOCS" ]; then
  while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    f=${hit%%:*}
    p=${hit#*:}
    p=${p//\`/}
    p=${p#com.ticket.}
    case " $ALLOW_DEAD_PKG " in *" $p "*) continue ;; esac
    # tr/dirname을 부르지 않는다 -- 셸 내장 치환으로 경로를 만든다(검사 2의 주석 참고).
    if [ ! -d "src/main/java/com/ticket/${p//./\/}" ]; then
      err "$f 가 없는 package를 가리킨다 -> $p"
      deadpkg=1
    fi
  done < <(printf '%s\n' "$PKG_DOCS" \
           | xargs grep -oHE '`(com\.ticket\.)?(booking|show|member|like|venue|payment|security|shared)(\.[a-z][a-z0-9]*)+`' 2>/dev/null \
           | sort -u)
fi
[ "$deadpkg" -eq 0 ] && ok "문서가 가리키는 package 전부 실재"

if [ "$SCOPE" = "changed" ]; then
  if [ "$fail" -ne 0 ]; then echo "문서 검사 실패"; exit 1; fi
  echo "문서 검사 통과 (바뀜 문서만)"
  exit 0
fi

if [ "$fail" -ne 0 ]; then
  echo "문서 검사 실패"
  exit 1
fi
echo "문서 검사 통과"
