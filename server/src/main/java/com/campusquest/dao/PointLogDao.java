package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class PointLogDao extends BaseDao {

    public static class Row {
        public long id;
        public int changeAmount;
        public int balanceAfter;
        public String type;
        public String taskTitle;
        public String remark;
        public String createdAt;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.changeAmount = rs.getInt("change_amount");
        row.balanceAfter = rs.getInt("balance_after");
        row.type = rs.getString("type");
        row.taskTitle = rs.getString("task_title");
        row.remark = rs.getString("remark");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        row.createdAt = ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
        return row;
    }

    public List<Row> listByUser(Connection conn, long userId, String type, int offset, int size)
            throws SQLException {
        String sql = "SELECT p.*, t.title task_title FROM point_logs p"
                + " LEFT JOIN tasks t ON p.related_task_id = t.id"
                + " WHERE p.user_id = ?"
                + (type == null || type.isEmpty() ? "" : " AND p.type = ?")
                + " ORDER BY p.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (type != null && !type.isEmpty()) {
            params.add(type);
        }
        params.add(size);
        params.add(offset);
        return queryList(conn, sql, this::mapRow, params.toArray());
    }

    public long countByUser(Connection conn, long userId, String type) throws SQLException {
        String sql = "SELECT COUNT(*) FROM point_logs WHERE user_id = ?"
                + (type == null || type.isEmpty() ? "" : " AND type = ?");
        if (type == null || type.isEmpty()) {
            return queryLong(conn, sql, userId);
        }
        return queryLong(conn, sql, userId, type);
    }
}
