package com.campusquest.servlet;

import com.campusquest.service.AdminAppealService;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端申诉接口：列表、计数、详情、通过、驳回。
 */
public class AdminAppealServlet extends BaseServlet {

    private final AdminAppealService adminAppealService = new AdminAppealService();

    public static class RejectReq {
        public String reason;
    }

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        long adminId = SessionUtil.currentAdminId(req);

        if ("/".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, adminAppealService.list(req.getParameter("status"),
                    parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("size"), 10)));
            return true;
        }

        if ("/counts".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, adminAppealService.counts());
            return true;
        }

        if (path.matches("/\\d+")) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, adminAppealService.detail(Long.parseLong(path.substring(1))));
            return true;
        }

        if (path.matches("/\\d+/approve")) {
            requireMethod(method, "POST");
            long id = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, adminAppealService.approve(id, adminId));
            return true;
        }

        if (path.matches("/\\d+/reject")) {
            requireMethod(method, "POST");
            long id = Long.parseLong(path.split("/")[1]);
            RejectReq body = JsonUtil.readBody(req, RejectReq.class);
            Resp.writeOk(resp, adminAppealService.reject(id, adminId, body.reason));
            return true;
        }

        return false;
    }
}
