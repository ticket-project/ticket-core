package com.ticket.seed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드가 서비스 소스로 다시 섞여 들어가지 않는지 확인한다.
 *
 * <p>시드 코드가 {@code src/main}에 있으면 (1) {@code bootJar}에 실행 코드와 1MB가 넘는 시드 SQL이
 * 들어가고 (2) Spring Modulith가 {@code seed}를 업무 모듈로 다시 탐지한다. 실제 jar 안에 없는지는
 * Gradle {@code verifySeedNotInBootJar} 작업이 {@code bootJar} 산출물을 열어 확인한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ServiceSourceSeparationTest {

    @Test
    void 시드_코드와_시드_SQL은_서비스_소스에_없다() {
        final Path projectDir = SeedTestPaths.projectDir();

        assertThat(projectDir.resolve("src/main/java/com/ticket/seed"))
                .as("시드 실행 코드는 seed/src/main/java에만 둔다")
                .doesNotExist();
        assertThat(projectDir.resolve("src/test/java/com/ticket/seed"))
                .as("시드 테스트는 seed/src/test/java에만 둔다")
                .doesNotExist();
        assertThat(projectDir.resolve("src/main/resources/seed"))
                .as("시드 SQL은 seed/sql에만 둔다 (classpath 리소스로 두면 bootJar에 들어간다)")
                .doesNotExist();
        assertThat(projectDir.resolve("tools/seed-kopis"))
                .as("KOPIS 도구는 seed/kopis로 옮겼다")
                .doesNotExist();

        assertThat(projectDir.resolve("seed/sql/kopis-curated.sql")).isRegularFile();
        assertThat(projectDir.resolve("seed/README.md")).isRegularFile();
        assertThat(projectDir.resolve("seed/kopis/fetch-kopis.mjs")).isRegularFile();
    }

    @Test
    void 기동_시_자동_적재_설정은_어느_프로파일에도_없다() {
        final Path resources = SeedTestPaths.projectDir().resolve("src/main/resources");

        for (final String profile : List.of("application.yml", "application-local.yml",
                "application-dev.yml", "application-prod.yml")) {
            final String content = read(resources.resolve(profile));

            assertThat(content)
                    .as("%s에 app.seed 설정이 남아 있다 — 적재는 seedLocal 명령이 맡는다", profile)
                    .doesNotContain("app.seed", "seed:\n    enabled");
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
