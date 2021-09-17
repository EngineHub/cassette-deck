package org.enginehub.cassettedeck.security;

import org.enginehub.cassettedeck.db.gen.tables.daos.AuthorizedTokenDao;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DatabaseAuthenticationProvider implements AuthenticationProvider {
    private final AuthorizedTokenDao tokenDao;

    public DatabaseAuthenticationProvider(AuthorizedTokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String customToken = (String) authentication.getCredentials();
        if (tokenDao.existsById(customToken)) {
            return new AnonymousAuthenticationToken(
                "token", customToken, Set.of(new SimpleGrantedAuthority("ROLE_SERVER"))
            );
        }
        throw new BadCredentialsException("Token is not accepted");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication == PreAuthenticatedAuthenticationToken.class;
    }
}
