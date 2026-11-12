package com.campusquest.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class SessionUtil {

    public static final String ROLE = "role";
    public static final String USER_ID = "userId";
    public static final String ADMIN_ID = "adminId";
    public static final String NICKNAME = "nickname";

    private SessionUtil() {
    }

    public static void loginStudent(HttpServletRequest req, long userId, String nickname) {
        HttpSession session = req.getSession(true);
        session.setAttribute(ROLE, "STUDENT");
        session.setAttribute(USER_ID, userId);
        session.setAttribute(NICKNAME, nickname);
    }

    public static void loginAdmin(HttpServletRequest req, long adminId, String username) {
        HttpSession session = req.getSession(true);
        session.setAttribute(ROLE, "ADMIN");
        session.setAttribute(ADMIN_ID, adminId);
        session.setAttribute(NICKNAME, username);
    }

    public static Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object v = session.getAttribute(USER_ID);
        return v instanceof Long ? (Long) v : (v instanceof Number ? ((Number) v).longValue() : null);
    }

    public static Long currentAdminId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object v = session.getAttribute(ADMIN_ID);
        return v instanceof Long ? (Long) v : (v instanceof Number ? ((Number) v).longValue() : null);
    }

    public static boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && "ADMIN".equals(session.getAttribute(ROLE));
    }

    public static boolean isStudent(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && "STUDENT".equals(session.getAttribute(ROLE));
    }

    public static void logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
