package com.ticket.core.api.error;

import com.ticket.core.app.error.ApplicationErrorCode;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.error.DomainErrorCode;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.support.error.CommonErrorCode;
import com.ticket.support.error.ErrorCode;
import com.ticket.support.error.ErrorDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 계약을 모듈 경계 너머에서 한 번에 검사한다. 세 카탈로그를 모두 클래스패스에 두는 모듈이
 * core-api뿐이라 여기에 둔다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ErrorCatalogContractTest {

    private static final List<ErrorDefinition> 모든_오류 = Stream.of(
            Arrays.stream(DomainErrorType.values()),
            Arrays.stream(ApplicationErrorType.values()),
            Arrays.stream(ApiErrorType.values())
    ).flatMap(stream -> stream).map(ErrorDefinition.class::cast).toList();

    @Test
    void 모든_오류는_상태와_코드와_메시지를_가진다() {
        for (final ErrorDefinition error : 모든_오류) {
            assertThat(error.getStatus()).as("%s의 status", error).isNotNull();
            assertThat(error.getErrorCode()).as("%s의 errorCode", error).isNotNull();
            assertThat(error.getErrorCode().getCode()).as("%s의 code", error).isNotBlank();
            assertThat(error.getErrorCode().getDescription()).as("%s의 description", error).isNotBlank();
            assertThat(error.getMessage()).as("%s의 message", error).isNotBlank();
        }
    }

    @Test
    void 모듈_오류_코드는_서로_겹치지_않는다() {
        final List<String> 코드들 = new ArrayList<>();
        Arrays.stream(CommonErrorCode.values()).map(ErrorCode::getCode).forEach(코드들::add);
        Arrays.stream(DomainErrorCode.values()).map(ErrorCode::getCode).forEach(코드들::add);
        Arrays.stream(ApplicationErrorCode.values()).map(ErrorCode::getCode).forEach(코드들::add);

        assertThat(코드들).doesNotHaveDuplicates();
    }

    @Test
    void 각_모듈의_오류는_자기_코드나_공용_코드만_참조한다() {
        assertOwnership(DomainErrorType.values(), Set.of(DomainErrorCode.class, CommonErrorCode.class));
        assertOwnership(ApplicationErrorType.values(), Set.of(ApplicationErrorCode.class, CommonErrorCode.class));
        assertOwnership(ApiErrorType.values(), Set.of(CommonErrorCode.class));
    }

    private void assertOwnership(final ErrorDefinition[] errors, final Set<Class<?>> 허용된_코드_타입) {
        for (final ErrorDefinition error : errors) {
            assertThat(error.getErrorCode().getClass())
                    .as("%s가 참조하는 코드 타입", error)
                    .isIn(허용된_코드_타입);
        }
    }
}
