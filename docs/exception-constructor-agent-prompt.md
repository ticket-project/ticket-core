# 예외 생성자 구체화 구현 프롬프트

아래 본문을 구현 에이전트에게 전달한다. 세부 생성자와 호환성 기준의 원본은
[설계안](exception-constructor-design.md)이다. 이 문서 자체는 구현 완료 기록이 아니다.

---

Ticket Core의 예외 생성자를 구체화해줘. 작업 저장소는
`C:\Users\mn040\IdeaProjects\ticket-workspace\ticket`이다. 별도 checkout이면 그 저장소 루트를 사용해.

1. `AGENTS.md`를 전부 읽고 `git status --short`로 기존 변경을 확인해. 기존 미커밋 변경을 보존하고
   작업 브랜치에서 진행해. 문서는 한국어·UTF-8 BOM 없이 작성해. 커밋과 push는 하지 마.
2. `docs/exception-constructor-design.md`를 전부 읽어. 현재 코드와 다른 부분은 구현 대상인지
   외부 변경인지 구분해. 이 설계안의 A~D 생성자 표와 외부 계약 표가 작업 범위와 완료 기준이야.
   `docs/adr/0010-exceptions-do-not-own-http-status.md`와 `docs/architecture.md`의 오류 처리 절도 확인해.
3. main/test의 생성자 호출부, 실제 예외 발생 조건, handler를 함께 조사하고 관련 기존 테스트로
   응답 기준을 확인해. `ShowSort`가 현재 만드는 상세 문구와 `error.data`를 특히 확인해.
4. A 그룹은 실제 ID·상태·수량을 생성자와 final 필드로 옮기고 호출부가 그 값을 전달하게 해.
   B 그룹은 정렬 원문을 받고 예외 내부에서 기존 상세 문구를 만들어. C 그룹은 `Object`를
   `String detail`로 좁히고, D 그룹은 설계대로 유지해. getter는 기존 코드 관례를 따라 구현해.
5. 필요한 인수를 가진 예외의 이전 무인수·범용 Object 생성자는 제거해. private helper에는 이미
   확보한 값을 전달할 수 있지만, DB/Redis 조회나 외부 호출을 추가하지 마. 예외 생성자에 새 업무
   검증을 넣지 말고 ID의 불필요한 unboxing으로 기존 실패가 NPE로 바뀌지 않게 해.
6. 발생 조건·발생 위치·catch 대상·상태 전이는 그대로 유지해. 특히 `Order`의 IllegalStateException을
   바꾸거나 취소 서비스의 검증을 옮기지 마. `ErrorType`, HTTP 매핑, 정적 factory, cause/로그 정책,
   Kakao·커서 오류 재분류는 이번 범위가 아니야.
7. 실제 실패 경로에서 전달된 인수를 확인하고 기존 HTTP 상태·E-code·공개 message·error.data가
   유지되는지 검증해. 새 진단 필드가 API에 노출되지 않게 해. 모든 getter를 개별 테스트하는 대신
   호출부와 응답 계약을 검증해. 상세 검증 기준은 설계안과 `.agents/skills/verify/SKILL.md`를 따라.
8. 완료하면 생성자 그룹별 변경 결과, 외부 계약 보존 결과, 실행한 검증의 통과·실패 수와 미실행
   범위를 보고해. 설계 문서의 상태는 실제 검증 결과에 맞춰 갱신해. `docs/technical-debt.md`의
   TD-17은 이번 작업으로 해결되지 않으므로 보류 상태로 남겨.

기존 코드나 환경의 실패가 나오면 이번 변경 때문인지 분리해 보고해. 테스트를 삭제·비활성화하거나
검사 범위를 줄여 완료로 처리하지 마. 검증에 필요한 환경·권한 문제가 있으면 저장소 규칙에 따라
해결하고, 해결되지 않은 검증은 미완료로 명시해.
