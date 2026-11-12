package com.campusquest.service;

import com.campusquest.dao.AdminAppealDao;
import com.campusquest.dao.NotificationDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.PageResult;
import com.campusquest.util.TextUtil;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端申诉处理：列表、计数、详情、通过（退 2 分信用并通知）、驳回（理由必填并通知）。
 */
public class AdminAppealService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final AdminAppealDao adminAppealDao = new AdminAppealDao();
    private final NotificationDao notificationDao = new NotificationDao();
    private final CreditService creditService = new CreditService();

    public PageResult<Map<String, Object>> list(String status, int page, int size) {
        page = Math.max(1, page);
        size = Math.max(1, Math.min(50, size));
        String normalized = normalizeStatus(status);
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = adminAppealDao.count(conn, normalized);
            List<Map<String, Object>> list = new ArrayList<>();
            for (AdminAppealDao.Row row : adminAppealDao.list(conn, normalized, (page - 1) * size, size)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.id);
                item.put("userId", row.userId);
                item.put("userNickname", row.userNickname);
                item.put("userCredit", row.userCredit);
                item.put("taskId", row.taskId);
                item.put("taskNo", row.taskNo);
                item.put("taskTitle", row.taskTitle);
                item.put("taskStatus", row.taskStatus);
                item.put("status", row.status);
                item.put("content", row.content);
                item.put("createdAt", row.createdAt);
                list.add(item);
            }
            return PageResult.of(page, size, total, list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取申诉列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> counts() {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pending", adminAppealDao.count(conn, STATUS_PENDING));
            data.put("approved", adminAppealDao.count(conn, STATUS_APPROVED));
            data.put("rejected", adminAppealDao.count(conn, STATUS_REJECTED));
            return data;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取申诉数量失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> detail(long id) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            AdminAppealDao.Row row = adminAppealDao.findById(conn, id);
            if (row == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "申诉不存在");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", row.id);
            data.put("userId", row.userId);
            data.put("userNickname", row.userNickname);
            data.put("userCredit", row.userCredit);
            data.put("taskId", row.taskId);
            data.put("taskNo", row.taskNo);
            data.put("taskTitle", row.taskTitle);
            data.put("taskStatus", row.taskStatus);
            data.put("content", row.content);
            data.put("screenshotPath", row.screenshotPath);
            data.put("status", row.status);
            data.put("rejectReason", row.rejectReason);
            data.put("createdAt", row.createdAt);
            data.put("handledAt", row.handledAt);
            List<Map<String, Object>> logs = new ArrayList<>();
            for (AdminAppealDao.CreditRow log : adminAppealDao.listCreditLogs(conn, row.userId, row.taskId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("changeAmount", log.changeAmount);
                item.put("reason", log.reason);
                item.put("remark", log.remark);
                item.put("createdAt", log.createdAt);
                logs.add(item);
            }
            data.put("creditLogs", logs);
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取申诉详情失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> approve(long id, long adminId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            adminAppealDao.begin(conn);
            AdminAppealDao.Row row = adminAppealDao.findByIdForUpdate(conn, id);
            if (row == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "申诉不存在");
            }
            if (!STATUS_PENDING.equals(row.status)) {
                throw new BizException(ErrorCode.APPEAL_HANDLED, "该申诉已处理，不能重复操作");
            }
            if (adminAppealDao.markApproved(conn, id, adminId) == 0) {
                throw new BizException(ErrorCode.APPEAL_HANDLED, "该申诉已处理，不能重复操作");
            }
            int credit = creditService.change(conn, row.userId, 2, "APPEAL_REFUND",
                    row.taskId, "申诉通过，退回扣分");
            notificationDao.insert(conn, row.userId, "APPEAL_RESULT", "申诉已通过",
                    "你对《" + nullToEmpty(row.taskTitle) + "》的申诉已通过，信用分已退回 2 分。",
                    row.taskId);
            adminAppealDao.commit(conn);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("status", STATUS_APPROVED);
            data.put("credit", credit);
            return data;
        } catch (BizException e) {
            adminAppealDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            adminAppealDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "处理申诉失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> reject(long id, long adminId, String reason) {
        reason = TextUtil.trim(reason);
        if (reason.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写驳回理由");
        }
        if (reason.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回理由最多 255 个字");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            adminAppealDao.begin(conn);
            AdminAppealDao.Row row = adminAppealDao.findByIdForUpdate(conn, id);
            if (row == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "申诉不存在");
            }
            if (!STATUS_PENDING.equals(row.status)) {
                throw new BizException(ErrorCode.APPEAL_HANDLED, "该申诉已处理，不能重复操作");
            }
            if (adminAppealDao.markRejected(conn, id, adminId, reason) == 0) {
                throw new BizException(ErrorCode.APPEAL_HANDLED, "该申诉已处理，不能重复操作");
            }
            notificationDao.insert(conn, row.userId, "APPEAL_RESULT", "申诉被驳回",
                    "你对《" + nullToEmpty(row.taskTitle) + "》的申诉未通过，理由：" + reason,
                    row.taskId);
            adminAppealDao.commit(conn);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("status", STATUS_REJECTED);
            data.put("rejectReason", reason);
            return data;
        } catch (BizException e) {
            adminAppealDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            adminAppealDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "处理申诉失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    private String normalizeStatus(String status) {
        if (STATUS_PENDING.equals(status) || STATUS_APPROVED.equals(status) || STATUS_REJECTED.equals(status)) {
            return status;
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
