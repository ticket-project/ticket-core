#!/usr/bin/env bash
# 에이전트 문서 구조를 검증한다. CI와 로컬에서 같은 명령으로 돌린다.
#
#   bash scripts/check-docs.sh             전체 (CI)
#   bash scripts/check-docs.sh --changed   미커밋 .md를 본다. 삭제·rename이면 전체 참조 검사 (Stop 훅)
#
# 검사 항목
#   1. AGENTS.md 줄 수 상한 (진입점이 다시 불어나는 것을 막는다)
#   2. Markdown 클릭 링크는 문서 위치 기준, backtick 저장소 경로는 문서·루트 기준으로 실재하는지
#      (.md 파일 대상만 검사하며 #anchor와 외부 URL은 검사하지 않는다)
#   3. 스킬 SKILL.md 프론트매터에 name과 description이 있는지
#   4. UTF-8 BOM이 섞이지 않았는지
#   5. 문서가 backtick으로 가리키는 package 경로가 src/main/java에 실재하는지
#
# 성능 주의: Windows(Git Bash)에서는 프로세스 생성이 압도적으로 비싸다. 문서 69개 기준으로
# 파일마다 grep/head/od/git log를 부르면 90초가 넘는다. Stop 훅이 매 턴 이 스크립트를 돌리므로
# 파일별 루프 대신 목록을 한 번에 넘기는 방식을 쓴다.

set -uo pipefail
cd "$(dirname "$0")/.."

AGENTS_MAX=80
# 조건부·미래 참조라 없어도 정상인 경로
ALLOW_MISSING=""
# 일부러 "이렇게 하지 말라"고 적은 package 이름과, DB에 박혀 바꿀 수 없는 호환성 식별자.
ALLOW_DEAD_PKG="booking.common booking.order.persistence.jpa.repository.adapter booking.application"

fail=0
err() { printf 'FAIL  %s\n' "$*"; fail=1; }
ok()  { printf 'ok    %s\n' "$*"; }

# --changed: 추가·수정된 미커밋 .md만 본다. 삭제·rename은 남은 문서의 링크 검사로 확인한다.
# Stop 훅이 매 턴 부르므로 빠른 경로가 필요하다. CI는 인자 없이 전체를 돌린다.
SCOPE="all"
[ "${1:-}" = "--changed" ] && SCOPE="changed"
# 삭제·rename된 문서의 참조자는 수정되지 않았을 수 있으므로 staged/unstaged 모두 전체를 본다.
if [ "$SCOPE" = "changed" ] && { [ -n "$(git diff -M --name-only --diff-filter=DR -- '*.md')" ] || [ -n "$(git diff --cached -M --name-only --diff-filter=DR -- '*.md')" ]; }; then
  SCOPE="all"
fi

# 검사 대상 문서 목록.
if [ "$SCOPE" = "changed" ]; then
  DOCS=$( { git diff --name-only --diff-filter=ACMR -- '*.md'; git diff --cached --name-only --diff-filter=ACMR -- '*.md'; git ls-files --others --exclude-standard -- '*.md'; } | sort -u | while IFS= read -r f; do [ -f "$f" ] && printf '%s\n' "$f"; done)
  if [ -z "$DOCS" ]; then
    echo "ok    바뀐 문서 없음"
    exit 0
  fi
else
  DOCS=$( { git ls-files --cached --others --exclude-standard '*.md'; find -L .agents/skills -name '*.md' 2>/dev/null; } \
          | sort -u | while IFS= read -r f; do [ -f "$f" ] && printf '%s\n' "$f"; done )
fi

# 1 ─ AGENTS.md 줄 수
for f in $(git ls-files '*AGENTS.md'); do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$AGENTS_MAX" ]; then
    err "$f 가 ${n}줄로 상한 ${AGENTS_MAX}줄을 넘었다. 해당 역할 문서에서 중복을 줄인다"
  else
    ok "$f ${n}줄 (<= ${AGENTS_MAX})"
  fi
done

# 2 ─ 문서 포인터
broken=0
mapfile -t doc_files <<< "$DOCS"
if ! hits=$(awk '
  FNR==1 { fenced=0 }
  /^[[:space:]]*(```|~~~)/ { fenced=!fenced; next }
  !fenced {
    line=$0
    if (FILENAME !~ /^docs\/adr\//) while (match(line, /`[A-Za-z0-9_./-]+\.md`/)) {
      print FILENAME ":" substr(line, RSTART, RLENGTH)
      line=substr(line, RSTART + RLENGTH)
    }
    line=$0
    while (match(line, /\]\([A-Za-z0-9_./#-]+\.md[^)]*\)/)) {
      print FILENAME ":" substr(line, RSTART, RLENGTH)
      line=substr(line, RSTART + RLENGTH)
    }
  }
' "${doc_files[@]}" | sort -u); then
  err "문서 포인터 검사 도구 실행 실패"
  broken=1
  hits=""
fi
while IFS= read -r hit; do
  [ -n "$hit" ] || continue
  f=${hit%%:*}
  p=${hit#*:}
  case "$p" in "]("*) is_link=1 ;; *) is_link=0 ;; esac
  p=${p#\`}; p=${p%\`}
  p=${p#](}; p=${p%)}
  p=${p%%#*}
  [ -n "$p" ] || continue
  case " $ALLOW_MISSING " in *" $p "*) continue ;; esac
  # backtick의 형제 저장소 경로 안내는 단독 checkout에서 확인할 수 없다. 클릭 링크에는 예외를 두지 않는다.
  if [ "$is_link" -eq 0 ]; then
    case "$p" in
      ../gatling-test/*|../../gatling-test/*|../../../gatling-test/*) continue ;;
      ../ticket-queue/*|../../ticket-queue/*|../../../ticket-queue/*) continue ;;
    esac
  fi
  # dirname을 부르지 않는다. 히트 102개 기준으로 프로세스 생성만 22초가 든다.
  if [ "${f#*/}" = "$f" ]; then d="."; else d="${f%/*}"; fi
  if { [ "$is_link" -eq 1 ] && [ ! -e "$d/$p" ]; } || { [ "$is_link" -eq 0 ] && [ ! -e "$d/$p" ] && [ ! -e "$p" ]; }; then
    err "$f 가 없는 경로를 가리킨다 -> $p"
    broken=1
  fi
done <<< "$hits"
[ "$broken" -eq 0 ] && ok "문서 포인터 전부 실재"

# 3 ─ 스킬 프론트매터
if [ -d .agents/skills ]; then
  skill_list_failed=0
  if ! skills=$(find -L .agents/skills -name 'SKILL.md'); then
    err "스킬 목록 검사 도구 실행 실패"
    skill_list_failed=1
    skills=""
  fi
  if [ -n "$skills" ]; then
    mapfile -t skill_files <<< "$skills"
    skillcount=${#skill_files[@]}
    for f in "${skill_files[@]}"; do [ -s "$f" ] || err "$f 프론트매터가 없다"; done
    # ENDFILE은 awk 구현마다 다르므로 다음 파일의 첫 줄과 END에서 직전 파일을 판정한다.
    skill_tool_failed=0
    if ! fmbad=$(awk '
      function finish() {
        if (!opened) { print previous "\t프론트매터가 없다"; return }
        if (!closed) print previous "\t프론트매터 닫힘 구분자가 없다"
        if (!hasname) print previous "\t프론트매터에 name이 없다"
        if (!hasdesc) print previous "\t프론트매터에 description이 없다"
      }
      FNR==1 {
        if (seen) finish()
        seen=1; previous=FILENAME; opened=($0=="---"); closed=0; hasname=0; hasdesc=0
        next
      }
      opened && !closed && $0=="---" { closed=1; next }
      opened && !closed && /^name:[[:space:]]*[^[:space:]]/ { hasname=1 }
      opened && !closed && /^description:[[:space:]]*[^[:space:]]/ { hasdesc=1 }
      END { if (seen) finish() }
    ' "${skill_files[@]}"); then
      err "스킬 프론트매터 검사 도구 실행 실패"
      skill_tool_failed=1
      fmbad=""
    fi
  else
    skillcount=0
    fmbad=""
    skill_tool_failed=0
  fi
  if [ -n "$fmbad" ]; then
    while IFS=$'\t' read -r f msg; do err "$f $msg"; done <<< "$fmbad"
  elif [ "$skill_tool_failed" -eq 0 ] && [ "$skill_list_failed" -eq 0 ]; then
    ok "스킬 프론트매터 검사 완료 (${skillcount}개)"
  fi
fi

# 4 ─ BOM
# awk 문자열의 8진 이스케이프로 EF BB BF를 비교한다. gawk/mawk 모두에서 동작한다.
if ! bomlist=$(awk 'FNR==1 && substr($0,1,3)=="\357\273\277" { print FILENAME }' "${doc_files[@]}"); then
  err "BOM 검사 도구 실행 실패"
  bom_error=1
  bomlist=""
fi
if [ -n "$bomlist" ]; then
  while IFS= read -r f; do err "$f 에 UTF-8 BOM이 있다"; done <<< "$bomlist"
elif [ "${bom_error:-0}" -eq 0 ]; then
  ok "BOM 없음"
fi


# 5 ─ 문서가 가리키는 package 경로
# 계층 이름이 바뀌는 리팩터링(application -> usecase 등)에서 문서만 옛 이름으로 남는 것을 막는다.
# 검사 2가 .md 링크를 지켜주듯 이쪽은 산문 속 package 경로를 지킨다.
# ADR은 결정 당시의 기록이라 옛 경로가 정상이므로 제외한다 -- 어긋난 부분은 갱신 배너로 덮는다.
# ponytail: package 경로만 본다. 타입 이름은 stdlib/enum 값/역할 이름 오탐이 많아 allowlist 관리 비용이
# 검사 가치를 넘는다. 필요해지면 그때 넓힌다.
deadpkg=0
pkg_files=()
for f in "${doc_files[@]}"; do
  case "$f" in docs/adr/*) ;; *) pkg_files+=("$f") ;; esac
done
if [ "${#pkg_files[@]}" -gt 0 ]; then
  if ! pkg_hits=$(awk '
    {
      line=$0
      while (match(line, /`(com\.ticket\.)?(booking|show|member|like|venue|payment|security|shared)(\.[a-z][a-z0-9]*)+`/)) {
        print FILENAME ":" substr(line, RSTART, RLENGTH)
        line=substr(line, RSTART + RLENGTH)
      }
    }
  ' "${pkg_files[@]}" | sort -u); then
    err "package 경로 검사 도구 실행 실패"
    deadpkg=1
    pkg_hits=""
  fi
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
  done <<< "$pkg_hits"
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
