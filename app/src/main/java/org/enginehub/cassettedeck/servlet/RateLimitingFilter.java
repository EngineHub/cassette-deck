package org.enginehub.cassettedeck.servlet;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitingFilter implements Filter {

    private static Bucket createNewBucket() {
        long overdraft = 50;
        Refill refill = Refill.greedy(10, Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(overdraft, refill);
        return Bucket4j.builder().addLimit(limit).build();
    }

    private final LoadingCache<String, Bucket> bucketCache = CacheBuilder.newBuilder()
        .build(CacheLoader.from(RateLimitingFilter::createNewBucket));

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        var token = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Bucket bucket = bucketCache.getUnchecked(token);

        if (bucket.tryConsume(1)) {
            // the limit is not exceeded
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            // limit is exceeded
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("application/json");
            httpResponse.setStatus(429);
            httpResponse.getWriter().append(
                """
                {"code":"rate.limit.exceeded"}
                """
            );
        }
    }
}
