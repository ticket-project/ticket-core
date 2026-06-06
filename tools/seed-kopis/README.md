# seed-kopis — KOPIS 공연 시드 최신화 도구

KOPIS OpenAPI에서 신규 공연을 가져와 `core/core-api/src/main/resources/seed/kopis-curated.sql`에 **누적 추가**한다.
기존 시드(SHOWS 1~292 등)는 건드리지 않고, 다음 id부터 이어 붙인다.

설계 문서: `docs/superpowers/specs/2026-06-06-kopis-data-refresh-design.md`

## 사전 준비

- Node 18+ (글로벌 `fetch` 사용, 외부 의존성 없음)
- KOPIS OpenAPI 서비스 키 → 환경변수 `KOPIS_SERVICE_KEY`
  - 키 발급: https://www.kopis.or.kr 회원가입 → 오픈API 신청
  - **키를 코드/문서에 하드코딩하지 말 것.**

## 실행

```bash
# 미리보기(파일 수정 없음)
KOPIS_SERVICE_KEY=xxxx node tools/seed-kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906 --dry-run

# 실제 병합
KOPIS_SERVICE_KEY=xxxx node tools/seed-kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906
```

Windows PowerShell:
```powershell
$env:KOPIS_SERVICE_KEY="xxxx"; node tools/seed-kopis/fetch-kopis.mjs --target 100 --from 20260606 --to 20260906 --dry-run
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
- **좌석/등급**: SHOW_GRADES / SHOW_SEATS / PERFORMANCE_SEATS는 SQL 파일 끝 CROSS JOIN INSERT가 신규 SHOWS·PERFORMANCES에 자동 적용하므로 별도 생성하지 않는다.

## 병합 후 검증

```bash
# 적재 무결성 (시드 켜고 기동하거나 테스트)
cd ..  # ticket 루트
./gradlew.bat :core:core-api:test
```

문제가 있으면 `kopis-curated.sql.bak`으로 복원한다.
