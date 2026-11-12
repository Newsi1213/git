package com.campusquest.util;

import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 统一响应体：{ code, message, data }
 */
public final class Resp {

    public final int code;
    public final String message;
    public final Object data;

    private Resp(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Resp ok(Object data) {
        return new Resp(ErrorCode.OK, "ok", data);
    }

    public static Resp ok() {
        return new Resp(ErrorCode.OK, "ok", new JsonObject());
    }

    public static Resp fail(int code, String message) {
        return new Resp(code, message, null);
    }

    public static void write(HttpServletResponse resp, int httpStatus, int code, String message, Object data)
            throws IOException {
        resp.setStatus(httpStatus);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();
        out.write(JsonUtil.toJson(new Resp(code, message, data)));
        out.flush();
    }

    public static void writeOk(HttpServletResponse resp, Object data) throws IOException {
        write(resp, 200, ErrorCode.OK, "ok", data);
    }

    public static void writeFail(HttpServletResponse resp, int code, String message) throws IOException {
        write(resp, ErrorCode.httpStatus(code), code, message, null);
    }
}
