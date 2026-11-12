package com.campusquest.servlet;

import com.campusquest.service.CreditService;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreditServlet extends BaseServlet {

    private final CreditService creditService = new CreditService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        long userId = SessionUtil.currentUserId(req);

        if ("/logs".equals(path)) {
            requireMethod(method, "GET");
            int page = Math.max(1, parseInt(req.getParameter("page"), 1));
            int size = Math.min(50, Math.max(1, parseInt(req.getParameter("size"), 10)));
            Resp.writeOk(resp, creditService.logs(userId, page, size));
            return true;
        }

        return false;
    }
}
