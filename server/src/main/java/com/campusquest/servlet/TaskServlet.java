package com.campusquest.servlet;

import com.campusquest.service.ApplicationService;
import com.campusquest.service.ReviewService;
import com.campusquest.service.SettlementService;
import com.campusquest.service.TaskService;
import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TaskServlet extends BaseServlet {

    private final TaskService taskService = new TaskService();
    private final ApplicationService applicationService = new ApplicationService();
    private final SettlementService settlementService = new SettlementService();
    private final ReviewService reviewService = new ReviewService();

    public static class PublishReq {
        public String category;
        public String title;
        public String description;
        public int points;
        public String locationDesc;
        public String timeDesc;
    }

    public static class ConfirmReq {
        public long applicationId;
    }

    public static class ReviewReq {
        public String rating;
        public String comment;
    }

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        Long userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
        }

        if ("/".equals(path)) {
            if ("POST".equals(method)) {
                PublishReq body = JsonUtil.readBody(req, PublishReq.class);
                Resp.writeOk(resp, taskService.publish(userId, body.category, body.title, body.description,
                        body.points, body.locationDesc, body.timeDesc));
                return true;
            }
            if ("GET".equals(method)) {
                String category = req.getParameter("category");
                String keyword = req.getParameter("keyword");
                String sort = req.getParameter("sort");
                boolean onlyWaiting = !"0".equals(req.getParameter("onlyWaiting"));
                int page = parseInt(req.getParameter("page"), 1);
                int size = parseInt(req.getParameter("size"), 12);
                Resp.writeOk(resp, taskService.lobby(category, keyword, sort, onlyWaiting, page, size));
                return true;
            }
            throw new BizException(ErrorCode.METHOD_NOT_ALLOWED, "不支持的请求方式");
        }

        if ("/mine".equals(path) || "/mine/published".equals(path) || "/mine/accepted".equals(path)) {
            requireMethod(method, "GET");
            String type = "/mine/accepted".equals(path) ? "accepted"
                    : "/mine/published".equals(path) ? "published"
                    : ("accepted".equals(req.getParameter("type")) ? "accepted" : "published");
            Resp.writeOk(resp, taskService.mine(userId, type, req.getParameter("status"),
                    parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("size"), 10)));
            return true;
        }

        if (path.matches("/\\d+")) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, taskService.detail(Long.parseLong(path.substring(1)), userId));
            return true;
        }

        if (path.matches("/\\d+/applications")) {
            requireMethod(method, "GET");
            long taskId = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, applicationService.listApplications(taskId, userId));
            return true;
        }

        if (path.matches("/\\d+/apply")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, applicationService.apply(taskId, userId));
            return true;
        }

        if (path.matches("/\\d+/confirm")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            ConfirmReq body = JsonUtil.readBody(req, ConfirmReq.class);
            Resp.writeOk(resp, applicationService.confirm(taskId, userId, body.applicationId));
            return true;
        }

        if (path.matches("/\\d+/cancel")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, applicationService.cancel(taskId, userId));
            return true;
        }

        if (path.matches("/\\d+/abandon")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, applicationService.abandon(taskId, userId));
            return true;
        }

        if (path.matches("/\\d+/deliver")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            Resp.writeOk(resp, taskService.deliver(taskId, userId));
            return true;
        }

        if (path.matches("/\\d+/complete")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            boolean done = settlementService.complete(taskId, userId, false);
            if (!done) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "任务已完成或状态已变更");
            }
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("status", "COMPLETED");
            Resp.writeOk(resp, data);
            return true;
        }

        if (path.matches("/\\d+/review")) {
            requireMethod(method, "POST");
            long taskId = Long.parseLong(path.split("/")[1]);
            ReviewReq body = JsonUtil.readBody(req, ReviewReq.class);
            Resp.writeOk(resp, reviewService.review(taskId, userId, body.rating, body.comment));
            return true;
        }
        return false;
    }
}
