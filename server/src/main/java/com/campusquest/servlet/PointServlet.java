package com.campusquest.servlet;

import com.campusquest.service.PointService;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PointServlet extends BaseServlet {

    private final PointService pointService = new PointService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        long userId = SessionUtil.currentUserId(req);

        if ("/logs".equals(path)) {
            requireMethod(method, "GET");
            int page = Math.max(1, parseInt(req.getParameter("page"), 1));
            int size = Math.min(50, Math.max(1, parseInt(req.getParameter("size"), 10)));
            Resp.writeOk(resp, pointService.logs(userId, req.getParameter("type"), page, size));
            return true;
        }

        if ("/recharge".equals(path)) {
            requireMethod(method, "POST");
            RechargeReq body = JsonUtil.readBody(req, RechargeReq.class);
            Resp.writeOk(resp, pointService.recharge(userId, body.amount));
            return true;
        }

        return false;
    }

    public static class RechargeReq {
        public int amount;
    }
}
