package com.campusquest.servlet;

import com.campusquest.entity.Admin;
import com.campusquest.service.AuthService;
import com.campusquest.util.JsonUtil;
import com.campusquest.util.Resp;
import com.campusquest.util.SessionUtil;
import com.campusquest.vo.LoginVO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuthServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    public static class RegisterReq {
        public String phone;
        public String nickname;
        public String password;
    }

    public static class LoginReq {
        public String phone;
        public String password;
    }

    public static class AdminLoginReq {
        public String username;
        public String password;
    }

    @Override
    protected boolean route(String method, String path, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if ("/register".equals(path)) {
            requireMethod(method, "POST");
            RegisterReq body = JsonUtil.readBody(req, RegisterReq.class);
            long userId = authService.register(body.phone, body.nickname, body.password);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", userId);
            Resp.writeOk(resp, data);
            return true;
        }
        if ("/login".equals(path)) {
            requireMethod(method, "POST");
            LoginReq body = JsonUtil.readBody(req, LoginReq.class);
            LoginVO vo = authService.login(body.phone, body.password);
            SessionUtil.loginStudent(req, vo.userId, vo.nickname);
            Resp.writeOk(resp, vo);
            return true;
        }
        if ("/admin/login".equals(path)) {
            requireMethod(method, "POST");
            AdminLoginReq body = JsonUtil.readBody(req, AdminLoginReq.class);
            Admin admin = authService.adminLogin(body.username, body.password);
            SessionUtil.loginAdmin(req, admin.getId(), admin.getUsername());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("adminId", admin.getId());
            data.put("username", admin.getUsername());
            data.put("role", "ADMIN");
            Resp.writeOk(resp, data);
            return true;
        }
        if ("/logout".equals(path)) {
            requireMethod(method, "POST");
            SessionUtil.logout(req);
            Resp.writeOk(resp, null);
            return true;
        }
        if ("/me".equals(path)) {
            requireMethod(method, "GET");
            Long userId = SessionUtil.currentUserId(req);
            Resp.writeOk(resp, authService.me(userId));
            return true;
        }
        return false;
    }
}
