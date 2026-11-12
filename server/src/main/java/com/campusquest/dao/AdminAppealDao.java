package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 管理端申诉查询与状态更新。
 */
public class AdminAppealDao extends BaseDao {

    private static final String BASE_SELECT =
            "SELECT a.*, u.nickname user_nickname, u.credit user_credit,"
                    + " t.task_no, t.title task_title, t.status task_status"
                    + " FROM appeals a"
                    + " JOIN users u ON a.user_id = u.id"
                    + " LEFT JOIN tasks t ON a.task_id = t.id";

    public static class Row {
        public long id;
        public long userId;
        public long taskId;
        public String userNickname;
        public int userCredit;
        public String taskNo;
        public String taskTitle;
        public String taskStatus;
        public String content;
        public String screenshotPath;
        public String status;
        public String rejectReason;
        public String createdAt;
        public String handledAt;
    }

    public static class CreditRow {
        public int changeAmount;
        public String reason;
        public String remark;
        public String createdAt;
    }

    private String fmt(java.sql.Timestamp ts) {
        return ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.userId = rs.getLong("user_id");
        row.taskId = rs.getLong("task_id");
        row.userNickname = rs.getString("user_nickname");
        row.userCredit = rs.getInt("user_credit");
        row.taskNo = rs.getString("task_no");
        row.taskTitle = rs.getString("task_title");
        row.taskStatus = rs.getString("task_status");
        row.content = rs.getString("content");
        row.screenshotPath = rs.getString("screenshot_path");
        row.status = rs.getString("status");
        row.rejectReason = rs.getString("reject_reason");
        row.createdAt = fmt(rs.getTimestamp("created_at"));
        row.handledAt = fmt(rs.getTimestamp("handled_at"));
        return row;
    }

    public List<Row> list(Connection conn, String status, int offset, int size) throws SQLException {
        if (status == null || status.isEmpty()) {
            return queryList(conn, BASE_SELECT + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                    this::mapRow, size, offset);
        }
        return queryList(conn, BASE_SELECT + " WHERE a.status = ? ORDER BY a.id DESC LIMIT ? OFFSET ?",
                this::mapRow, status, size, offset);
    }

    public long count(Connection conn, String status) throws SQLException {
        if (status == null || status.isEmpty()) {
            return queryLong(conn, "SELECT COUNT(*) FROM appeals");
        }
        return queryLong(conn, "SELECT COUNT(*) FROM appeals WHERE status = ?", status);
    }

    public Row findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, BASE_SELECT + " WHERE a.id = ?", this::mapRow, id);
    }

    public Row findByIdForUpdate(Connection conn, long id) throws SQLException {
        return queryOne(conn, BASE_SELECT + " WHERE a.id = ? FOR UPDATE", this::mapRow, id);
    }

    public List<CreditRow> listCreditLogs(Connection conn, long userId, long taskId) throws SQLException {
        return queryList(conn,
                "SELECT change_amount, reason, remark, created_at FROM credit_logs"
                        + " WHERE user_id = ? AND related_task_id = ? ORDER BY id DESC",
                new RowMapper<CreditRow>() {
                    @Override
                    public CreditRow map(ResultSet rs) throws SQLException {
                        CreditRow row = new CreditRow();
                        row.changeAmount = rs.getInt("change_amount");
                        row.reason = rs.getString("reason");
                        row.remark = rs.getString("remark");
                        row.createdAt = fmt(rs.getTimestamp("created_at"));
                        return row;
                    }
                }, userId, taskId);
    }

    public int markApproved(Connection conn, long id, long adminId) throws SQLException {
        return update(conn,
                "UPDATE appeals SET status = 'APPROVED', handled_by = ?, handled_at = NOW()"
                        + " WHERE id = ? AND status = 'PENDING'",
                adminId, id);
    }

    public int markRejected(Connection conn, long id, long adminId, String reason) throws SQLException {
        return update(conn,
                "UPDATE appeals SET status = 'REJECTED', reject_reason = ?, handled_by = ?, handled_at = NOW()"
                        + " WHERE id = ? AND status = 'PENDING'",
                reason, adminId, id);
    }
}
