# 기여 가이드

커밋·PR·이슈·라벨·코드 리뷰의 기준이다.

## 커밋과 PR

- 하나의 논리적 목적을 한 커밋에 담는다. 기존 변경과 요청 범위 밖 파일은 포함하지 않는다.
- 제목은 `<type>(<scope>): <한국어 설명>`이다. type은 `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`, `build`, `ci`, `security`, `revert` 중 고른다. scope는 가장 좁은 도메인·모듈 단위로 쓴다.
- 본문에는 변경 배경과 선택 이유, 계약·동작의 변화, 실제 검증과 미실행 범위를 남긴다. 작은 변경은 짧게 써도 된다.
- PR은 목적, 주요 변경, 검증 결과, 호환성·운영 영향과 남은 위험을 적는다. 관련 Issue를 연결하고, 반영 전 대상 브랜치와 충돌을 확인한다.

## 이슈와 라벨

미해결 결함·조사·제품 결정·구조 변경 제안·기술 부채의 추적 원본은 [ticket-project/ticket-core Issues](https://github.com/ticket-project/ticket-core/issues)다. 코드·문서에는 부채·TODO 번호를 따로 매기지 않고, 필요하면 Issue 번호만 적는다. 한국어 제목과 본문에 확인한 사실, 가정, 영향, 보류 이유, 후보 대안, 승인 여부, 완료 조건, 근거를 구분해 적는다. 기존 Issue와 닫힌 해결 기록을 먼저 확인해 중복을 피한다. Issue 생성 자체는 구현 승인이나 담당자 지정이 아니다.

트리아지 라벨은 `needs-triage`(평가 대기), `needs-info`(추가 정보 대기), `ready-for-agent`(명세 완료·에이전트 처리), `ready-for-human`(사람 처리), `wontfix`(진행하지 않음)다. `bug`·`enhancement`·`question`·`documentation`·`refactoring` 등 실제 저장소의 일반 라벨은 내용의 성격에 맞게 쓴다. 미확정 제안에 `ready-for-agent`를 붙이지 않는다. 다른 Issue를 임의로 닫거나 담당자·마일스톤·우선순위를 임의로 정하지 않는다.

## 코드 리뷰

패치 밖의 관련 코드·테스트·설정까지 확인하고, 스타일보다 결함 가능성을 우선하며, 근거가 약한 지적은 하지 않는다. 특히 다음을 본다.

- 트랜잭션 경계와 DB connection 점유: 트랜잭션 안에서 Redis·WebSocket·다른 모듈 호출로 connection을 붙들지 않는지, 락을 먼저 잡고 그 안에서 트랜잭션을 여는 순서인지.
- 락 범위: 락이 보호하는 대상과 수명이 좌석·회원·주문 단위와 맞는지, 락 안의 외부 I/O가 필요한 것뿐인지.
- Redis와 DB 정합성: TTL·만료 listener·scheduler 보정이 같은 정합성을 함께 지키는지, 실패 보상과 이벤트 재전달에 멱등한지.
- 공개 계약: JSON 필드·오류 코드·HTTP 상태를 바꾸면 소비자(프론트, `gatling-test`, `ticket-queue`)가 깨지지 않는지.
- migration 안전: 적용된 파일을 고치지 않았는지, 운영 데이터와 Oracle에서 실패·재시도해도 안전한지.
- 테스트: 새 분기와 취소·만료·재시도 경로가 테스트로 고정됐는지.
