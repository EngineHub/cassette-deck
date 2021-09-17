package org.enginehub.cassettedeck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class TokenExtractingFilter extends AbstractAuthenticationProcessingFilter {

    public TokenExtractingFilter(ObjectMapper mapper,
                                 RequestMatcher requiresAuthenticationRequestMatcher,
                                 AuthenticationManager authenticationManager) {
        super(requiresAuthenticationRequestMatcher);
        setAuthenticationManager(authenticationManager);
        setAuthenticationFailureHandler((request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            mapper.writeValue(
                response.getWriter(),
                Map.of("code", "access.denied")
            );
        });
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Token ")) {
            throw new BadCredentialsException("Not token authorization");
        }
        var tokenString = header.substring("Token ".length());
        var token = new PreAuthenticatedAuthenticationToken("token", tokenString);
        return getAuthenticationManager().authenticate(token);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        SecurityContextHolder.getContext().setAuthentication(authResult);
        chain.doFilter(request, response);
    }
}
