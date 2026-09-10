#!/usr/bin/env python3
"""KOPIS 시드 SQL의 공연일/판매기간을 기준일(anchor) 중심으로 재배치한다.

배경
----
seed/kopis-curated.sql 의 공연일이 특정 시즌(예: 2026 봄)에 고정되어 있어,
시간이 지나면 대부분의 공연이 '예매 종료(CLOSED)' 상태가 되어 좌석 선택이 불가능해진다.
이 스크립트는 SHOWS/PERFORMANCES 의 날짜 컬럼만 재계산해서,
기준일 시점에 대다수 공연이 '예매중(ON_SALE)' 이 되도록 만든다.

표시 상태 판정(도메인 규칙, DisplaySaleWindow.statusAt — ADR 0007). 이건 화면 표시일 뿐
실제 주문 접수 가능 여부는 booking의 PerformanceSalesPolicy가 회차 단위로 따로 판단한다:
  - now <  display_sale_starts_at              -> BEFORE_OPEN (오픈 예정)
  - display_sale_starts_at <= now <= display_sale_ends_at -> ON_SALE (예매중, 좌석 선택 가능)
  - now >  display_sale_ends_at               -> CLOSED      (예매 종료)
그리고 SeedDataLoader/테스트가 강제하는 불변식:
  - show.display_sale_starts_at == min(performance 회차들의 접수 시작 시각)
  - show.display_sale_ends_at   == max(performance 회차들의 접수 종료 시각)
  - 회차가 2개 이상이면 날짜가 2개 이상으로 분산
  - 모든 회차일은 [show.start_date, show.end_date] 안

분포(기본): ON_SALE 80% / BEFORE_OPEN 15% / CLOSED 5%
ON_SALE 공연은 공연일을 기준일+이후 수개월에 분산하고 판매는 이미 오픈된 상태로 둔다.

사용:
  python reschedule-dates.py <sql경로> [--anchor YYYY-MM-DD]
"""
from __future__ import annotations

import argparse
import re
from datetime import datetime, timedelta

SHOW_RE = re.compile(
    r"^(INSERT INTO SHOWS \([^)]*\) VALUES \()"      # 1 head
    r"(\d+)"                                          # 2 id
    r"(, .*?, )"                                       # 3 title/sub/info
    r"'(\d{4}-\d{2}-\d{2})'"                          # 4 start_date
    r"(, )"                                            # 5
    r"'(\d{4}-\d{2}-\d{2})'"                          # 6 end_date
    r"(, \d+, '[A-Z_]+', )"                           # 7 view_count + display_sale_type
    r"'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"        # 8 display_sale_starts_at
    r"(, )"                                            # 9
    r"'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"        # 10 display_sale_ends_at
    r"(.*)$",                                          # 11 rest
    re.DOTALL,
)

PERF_RE = re.compile(
    r"^(INSERT INTO PERFORMANCES \([^)]*\) VALUES \()"  # 1 head
    r"(\d+), (\d+), (\d+), "                             # 2 id, 3 show_id, 4 perf_no
    r"'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"          # 5 start_time
    r"(, )'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"      # 6 sep, 7 end_time
    r"(, )'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"      # 8 sep, 9 order_open
    r"(, )'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})'"      # 10 sep, 11 order_close
    r"(.*)$",                                            # 12 rest
    re.DOTALL,
)

DT = "%Y-%m-%d %H:%M:%S"
D = "%Y-%m-%d"


def parse_dt(s: str) -> datetime:
    return datetime.strptime(s, DT)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--anchor", default=None, help="기준일 YYYY-MM-DD (기본: 오늘)")
    args = ap.parse_args()

    anchor = (
        datetime.strptime(args.anchor, D) if args.anchor else datetime.now()
    ).replace(hour=0, minute=0, second=0, microsecond=0)

    with open(args.path, encoding="utf-8") as f:
        lines = f.read().split("\n")

    # 1) 파싱: show -> 라인index/날짜, show -> [회차]
    shows: dict[int, dict] = {}
    perfs: dict[int, list[dict]] = {}
    for i, line in enumerate(lines):
        ms = SHOW_RE.match(line)
        if ms:
            shows[int(ms.group(2))] = {"line": i}
            continue
        mp = PERF_RE.match(line)
        if mp:
            sid = int(mp.group(3))
            perfs.setdefault(sid, []).append(
                {
                    "line": i,
                    "perf_no": int(mp.group(4)),
                    "start": parse_dt(mp.group(5)),
                    "end": parse_dt(mp.group(7)),
                }
            )

    show_ids = sorted(shows)
    # 2) 버킷 배정: 20주기로 0->CLOSED(5%), 1..3->BEFORE_OPEN(15%), 나머지->ON_SALE(80%)
    def bucket(rank: int) -> str:
        r = rank % 20
        if r == 0:
            return "CLOSED"
        if r in (1, 2, 3):
            return "BEFORE_OPEN"
        return "ON_SALE"

    on_sale_ids = [s for r, s in enumerate(show_ids) if bucket(r) == "ON_SALE"]
    before_ids = [s for r, s in enumerate(show_ids) if bucket(r) == "BEFORE_OPEN"]
    closed_ids = [s for r, s in enumerate(show_ids) if bucket(r) == "CLOSED"]
    on_rank = {s: i for i, s in enumerate(on_sale_ids)}
    bf_rank = {s: i for i, s in enumerate(before_ids)}
    cl_rank = {s: i for i, s in enumerate(closed_ids)}

    edits: dict[int, str] = {}  # line index -> new line

    for rank, sid in enumerate(show_ids):
        plist = sorted(perfs.get(sid, []), key=lambda p: p["perf_no"])
        if not plist:
            continue
        b = bucket(rank)

        # 회차 간 날짜 오프셋(런 모양) 보존. 단일일이면 2~3일로 분산(다중 날짜 보장).
        base0 = min(p["start"].date() for p in plist)
        offs = [(p["start"].date() - base0).days for p in plist]
        n = len(plist)
        if len(set(offs)) < 2 and n >= 2:
            buckets = min(3, n)
            offs = [k * buckets // n for k in range(n)]
        run_span = max(offs)

        # 새 첫 공연일 / 판매오픈일 결정
        if b == "ON_SALE":
            total = max(1, len(on_sale_ids) - 1)
            new_base = anchor + timedelta(days=3 + round(on_rank[sid] * 150 / total))
            order_open_d = anchor - timedelta(days=15 + (on_rank[sid] % 60))
        elif b == "BEFORE_OPEN":
            order_open_d = anchor + timedelta(days=5 + (bf_rank[sid] % 40))
            new_base = order_open_d + timedelta(days=25 + (bf_rank[sid] % 30))
        else:  # CLOSED
            last_d = anchor - timedelta(days=5 + (cl_rank[sid] % 25))
            new_base = last_d - timedelta(days=run_span)
            order_open_d = new_base - timedelta(days=20)

        order_open = order_open_d.replace(hour=10, minute=0, second=0)

        # 회차별 새 시각 계산 + 라인 치환
        oo_min = order_open
        oc_max = None
        for p, off in zip(plist, offs):
            new_start = datetime.combine(
                (new_base + timedelta(days=off)).date(), p["start"].time()
            )
            dur = p["end"] - p["start"]
            new_end = new_start + dur
            new_oc = new_start - timedelta(hours=1)  # 공연 1시간 전 마감(원본 관례)
            if oc_max is None or new_oc > oc_max:
                oc_max = new_oc

            mp = PERF_RE.match(lines[p["line"]])
            new_perf_line = (
                f"{mp.group(1)}{mp.group(2)}, {mp.group(3)}, {mp.group(4)}, "
                f"'{new_start.strftime(DT)}'{mp.group(6)}'{new_end.strftime(DT)}'"
                f"{mp.group(8)}'{order_open.strftime(DT)}'"
                f"{mp.group(10)}'{new_oc.strftime(DT)}'{mp.group(12)}"
            )
            edits[p["line"]] = new_perf_line

        # SHOWS 라인 치환: start/end_date, sale_start(=oo_min), sale_end(=oc_max)
        new_show_start = new_base.date()
        new_show_end = (new_base + timedelta(days=run_span)).date()
        sline = lines[shows[sid]["line"]]
        ms = SHOW_RE.match(sline)
        new_show_line = (
            f"{ms.group(1)}{ms.group(2)}{ms.group(3)}"
            f"'{new_show_start.strftime(D)}'{ms.group(5)}'{new_show_end.strftime(D)}'"
            f"{ms.group(7)}'{oo_min.strftime(DT)}'{ms.group(9)}'{oc_max.strftime(DT)}'"
            f"{ms.group(11)}"
        )
        edits[shows[sid]["line"]] = new_show_line

    for idx, new_line in edits.items():
        lines[idx] = new_line

    with open(args.path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(
        f"재배치 완료: anchor={anchor.date()} shows={len(show_ids)} "
        f"ON_SALE={len(on_sale_ids)} BEFORE_OPEN={len(before_ids)} CLOSED={len(closed_ids)} "
        f"lines_changed={len(edits)}"
    )


if __name__ == "__main__":
    main()
