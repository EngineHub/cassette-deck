package org.enginehub.cassettedeck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CassetteDeckAccessDeniedHandler {
    private final ObjectMapper mapper;

    public CassetteDeckAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void handle(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        mapper.writeValue(
            response.getWriter(),
            Map.of("code", "access.denied")
        );
    }
}
