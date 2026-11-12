package com.campusquest.service;

import com.campusquest.dao.TaskDao;
import com.campusquest.entity.Task;
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
 * 管理端任务管理：搜索、下架、恢复。下架只改 offline 三个字段，不动 status。
 */
public class AdminTaskService {

    private final TaskDao taskDao = new TaskDao();

    public PageResult<Map<String, Object>> list(String keyword, String offline, int page, int size) {
        page = Math.max(1, page);
        size = Math.max(1, Math.min(50, size));
        String kw = TextUtil.trim(keyword);
        Integer offlineValue = null;
        if ("0".equals(offline)) {
            offlineValue = 0;
        } else if ("1".equals(offline)) {
            offlineValue = 1;
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = taskDao.adminCount(conn, kw, offlineValue);
            List<Map<String, Object>> list = new ArrayList<>();
            for (TaskDao.AdminTaskRow row : taskDao.adminList(conn, kw, offlineValue, (page - 1) * size, size)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.id);
                item.put("taskNo", row.taskNo);
                item.put("title", row.title);
                item.put("category", row.category);
                item.put("points", row.points);
                item.put("status", row.status);
                item.put("offline", row.offline);
                item.put("offlineReason", row.offlineReason);
                item.put("offlineAt", row.offlineAt);
                item.put("publisherNickname", row.publisherNickname);
                item.put("createdAt", row.createdAt);
                list.add(item);
            }
            return PageResult.of(page, size, total, list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取任务列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> offline(long taskId, String reason) {
        reason = TextUtil.trim(reason);
        if (reason.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写下架理由");
        }
        if (reason.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "下架理由最多 255 个字");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Task task = taskDao.findById(conn, taskId);
            if (task == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
            }
            if (task.getOffline() == 1) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该任务已下架");
            }
            if (taskDao.setOffline(conn, taskId, reason) == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该任务已下架");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("offline", 1);
            data.put("offlineReason", reason);
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "下架任务失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> online(long taskId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Task task = taskDao.findById(conn, taskId);
            if (task == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
            }
            if (task.getOffline() == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该任务未被下架");
            }
            if (taskDao.setOnline(conn, taskId) == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "该任务未被下架");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("offline", 0);
            data.put("status", task.getStatus());
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "恢复任务失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
