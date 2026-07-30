# Datadog Docker Compose Design

> 보관 문서: Datadog 도입 당시의 설계 기록입니다. 파일 링크는 현재 저장소 위치로 정리했지만, 본문의 배포 구성과 의존성 상태는 당시 기준이므로 현재 운영 절차는 `docs/operations.md`를 우선하세요.

## 목표

`oneticket.site` 운영 서버의 Docker Compose 환경에 Datadog을 도입해 아래 항목을 한 번에 관측 가능하게 만든다.

- `ticket-be` 애플리케이션 메트릭
- `ticket-be` 애플리케이션 로그
- `ticket-be` APM trace
- Docker/호스트 인프라 메트릭

## 현재 구조

- 애플리케이션은 Ubuntu 서버에서 `docker compose`로 배포된다.
- 당시 저장소의 `docker-compose.yml`은 로컬 Redis만 포함했고, 실제 운영 배포는 GitHub Actions가 서버의 `/home/ubuntu/docker-compose.yml`을 사용했다. 해당 로컬 Compose 파일은 현재 저장소에는 없다.
- [Dockerfile](../../../Dockerfile)은 Spring Boot fat jar만 복사해 실행한다.
- [logback-prod.xml](../../../support/logging/src/main/resources/logback/logback-prod.xml)은 stdout 로그를 사용하고 `traceId`, `spanId` MDC 자리를 이미 포함한다.
- [core-api/build.gradle](../../../core/core-api/build.gradle)에는 당시 Actuator/Prometheus 의존성이 없었다.

## 설계

### 1. Agent 중심 수집 구조를 사용한다

- 운영 compose에 `datadog-agent` 컨테이너를 추가한다.
- `ticket-be` 컨테이너는 Datadog SaaS와 직접 통신하지 않고 같은 compose 네트워크의 `datadog-agent`로 trace를 보낸다.
- 메트릭과 로그도 Agent가 수집해 Datadog으로 전송한다.

### 2. 애플리케이션은 관측 데이터만 노출한다

- Spring Boot는 `/actuator/prometheus`를 노출해 Agent가 scrape 가능하게 만든다.
- stdout 로그는 그대로 유지하고 Agent가 Docker 로그를 수집한다.
- Java APM은 `dd-java-agent.jar`를 이미지에 포함하고, 운영 compose에서 `-javaagent`를 활성화한다.

### 3. 운영 compose가 최종 제어 지점이 된다

- Datadog API key, site, `DD_SERVICE`, `DD_ENV`, `DD_VERSION`은 운영 compose 또는 서버 환경변수에서 관리한다.
- Docker socket, `/proc`, `/sys/fs/cgroup` 등을 Agent 컨테이너에 마운트해 Docker/호스트 메트릭을 수집한다.
- 서비스/환경 태그는 `ticket-be`, `prod` 기준으로 통일한다.

## 코드 변경 범위

- [core/core-api/build.gradle](../../../core/core-api/build.gradle)
  - Actuator/Prometheus 의존성 추가
- [core/core-api/src/main/resources/application.yml](../../../core/core-api/src/main/resources/application.yml)
  - `health`, `info`, `prometheus` endpoint 노출
- [core/core-api/src/main/java/com/ticket/core/config/security/SecurityConfig.java](../../../core/core-api/src/main/java/com/ticket/core/config/security/SecurityConfig.java)
  - actuator endpoint 허용
- [Dockerfile](../../../Dockerfile)
  - `dd-java-agent.jar` 포함

## 운영 변경 범위

- 서버 `/home/ubuntu/docker-compose.yml`에 `datadog-agent` 추가
- `ticket-be` 컨테이너에 Datadog 환경변수와 `JAVA_TOOL_OPTIONS` 추가
- Datadog Agent에 Docker/host/APM/log collection 활성화

## 리스크와 대응

- `dd-java-agent` 경로 오류로 앱 기동 실패 가능
  - 이미지에 jar를 명시적으로 포함하고 compose에서만 활성화한다.
- `/actuator/prometheus`가 Spring Security에 막힐 수 있다.
  - actuator 경로만 최소 허용한다.
- 로그/트레이스 상관관계가 깨질 수 있다.
  - `service`, `env`, `version` 값을 compose에서 단일 기준으로 맞춘다.
