package com.ticket.core.config.security;

import com.ticket.support.passport.Passport;
import com.ticket.support.passport.web.PassportVerifier;
import com.ticket.support.token.passport.PassportService;
import com.ticket.support.token.passport.PassportTokenException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies the passport header for core, translating verification failures into the
 * {@code expired}/{@code invalid} reasons that {@link RestAuthenticationEntryPoint} maps to messages.
 */
public class CorePassportVerifier implements PassportVerifier {

    private static final String REASON_EXPIRED = "expired";
    private static final String REASON_INVALID = "invalid";

    private final PassportService passportService;

    public CorePassportVerifier(final PassportService passportService) {
        this.passportService = Objects.requireNonNull(passportService, "passportService must not be null");
    }

    @Override
    public Passport verify(final String passportHeader) {
        try {
            return passportService.verifyBearer(passportHeader);
        } catch (PassportTokenException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, resolveReason(exception));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REASON_INVALID);
        }
    }

    private String resolveReason(final PassportTokenException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("expired") ? REASON_EXPIRED : REASON_INVALID;
    }
}
