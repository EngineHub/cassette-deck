package org.enginehub.cassettedeck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.Filter;

@Configuration
@EnableWebSecurity
public class CassetteDeckSecurity extends WebSecurityConfigurerAdapter {
    private final ObjectMapper mapper;
    private final DatabaseAuthenticationProvider authenticationProvider;

    public CassetteDeckSecurity(ObjectMapper mapper,
                                DatabaseAuthenticationProvider authenticationProvider) {
        this.mapper = mapper;
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authenticationProvider);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.httpBasic().disable()
            .csrf().disable()
            .formLogin().disable()
            .logout().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(getFilter(), AnonymousAuthenticationFilter.class).authorizeRequests()
            .requestMatchers(getRequestMatcher()).access("hasRole('ROLE_SERVER')").and();
    }

    private RequestMatcher getRequestMatcher() {
        return AnyRequestMatcher.INSTANCE;
    }

    private Filter getFilter() throws Exception {
        return new TokenExtractingFilter(mapper, getRequestMatcher(), authenticationManager());
    }
}
