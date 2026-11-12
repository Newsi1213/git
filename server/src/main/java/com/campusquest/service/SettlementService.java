package com.campusquest.service;

import com.campusquest.dao.BaseDao;
import com.campusquest.dao.TaskDao;
import com.campusquest.entity.Task;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算服务：完成确认、积分到账、24 小时超时自动确认。
 */
public class SettlementService extends BaseDao {

    private static long lastRunAt = 0L;

    private final TaskDao taskDao = new TaskDao();
    private final PointService pointService = new PointService();
    private final NotificationService notificationService = new NotificationService();

    /**
     * 完成结算：把待确认任务置为已完成并把预算积分结算给接单者。
     * 幂等：状态不是 PENDING_CONFIRM 时直接返回 false。
     */
    public boolean complete(long taskId, Long operatorUserId, boolean auto) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || !"PENDING_CONFIRM".equals(task.getStatus()) || task.getAcceptorId() == null) {
                rollback(conn);
                return false;
            }
            if (operatorUserId != null && task.getPublisherId() != operatorUserId) {
                rollback(conn);
                throw new BizException(ErrorCode.FORBIDDEN, "只有发布者可以确认完成");
            }
            int rows = update(conn,
                    "UPDATE tasks SET status='COMPLETED', finished_at=NOW()"
                            + " WHERE id=? AND status='PENDING_CONFIRM'",
                    taskId);
            if (rows == 0) {
                rollback(conn);
                return false;
            }
            pointService.earn(conn, task.getAcceptorId(), task.getPoints(), "INCOME", taskId,
                    "完成任务收入 " + task.getTaskNo());
            commit(conn);

            String suffix = auto ? "（系统自动确认）" : "";
            notificationService.notify(task.getPublisherId(), "SETTLEMENT", "任务已完成",
                    "你发布的任务「" + task.getTitle() + "」已完成，预算 " + task.getPoints() + " 积分已结算给接单同学" + suffix,
                    taskId);
            notificationService.notify(task.getAcceptorId(), "SETTLEMENT", "积分已到账",
                    "你完成的任务「" + task.getTitle() + "」已确认完成，" + task.getPoints() + " 积分已到账" + suffix,
                    taskId);
            return true;
        } catch (BizException e) {
            rollback(conn);
            throw e;
        } catch (Exception e) {
            rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "确认完成失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    /**
     * 扫描并结算已超时的待确认任务。整体加锁并做 3 秒节流，单条失败不影响其他任务。
     */
    public static synchronized void settleExpired() {
        long now = System.currentTimeMillis();
        if (now - lastRunAt < 3000) {
            return;
        }
        lastRunAt = now;

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            List<Long> ids = new ArrayList<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM tasks WHERE status='PENDING_CONFIRM'"
                            + " AND confirm_deadline IS NOT NULL AND confirm_deadline <= NOW()"
                            + " ORDER BY id ASC LIMIT 50")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
            }
            SettlementService service = new SettlementService();
            for (Long id : ids) {
                try {
                    service.complete(id, null, true);
                } catch (Exception ignored) {
                    // 单条失败只跳过，不中断整批
                }
            }
        } catch (SQLException ignored) {
            // 数据库不可用时跳过本轮结算
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
