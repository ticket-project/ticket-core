# Ticket Core 에이전트 안내

문서와 응답은 한국어, 파일은 UTF-8(BOM 제외)로 작성한다.

- 커밋은 요청을 기다리지 않고 목적 하나마다 잘게 나눠 만든다. push·PR·배포는 명시적인 요청이 있을 때만 한다. `master` push는 운영 배포를 시작할 수 있다.
- 테스트는 작업 중·커밋마다 돌리지 않는다. 모든 커밋이 끝나고 완료를 알리기 전에 한 번 돌린다.
- 기존 사용자 변경을 임의로 되돌리지 않고 요청 범위 밖 작업을 섞지 않는다.
- 읽지 못한 문서·코드·설정은 추측하지 않고, 실행하지 않은 검증은 통과했다고 보고하지 않는다.
- 코드·테스트가 문서와 다르면 정책을 자동으로 바꾸지 않고 불일치를 근거와 함께 드러낸다.

| 작업 | 읽을 문서 |
| --- | --- |
| 로컬 실행·배포 한 줄 | [README](README.md) |
| 커밋·PR·Issue·라벨·코드 리뷰 | [CONTRIBUTING](CONTRIBUTING.md) |
| 용어·개념 | [용어집](docs/glossary.md) |
| 모듈 경계·코드 배치·결정 배경 | [아키텍처](docs/architecture.md), [ADR 색인](docs/adr/README.md) |
| 이름·코드 작성 | [코드 작성 기준](docs/coding-guidelines.md) |
| 예매 상태·선택·선점·실패 처리 | [예매 수명주기](docs/core-booking-lifecycle.md) |
| 테스트 선택·시점·부하 검증 | [테스트 기준](docs/testing.md) |
| DB migration | [migration 규칙](src/main/resources/db/README.md) |
| 초기 데이터 적재 | [seed](seed/README.md) |
| 배포·프로파일·Redis 전환·관측 | [운영](docs/operations.md) |
