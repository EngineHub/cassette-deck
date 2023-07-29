/*
 * Copyright (c) EngineHub <https://enginehub.org>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.enginehub.cassettedeck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.enginehub.cassettedeck.db.gen.tables.daos.AuthorizedTokenDao;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class TokenExtractingFilter extends AbstractAuthenticationProcessingFilter {
    private final AuthorizedTokenDao tokenDao;

    public TokenExtractingFilter(
        AuthenticationManager authenticationManager,
        AuthorizedTokenDao tokenDao,
        CassetteDeckAccessDeniedHandler accessDeniedHandler
    ) {
        super(AnyRequestMatcher.INSTANCE, authenticationManager);
        this.tokenDao = tokenDao;
        setAuthenticationFailureHandler((request, response, exception) -> accessDeniedHandler.handle(response));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return new AnonymousAuthenticationToken(
                "anonymous", "anonymous", Set.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
        }
        if (!header.startsWith("Token ")) {
            throw new BadCredentialsException("Not token authorization");
        }
        var tokenString = header.substring("Token ".length());
        if (tokenDao.existsById(tokenString)) {
            return new AnonymousAuthenticationToken(
                "token", tokenString, Set.of(new SimpleGrantedAuthority("ROLE_SERVER"))
            );
        }
        throw new BadCredentialsException("Invalid token");
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        SecurityContextHolder.getContext().setAuthentication(authResult);
        chain.doFilter(request, response);
    }
}
