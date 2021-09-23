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

package org.enginehub.cassettedeck;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

import javax.servlet.Filter;
import java.time.Duration;

@Configuration
public class AppWebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "HEAD")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(7200);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var content = new WebContentInterceptor();
        content.addCacheMapping(
            CacheControl
                // Refresh semi-often
                .maxAge(Duration.ofHours(1))
                // This data should last for a while even if the server dies
                .staleIfError(Duration.ofDays(1))
                .staleWhileRevalidate(Duration.ofDays(1)),
            "/**"
        );
        registry.addInterceptor(content);
    }

    @Bean
    public Filter etagFilter() {
        return new ShallowEtagHeaderFilter();
    }
}
