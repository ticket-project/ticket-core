package com.ticket.member.internal.application.publicapi;

import com.ticket.member.MemberMetadata;
import com.ticket.member.internal.domain.member.model.Role;
import com.ticket.member.internal.domain.member.model.SocialProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * {@link MemberMetadata}의 member 소유 구현이다. metadata module은 이 계약을 통해서만
 * member의 code/label 값을 조합하고, internal enum을 직접 import하지 않는다.
 */
@Service
public class MemberMetadataService implements MemberMetadata {

    @Override
    public List<CodeLabel> roles() {
        return Arrays.stream(Role.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    @Override
    public List<CodeLabel> socialProviders() {
        return Arrays.stream(SocialProvider.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }
}
