package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class NotificationDao extends BaseDao {

    public static class Row {
        public long id;
        public String type;
        public String title;
        public String content;
        public Long taskId;
        public int isRead;
        public String createdAt;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.type = rs.getString("type");
        row.title = rs.getString("title");
        row.content = rs.getString("content");
        long taskId = rs.getLong("related_task_id");
        row.taskId = rs.wasNull() ? null : taskId;
        row.isRead = rs.getInt("is_read");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        row.createdAt = ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
        return row;
    }

    public void insert(Connection conn, long userId, String type, String title, String content, Long taskId)
            throws SQLException {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO notifications(user_id, type, title, content, related_task_id) VALUES(?,?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, content);
            if (taskId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setLong(5, taskId);
            }
            ps.executeUpdate();
        }
    }

    public List<Row> listByUser(Connection conn, long userId, boolean onlyUnread, int offset, int size)
            throws SQLException {
        String sql = "SELECT * FROM notifications WHERE user_id = ?"
                + (onlyUnread ? " AND is_read = 0" : "")
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(size);
        params.add(offset);
        return queryList(conn, sql, this::mapRow, params.toArray());
    }

    public long countByUser(Connection conn, long userId, boolean onlyUnread) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ?"
                + (onlyUnread ? " AND is_read = 0" : "");
        return queryLong(conn, sql, userId);
    }

    public int countUnread(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0", userId);
    }

    public int markRead(Connection conn, long id, long userId) throws SQLException {
        return update(conn, "UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ? AND is_read = 0",
                id, userId);
    }
}
