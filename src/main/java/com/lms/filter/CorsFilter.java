package com.lms.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@WebFilter(filterName = "CorsFilter", urlPatterns = {"/*"})
public class CorsFilter implements Filter {
    private Set<String> allowedOrigins;

    @Override public void init(FilterConfig filterConfig) {
        String raw = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", "");
        allowedOrigins = Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
    }

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8"); res.setCharacterEncoding("UTF-8");

        String origin = req.getHeader("Origin");
        boolean sameOrigin = origin == null || origin.equals(req.getScheme() + "://" + req.getServerName() + ((req.getServerPort()==80||req.getServerPort()==443) ? "" : ":"+req.getServerPort()));
        if (origin != null && (sameOrigin || allowedOrigins.contains(origin))) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Vary", "Origin");
            res.setHeader("Access-Control-Allow-Credentials", "true");
        }
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With");
        res.setHeader("Access-Control-Max-Age", "3600");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) { res.setStatus(HttpServletResponse.SC_NO_CONTENT); return; }
        chain.doFilter(request, response);
    }
}
