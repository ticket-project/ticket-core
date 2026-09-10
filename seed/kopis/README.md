# seed-kopis — KOPIS 공연 시드 최신화 도구

KOPIS OpenAPI에서 신규 공연을 가져와 `src/main/resources/seed/kopis-curated.sql`에 **누적 추가**한다.
기존 시드(SHOWS 1~292 등)는 건드리지 않고, 다음 id부터 이어 붙인다.


## 사전 준비

- Node 18+ (글로벌 `fetch` 사용, 외부 의존성 없음)
- KOPIS OpenAPI 서비스 키 → 환경변수 `KOPIS_SERVICE_KEY`
  - 키 발급: https://www.kopis.or.kr 회원가입 → 오픈API 신청
  - **키를 코드/문서에 하드코딩하지 말 것.**

## 실행

```bash
# 미리보기(파일 수정 없음)
KOPIS_SERVICE_KEY=xxxx node seed/kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906 --dry-run

# 실제 병합
KOPIS_SERVICE_KEY=xxxx node seed/kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906
```

Windows PowerShell:
```powershell
$env:KOPIS_SERVICE_KEY="xxxx"; node seed/kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906 --dry-run
```

### 옵션

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `--target` | 100 | 추가할 신규 공연 수 |
| `--from` | 20260606 | 조회 시작일 (YYYYMMDD) |
| `--to` | 20260906 | 조회 종료일 (YYYYMMDD) |
| `--rows` | 100 | 목록 API 페이지당 행 수 |
| `--max-pages` | 50 | 목록 페이징 상한 |
| `--dry-run` | - | 파일을 수정하지 않고 요약/미리보기만 출력 |

## 동작 / 안전장치

- **멱등성**: 실행 시마다 SQL에서 현재 max id와 기존 공연(제목+공연장 정규화 키, 포스터 PF id)을 다시 읽어 중복을 거른다. 재실행해도 동일 공연은 다시 추가되지 않는다.
- **결정성**: `view_count`는 `mt20id` 해시 기반 → 재실행 시 diff 안정.
- **백업**: 병합 전 `kopis-curated.sql.bak` 생성.
- **장르 매핑**: `genre-map.mjs` 참고. 복합/기타 등 매핑 불가 장르는 스킵.
- **좌석/등급**: `GRADES` / `PERFORMANCE_GRADES` / `PERFORMANCE_SEATS`는 SQL 파일 끝 `INSERT ... SELECT`가 신규 SHOWS·PERFORMANCES에 자동 적용하므로 별도 생성하지 않는다. 자세한 위치는 아래 "병합 지점" 참고.

## 병합 후 검증

ticket 저장소 루트에서 적재 무결성 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.ticket.seed.*"
```

```bash
./gradlew test --tests "com.ticket.seed.*"
```

문제가 있으면 `kopis-curated.sql.bak`으로 복원한다.

## 병합 지점

생성한 블록은 SQL 파일 끝의 집합 기반 `INSERT INTO GRADES (` 바로 앞에 끼워 넣는다
(`SPLICE_MARKER`). 그 뒤의 `GRADES` / `PERFORMANCE_GRADES` / `PERFORMANCE_SEATS`는
`INSERT ... SELECT`라 실행 시점의 모든 `SHOWS` / `PERFORMANCES`를 대상으로 삼으므로, 이 지점에
넣으면 신규 공연도 등급·가격·회차좌석을 자동으로 받는다.

예전 마커였던 `INSERT INTO SHOW_GRADES`는 `SHOW_GRADES` 테이블이 `PERFORMANCE_GRADES`로
대체되며 폐지돼(ADR 0005 §2) 파일에서 사라졌다. 지금 마커는 현재 파일에 정확히 한 번 나온다.

주의: `SEATS`의 VENUE별 복제 `INSERT ... SELECT`는 파일 중간(리터럴 `SEATS` 템플릿 바로 뒤)에
있다. 이 마커 위치에 새로 추가되는 `VENUES`는 좌석 복제 대상에 들어가지 않으므로, 그 공연장의
공연에는 `PERFORMANCE_SEATS`가 생기지 않는다 — 기존 "추가 공연장" 블록도 같은 상태다.
