# 관측된 실패

에이전트가 실제로 틀린 것과, **문서가 코드와 어긋나 에이전트를 틀리게 만들 것**을 여기 적는다.
추측으로 미리 막는 규칙은 여기 오지 않는다.

## 왜 이 파일이 있는가

규칙을 대화에서 고치면 그 세션에서만 산다. 여기 적으면 남고, 무엇으로 막았는지까지 적으면
같은 실패가 두 번 일어나지 않는다.

그리고 **이 파일이 없으면 규칙을 지울 수 없다.** 어떤 줄이 실제 실패에서 나왔고 어떤 줄이 예방적
추측인지 구분이 안 되면, 문서는 한 방향으로만 자란다.

## 어떻게 쓰는가

1. 에이전트가 틀리면 그 자리에서 고쳐 주고 끝내지 말고 여기 항목을 추가한다.
2. 무엇으로 막았는지 적는다. **문서 한 줄보다 테스트나 훅이 낫다.**
3. 문서에 규칙을 남겨야만 한다면 그 줄에 `[관측 YYYY-MM-DD]` 태그를 단다.
   `scripts/check-docs.sh`가 태그와 이 파일의 날짜를 대조한다. 짝이 없으면 CI가 실패한다.

규칙 줄의 출처 태그는 네 가지다.

| 태그 | 뜻 |
| --- | --- |
| `[테스트:<이름>]` | 테스트가 강제한다. 문서는 이름만 갖고 규칙 본문은 테스트가 원본이다 |
| `[훅:<이름>]` | `scripts/hooks/`의 훅이 강제한다 |
| `[관측 YYYY-MM-DD]` | 실제 실패에서 나왔고 아직 자동화하지 못했다. 이 파일에 짝이 있어야 한다 |
| `[근거없음]` | 예방적 추측이다. **다음 ablation의 1순위 삭제 대상이다** |

---

## 2026-08-28 — 로컬 실행 명령이 실행 모듈 이전을 따라오지 않았다

**무엇을**: `docs/operations.md`가 로컬 실행을 `./gradlew :core:core-api:bootRun`으로 안내했다.
이 명령은 돌지 않는다.

**왜**: `@SpringBootApplication`은 `bootstrap/src/main/java/com/ticket/TicketApplication.java`
하나뿐이고 `application*.yml`도 `bootstrap/src/main/resources`에만 있다. `core-api`에는 main
class도 설정도 없다. 실행 모듈을 `bootstrap`으로 옮긴 리팩터링 때 문서가 따라오지 않았다.

**영향**: 에이전트가 앱을 띄워 확인하려 하면 실패하고, 실패 원인을 코드에서 찾게 된다.

**막은 방법**: 명령을 `:bootstrap:bootRun --args='--spring.profiles.active=local'`로 고쳤다.
실제로 돌려 확인했다 — 기존 명령은 `Main class name has not been configured`로 실패하고,
새 명령은 `TicketApplication`이 기동한다. `application.yml`에 기본 프로파일이 없어 프로파일
지정도 함께 필요하다.

근본 원인은 문서가 코드에서 확인 가능한 사실을 손으로 적어 둔 것이므로, `docs/architecture.md`의
모듈·패키지 나열도 함께 걷어냈다.

## 2026-08-28 — 판단 기준이 문서와 스킬로 갈려 어긋났다

**무엇을**: `.claude/skills/place-code/SKILL.md`가 `docs/architecture.md`의 코드 위치 결정표를
"27개 책임별 위치"라고 불렀다. 실제 표는 39행이었다.

**왜**: 같은 판단 기준이 두 파일에 나뉘어 있고 한쪽만 자랐다.

**막은 방법**: 결정표를 `/place-code` 스킬로 옮겨 원본을 한 곳으로 모았다. 개수를 세어 부르는
표현은 지웠다 — 세는 순간 어긋난다.

## [관측 2026-09-04] 병렬 워크트리가 같은 module Flyway 버전 번호를 잡았다

**무엇을**: Task 3(Seat-Venue)와 Task 4(Grade/PerformanceGrade)를 각자 워크트리에서 병렬로
진행하며 둘 다 `catalog` 모듈의 `V2` 파일명을 독립적으로 선점했다. 병합 후 버전이 충돌했고,
무관하게 진행 중이던 다른 작업(ADR 0004, 공통 코드)의 untracked `V2__create_common_codes.sql`과도
겹쳤다.

**왜**: module별 Flyway 버전 번호는 파일시스템 다음 정수를 손으로 고르는 방식이라, 같은 모듈을
동시에 건드리는 워크트리끼리는 서로의 선점을 볼 수 없다.

**막은 방법**: `bd78f8b2`에서 V3/V4로 재번호하고, 영향받은 schema 검증 테스트(baseline에
VENUES/SEATS 컬럼 추가, `CatalogModuleSlicingSchemaTest`, `CurrentSeatVenueShowGradeSchemaTest`)를
같은 커밋에서 맞췄다. 병렬 워크트리로 같은 module의 Flyway migration을 나눠 맡길 때는 병합 직전에
버전 번호가 실제로 비어 있는지 다시 확인해야 한다 — 계획 단계에서 번호를 미리 예약해도 다른
무관한 작업이 같은 번호를 쓸 수 있다.

## [관측 2026-09-04] 공유 EntityScan 테스트 설정이 새 module entity 패키지를 누락했다

**무엇을**: Task 11(`payment` 모듈 신설) 병합 후 catalog `internal.infrastructure` 계열
`@DataJpaTest` 22건이 `Not a managed type: Payment`로 실패했다.

**왜**: `@EnableJpaRepositories(basePackages = "com.ticket")`는 `SpringDataPaymentJpaRepository`를
자동으로 주웠지만, 같은 테스트가 공유하는 `com.ticket.core.infra.support.ReadRepositoryTestSupport`의
`@EntityScan`은 기존 module domain 패키지 목록으로 하드코딩돼 있어 `Payment` entity를 Hibernate가
몰랐다.

**막은 방법**: `f8d54281`에서 `ReadRepositoryTestSupport`의 `@EntityScan`에 payment domain 패키지를
추가했다. 새 Application Module을 추가할 때는 그 module의 entity를 Hibernate가 인식하는지
`ModularityTests`뿐 아니라 여러 module 테스트가 공유하는 `@EntityScan`/`@DataJpaTest` 기반 클래스
목록도 함께 갱신해야 한다 — 새 module 자신의 테스트만 통과 확인하면 이 종류의 실패는 놓친다.
