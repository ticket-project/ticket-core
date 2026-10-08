package com.ticket.shared.config;

import java.sql.Types;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * 과거 H2({@code MODE=Oracle}) 스키마를 검증하는 테스트 전용 dialect다. 서비스 프로파일은 PostgreSQL을 쓴다.
 *
 * <p>H2의 Oracle 호환 모드는 Oracle처럼 {@code DATE}를 {@code TIMESTAMP(0)}으로 저장한다. {@code OracleDialect}는 둘을 같은 타입으로 보지만
 * {@link H2Dialect}는 아니라서, migration으로 만든 {@code shows.start_date}를 {@code LocalDate} 매핑과 다르다고 판정한다. 그 한 쌍만 같은 타입으로
 * 인정한다.
 */
public class H2OracleModeDialect extends H2Dialect {
    public H2OracleModeDialect(final DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public boolean equivalentTypes(final int typeCode1, final int typeCode2) {
        return super.equivalentTypes(typeCode1, typeCode2)
                || isDateOrTimestamp(typeCode1) && isDateOrTimestamp(typeCode2);
    }

    private static boolean isDateOrTimestamp(final int typeCode) {
        return typeCode == Types.DATE || typeCode == Types.TIMESTAMP;
    }
}
