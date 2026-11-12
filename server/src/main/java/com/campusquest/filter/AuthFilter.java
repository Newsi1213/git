package com.campusquest.filter;

import com.campusquest.util.ErrorCode;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, resp);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod().toUpperCase();

        if (isPublic(method, path)) {
            chain.doFilter(req, resp);
            return;
        }

        if ("/api/auth/logout".equals(path) && (SessionUtil.isStudent(request) || SessionUtil.isAdmin(request))) {
            chain.doFilter(req, resp);
            return;
        }

        if (path.startsWith("/api/admin/")) {
            if (SessionUtil.isAdmin(request)) {
                chain.doFilter(req, resp);
                return;
            }
            if (SessionUtil.isStudent(request)) {
                Resp.writeFail(response, ErrorCode.FORBIDDEN, "没有权限执行该操作");
            } else {
                Resp.writeFail(response, ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
            }
            return;
        }

        if (SessionUtil.isStudent(request)) {
            chain.doFilter(req, resp);
            return;
        }
        Resp.writeFail(response, ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
    }

    private boolean isPublic(String method, String path) {
        if ("GET".equals(method) && "/api/ping".equals(path)) {
            return true;
        }
        if (!"POST".equals(method)) {
            return false;
        }
        return "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/admin/login".equals(path);
    }

    @Override
    public void destroy() {
    }
}
