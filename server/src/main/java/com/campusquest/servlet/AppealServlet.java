package com.campusquest.servlet;

import com.campusquest.service.AppealService;
import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 20L * 1024 * 1024, maxRequestSize = 25L * 1024 * 1024)
public class AppealServlet extends BaseServlet {

    private final AppealService appealService = new AppealService();

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        long userId = SessionUtil.currentUserId(req);

        if ("/mine".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, appealService.listMine(userId));
            return true;
        }

        if ("/".equals(path)) {
            requireMethod(method, "POST");
            long taskId;
            try {
                taskId = Long.parseLong(req.getParameter("taskId"));
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择要申诉的任务");
            }
            Part screenshot = req.getPart("screenshot");
            Resp.writeOk(resp, appealService.submit(userId, taskId, req.getParameter("content"), screenshot));
            return true;
        }

        return false;
    }
}
