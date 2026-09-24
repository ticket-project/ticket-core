# 기여 가이드

작업 브랜치에서 요청 범위만 변경하고 기존 사용자 변경은 보존한다. 현재 동작과 합의된 정책이 충돌하면 근거를 확인하고 변경 영향과 미결 사항을 드러낸다. 로컬 실행은 [README.md](README.md), 기술 기준은 [코드 작성](docs/coding-guidelines.md)·[테스트](docs/testing.md)·[운영](docs/operations.md)·[ADR](docs/adr/README.md)를 따른다.

## 검증과 완료

변경 영향에 맞는 테스트와 정적 검사를 [testing.md](docs/testing.md)의 기준으로 선택한다. 실제 실행 명령·결과, 실패와 미실행 범위, 환경 제약을 구분해 보고한다. 문서 변경은 `bash scripts/check-docs.sh`와 `git diff --check`를 확인한다. 실패한 검증을 통과로 보고하거나 테스트를 건너뛰어 완료로 처리하지 않는다.

## 커밋과 PR

- 하나의 논리적 목적을 한 커밋에 담고, 각 커밋에서 가능한 검증을 마친다. 기존 변경과 요청 범위 밖 파일은 포함하지 않는다.
- 제목은 `<type>(<scope>): <한국어 설명>`이다. type은 `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`, `build`, `ci`, `security`, `revert` 중 고른다. scope는 가장 좁은 도메인·모듈 단위로 쓴다.
- 본문에는 변경 배경과 선택 이유, 계약·동작의 변화, 실제 검증과 미실행 범위를 남긴다. 작은 변경은 짧게 써도 된다. 중요한 구조 결정은 [ADR 기준](docs/adr/README.md)에 따라 별도로 기록한다.
- PR은 목적, 주요 변경, 검증 결과, 호환성·운영 영향과 남은 위험을 적는다. 관련 Issue를 연결하고, 반영 전 대상 브랜치와 충돌을 확인한다.
- `master` push는 [배포 workflow](.github/workflows/deploy.yml) 조건에 맞으면 운영 배포를 시작한다. 문서만의 push는 경로 무시 조건에 해당하지만 `scripts/**` 변경은 무시 대상이 아니다.

## 이슈와 라벨

미해결 결함·조사·제품 결정·구조 변경 제안의 추적 원본은 [ticket-project/ticket-core Issues](https://github.com/ticket-project/ticket-core/issues)다. 한국어 제목과 본문에 확인한 사실, 가정, 영향, 보류 이유, 후보 대안, 승인 여부, 완료 조건, 근거를 구분해 적는다. 기존 Issue와 닫힌 해결 기록을 먼저 확인해 중복을 피한다. Issue 생성 자체는 구현 승인이나 담당자 지정이 아니다.

트리아지 라벨은 `needs-triage`(평가 대기), `needs-info`(추가 정보 대기), `ready-for-agent`(명세 완료·에이전트 처리), `ready-for-human`(사람 처리), `wontfix`(진행하지 않음)다. `bug`·`enhancement`·`question`·`documentation`·`refactoring` 등 실제 저장소의 일반 라벨은 내용의 성격에 맞게 쓴다. 미확정 제안에 `ready-for-agent`를 붙이지 않는다. 다른 Issue를 임의로 닫거나 담당자·마일스톤·우선순위를 임의로 정하지 않는다.
