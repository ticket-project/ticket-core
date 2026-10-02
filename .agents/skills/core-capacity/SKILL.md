---
name: core-capacity
description: Core 수용량 부하 사다리를 돌리고, 문제가 나오면 멈춰서 근거·원인 후보·코드 위치·해결 선택지를 보고한다
disable-model-invocation: true
argument-hint: "[local|prod] [재측정|한계]"
---

# Core 수용량 측정

목적은 둘이다. Queue가 Core에 1초에 들여보낼 새 예매 사용자 수(입장률)를 찾는 것, 그리고 사용자가 병목을 직접 고치며 배우는 것.
그래서 역할이 나뉜다.

- **Claude**: 부하를 돌리고, 문제를 찾고, 근거·원인 후보·코드 위치·해결 선택지를 보고한다.
- **사용자**: 원인을 확인하고, 고칠지와 어떻게 고칠지 정하고, 직접 고친다.

Claude가 고치는 범위는 gatling-test의 측정 도구(시나리오, `scripts/capacity`, 판정)다. 고쳤으면 무엇이 왜 틀렸는지 보고에 쓴다.
Core 코드, migration·인덱스, DB 데이터, Core 설정(연결 풀·스레드 포함)은 보고 대상이다. 풀 크기를 바꿔 보는 실험도 사용자가 고른 뒤에 한다.

경로는 ticket 저장소 루트 기준이다. gatling-test는 형제 저장소 `../gatling-test`다.

원본 문서(여기에 옮기지 않는다):

- 측정 순서, 판정 기준, 입장률 계산: [core-capacity.md](../../../docs/core-capacity.md)
- 스크립트 사용법, 유효성(INVALID) 규칙, 측정 규칙(예열, 반복, 한 번에 하나만 바꾸기): `../gatling-test/scripts/capacity/README.md`
- 문제 목록(P-NNN), 성능 변화 표, 일지 틀: `../gatling-test/docs/capacity-log/README.md`

## 대상

- 인자가 없거나 `local`: [local.md](local.md)를 읽는다. localhost 실행은 이 스킬 호출로 승인된 것이다.
- `prod`이거나 대상이 localhost가 아니다: [production.md](production.md)를 읽는다. 실행마다 승인을 받는다.
  그 파일의 "아직 정하지 않은 것"이 남아 있으면 실행하기 전에 사용자와 먼저 정한다.

## 단계

### 1. 어디까지 왔는지 본다

capacity-log README의 문제 목록, 가장 최근 일지, `../gatling-test/distributed-results-join/capacity/steps.md` 끝부분을 읽는다.

- 인자 `재측정`: 사용자가 코드를 고쳤다. 마지막 FAIL 부하부터 잰다.
- 인자 `한계`: 사용자가 마지막 FAIL을 고치지 않고 한계로 본다. 5단계로 간다.

끝: 시작할 부하, 결정 대기 중인 문제, 측정할 Core commit을 한 줄로 말했다.

### 2. 환경을 준비한다

대상 파일(local.md)의 준비 절차를 따른다. 끝: 예열 실행에서 기술 실패와 비즈니스 거절이 0이었고, 그 숫자는 버렸다.

### 3. 사다리를 오른다

03 고정 조건 30초를 5 → 10 → 15 → 20 → 30 → 40 → 50 → 60 → 80 → 100 users/sec로 잰다. 실행마다 새 회차를 쓴다.
같은 Core commit·데이터 조건에서 이미 PASS한 부하는 건너뛴다. 판정은 `run-step.ps1`이 낸 verdict다.

| verdict (종료 코드) | 다음 행동 |
| --- | --- |
| PASS (0) | 다음 부하 |
| FAIL (1) | 같은 부하를 새 회차로 한 번 더 잰다(**재현**). 결과와 상관없이 그 부하는 FAIL이다. 이어서 **진단 실행**을 한 번 하고 4단계로 간다 |
| INVALID (3) | 한 번 더 잰다. 또 INVALID면 측정 도구 문제다. 고칠 수 있으면 고치고 이유를 적은 뒤 그 부하부터 다시 잰다. 못 고치면 4단계로 간다 |
| ABORT (2) | 메시지대로 준비를 고친다. 회차가 이미 쓰였으면 다음 회차를 쓴다 |

끝: FAIL 또는 못 고친 INVALID가 나왔다. 아니면 100 users/sec까지 PASS했다(그 사실을 보고하고 멈춘다).

### 4. 진단하고 보고한다

아래 "진단"을 하고 "보고 형식"대로 보고한다. 보고 전에 일지를 쓴다. 보고하면 멈추고 사용자의 결정을 기다린다.

끝: 원인 후보마다 지지하는 근거, 반대 근거, 코드 위치가 있다. 확인하지 못한 가정은 "미확인"으로 표시했다.

### 5. 한계를 좁힌다

사용자가 FAIL을 한계로 보기로 하면, 마지막 PASS와 첫 FAIL 사이를 반씩 좁힌다. 차이가 2 이하이거나 마지막 PASS의
10% 이하가 되면 마지막 PASS가 후보값 C다. C를 새 회차로 두 번 더 재서 세 번 모두 PASS면 확정한다.

끝: C와 그 조건(commit, 풀, 데이터량, 회차 좌석 수)을 일지와 성능 변화 표에 적고 보고했다. 장시간 실행(03-3)과 Queue를 켠
확인은 core-capacity.md 7~9단계이고 아직 `scripts/capacity`에 없다. 다음 할 일로 보고한다.

## 진단

목표는 "어느 API의 어느 부분이 왜 느린가"를 근거와 함께 말하는 것이다. FAIL 실행, 직전 PASS 실행, 진단 실행의 결과 폴더를 쓴다.

1. **어디가 먼저 느려졌나.** 직전 PASS와 FAIL의 `summary.json`을 나란히 놓는다. 요청별 Core p95/p99(`coreRequests`),
   Hikari active·pending 최대, Tomcat busy 최대, CPU 최대, 실행 전 주문 수.
   - 모든 요청이 함께 느려졌다 → 공용 자원(DB 연결, 요청 스레드, CPU, GC)부터 본다.
   - 한 요청만 느려졌다 → 그 요청의 코드와 쿼리부터 본다.
2. **전후 지표 차이.** `prometheus-before.txt`와 `prometheus-after.txt`의 `_sum`·`_count` 차이로 평균을 낸다.
   - `hikaricp_connections_usage_seconds`: 연결을 한 번 빌려 쥔 평균 시간. 쿼리 시간보다 훨씬 길면 트랜잭션이 DB 밖의 일을 감싸고 있다.
   - `hikaricp_connections_acquire_seconds`: 연결을 얻기까지 기다린 평균 시간.
   - `spring_data_repository_invocations_seconds`(`repository`, `method` 태그): 저장소 메서드별 호출 수와 평균. 호출 수를
     HTTP 요청 수로 나눠 요청 하나당 호출 수를 본다. 저장소 인터페이스를 거치지 않는 쿼리(직접 만든 `JPAQueryFactory` 등)는 여기 없다.
   - `jvm_gc_pause_seconds`: Core GC.
3. **스레드 덤프.** 진단 실행의 `thread-dump-<n>.txt`에서 요청 스레드가 어디서 기다리는지 센다(읽는 법은 대상 파일).
4. **로그.** 기술 실패가 있으면 Core 로그에서 그 시간대의 ERROR·WARN과 예외를 찾는다.
5. **코드.** 원인 후보마다 컨트롤러 → use case → 저장소·쿼리 → migration의 인덱스 순으로 읽어 위치를 찾는다.
   [query-performance-review](../query-performance-review/SKILL.md)의 Checks를 점검표로 쓴다.

이미 문제 목록에 있는 문제면 새 번호를 만들지 않고 그 번호에 이번 근거를 더한다.

## 보고 형식

쉬운 말 요약 2~3줄을 먼저 쓴다(무엇을 쟀고, 어디서 막혔고, 무엇을 정해야 하는지). 그다음 문제마다:

```markdown
### P-NNN 한 줄 제목 (예: 초당 30명에서 주문 생성이 DB 연결을 기다린다)

**증상**: 어느 부하에서, 어느 요청이, 직전 PASS보다 얼마나
**근거**: 수치·덤프 집계·로그. 결과 폴더 이름을 함께 쓴다
**원인 후보** (가능성 높은 순)
1. 원인 — `파일:줄` — 지지하는 근거 / 반대 근거·미확인 — 사용자가 확인하는 방법
**해결 선택지**
- 선택지 — 장점 / 단점 / 다시 잴 때 기대하는 변화
**다음 실행**: 사용자가 고르면 무엇을 어떤 조건으로 다시 잴지
```

코드 위치는 처음부터 알려준다(사용자 결정 2026-10-02). 해결은 선택지와 장단점으로 말하고, 고치는 코드는 사용자가 쓴다.
P-NNN은 문제 목록의 다음 번호다. 목록에 "결정 대기"로 올린다.

## 일지

`../gatling-test/docs/capacity-log/YYYY-MM-DD-주제.md`에 날마다 하나 쓴다. 틀은 capacity-log README에 있다.

- 실행 표는 steps.md의 줄을 옮긴다. 예열·재현·진단 실행도 빼지 않는다.
- 틀린 가설과 버린 실행도 이유와 함께 남긴다.
- 같은 조건으로 고치기 전과 후를 쟀으면 README의 성능 변화 표를 고친다.
