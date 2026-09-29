package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ticket.seed.support.AppSchema;

/**
 * {@link AppSchema}가 만든 스키마가 시드의 적재 전 점검({@link SeedPreconditions})을 통과하는지 확인한다. 이 테스트가 깨지면 다른 시드 테스트의 전제가 무너지므로 가장 먼저
 * 본다.
 *
 * <p>필요한 테이블·{@code MEMBERS} 컬럼 목록은 {@link SeedPreconditions}가 원본이다. 여기 따로 적지 않는다. 이 테스트는 {@code SeedPreconditions}가
 * package-private이라 {@code support}가 아니라 이 package에 둔다.
 */
@SuppressWarnings("NonAsciiCharacters")
class AppSchemaTest {
    @Test
    void 시드가_쓰는_테이블과_MEMBERS_컬럼을_모두_만든다(@TempDir final Path tempDir) {
        final String jdbcUrl = AppSchema.createIn(tempDir, "app-schema");

        assertThatCode(() -> SeedPreconditions.verify(new DriverManagerDataSource(jdbcUrl, "sa", ""), jdbcUrl))
                .doesNotThrowAnyException();
    }
}
