"""usecase·event가 persistence 구현을 직접 참조하면 그 규칙을 에이전트 컨텍스트에 넣는다.

- PostToolUse(Write/Edit 직후): 방금 쓴 파일 하나만 본다. 그 자리에서 고치게 하는 용도다.
- Stop(끝내기 직전): 브랜치 변경 전체를 본다. 셸로 고쳐 PostToolUse를 건너뛴 파일까지 잡는 안전망이다.

정규식은 후보만 좁힌다. 고칠지는 규칙을 받은 에이전트가 판단한다. 규칙의 원본은
ArchitectureRulesTest.usecase와_event는_저장_구현과_HTTP를_모른다 다.
"""

import json
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RULE = "usecase-persistence-impl"
PERSISTENCE_REF = re.compile(r"\bcom\.ticket\.[\w.]*?\.persistence\.(\w+|\*)")
LOG = ROOT / ".claude" / "hooks" / "fired.log"
STATE_DIR = Path(tempfile.gettempdir()) / "ticket-claude-hooks"

HEADERS = {
    "PostToolUse": "[ticket-core 팀 규칙] 방금 쓴 코드에 아래 규칙을 적용해 줘.",
    "Stop": "[ticket-core 팀 규칙] 끝내기 전에, 이번 변경이 아래 규칙에 닿는다. 한 번만 다시 확인해 줘. "
    "이미 지키고 있으면 그대로 두고 끝내라 — 실패가 아니라 재확인이다.",
}
RULE_TEXT = """usecase·event는 저장 구현(persistence)을 직접 참조하지 않는다
{hits}
조립은 계약(domain *Repository, port의 output port)만 부르고 저장 구현 선택은 persistence가 갖는다. 구현이 필요하면 계약 뒤에 두고 use case는 계약 타입으로 받는다. 예외는 같은 module의 *QuerydslRepository 조회뿐이다.
(rule: usecase-persistence-impl)"""


def git(*args: str) -> list[str]:
    out = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    return [line for line in out.stdout.splitlines() if line]


def changed_files() -> list[str]:
    # 세션 중에 커밋해도 빠지지 않게 브랜치 분기점부터 본다. 새로 만든(untracked) 파일도 포함한다.
    base = (git("merge-base", "HEAD", "origin/master") or ["HEAD"])[0]
    return sorted(set(git("diff", "--name-only", base)) | set(git("ls-files", "--others", "--exclude-standard")))


def written_file(event: dict) -> list[str]:
    raw = (event.get("tool_input") or {}).get("file_path")
    if not raw:
        return []
    try:
        return [Path(raw).resolve().relative_to(ROOT).as_posix()]
    except ValueError:
        return []  # 저장소 밖 파일


def hits(rel: str) -> list[str]:
    path = ROOT / rel
    if not (rel.startswith("src/main/java/") and rel.endswith(".java") and re.search(r"/(usecase|event)/", rel)):
        return []
    if not path.exists():
        return []
    names = sorted(set(PERSISTENCE_REF.findall(path.read_text(encoding="utf-8"))))
    # shortcut: 이름만 본다. ArchUnit은 JPAQueryFactory 보유까지 보니 애매하면 architectureTest가 최종 기준이다.
    return [f"- {rel} → persistence.{name}" for name in names if not name.endswith("QuerydslRepository")]


def main() -> None:
    event = json.loads(sys.stdin.buffer.read() or b"{}")
    point = event.get("hook_event_name", "Stop")
    files = written_file(event) if point == "PostToolUse" else changed_files()
    found = [hit for rel in files for hit in hits(rel)]
    if not found:
        return
    STATE_DIR.mkdir(exist_ok=True)
    marker = STATE_DIR / f"{event.get('session_id', 'unknown')}.{point}.{RULE}"
    if marker.exists():
        return  # 같은 규칙은 지점마다 세션당 한 번. Stop을 다시 막는 무한 반복도 여기서 끊긴다.
    marker.touch()
    record = {"at": time.strftime("%Y-%m-%dT%H:%M:%S"), "session": event.get("session_id"), "point": point,
              "rule": RULE, "hits": found}
    with LOG.open("a", encoding="utf-8") as log:
        log.write(json.dumps(record, ensure_ascii=False) + "\n")
    text = HEADERS[point] + "\n\n" + RULE_TEXT.format(hits="\n".join(found))
    if point == "PostToolUse":
        out = {"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": text}}
    else:
        out = {"decision": "block", "reason": text}
    print(json.dumps(out))


if __name__ == "__main__":
    main()
