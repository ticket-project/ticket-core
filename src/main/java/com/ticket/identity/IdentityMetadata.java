package com.ticket.identity;

import java.util.List;

/**
 * metadata module이 조합하는 코드/라벨 목록 중 identity가 소유한 부분(Role, SocialProvider)이다.
 * 어떤 module도 identity internal enum·entity를 직접 import하지 않고 이 계약만 쓴다.
 *
 * <p>metadata module 자체는 아직 빈 skeleton이다 — 이 계약을 실제로 조합해 쓰는 쪽은 이후 Task에서
 * 연결된다.
 */
public interface IdentityMetadata {

    List<CodeLabel> roles();

    List<CodeLabel> socialProviders();

    record CodeLabel(String code, String label) {
    }
}
