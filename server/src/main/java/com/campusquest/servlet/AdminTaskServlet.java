package com.campusquest.servlet;

import com.campusquest.service.AdminTaskService;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端任务接口：搜索列表、下架、恢复。
 */
public class AdminTaskServlet extends BaseServlet {

    private final AdminTaskService adminTaskService = new AdminTaskService();

    public static class OfflineReq {
        public String reason;
    }

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if ("/".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, adminTaskService.list(req.getParameter("keyword"),
                    req.getParameter("offline"), parseInt(req.getParameter("page"), 1),
                    parseInt(req.getParameter("size"), 10)));
            return true;
        }

        if (path.matches("/\\d+/offline")) {
            requireMethod(method, "POST");
            long id = Long.parseLong(path.split("/")[1]);
            OfflineReq body = JsonUtil.readBody(req, OfflineReq.class);
            Resp.writeOk(resp, adminTaskService.offline(id, body.reason));
            return true;
        }

        if (path.matches("/\\d+/online")) {
            requireMethod(method, "POST");
            long id = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, adminTaskService.online(id));
            return true;
        }

        return false;
    }
}
