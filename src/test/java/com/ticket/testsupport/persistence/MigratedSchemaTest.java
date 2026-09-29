package com.ticket.testsupport.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.core.ApplicationModules;

import com.ticket.TicketApplication;

/**
 * {@link MigratedSchema#MODULES_IN_RUNTIME_ORDER}가 Modulith가 계산한 module 순서와 같은지 고정한다. 운영 기동은 그 순서로 module migration을
 * 적용하므로, module을 더하거나 의존을 바꾸고 목록을 고치지 않으면 테스트 스키마가 운영과 다른 순서로 만들어진다.
 *
 * <p>목록을 기동마다 계산하지 않는 이유: {@code ApplicationModules.of}는 클래스 전체를 분석해 느리고, 슬라이스 context마다 부르게 된다.
 */
@SuppressWarnings("NonAsciiCharacters")
class MigratedSchemaTest {
    @Test
    void module_적용_순서가_Modulith의_module_순서와_같다() {
        final var modulithOrder =
                ApplicationModuleIdentifiers.of(ApplicationModules.of(TicketApplication.class)).stream()
                        .map(ApplicationModuleIdentifier::toString)
                        .toList();

        assertThat(MigratedSchema.MODULES_IN_RUNTIME_ORDER).containsExactlyElementsOf(modulithOrder);
    }
}
