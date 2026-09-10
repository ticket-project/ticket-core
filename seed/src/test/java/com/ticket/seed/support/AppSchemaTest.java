package com.ticket.seed.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AppSchema}가 실제 앱 매핑으로 시드 대상 테이블을 만드는지 확인한다. 이 테스트가 깨지면
 * 다른 시드 테스트의 전제가 무너지므로 가장 먼저 본다.
 */
@SuppressWarnings("NonAsciiCharacters")
class AppSchemaTest {

    @Test
    void 시드가_쓰는_테이블과_MEMBERS_컬럼을_모두_만든다(@TempDir final Path tempDir) throws Exception {
        final String jdbcUrl = AppSchema.createIn(tempDir, "app-schema");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            final Set<String> tables = new LinkedHashSet<>();
            try (ResultSet resultSet =
                         connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
                }
            }

            assertThat(tables).contains(
                    "CATEGORIES", "GENRES", "PERFORMERS", "VENUES", "SHOWS", "SHOW_GENRES",
                    "PERFORMANCES", "SEATS", "GRADES", "PERFORMANCE_GRADES", "PERFORMANCE_SEATS",
                    "BOOKING_PERFORMANCE_SALES_POLICIES", "MEMBERS");

            final Set<String> memberColumns = new LinkedHashSet<>();
            try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, "MEMBERS", null)) {
                while (resultSet.next()) {
                    memberColumns.add(resultSet.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                }
            }

            assertThat(memberColumns)
                    .as("LoadTestMemberSeeder의 INSERT가 쓰는 컬럼")
                    .contains("EMAIL", "PASSWORD", "NAME", "ROLE", "CREATED_AT", "CREATED_BY", "DELETED_AT");
        }
    }
}
