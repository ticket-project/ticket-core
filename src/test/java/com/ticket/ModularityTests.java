package com.ticket;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modulith 구조 검증이다.
 *
 * <p>{@code com.ticket.core}, {@code com.ticket.bootstrap}, {@code com.ticket.storage},
 * {@code com.ticket.support}는 아직 target Application Module로 이동하지 않은 legacy package다.
 * 기본 {@code direct-sub-packages} 감지 전략은 root 직접 하위 package를 모두 후보 module로 보므로,
 * 이 legacy package들을 그대로 두면 서로 얽힌 참조가 닫힌 module 캡슐화 위반으로 잡힌다.
 *
 * <p>이 legacy package를 임시 명시 module로 선언하지 않기 위해(그 자체가 이후 이동 Task가 할 일이다),
 * {@link ApplicationModules#of(Class, DescribedPredicate)}로 legacy package의 클래스를 검증 대상에서
 * 제외한다. 실제 코드가 이동을 마치면 이 predicate와 함께 이 주석도 지운다. 그 전까지는 정확한 module
 * 집합을 단정하지 않고 {@code verify()} 자체만 항상 통과시킨다.
 */
class ModularityTests {

    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "com.ticket.core, com.ticket.bootstrap, com.ticket.storage, com.ticket.support 아래의 "
                    + "아직 이동하지 않은 legacy 코드",
            ModularityTests::isLegacy);

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES).verify();
    }

    private static boolean isLegacy(final JavaClass javaClass) {
        final String packageName = javaClass.getPackageName();
        return packageName.equals("com.ticket.core") || packageName.startsWith("com.ticket.core.")
                || packageName.equals("com.ticket.bootstrap") || packageName.startsWith("com.ticket.bootstrap.")
                || packageName.equals("com.ticket.storage") || packageName.startsWith("com.ticket.storage.")
                || packageName.equals("com.ticket.support") || packageName.startsWith("com.ticket.support.");
    }
}
