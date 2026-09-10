package com.ticket;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 module(Bounded Context) 안에서 서로 다른 Aggregate를 객체 연관관계로 묶는 것을 막는다.
 *
 * <p>{@code com.ticket.ModularityTests}는 module(BC) 경계를 넘는 결합만 잡는다 — 같은 module
 * 안에서 {@code Show}가 {@code @OneToMany List<Performance>}를 새로 얻어도 그 테스트는 통과한다.
 * 이 테스트가 그 사각지대를 메운다. Aggregate 목록은 {@code docs/architecture.md}의 "Aggregates"
 * 절이, "같은 aggregate 안에서만 entity 연관관계를 허용한다"는 규칙과 경계별 참조 표는
 * "Aggregate Rules" 절이 원본이다.
 *
 * <p>{@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}와 같은 방식을 쓴다 — 허용
 * 상한이 아니라 실제로 관측된 연관관계를 고정해서, 새 연관관계가 조용히 늘면 이 테스트가 잡는다.
 * 새 연관관계가 정말 같은 aggregate 안(자식이 부모 없이 존재할 수 없는 관계)이면
 * {@link #APPROVED_ASSOCIATIONS}에 추가하고, 아니라면 scalar ID 참조로 바꾼다 — 판단 절차는
 * docs/architecture.md의 Aggregate Rules가 원본이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class AggregateAssociationTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ticket");

    /**
     * 실측된 (선언 클래스 → 대상 타입) 쌍이다. 전부 "자식이 부모 없이 존재할 수 없는" 같은
     * aggregate 안의 관계다 — {@code docs/architecture.md}의 나누는 기준을 본다.
     */
    private static final Set<Association> APPROVED_ASSOCIATIONS = Set.of(
            new Association("com.ticket.booking.order.domain.Order", "com.ticket.booking.order.domain.OrderSeat"),
            new Association("com.ticket.booking.order.domain.OrderSeat", "com.ticket.booking.order.domain.Order"),
            new Association("com.ticket.member.account.domain.Member", "com.ticket.member.account.domain.MemberSocialAccount"),
            new Association("com.ticket.member.account.domain.MemberSocialAccount", "com.ticket.member.account.domain.Member"),
            new Association("com.ticket.show.performance.domain.PerformanceGrade", "com.ticket.show.performance.domain.Performance")
    );

    @Test
    void JPA_연관관계는_승인된_목록과_일치한다() {
        final Set<Association> actual = discoverAssociations();

        assertThat(actual)
                .as("@ManyToOne/@OneToOne/@OneToMany/@ManyToMany로 맺어진 (선언 클래스 → 대상 타입) 전부. "
                        + "새 연관관계가 있다면 같은 aggregate 안인지 먼저 판단한다 — docs/architecture.md의 Aggregate Rules")
                .containsExactlyInAnyOrderElementsOf(APPROVED_ASSOCIATIONS);
    }

    private Set<Association> discoverAssociations() {
        final Set<Association> associations = new LinkedHashSet<>();

        for (final JavaClass javaClass : MAIN_CLASSES) {
            final Class<?> reflected;
            try {
                reflected = javaClass.reflect();
            } catch (final NoClassDefFoundError ignored) {
                continue;
            }

            for (final Field field : reflected.getDeclaredFields()) {
                if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                    associations.add(new Association(reflected.getName(), field.getType().getName()));
                } else if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
                    final Class<?> elementType = collectionElementType(field);
                    if (elementType != null) {
                        associations.add(new Association(reflected.getName(), elementType.getName()));
                    }
                }
            }
        }

        return associations;
    }

    private static Class<?> collectionElementType(final Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return null;
        }
        final Type genericType = field.getGenericType();
        if (!(genericType instanceof final ParameterizedType parameterizedType)) {
            return null;
        }
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 1 || !(typeArguments[0] instanceof final Class<?> elementType)) {
            return null;
        }
        return elementType;
    }

    private record Association(String from, String to) {
    }
}
