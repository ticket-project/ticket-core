package com.ticket.core;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 사이의 의존 방향을 고정한다. core-api는 네 모듈을 모두 클래스패스에 두는 유일한 모듈이라
 * 전체 방향을 여기에서 한 번에 검사한다.
 *
 * <p>허용 방향은 core-api -> core-app -> core-domain이며, core-infra는 어댑터로서
 * core-app과 core-domain을 향한다. 반대 방향은 모두 금지한다.
 */
@AnalyzeClasses(
        packages = "com.ticket",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class CoreLayerArchitectureTest {

    private static final String DOMAIN = "com.ticket.core.domain..";
    private static final String APP = "com.ticket.core.app..";
    private static final String INFRA = "com.ticket.core.infra..";
    private static final String API_CONTROLLER = "com.ticket.core.api..";
    private static final String API_CONFIG = "com.ticket.core.config..";

    /**
     * Querydsl이 엔티티에서 생성한 Q 타입. core-domain에서 컴파일되므로 Querydsl 금지 규칙에서 뺀다.
     * 이름이 Q + 대문자인 것만 골라 QueueMode 같은 도메인 타입과 구분한다.
     */
    private static final DescribedPredicate<JavaClass> QUERYDSL이_생성한_타입 =
            new DescribedPredicate<>("Querydsl이 생성한 Q 타입") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    return javaClass.getSimpleName().matches("Q[A-Z].*");
                }
            };

    @ArchTest
    static final ArchRule core_domain은_바깥_계층을_참조하지_않는다 =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(APP, INFRA, API_CONTROLLER, API_CONFIG);

    @ArchTest
    static final ArchRule core_app은_infra와_api를_참조하지_않는다 =
            noClasses()
                    .that().resideInAPackage(APP)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(INFRA, API_CONTROLLER, API_CONFIG);

    /**
     * core-api는 core-app을 거쳐서만 도메인에 닿는다. 엔티티와 리포지토리를 실행 모듈이 직접
     * 만지면 use case를 우회하게 되므로 막는다. build.gradle에서도 프로덕션 의존을 뺐고,
     * 도메인 픽스처가 필요한 계약 테스트만 testImplementation으로 허용한다.
     */
    @ArchTest
    static final ArchRule core_api는_core_domain을_참조하지_않는다 =
            noClasses()
                    .that().resideInAnyPackage(API_CONTROLLER, API_CONFIG)
                    .should().dependOnClassesThat().resideInAPackage(DOMAIN);

    @ArchTest
    static final ArchRule core_infra는_api를_참조하지_않는다 =
            noClasses()
                    .that().resideInAPackage(INFRA)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(API_CONTROLLER, API_CONFIG);

    /**
     * Querydsl 조회 구현은 core-infra에만 둔다. 조건 생성과 정렬은 DB 연동 코드이므로
     * 도메인 규칙이나 use case가 직접 다루지 않는다.
     */
    @ArchTest
    static final ArchRule core_domain은_querydsl을_직접_쓰지_않는다 =
            noClasses()
                    .that(resideInAPackage(DOMAIN).and(DescribedPredicate.not(QUERYDSL이_생성한_타입)))
                    .should().dependOnClassesThat().resideInAnyPackage("com.querydsl..");

    @ArchTest
    static final ArchRule core_app은_querydsl을_직접_쓰지_않는다 =
            noClasses()
                    .that().resideInAPackage(APP)
                    .should().dependOnClassesThat().resideInAnyPackage("com.querydsl..");

    /**
     * core-domain에 허용된 Spring은 엔티티 매핑과 저장소 선언에 필요한 것뿐이다.
     * 즉 {@code data}(JPA repository, auditing)와 {@code stereotype}(빈 선언)만 쓴다.
     *
     * <p>표현·전송뿐 아니라 트랜잭션 경계와 이벤트 발행, 표현식 해석도 막는다. 이것들은
     * 업무 규칙이 아니라 흐름을 엮는 방법이므로 core-app이나 core-infra가 맡는다.
     */
    @ArchTest
    static final ArchRule core_domain은_매핑과_빈선언_외의_spring을_참조하지_않는다 =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.http..",
                            "org.springframework.security..",
                            "org.springframework.messaging..",
                            "org.springframework.transaction..",
                            "org.springframework.context..",
                            "org.springframework.expression..",
                            "org.springframework.scheduling..",
                            "org.springframework.dao.."
                    );

    /**
     * core-app은 트랜잭션 경계를 소유하므로 {@code transaction}은 허용한다.
     * 표현·전송과 스케줄링은 core-api와 core-infra의 몫이다.
     */
    @ArchTest
    static final ArchRule core_app은_http_보안_스케줄링을_참조하지_않는다 =
            noClasses()
                    .that().resideInAPackage(APP)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.http..",
                            "org.springframework.security..",
                            "org.springframework.messaging..",
                            "org.springframework.scheduling.."
                    );
}
