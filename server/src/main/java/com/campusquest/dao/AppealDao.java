package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class AppealDao extends BaseDao {

    public static class Row {
        public long id;
        public long userId;
        public long taskId;
        public String taskTitle;
        public String content;
        public String screenshotPath;
        public String status;
        public String rejectReason;
        public String createdAt;
        public String handledAt;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.userId = rs.getLong("user_id");
        row.taskId = rs.getLong("task_id");
        row.taskTitle = rs.getString("task_title");
        row.content = rs.getString("content");
        row.screenshotPath = rs.getString("screenshot_path");
        row.status = rs.getString("status");
        row.rejectReason = rs.getString("reject_reason");
        java.sql.Timestamp created = rs.getTimestamp("created_at");
        row.createdAt = created == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(created);
        java.sql.Timestamp handled = rs.getTimestamp("handled_at");
        row.handledAt = handled == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(handled);
        return row;
    }

    public Row findByUserTask(Connection conn, long userId, long taskId) throws SQLException {
        return queryOne(conn,
                "SELECT a.*, t.title task_title FROM appeals a LEFT JOIN tasks t ON a.task_id = t.id"
                        + " WHERE a.user_id = ? AND a.task_id = ?",
                this::mapRow, userId, taskId);
    }

    public long insert(Connection conn, long userId, long taskId, String content, String screenshotPath)
            throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO appeals(user_id, task_id, content, screenshot_path, status)"
                        + " VALUES(?,?,?,?,'PENDING')",
                userId, taskId, content, screenshotPath);
    }

    public List<Row> listByUser(Connection conn, long userId) throws SQLException {
        return queryList(conn,
                "SELECT a.*, t.title task_title FROM appeals a LEFT JOIN tasks t ON a.task_id = t.id"
                        + " WHERE a.user_id = ? ORDER BY a.id DESC",
                this::mapRow, userId);
    }
}
