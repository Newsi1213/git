package com.campusquest.servlet;

import com.campusquest.service.ChatService;
import com.campusquest.util.BizException;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 5L * 1024 * 1024, maxRequestSize = 6L * 1024 * 1024)
public class ChatServlet extends BaseServlet {

    private final ChatService chatService = new ChatService();

    public static class TextReq {
        public String content;
    }

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        Long userId = SessionUtil.currentUserId(req);
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
        }

        if ("/".equals(path)) {
            requireMethod(method, "GET");
            Resp.writeOk(resp, chatService.listMyConversations(userId));
            return true;
        }

        if (path.matches("/\\d+/messages")) {
            long convId = Long.parseLong(path.split("/")[1]);
            if ("GET".equals(method)) {
                String after = req.getParameter("afterId");
                long afterId = 0L;
                try {
                    afterId = after == null ? 0L : Long.parseLong(after);
                } catch (NumberFormatException ignored) {
                }
                Resp.writeOk(resp, chatService.listMessages(convId, userId, afterId,
                        parseInt(req.getParameter("page"), 1), parseInt(req.getParameter("size"), 30)));
                return true;
            }
            if ("POST".equals(method)) {
                TextReq body = JsonUtil.readBody(req, TextReq.class);
                Resp.writeOk(resp, chatService.sendText(convId, userId, body.content));
                return true;
            }
            throw new BizException(ErrorCode.METHOD_NOT_ALLOWED, "不支持的请求方式");
        }

        if (path.matches("/\\d+/images")) {
            requireMethod(method, "POST");
            long convId = Long.parseLong(path.split("/")[1]);
            Part part;
            try {
                part = req.getPart("file");
            } catch (IllegalStateException | javax.servlet.ServletException e) {
                throw new BizException(ErrorCode.FILE_TOO_LARGE, "图片不能超过 5MB");
            }
            Resp.writeOk(resp, chatService.sendImage(convId, userId, part));
            return true;
        }

        return false;
    }
}
