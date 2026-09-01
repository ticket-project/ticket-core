package com.ticket;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * core-api 슬라이스 테스트용 Spring Boot 설정 진입점이다.
 *
 * <p>실행용 main은 bootstrap 모듈에만 있으므로, {@code @WebMvcTest} 같은 슬라이스 테스트가
 * 찾을 {@code @SpringBootConfiguration}을 이 모듈 테스트 소스에 둔다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class CoreApiTestApplication {
}
