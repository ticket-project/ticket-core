package com.ticket.core.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class CoreDomainModuleStructureTest {

    @Test
    void core_domain_모듈은_주문과_큐_비즈니스를_소유해야_한다() {
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/order"))).isTrue();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/queue"))).isTrue();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/domain/order"))).isFalse();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/domain/queue"))).isFalse();
    }

    @Test
    void core_api는_core_app을_거쳐_도메인에_닿아야_한다() throws Exception {
        final String apiBuild = Files.readString(resolve("../core-api/build.gradle"));

        assertThat(apiBuild).contains("implementation project(':core:core-app')");
        assertThat(apiBuild).doesNotContain("    implementation project(':core:core-domain')");
        assertThat(apiBuild).contains("testImplementation project(':core:core-domain')");
    }

    @Test
    void core_enum_모듈은_제거되고_enum은_core_domain에_존재해야_한다() throws Exception {
        final String settings = Files.readString(resolve("../../settings.gradle"));
        final String apiBuild = Files.readString(resolve("../core-api/build.gradle"));
        final String domainBuild = Files.readString(resolve("build.gradle"));

        assertThat(settings).doesNotContain("'core:core-enum'");
        assertThat(apiBuild).doesNotContain("project(':core:core-enum')");
        assertThat(domainBuild).doesNotContain("project(':core:core-enum')");
        assertThat(Files.exists(resolve("../core-enum"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/member/model/Role.java"))).isTrue();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/show/BookingStatus.java"))).isTrue();
    }

    @Test
    void jwt_구현은_core_infra에_있고_core_domain에는_남지_않아야_한다() throws Exception {
        final String infraBuild = Files.readString(resolve("../core-infra/build.gradle"));
        final String domainBuild = Files.readString(resolve("build.gradle"));

        // 토큰 발급·검증은 어댑터다. 실행 모듈과 도메인 어디에도 두지 않는다.
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/config/security/JwtTokenService.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/config/security/JwtTokenService.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/config/security/JwtProperties.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/auth/token/JwtTokenService.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/auth/token/JwtProperties.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/auth/token/JwtAuthTokenManager.java"))).isTrue();

        // 인증 필터는 구현이 아니라 포트를 본다.
        assertThat(Files.exists(resolve("../core-app/src/main/java/com/ticket/core/app/auth/token/AccessTokenReader.java"))).isTrue();

        // OAuth2 엔드포인트 상수는 security filter chain 설정의 일부라 core-api에 남는다.
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/config/security/OAuth2EndpointConstants.java"))).isTrue();

        assertThat(infraBuild).contains("io.jsonwebtoken:jjwt-api:0.13.0");
        assertThat(domainBuild).doesNotContain("io.jsonwebtoken");
        // API는 토큰 라이브러리를 보지 않는다. 예외 중립화는 어댑터가 한다.
        assertThat(Files.readString(resolve("../core-api/build.gradle"))).doesNotContain("io.jsonwebtoken");
    }

    @Test
    void core_domain은_swagger_의존을_직접_가지지_않아야_한다() throws Exception {
        final String domainBuild = Files.readString(resolve("build.gradle"));

        assertThat(domainBuild).doesNotContain("springdoc-openapi");
        assertThat(findSwaggerImports(resolve("src/main/java"))).isEmpty();
    }

    @Test
    void 직접_Output으로_대체한_response_파일은_core_domain에_남지_않아야_한다() {
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/AuthLoginResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/OrderDetailResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/PerformanceScheduleListResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/SeatAvailabilityResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/SeatStatusResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/GenreResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/MetaCodesResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowDetailResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowLikeSummaryResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowOpeningSoonDetailResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowSearchResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowSeatResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowSummaryResponse.java"))).isFalse();
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/response/ShowOpeningSoonSummaryResponse.java"))).isFalse();
    }

    @Test
    void http_cookie_유틸리티는_core_api에_있고_core_domain에는_없어야_한다() {
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/support/util/CookieUtils.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/support/util/CookieUtils.java"))).isTrue();
    }

    @Test
    void 백그라운드_트리거는_bootstrap에_있고_처리는_각_계층이_소유해야_한다() {
        // 트리거(@Scheduled)는 실행 모듈에만 둔다.
        assertThat(Files.exists(resolve("../../bootstrap/src/main/java/com/ticket/bootstrap/worker/OrderExpirationTrigger.java"))).isTrue();
        assertThat(Files.exists(resolve("../../bootstrap/src/main/java/com/ticket/bootstrap/worker/HoldOutboxRelayTrigger.java"))).isTrue();

        // 업무 배치는 유스케이스가, 순수 relay는 infra가 소유한다.
        assertThat(Files.exists(resolve("../core-app/src/main/java/com/ticket/core/app/order/command/ExpirePendingOrdersUseCase.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/order/outbox/create/HoldCreationOutboxRelay.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/order/outbox/release/HoldReleaseOutboxRelay.java"))).isTrue();

        // 옛 위치에는 남지 않는다.
        assertThat(Files.exists(resolve("src/main/java/com/ticket/core/domain/order/command/expire/OrderExpirationScheduler.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/order/OrderExpirationScheduler.java"))).isFalse();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/order/HoldReleaseOutboxScheduler.java"))).isFalse();
    }

    @Test
    void 실행_모듈은_bootstrap_하나여야_한다() throws Exception {
        final String settings = Files.readString(resolve("../../settings.gradle"));
        final String bootstrapBuild = Files.readString(resolve("../../bootstrap/build.gradle"));
        final String apiBuild = Files.readString(resolve("../core-api/build.gradle"));

        assertThat(settings).contains("'bootstrap'");
        assertThat(bootstrapBuild).contains("bootJar");
        assertThat(bootstrapBuild).contains("enabled = true");
        assertThat(apiBuild).doesNotContain("bootJar");
        assertThat(Files.exists(resolve("../../bootstrap/src/main/java/com/ticket/TicketApplication.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/CoreApiApplication.java"))).isFalse();
    }

    @Test
    void 시드_러너는_core_infra에_있어야_한다() {
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/seed/SeedDataLoader.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-infra/src/main/java/com/ticket/core/infra/seed/LoadTestFixtureSeeder.java"))).isTrue();
        assertThat(Files.exists(resolve("../core-api/src/main/java/com/ticket/core/config/seed"))).isFalse();
    }

    private List<Path> findSwaggerImports(final Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsSwaggerImport)
                    .toList();
        }
    }

    private boolean containsSwaggerImport(final Path path) {
        try {
            final String content = Files.readString(path);
            return content.contains("io.swagger.v3.oas.annotations")
                    || content.contains("org.springdoc");
        } catch (final IOException exception) {
            throw new IllegalStateException("파일을 읽을 수 없습니다: " + path, exception);
        }
    }

    private Path resolve(final String relativePath) {
        return Path.of(relativePath).normalize();
    }
}
