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
 * 한 번에 봐야만 배선할 수 있는 전역 기술 설정이 이 자리에 속한다 — 그런 코드를 특정 module
 * 소유로 두면 그 module이 나머지 module을 부당하게 참조하게 되므로, 애초에 module 후보에서 빼는
 * 쪽이 맞다. 이 category를 legacy와 같은 predicate로 검증에서 제외하는 이유는 legacy와 같다(참조
 * 하는 module에 따라 결합이 거짓으로 잡히는 것을 막는다)는 점뿐이고, "언젠가 이동해서 없어진다"는
 * legacy의 성질은 없다.
 *
 * <p>이 정리 작업 시점에 이전까지 {@code bootstrap.config}에 있던 코드는 실은 둘로 나뉜다는 게
 * 드러났다. {@code EventPublicationMaintenance}/{@code SchedulingConfig}/{@code SystemClockConfig}는
 * 어떤 business module도 참조하지 않는 domain-free 코드라 처음에는 {@code com.ticket.shared}로
 * 옮겼다가, bean을 등록하는 코드는 호출 대상 계약과 성질이 다르고 {@code sharedModules} 선언
 * 때문에 모든 module 테스트에 함께 뜬다는 이유로 {@code com.ticket.config.internal}로 다시
 * 옮겼다({@code com.ticket.shared.SharedModulePurityTest}가 그 규칙을 강제한다).
 * {@code JpaAuditingConfig}/{@code SecurityContextAuditorAware}(identity의 공개 계약
 * {@code AuthenticatedMember} 참조)와, legacy {@code com.ticket.core.config}/
 * {@code com.ticket.core.infra.config}에 있던 {@code WebConfig}/{@code WebSocketConfig}/
 * {@code HttpServiceConfig}/{@code JwtConfig}(각각 identity·booking의 internal을 직접 참조)는
 * 실제로 여러 module의 internal/공개 계약을 동시에 알아야 하는 코드다. 이 여섯 개는
 * {@code bootstrap}에 두는 대신 별도의 정식 8번째 module {@code com.ticket.config}로 옮기고,
 * {@code org.springframework.modulith.NamedInterface}로 identity/booking의 필요한 internal
 * package만 좁게 열었다({@code com.ticket.config}의 package-info, 그리고 각 NamedInterface가
 * 선언된 package-info 참고) — {@code bootstrap}으로 두면 검증에서 완전히 빠져 이 참조가 실제로
 * 무엇을 얼마나 쓰는지 어떤 테스트도 확인하지 않지만, 정식 module + NamedInterface는
 * {@code verifiesModuleStructure()}가 계속 감시한다. 그 결과 {@code com.ticket.bootstrap}에는
 * 지금 production class가 하나도 없다 — 그래도 이 자리 자체(그리고 검증 제외)는 legacy와 무관하게
 * 계속 필요하다: 앞으로도 여러 module의 internal을 동시에 참조해야 하는 코드가 생기면, NamedInterface로
 * 좁혀 열 수 없을 만큼 결합이 크거나 임시적인 경우 이 자리를 쓴다.
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
 * <p><b>{@code shared}의 {@code package-info.java}가 {@code @ApplicationModule}을 명시 선언하는
 * 이유</b> — 지금은 {@code shared}에도 class가 있지만, 애초에 이 선언을 추가한 이유는 class가 하나도
 * 없던 시점에 남아 있다: javac는 애노테이션이 없는 {@code package-info.java}에는
 * {@code package-info.class}를 만들지 않아 Modulith가 그 존재 자체를 볼 수 없다({@code shared}
 * package-info의 javadoc 참고) — class가 없어도 있어도 이 선언은 그대로 둔다. 그래서
 * {@link ApplicationModules#of(Class, DescribedPredicate)}가 legacy를 뺀 뒤 찾아내는 module은
 * {@code booking}, {@code catalog}, {@code identity}, {@code admission}, {@code showlike},
 * {@code metadata}, {@code shared}, {@code config}, {@code error}, {@code seed} 정확히 10개다.
 */
class ModularityTests {

    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "com.ticket.core, com.ticket.storage, com.ticket.support 아래의 아직 이동하지 않은 legacy "
                    + "코드, 그리고 com.ticket.bootstrap의 영구 composition-root 코드",
            ModularityTests::isLegacy);

    /** 검증에서 빠지는 package 이름. {@code bootstrap}이 legacy와 같은 목록에 있는 이유는 클래스 javadoc 참고. */
    private static final Set<String> LEGACY_PACKAGE_NAMES = Set.of("core", "bootstrap", "storage", "support");

    /** 파일시스템 기준으로 선언된 10개 module package다. {@code shared}가 왜 여기 있는지는 클래스 javadoc 참고. */
    private static final Set<String> DECLARED_MODULE_PACKAGES = Set.of(
            "booking", "catalog", "identity", "admission", "showlike", "metadata", "shared", "config", "error", "seed");

    /**
     * 승인된 module 의존 DAG다. 각 module이 나머지 module 중 실제로 직접 참조하는 module 이름
     * 집합이다 — {@code @ApplicationModule(allowedDependencies = ...)}가 선언한 상한이 아니라
     * (catalog/identity/admission은 상한을 비워 둬 "제한 없음"을 뜻한다), 실제로 관측되는 edge를
     * 여기 고정해 새 module 간 결합이 조용히 늘어나는 것을 잡는다. admission은 다른 module을
     * 참조하지 않는 기반 module이다(오류 계약과 응답 봉투를 뜻하는 error·shared는 예외다). {@code shared}는 {@code CursorPage}(catalog가 참조)·
     * {@code CorsProperties}(identity·config가 참조)·{@code UuidSupplier}(identity가 참조) 같은
     * <b>호출 대상 계약만</b> 담아 다른 어떤 module도 참조하지 않는 leaf고, 그래서 이 module들이
     * shared를 향한 edge를 갖는다. 전역 {@code @Configuration}은 {@code config}가 소유한다.
     * {@code ApiResponse}/{@code ErrorMessage}/{@code SliceResponse} 응답 봉투도 shared에 있어
     * controller를 가진 module은 전부 shared를 향한 edge를 갖는다 — 자체 오류를 던지지 않는
     * {@code metadata}가 shared edge를 갖는 이유가 이것뿐이다.
     * {@code config}는 여러 module의 internal을 동시에 참조해야 하는 composition-root 성격의
     * 8번째 module이라(클래스 javadoc과 {@code com.ticket.config}의 package-info 참고) identity·
     * booking을 향한 edge를 갖고, {@code CorsProperties}(shared) 참조로 shared를 향한 edge도
     * 갖는다. 반대로 이 module을 참조하는 다른 module은 없다(leaf).
     * {@code error}는 공통 오류 계약과 전역 handler를 소유하고 응답 봉투를 만들기 위해 shared만
     * 참조한다 — 업무 module이 자기 오류를 소유해 가면서 이 module을 향한 edge가 늘어난다.
     * {@code seed}는 여러 module의 테이블을 raw SQL로 적재하는 10번째 module이고, 부하 테스트
     * 회원만 identity가 {@code @NamedInterface("seed")}로 연 {@code member.command} package를
     * 통해 호출해 identity를 향한 edge를 갖는다. 반대로 이 module을 참조하는 다른 module은
     * 없다(leaf).
     */
    private static final Map<String, Set<String>> APPROVED_DEPENDENCY_DAG = Map.ofEntries(
            Map.entry("booking", Set.of("catalog", "identity", "admission", "shared", "error")),
            Map.entry("catalog", Set.of("shared", "error")),
            Map.entry("identity", Set.of("shared", "error")),
            Map.entry("admission", Set.of("shared", "error")),
            Map.entry("showlike", Set.of("catalog", "identity", "shared", "error")),
            Map.entry("metadata", Set.of("catalog", "booking", "identity", "shared")),
            Map.entry("shared", Set.of()),
            Map.entry("config", Set.of("identity", "booking", "shared")),
            Map.entry("error", Set.of("shared")),
            Map.entry("seed", Set.of("identity"))
    );

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES).verify();
    }

    @Test
    void 모듈_package가_com_ticket_직속에_정확히_10개_선언돼_있다() {
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
