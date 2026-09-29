package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * vendor별 migration({@code db/migration-vendor/h2}, {@code db/migration-vendor/oracle})이 같은 상대 경로의 파일 쌍인지 고정한다.
 *
 * <p>테스트는 H2 쪽으로, 운영은 Oracle 쪽으로 스키마를 만든다. 한쪽에만 migration을 더하면 테스트는 통과하는데 운영 스키마만 달라진다.
 */
@SuppressWarnings("NonAsciiCharacters")
class VendorMigrationPairTest {
    @Test
    void h2와_oracle_vendor_migration은_같은_파일_이름의_쌍이다() throws IOException {
        final Set<String> h2 = relativePaths("h2");

        assertThat(h2).isNotEmpty();
        assertThat(relativePaths("oracle")).containsExactlyInAnyOrderElementsOf(h2);
    }

    private static Set<String> relativePaths(final String vendor) throws IOException {
        final String root = "db/migration-vendor/" + vendor + "/";
        final Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath*:" + root + "**/*.sql");
        return Arrays.stream(resources)
                .map(resource -> {
                    try {
                        final String url = resource.getURL().toString();
                        return url.substring(url.lastIndexOf(root) + root.length());
                    } catch (final IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .collect(Collectors.toSet());
    }
}
