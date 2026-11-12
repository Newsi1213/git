package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class CreditLogDao extends BaseDao {

    public static class Row {
        public long id;
        public int changeAmount;
        public String reason;
        public Long taskId;
        public String taskTitle;
        public String remark;
        public String createdAt;
        public Long appealId;
        public String appealStatus;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.changeAmount = rs.getInt("change_amount");
        row.reason = rs.getString("reason");
        long taskId = rs.getLong("related_task_id");
        row.taskId = rs.wasNull() ? null : taskId;
        row.taskTitle = rs.getString("task_title");
        row.remark = rs.getString("remark");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        row.createdAt = ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
        long appealId = rs.getLong("appeal_id");
        row.appealId = rs.wasNull() ? null : appealId;
        row.appealStatus = rs.getString("appeal_status");
        return row;
    }

    public List<Row> listByUser(Connection conn, long userId, int offset, int size) throws SQLException {
        String sql = "SELECT c.*, t.title task_title, a.id appeal_id, a.status appeal_status"
                + " FROM credit_logs c"
                + " LEFT JOIN tasks t ON c.related_task_id = t.id"
                + " LEFT JOIN appeals a ON a.user_id = c.user_id AND a.task_id = c.related_task_id"
                + " WHERE c.user_id = ? ORDER BY c.id DESC LIMIT ? OFFSET ?";
        return queryList(conn, sql, this::mapRow, userId, size, offset);
    }

    public long countByUser(Connection conn, long userId) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM credit_logs WHERE user_id = ?", userId);
    }
}
