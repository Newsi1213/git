package com.campusquest.servlet;

import com.campusquest.service.LeaderboardService;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LeaderboardServlet extends BaseServlet {

    private final LeaderboardService leaderboardService = new LeaderboardService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if ("/".equals(path)) {
            requireMethod(method, "GET");
            long userId = SessionUtil.currentUserId(req);
            int limit = parseInt(req.getParameter("limit"), 20);
            Resp.writeOk(resp, leaderboardService.board(userId, limit));
            return true;
        }
        return false;
    }
}
