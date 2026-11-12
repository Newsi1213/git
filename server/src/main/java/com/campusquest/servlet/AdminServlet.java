package com.campusquest.servlet;

import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端登录信息：供管理端页面显示管理员名称。
 */
public class AdminServlet extends BaseServlet {

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if ("/".equals(path)) {
            requireMethod(method, "GET");
            HttpSession session = req.getSession(false);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("adminId", SessionUtil.currentAdminId(req));
            data.put("username", session == null ? "" : session.getAttribute(SessionUtil.NICKNAME));
            data.put("role", "ADMIN");
            Resp.writeOk(resp, data);
            return true;
        }
        return false;
    }
}
