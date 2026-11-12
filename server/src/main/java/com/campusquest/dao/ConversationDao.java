package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class ConversationDao extends BaseDao {

    public static class Row {
        public long id;
        public long taskId;
        public String taskNo;
        public String taskTitle;
        public String taskStatus;
        public long publisherId;
        public long acceptorId;
        public String publisherNickname;
        public String acceptorNickname;
        public int publisherCredit;
        public int acceptorCredit;
        public String lastContent;
        public String lastImagePath;
        public String lastAt;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.taskId = rs.getLong("task_id");
        row.taskNo = rs.getString("task_no");
        row.taskTitle = rs.getString("task_title");
        row.taskStatus = rs.getString("task_status");
        row.publisherId = rs.getLong("publisher_id");
        row.acceptorId = rs.getLong("acceptor_id");
        row.publisherNickname = rs.getString("publisher_nickname");
        row.acceptorNickname = rs.getString("acceptor_nickname");
        row.publisherCredit = rs.getInt("publisher_credit");
        row.acceptorCredit = rs.getInt("acceptor_credit");
        row.lastContent = rs.getString("last_content");
        row.lastImagePath = rs.getString("last_image_path");
        java.sql.Timestamp ts = rs.getTimestamp("last_at");
        row.lastAt = ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
        return row;
    }

    private static final String BASE_SELECT = "SELECT c.id, c.task_id, c.publisher_id, c.acceptor_id,"
            + " t.task_no, t.title task_title, t.status task_status,"
            + " pu.nickname publisher_nickname, pu.credit publisher_credit,"
            + " au.nickname acceptor_nickname, au.credit acceptor_credit,"
            + " (SELECT m.content FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1)"
            + " AS last_content,"
            + " (SELECT m.image_path FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1)"
            + " AS last_image_path,"
            + " (SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1)"
            + " AS last_at,"
            + " (SELECT m.id FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1)"
            + " AS last_id"
            + " FROM conversations c"
            + " JOIN tasks t ON c.task_id = t.id"
            + " JOIN users pu ON c.publisher_id = pu.id"
            + " JOIN users au ON c.acceptor_id = au.id";

    public Row findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, BASE_SELECT + " WHERE c.id = ?", this::mapRow, id);
    }

    public Row findByTask(Connection conn, long taskId) throws SQLException {
        return queryOne(conn, BASE_SELECT + " WHERE c.task_id = ?", this::mapRow, taskId);
    }

    public List<Row> listByUser(Connection conn, long userId) throws SQLException {
        return queryList(conn,
                BASE_SELECT + " WHERE c.publisher_id = ? OR c.acceptor_id = ?"
                        + " ORDER BY COALESCE((SELECT MAX(m.created_at) FROM messages m"
                        + " WHERE m.conversation_id = c.id), c.created_at) DESC, c.id DESC",
                this::mapRow, userId, userId);
    }
}
