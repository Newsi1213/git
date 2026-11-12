package com.campusquest.service;

import com.campusquest.dao.NotificationDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.PageResult;
import com.campusquest.vo.NotificationVO;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {

    private final NotificationDao notificationDao = new NotificationDao();

    public void notify(long userId, String type, String title, String content, Long taskId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            notificationDao.insert(conn, userId, type, title, content, taskId);
        } catch (Exception ignored) {
            // 通知失败不影响主流程
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public PageResult<NotificationVO> list(long userId, boolean onlyUnread, int page, int size) {
        page = Math.max(1, page);
        size = Math.max(1, Math.min(50, size));
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = notificationDao.countByUser(conn, userId, onlyUnread);
            List<NotificationDao.Row> rows = notificationDao.listByUser(conn, userId, onlyUnread,
                    (page - 1) * size, size);
            List<NotificationVO> list = new ArrayList<>();
            for (NotificationDao.Row r : rows) {
                NotificationVO vo = new NotificationVO();
                vo.id = r.id;
                vo.type = r.type;
                vo.title = r.title;
                vo.content = r.content;
                vo.taskId = r.taskId;
                vo.isRead = r.isRead;
                vo.createdAt = r.createdAt;
                list.add(vo);
            }
            return PageResult.of(page, size, total, list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取消息列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public int countUnread(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Integer count = notificationDao.countUnread(conn, userId);
            return count == null ? 0 : count;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取未读消息失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public int markRead(long userId, long id) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            notificationDao.markRead(conn, id, userId);
            Integer count = notificationDao.countUnread(conn, userId);
            return count == null ? 0 : count;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "更新消息状态失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
