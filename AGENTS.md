# Ticket Core 에이전트 안내

공통 개발·검증·커밋·PR·이슈 절차는 [CONTRIBUTING.md](CONTRIBUTING.md)를 따른다. 문서와 응답은 한국어, 파일은 UTF-8(BOM 제외)로 작성한다.

- 기존 사용자 변경을 임의로 되돌리지 않고 요청 범위 밖 작업을 섞지 않는다.
- 명시적인 요청 없이 커밋·push·배포하지 않는다. `master` push는 배포 workflow 조건을 확인한다.
- 읽지 못한 문서·코드·설정은 추측하지 않고, 실행하지 않은 검증은 통과했다고 보고하지 않는다.
- 현재 구현, 합의된 정책, 미적용 결정과 제안을 구분한다. 충돌은 근거와 함께 드러낸다.

| 작업 | 읽을 문서 |
| --- | --- |
| 용어·개념 변경 | [용어집](docs/glossary.md) |
| 모듈 경계·코드 배치 | [아키텍처](docs/architecture.md)와 관련 [ADR](docs/adr/README.md) |
| 이름·코드 작성 판단 | [코드 작성 기준](docs/coding-guidelines.md) |
| 예매 상태·선택·선점·실패 처리 | [예매 수명주기](docs/core-booking-lifecycle.md) |
| 테스트 선택·결과 보고 | [테스트 기준](docs/testing.md), 실행 보조는 `/verify` |
| 배포·DB·Redis 전환·관측 | [운영](docs/operations.md) |
| 부하 실행 | [테스트의 부하 검증](docs/testing.md#core-부하-검증), 실행 보조는 `/loadtest` |

모든 문서를 매 작업마다 읽을 필요는 없다. 현재 코드와 테스트가 문서와 다르면 정책을 자동으로 바꾸지 않고 불일치를 확인한다.
