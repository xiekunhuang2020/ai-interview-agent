package com.xkh.ai.interview.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    /**
     * 请求链路 ID 的 HTTP Header 名称，前端或网关传入时会继续沿用。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 写入日志 MDC 的 Key，日志格式通过 %X{traceId} 输出它。
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 为每个请求准备 traceId，并在请求结束后清理 MDC，避免线程复用导致串日志。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
