package com.ticket;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulith 구조 검증이다.
 *
 * <p>이 클래스는 세 가지를 확인한다: (1) 모든 Application Module이 CLOSED(하위 package 캡슐화)
 * 상태인지, (2) {@code com.ticket} 직속 module package 목록이 선언된 목록과 일치하는지,
 * (3) module 간 실제 의존이 승인된 DAG와 일치하는지. module 구성과 역사(BC 재편, module 신설·흡수·
 * 제거 이력)는 {@code docs/architecture.md}와 {@code docs/adr/0003-spring-modulith-application-module-boundaries.md}
 * / {@code docs/adr/0006-bounded-context-module-boundaries.md} / {@code docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md}가
 * 원본이다.
 *
 * <p>{@code shared}의 {@code package-info.java}가 class가 없던 시점부터 {@code @ApplicationModule}을
 * 명시 선언해 온 이유는 {@code shared} package-info의 javadoc 참고(javac가 애노테이션 없는
 * {@code package-info.java}는 class 파일을 만들지 않아 Modulith가 존재 자체를 못 본다).
 */
class ModularityTests {

    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "현재 com.ticket 아래에 남아 있지 않은 legacy/composition-root 예외 목록(현재는 매치 없음)",
            ModularityTests::isLegacy);

    /** 검증에서 빠지는 package 이름. core/storage/support/bootstrap은 모두 이동이 끝나 현재 존재하지 않는다. */
    private static final Set<String> LEGACY_PACKAGE_NAMES = Set.of("core", "bootstrap", "storage", "support");

    /** 파일시스템 기준으로 선언된 11개 module package다. {@code shared}가 왜 여기 있는지는 클래스 javadoc 참고. */
    private static final Set<String> DECLARED_MODULE_PACKAGES = Set.of(
            "booking", "show", "venue", "favorite", "member", "shared", "web", "config",
            "error", "seed", "payment");

    /**
     * 승인된 module 의존 DAG다. 각 module이 실제로 직접 참조하는 module 이름 집합이며,
     * {@code @ApplicationModule(allowedDependencies = ...)}가 선언한 상한이 아니라 관측되는 edge를
     * 여기 고정해 결합이 조용히 늘어나는 것을 잡는다(venue/member/favorite/payment는
     * {@code allowedDependencies}가 비어 있다 — {@code sharedModules}인 shared·error·web은
     * 비워 두어도 항상 허용된다). 각 edge의 근거는 참조하는 module의 package-info와
     * ADR 0006을 원본으로 본다.
     */
    private static final Map<String, Set<String>> APPROVED_DEPENDENCY_DAG = Map.ofEntries(
            Map.entry("booking", Set.of("show", "member", "shared", "web", "error")),
            Map.entry("show", Set.of("venue", "favorite", "member", "shared", "web", "error")),
            Map.entry("venue", Set.of()),
            Map.entry("favorite", Set.of("shared", "web", "error")),
            Map.entry("member", Set.of("shared", "web", "error")),
            Map.entry("shared", Set.of()),
            Map.entry("web", Set.of()),
            Map.entry("config", Set.of("member", "shared")),
            Map.entry("error", Set.of("web")),
            Map.entry("seed", Set.of("member")),
            Map.entry("payment", Set.of())
    );

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES).verify();
    }

    @Test
    void 모듈_package가_com_ticket_직속에_선언된_목록과_일치한다() {
        final Path ticketRoot = Path.of("src", "main", "java", "com", "ticket");

        try (Stream<Path> children = Files.list(ticketRoot)) {
            final Set<String> declaredModulePackages = children
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("package-info.java")))
                    .map(dir -> dir.getFileName().toString())
                    .filter(name -> !LEGACY_PACKAGE_NAMES.contains(name))
                    .collect(Collectors.toSet());

            assertThat(declaredModulePackages).containsExactlyInAnyOrderElementsOf(DECLARED_MODULE_PACKAGES);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void module_dependency는_승인된_DAG와_일치한다() {
        final ApplicationModules modules = ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES);

        final Set<String> moduleNames = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());
        assertThat(moduleNames)
                .as("legacy를 뺀 뒤 Modulith가 실제로 찾아낸 module 집합 (shared는 클래스 javadoc 참고)")
                .containsExactlyInAnyOrderElementsOf(APPROVED_DEPENDENCY_DAG.keySet());

        for (final ApplicationModule module : modules) {
            final String moduleName = module.getIdentifier().toString();
            final Set<String> actualDependencies = module.getDirectDependencies(modules).uniqueModules()
                    .map(dependency -> dependency.getIdentifier().toString())
                    .collect(Collectors.toSet());

            assertThat(actualDependencies)
                    .as("%s module의 직접 의존", moduleName)
                    .containsExactlyInAnyOrderElementsOf(APPROVED_DEPENDENCY_DAG.get(moduleName));
        }
    }

    @Test
    void open_module은_하나도_없다() {
        final ApplicationModules modules = ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES);

        assertThat(modules.stream().filter(ApplicationModule::isOpen))
                .as("모든 module은 CLOSED(하위 package 캡슐화)여야 한다")
                .isEmpty();
    }

    private static boolean isLegacy(final JavaClass javaClass) {
        final String packageName = javaClass.getPackageName();
        return packageName.equals("com.ticket.core") || packageName.startsWith("com.ticket.core.")
                || packageName.equals("com.ticket.bootstrap") || packageName.startsWith("com.ticket.bootstrap.")
                || packageName.equals("com.ticket.storage") || packageName.startsWith("com.ticket.storage.")
                || packageName.equals("com.ticket.support") || packageName.startsWith("com.ticket.support.");
    }
}
