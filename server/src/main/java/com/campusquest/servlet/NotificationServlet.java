package com.campusquest.servlet;

import com.campusquest.service.NotificationService;
import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

public class NotificationServlet extends BaseServlet {

    private final NotificationService notificationService = new NotificationService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        Long userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
        }

        if ("/".equals(path)) {
            requireMethod(method, "GET");
            boolean onlyUnread = "1".equals(req.getParameter("onlyUnread"));
            Resp.writeOk(resp, notificationService.list(userId, onlyUnread,
                    parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("size"), 10)));
            return true;
        }

        if ("/unread-count".equals(path)) {
            requireMethod(method, "GET");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("unreadCount", notificationService.countUnread(userId));
            Resp.writeOk(resp, data);
            return true;
        }

        if (path.matches("/\\d+/read")) {
            requireMethod(method, "POST");
            long id = Long.parseLong(path.split("/")[1]);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("unreadCount", notificationService.markRead(userId, id));
            Resp.writeOk(resp, data);
            return true;
        }

        return false;
    }
}
