"""방금 쓴 파일이 다른 module의 공개 계약 밖을 참조하면 그 규칙을 에이전트 컨텍스트에 넣는다.

PostToolUse(Write/Edit 직후)에서 방금 쓴 파일 하나만 본다. 허용 목록은 쓰는 쪽 module의
package-info.java `allowedDependencies`를 그대로 읽으므로 따로 관리할 목록이 없다.
정규식은 후보만 좁히고 고칠지는 에이전트가 판단한다. 규칙의 원본은 ModularityTests(Spring Modulith verify)다.
"""

import json
import re
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULES = ROOT / "src" / "main" / "java" / "com" / "ticket"
RULE = "module-boundary"
REF = re.compile(r"\bcom\.ticket\.([a-z]\w*)\.([\w.]*\w)")
ALLOWED = re.compile(r'"(\w+)\s*::\s*(\w+)"')
COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)
# named interface 이름 = 공개 package 이름. 예외: shared의 config는 package가 아니라 이 타입만 연다.
INTERFACE_PATH = {"config": "config.CorsProperties"}
LOG = ROOT / ".claude" / "hooks" / "fired.log"
STATE_DIR = Path(tempfile.gettempdir()) / "ticket-claude-hooks"

RULE_TEXT = """[ticket-core 팀 규칙] 방금 쓴 코드에 아래 규칙을 적용해 줘.

module은 다른 module의 공개 계약만 참조한다
{hits}
다른 module은 쓰는 쪽 package-info.java의 allowedDependencies에 적힌 named interface로만 부른다. 다른 module의 domain·usecase·persistence·entity가 필요하면 그 module의 api에 계약을 추가하고, 새 의존 방향이 필요하면 allowedDependencies와 ModularityTests의 승인 DAG를 함께 검토한다.
{module}의 allowedDependencies: {allowed}
(rule: module-boundary)"""


def written_file(event: dict) -> str | None:
    raw = (event.get("tool_input") or {}).get("file_path")
    if not raw:
        return None
    try:
        return Path(raw).resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return None  # 저장소 밖 파일


def allowed_of(module: str) -> list[tuple[str, str]]:
    info = MODULES / module / "package-info.java"
    return ALLOWED.findall(info.read_text(encoding="utf-8")) if info.exists() else []


def is_open(target: str, rest: str, allowed: list[tuple[str, str]]) -> bool:
    for module, interface in allowed:
        path = INTERFACE_PATH.get(interface, interface)
        if module == target and (rest == path or rest.startswith(path + ".")):
            return True
    return False


def check(rel: str) -> tuple[str, list[tuple[str, str]], list[str]] | None:
    match = re.fullmatch(r"src/main/java/com/ticket/(\w+)/.+\.java", rel)
    if not match or not (ROOT / rel).exists():
        return None
    module = match.group(1)
    allowed = allowed_of(module)
    # javadoc이 다른 module 클래스를 설명용으로 언급하는 경우가 있어 주석은 지우고 본다.
    code = COMMENT.sub("", (ROOT / rel).read_text(encoding="utf-8"))
    refs = sorted(
        {
            (target, rest)
            for target, rest in REF.findall(code)
            if target != module and (MODULES / target).is_dir() and not is_open(target, rest, allowed)
        }
    )
    hits = [f"- {rel} → com.ticket.{target}.{rest}" for target, rest in refs]
    return (module, allowed, hits) if hits else None


def main() -> None:
    event = json.loads(sys.stdin.buffer.read() or b"{}")
    rel = written_file(event)
    found = check(rel) if rel else None
    if not found:
        return
    module, allowed, hits = found
    STATE_DIR.mkdir(exist_ok=True)
    marker = STATE_DIR / f"{event.get('session_id', 'unknown')}.PostToolUse.{RULE}"
    if marker.exists():
        return  # 같은 규칙은 세션당 한 번
    marker.touch()
    record = {"at": time.strftime("%Y-%m-%dT%H:%M:%S"), "session": event.get("session_id"), "point": "PostToolUse",
              "rule": RULE, "hits": hits}
    with LOG.open("a", encoding="utf-8") as log:
        log.write(json.dumps(record, ensure_ascii=False) + "\n")
    text = RULE_TEXT.format(
        hits="\n".join(hits), module=module, allowed=", ".join(f"{m} :: {i}" for m, i in allowed) or "(없음)"
    )
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": text}}))


if __name__ == "__main__":
    main()
