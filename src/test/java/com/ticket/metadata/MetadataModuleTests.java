package com.ticket.metadata;

import com.ticket.booking.BookingMetadata;
import com.ticket.catalog.CatalogMetadata;
import com.ticket.member.MemberMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.mockito.Mockito.mock;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다.
 * {@code CatalogModuleTests}와 같은 이유로, 이 STANDALONE 테스트에서 기본값(true)으로 다시
 * {@code verify()}를 돌리면 아직 legacy {@code com.ticket.core} 아래에 남은 코드와의 임시 결합이
 * 오탐될 수 있어 자동 검증을 꺼 둔다.
 *
 * <p>metadata는 자체 도메인이 없고 {@link CatalogMetadata}/{@link BookingMetadata}/
 * {@link MemberMetadata}만 조합한다. STANDALONE bootstrap은 {@code com.ticket.metadata}
 * package tree만 component-scan하므로 이 세 계약의 실제 구현(catalog/booking/member 소유)은
 * 스캔되지 않는다 — 각 module의 infra까지 띄우지 않고 이 module만 독립적으로 기동됨을 확인하기
 * 위해, 세 계약의 Mockito mock을 bean으로 채워 넣는다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@Import(MetadataModuleTests.TestPublicApiConfig.class)
class MetadataModuleTests {

    @Test
    void bootstraps() {
    }

    @TestConfiguration
    static class TestPublicApiConfig {

        @Bean
        CatalogMetadata catalogMetadata() {
            return mock(CatalogMetadata.class);
        }

        @Bean
        BookingMetadata bookingMetadata() {
            return mock(BookingMetadata.class);
        }

        @Bean
        MemberMetadata memberMetadata() {
            return mock(MemberMetadata.class);
        }
    }
}
