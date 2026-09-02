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
 * <p>{@code com.ticket.core}, {@code com.ticket.bootstrap}, {@code com.ticket.storage},
 * {@code com.ticket.support}는 아직 target Application Module로 이동하지 않은 legacy package다.
 * 기본 {@code direct-sub-packages} 감지 전략은 root 직접 하위 package를 모두 후보 module로 보므로,
 * 이 legacy package들을 그대로 두면 서로 얽힌 참조가 닫힌 module 캡슐화 위반으로 잡힌다.
 *
 * <p>이 legacy package를 임시 명시 module로 선언하지 않기 위해(그 자체가 이후 이동 Task가 할 일이다),
 * {@link ApplicationModules#of(Class, DescribedPredicate)}로 legacy package의 클래스를 검증 대상에서
 * 제외한다. 실제 코드가 이동을 마치면 이 predicate와 함께 이 주석도 지운다.
 *
 * <p><b>"7개 module"과 "6개 module"이 둘 다 등장하는 이유</b> — {@code com.ticket} 직속에는 7개
 * module package({@code booking}, {@code catalog}, {@code identity}, {@code admission},
 * {@code showlike}, {@code metadata}, {@code shared})가 선언돼 있다({@link #모듈_package가_com_ticket_직속에_정확히_7개_선언돼_있다()}
 * 참고, 파일시스템 기준). 그런데 {@code shared}의 {@code package-info.java}는 아직 아무 애노테이션도
 * 없는 순수 문서 주석뿐이다({@code shared} package-info의 javadoc 참고 — 그 안에 실제 계약이 생기면
 * {@code @Modulith(sharedModules = "shared")}로 선언하기로 이미 정해져 있고, 그때까지는 일부러
 * {@code @ApplicationModule}을 붙이지 않는다). javac는 애노테이션이 하나도 없는
 * {@code package-info.java}에 대해 {@code package-info.class}를 만들지 않는다 — 즉 {@code shared}는
 * 컴파일 산출물이 전혀 없어 ArchUnit/Modulith가 애초에 그 package의 존재를 볼 수 없다. 그래서
 * {@link ApplicationModules#of(Class, DescribedPredicate)}가 실제로 찾아내는 module은 6개이고, DAG·open
 * module 검사는 이 6개를 대상으로 한다. {@code shared}가 내용을 갖게 되는 후속 Task에서 두 숫자는
 * 다시 7로 맞아떨어진다.
 */
class ModularityTests {

    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "com.ticket.core, com.ticket.bootstrap, com.ticket.storage, com.ticket.support 아래의 "
                    + "아직 이동하지 않은 legacy 코드",
            ModularityTests::isLegacy);

    private static final Set<String> LEGACY_PACKAGE_NAMES = Set.of("core", "bootstrap", "storage", "support");

    /** 파일시스템 기준으로 선언된 7개 module package다. {@code shared}가 왜 여기 있는지는 클래스 javadoc 참고. */
    private static final Set<String> DECLARED_MODULE_PACKAGES = Set.of(
            "booking", "catalog", "identity", "admission", "showlike", "metadata", "shared");

    /**
     * 승인된 module 의존 DAG다. 각 module이 나머지 module 중 실제로 직접 참조하는 module 이름
     * 집합이다 — {@code @ApplicationModule(allowedDependencies = ...)}가 선언한 상한이 아니라
     * (catalog/identity/admission은 상한을 비워 둬 "제한 없음"을 뜻한다), 실제로 관측되는 edge를
     * 여기 고정해 새 module 간 결합이 조용히 늘어나는 것을 잡는다. catalog/identity/admission은
     * 다른 module을 참조하지 않는 기반 module이다. {@code shared}는 클래스 javadoc에서 설명한
     * 이유로 Modulith가 지금은 찾아내지 못해 이 표에서 뺐다.
     */
    private static final Map<String, Set<String>> APPROVED_DEPENDENCY_DAG = Map.of(
            "booking", Set.of("catalog", "identity", "admission"),
            "catalog", Set.of(),
            "identity", Set.of(),
            "admission", Set.of(),
            "showlike", Set.of("catalog", "identity"),
            "metadata", Set.of("catalog", "booking", "identity")
    );

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES).verify();
    }

    @Test
    void 모듈_package가_com_ticket_직속에_정확히_7개_선언돼_있다() {
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
                .as("모든 module은 CLOSED(internal package 캡슐화)여야 한다")
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
