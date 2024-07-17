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

import org.enginehub.cassettedeck.data.blob.DiskStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@PropertySource("classpath:application.properties")
@EnableScheduling
public class AppConfig {
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, CassetteDeck.USER_AGENT)
            .build();
    }

    @Bean("library")
    public DiskStorage libraryBlobStorage(
        @Value("${disk.library.storage-dir}") Path storageDir
    ) {
        return new DiskStorage(storageDir);
    }

    @Bean("blockStateData")
    public DiskStorage blockStateDataBlobStorage(
        @Value("${disk.block-state-data.storage-dir}") Path storageDir
    ) {
        return new DiskStorage(storageDir);
    }

    @Bean("worldEditCliData")
    public DiskStorage worldEditCliDataBlobStorage(
        @Value("${disk.worldedit-cli-data.storage-dir}") Path storageDir
    ) {
        return new DiskStorage(storageDir);
    }
}
