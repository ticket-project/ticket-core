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
 * <p>{@code com.ticket.core}, {@code com.ticket.storage}, {@code com.ticket.support}는 아직 target
 * Application Module로 이동하지 않은 legacy package다. 다른 작업이 이 코드를 계속 줄이는 중이고,
 * 이동이 끝나면 이 package들은 사라진다.
 *
 * <p>{@code com.ticket.bootstrap}은 성격이 다르다 — legacy(언젠가 없어질 코드)가 아니라
 * **composition root/전역 기술 설정 계층**이고, 영구히 남는다. 여러 business module의 internal을
 * 한 번에 봐야만 배선할 수 있는 전역 기술 설정(예: 여러 module의 argument resolver·HTTP
 * client·JWT 설정을 한 곳에서 등록하는 {@code @Configuration})이 이 자리에 속한다 — 그런 코드를
 * 특정 module 소유로 두면 그 module이 나머지 module을 부당하게 참조하게 되므로, 애초에 module
 * 후보에서 빼는 쪽이 맞다. 이 category를 legacy와 같은 predicate로 검증에서 제외하는 이유는
 * legacy와 같다(참조하는 module에 따라 결합이 거짓으로 잡히는 것을 막는다)는 점뿐이고, "언젠가
 * 이동해서 없어진다"는 legacy의 성질은 없다.
 *
 * <p>이 정리 작업 시점에 이전까지 {@code bootstrap.config}에 있던 순수 기술 설정
 * ({@code EventPublicationMaintenance}, {@code SchedulingConfig}, {@code SystemClockConfig})은
 * 실은 어떤 business module의 internal도 보지 않는 domain-free 코드였음이 드러나
 * {@code com.ticket.shared.internal.config}로 옮겼다. 반대로 {@code JpaAuditingConfig}/
 * {@code SecurityContextAuditorAware}(JPA auditing이 채우는 감사자 id)는 identity의 공개 계약
 * {@code AuthenticatedMember}를 참조하는데, {@code shared}에 두면 {@code identity}가 이미
 * {@code shared}를 참조하는 것과 맞물려 module 간 순환(cycle)이 되어 {@code verifiesModuleStructure()}가
 * 실패한다 — 그래서 이 둘은 {@code bootstrap.config}에 남았고, 이게 바로 "여러 module의 internal/공개
 * 계약을 동시에 알아야 하는 코드"의 실제 사례다. {@code WebConfig}/{@code WebSocketConfig}/
 * {@code HttpServiceConfig}/{@code JwtConfig}(현재 {@code com.ticket.core.config}/
 * {@code com.ticket.core.infra.config}에 legacy로 남아 있다)도 identity(그리고
 * {@code WebSocketConfig}는 booking)의 internal을 직접 참조해 이 자리로 옮길 후보이지만, 그러려면
 * 먼저 identity/booking이 각자 필요한 최소 공개 API를 노출해야 한다 — 그 작업은 아직 하지 않은
 * 후속 과제다.
 *
 * <p>기본 {@code direct-sub-packages} 감지 전략은 root 직접 하위 package를 모두 후보 module로
 * 보므로, 이 legacy package들과 {@code bootstrap}을 그대로 두면 서로 얽힌 참조가 닫힌 module
 * 캡슐화 위반으로 잡힌다.
 *
 * <p>legacy package와 {@code bootstrap}을 명시 module로 선언하지 않기 위해,
 * {@link ApplicationModules#of(Class, DescribedPredicate)}로 이 package들의 클래스를 검증 대상에서
 * 제외한다. legacy 코드가 이동을 마치면 그만큼만 이 predicate와 주석에서 지운다 —
 * {@code bootstrap} 관련 제외는 legacy가 모두 사라진 뒤에도 남는다.
 *
 * <p><b>{@code shared}가 class 없이도 module로 잡히는 이유</b> — {@code shared}의
 * {@code package-info.java}는 아직 업무 class가 하나도 없지만 {@code @ApplicationModule}을
 * 선언하고 있다({@code shared} package-info의 javadoc 참고). javac는 애노테이션이 없는
 * {@code package-info.java}에는 {@code package-info.class}를 만들지 않아 Modulith가 그 존재
 * 자체를 볼 수 없는데, annotation이 있으면 class 없이도 감지된다. 그래서
 * {@link ApplicationModules#of(Class, DescribedPredicate)}가 legacy를 뺀 뒤 찾아내는 module은
 * {@code booking}, {@code catalog}, {@code identity}, {@code admission}, {@code showlike},
 * {@code metadata}, {@code shared} 정확히 7개다.
 */
class ModularityTests {

    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "com.ticket.core, com.ticket.storage, com.ticket.support 아래의 아직 이동하지 않은 legacy "
                    + "코드, 그리고 com.ticket.bootstrap의 영구 composition-root 코드",
            ModularityTests::isLegacy);

    /** 검증에서 빠지는 package 이름. {@code bootstrap}이 legacy와 같은 목록에 있는 이유는 클래스 javadoc 참고. */
    private static final Set<String> LEGACY_PACKAGE_NAMES = Set.of("core", "bootstrap", "storage", "support");

    /** 파일시스템 기준으로 선언된 7개 module package다. {@code shared}가 왜 여기 있는지는 클래스 javadoc 참고. */
    private static final Set<String> DECLARED_MODULE_PACKAGES = Set.of(
            "booking", "catalog", "identity", "admission", "showlike", "metadata", "shared");

    /**
     * 승인된 module 의존 DAG다. 각 module이 나머지 module 중 실제로 직접 참조하는 module 이름
     * 집합이다 — {@code @ApplicationModule(allowedDependencies = ...)}가 선언한 상한이 아니라
     * (catalog/identity/admission은 상한을 비워 둬 "제한 없음"을 뜻한다), 실제로 관측되는 edge를
     * 여기 고정해 새 module 간 결합이 조용히 늘어나는 것을 잡는다. admission은 다른 module을
     * 참조하지 않는 기반 module이다. {@code shared}는 {@code RequiredInput}(booking/catalog/
     * identity/showlike가 참조)·{@code CursorPage}(catalog가 참조) 같은 순수 범용 유틸리티와
     * {@code UuidSupplier}(identity가 참조)·{@code SwaggerConfig}·{@code QuerydslConfig} 등
     * domain-free 기술 설정만 담아 다른 어떤 module도 참조하지 않는 leaf고, 그래서 이 module들이
     * shared를 향한 edge를 갖는다. identity의 공개 계약을 참조하는 {@code SecurityContextAuditorAware}는
     * 그래서 shared가 아니라 {@code bootstrap}에 있다(클래스 javadoc 참고) — shared에 두면
     * identity·shared가 서로를 향하는 순환이 된다.
     */
    private static final Map<String, Set<String>> APPROVED_DEPENDENCY_DAG = Map.of(
            "booking", Set.of("catalog", "identity", "admission", "shared"),
            "catalog", Set.of("shared"),
            "identity", Set.of("shared"),
            "admission", Set.of(),
            "showlike", Set.of("catalog", "identity", "shared"),
            "metadata", Set.of("catalog", "booking", "identity"),
            "shared", Set.of()
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
