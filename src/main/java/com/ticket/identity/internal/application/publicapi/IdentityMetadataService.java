package com.ticket.identity.internal.application.publicapi;

import com.ticket.identity.IdentityMetadata;
import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.domain.member.model.SocialProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * {@link IdentityMetadata}의 identity 소유 구현이다. metadata module은 이 계약을 통해서만
 * identity의 code/label 값을 조합하고, internal enum을 직접 import하지 않는다.
 */
@Service
public class IdentityMetadataService implements IdentityMetadata {

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
