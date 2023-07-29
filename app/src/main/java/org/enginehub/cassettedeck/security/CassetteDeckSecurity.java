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
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
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
            .cors(Customizer.withDefaults())
            .headers().cacheControl().disable().and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(getFilter(), AnonymousAuthenticationFilter.class).authorizeRequests()
            .requestMatchers(getRequestMatcher()).access("hasRole('ROLE_SERVER')").and();
    }

    private RequestMatcher getRequestMatcher() {
        return request -> "PUT".equals(request.getMethod());
    }

    private Filter getFilter() throws Exception {
        return new TokenExtractingFilter(mapper, getRequestMatcher(), authenticationManager());
    }
}
