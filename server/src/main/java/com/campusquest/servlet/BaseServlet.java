package com.campusquest.servlet;

import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.Resp;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 接口基类：统一异常与响应格式，子类只需实现 route()。
 */
public abstract class BaseServlet extends HttpServlet {

    protected abstract boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod().toUpperCase();
        String path = req.getPathInfo();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            boolean handled = route(method, path, req, resp);
            if (!handled) {
                Resp.writeFail(resp, ErrorCode.NOT_FOUND, "接口不存在");
            }
        } catch (BizException e) {
            Resp.writeFail(resp, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log("接口处理异常：" + method + " " + path, e);
            try {
                Resp.writeFail(resp, ErrorCode.SERVER_ERROR, "服务器开小差了，请稍后再试");
            } catch (IOException ignored) {
            }
        }
    }

    protected void requireMethod(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new BizException(ErrorCode.METHOD_NOT_ALLOWED, "不支持的请求方式");
        }
    }

    protected static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
