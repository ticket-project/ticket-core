package com.ticket.core.domain.auth;

import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.RawPassword;

public interface PasswordService {

    String encode(String rawPassword);

    boolean matches(RawPassword rawPassword, EncodedPassword encodedPassword);
}
