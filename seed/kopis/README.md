# seed-kopis — KOPIS 공연 시드 최신화 도구

KOPIS OpenAPI에서 신규 공연을 가져와 `seed/sql/kopis-curated.sql`에 **누적 추가**한다.
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
- **좌석/등급**: `SEATS` 복제와 `GRADES` / `PERFORMANCE_GRADES` / `PERFORMANCE_SEATS`는 SQL 파일 안의 `INSERT ... SELECT`가 신규 VENUES·SHOWS·PERFORMANCES에 자동 적용하므로 별도 생성하지 않는다. **다만 끼워 넣는 지점이 둘로 나뉜다** — 아래 "병합 지점" 참고.

## 병합 후 검증

ticket 저장소 루트에서 시드 테스트를 실행한다. 병합 결과 SQL을 실제 앱 스키마에 적재해 보고
공연·회차·좌석·등급·가격·판매정책 관계까지 확인한다.

```powershell
.\gradlew.bat seedTest
```

```bash
./gradlew seedTest
```

id 연속성·중복·FK만 빠르게 보려면 검증 스크립트를 쓴다.

```bash
node seed/kopis/verify-seed.mjs seed/sql/kopis-curated.sql
```

문제가 있으면 `kopis-curated.sql.bak`으로 복원한다.

## 병합 지점

생성한 블록은 **한 덩어리로 넣을 수 없다.** 시드 SQL에는 "실행 시점에 존재하는 행"만 대상으로
삼는 집합 기반 `INSERT ... SELECT`가 둘 있고, 새 데이터는 각각 그 앞에 놓여야 한다. 그래서
`kopis-curated.sql`에 마커 주석 두 개를 두고 도구가 블록을 나눠 끼워 넣는다.

| 마커 | 앞에 놓아야 하는 것 | 뒤에 오는 집합 기반 INSERT |
| --- | --- | --- |
| `-- @seed-splice: venues` | `PERFORMERS` / `VENUES` / `SHOWS` / `SHOW_GENRES` | `SEATS`의 VENUE별 복제 (`CROSS JOIN VENUES`) |
| `-- @seed-splice: performances` | `PERFORMANCES` | `GRADES` / `PERFORMANCE_GRADES` / `PERFORMANCE_SEATS` |

마커가 하나라도 없으면 도구는 파일을 고치지 않고 중단한다.

### 왜 나눠야 하는가

예전에는 블록 전체를 `INSERT INTO GRADES (` 앞 한 곳에 넣었다. 그 지점은 좌석 복제보다
**뒤**라서, 그렇게 추가된 공연장 179개가 물리 좌석을 하나도 받지 못했고 그 공연장의 공연
198개·회차 662개에 `PERFORMANCE_SEATS`가 생기지 않았다. 화면에는 공연이 보이는데 좌석이 없는
상태다.

같은 실수가 반복되지 않도록 두 겹으로 막아 뒀다.

- `CuratedSeedStatements.verifyStatementOrder()`가 마커 뒤에 선언된 `VENUES` / `SHOWS` /
  `PERFORMANCES` 리터럴 INSERT를 적재 **전에** 실패로 만든다.
- `CuratedSeedVerifier`가 적재 트랜잭션 **커밋 전에** "좌석 없는 공연장 0건, 회차좌석 없는 회차
  0건"을 확인하고, 어긋나면 전부 되돌린다.
