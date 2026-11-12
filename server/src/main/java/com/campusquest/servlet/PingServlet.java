package com.campusquest.servlet;

import com.campusquest.util.Resp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class PingServlet extends BaseServlet {

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if (!"/".equals(path)) {
            return false;
        }
        requireMethod(method, "GET");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        Resp.writeOk(resp, data);
        return true;
    }
}
