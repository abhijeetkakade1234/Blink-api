package com.urlshortener.config;

import com.urlshortener.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

  private final StringRedisTemplate redisTemplate;
  private final int requestsPerMinute;

  public RateLimitInterceptor(
      StringRedisTemplate redisTemplate,
      @Value("${app.rate-limit.requests-per-minute}") int requestsPerMinute) {
    this.redisTemplate = redisTemplate;
    this.requestsPerMinute = requestsPerMinute;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String clientIp = request.getHeader("X-Forwarded-For");
    if (clientIp == null || clientIp.isBlank()) {
      clientIp = request.getRemoteAddr();
    } else {
      clientIp = clientIp.split(",")[0].trim();
    }

    String key = "rate-limit:create:" + clientIp;

    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count != null && count == 1L) {
        redisTemplate.expire(key, Duration.ofMinutes(1));
      }
      if (count != null && count > requestsPerMinute) {
        throw new BadRequestException("rate limit exceeded");
      }
    } catch (Exception ignored) {
      // ponytail: fail open if redis is down; tighten later if abuse becomes real.
    }

    return true;
  }
}
