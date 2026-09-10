package com.ticket.seed;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 시드 테스트가 보는 저장소 경로다. {@code seedTest} 작업이 넘겨주는 {@code seed.project-dir}를
 * 우선 쓰고, 없으면 현재 작업 디렉터리를 저장소 루트로 본다(IDE 실행).
 */
final class SeedTestPaths {

    private SeedTestPaths() {
    }

    static Path projectDir() {
        final String configured = System.getProperty("seed.project-dir");
        final Path candidate = configured == null || configured.isBlank()
                ? Path.of("").toAbsolutePath()
                : Path.of(configured);
        return candidate.toAbsolutePath().normalize();
    }

    /** 테스트용 최소 시드 SQL이다. 실행 경로·완전성 판정을 빠르게 확인할 때 쓴다. */
    static Path minimalSeedSql() {
        final Path sqlPath = projectDir()
                .resolve(Path.of("seed", "src", "test", "resources", "sql", "minimal-curated.sql"));
        if (!Files.isRegularFile(sqlPath)) {
            throw new IllegalStateException("테스트용 시드 SQL을 찾을 수 없습니다: " + sqlPath);
        }
        return sqlPath;
    }

    static Path seedSql() {
        final Path sqlPath = projectDir().resolve("seed").resolve("sql").resolve("kopis-curated.sql");
        if (!Files.isRegularFile(sqlPath)) {
            throw new IllegalStateException("시드 SQL을 찾을 수 없습니다: " + sqlPath);
        }
        return sqlPath;
    }
}
