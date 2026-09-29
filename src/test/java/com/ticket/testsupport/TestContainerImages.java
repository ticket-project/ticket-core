package com.ticket.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers가 띄우는 image를 한 곳에서 고른다. 테스트마다 태그가 갈리면 같은 Redis를 검증한다고 믿은 두 테스트가 다른 버전에 붙는다.
 *
 * <p>seed 테스트({@code seed/src/test})는 이 source set을 classpath에 두지 않아 같은 값을 따로 적는다.
 */
public final class TestContainerImages {
    public static final DockerImageName REDIS = DockerImageName.parse("redis:7.4-alpine");
    public static final DockerImageName ORACLE = DockerImageName.parse("gvenzl/oracle-free:23-slim");

    private TestContainerImages() {}
}
