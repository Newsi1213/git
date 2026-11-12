package com.campusquest.servlet;

import com.campusquest.service.StatsService;
import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StatsServlet extends BaseServlet {

    private final StatsService statsService = new StatsService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        Long userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
        }

        if ("/summary".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, statsService.summary(userId));
            return true;
        }

        if ("/personal".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, statsService.personal(userId));
            return true;
        }

        if ("/overview".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, statsService.overview());
            return true;
        }

        return false;
    }
}
