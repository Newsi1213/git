package com.campusquest.service;

import com.campusquest.dao.AdminDao;
import com.campusquest.dao.NotificationDao;
import com.campusquest.dao.UserDao;
import com.campusquest.entity.Admin;
import com.campusquest.entity.User;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.TextUtil;
import com.campusquest.vo.LoginVO;
import com.campusquest.vo.MeVO;

import java.sql.Connection;
import java.sql.SQLIntegrityConstraintViolationException;
import java.text.SimpleDateFormat;

public class AuthService {

    private final UserDao userDao = new UserDao();
    private final AdminDao adminDao = new AdminDao();
    private final NotificationDao notificationDao = new NotificationDao();

    public long register(String phone, String nickname, String password) {
        phone = TextUtil.trim(phone);
        nickname = TextUtil.trim(nickname);
        password = password == null ? "" : password;

        if (!TextUtil.isPhone(phone)) {
            throw new BizException(ErrorCode.PHONE_INVALID, "请输入 11 位手机号");
        }
        if (nickname.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "昵称不能为空");
        }
        if (nickname.length() > 32) {
            throw new BizException(ErrorCode.PARAM_INVALID, "昵称最多 32 个字");
        }
        if (password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码不能为空");
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            if (userDao.findByPhone(conn, phone) != null) {
                throw new BizException(ErrorCode.PHONE_EXISTS, "该手机号已注册，请直接登录");
            }
            userDao.begin(conn);
            long userId = userDao.insert(conn, phone, nickname, TextUtil.sha256Hex(password));
            userDao.update(conn, "INSERT INTO point_logs(user_id, change_amount, balance_after, type, remark)"
                    + " VALUES(?, 100, 100, 'REGISTER', '注册赠送')", userId);
            userDao.update(conn, "INSERT INTO credit_logs(user_id, change_amount, reason, remark)"
                    + " VALUES(?, 100, 'INIT', '初始信用')", userId);
            userDao.commit(conn);
            return userId;
        } catch (BizException e) {
            userDao.rollback(conn);
            throw e;
        } catch (SQLIntegrityConstraintViolationException e) {
            userDao.rollback(conn);
            throw new BizException(ErrorCode.PHONE_EXISTS, "该手机号已注册，请直接登录");
        } catch (Exception e) {
            userDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "注册失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public LoginVO login(String phone, String password) {
        phone = TextUtil.trim(phone);
        password = password == null ? "" : password;
        if (!TextUtil.isPhone(phone)) {
            throw new BizException(ErrorCode.PHONE_INVALID, "请输入 11 位手机号");
        }
        if (password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码不能为空");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            User user = userDao.findByPhone(conn, phone);
            if (user == null || !user.getPasswordHash().equals(TextUtil.sha256Hex(password))) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "手机号或密码不正确");
            }
            return new LoginVO(user.getId(), user.getNickname(), user.getPoints(), user.getCredit());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "登录失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Admin adminLogin(String username, String password) {
        username = TextUtil.trim(username);
        password = password == null ? "" : password;
        if (username.isEmpty() || password.isEmpty()) {
            throw new BizException(ErrorCode.ADMIN_LOGIN_FAILED, "管理员账号或密码不正确");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Admin admin = adminDao.findByUsername(conn, username);
            if (admin == null || !admin.getPasswordHash().equals(TextUtil.sha256Hex(password))) {
                throw new BizException(ErrorCode.ADMIN_LOGIN_FAILED, "管理员账号或密码不正确");
            }
            return admin;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "登录失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public MeVO me(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
            }
            int unread = notificationDao.countUnread(conn, userId);
            String created = user.getCreatedAt() == null ? ""
                    : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(user.getCreatedAt());
            return new MeVO(user.getId(), user.getNickname(), TextUtil.maskPhone(user.getPhone()),
                    user.getPoints(), user.getCredit(), unread, user.getAvatarPath(), created);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取用户信息失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
