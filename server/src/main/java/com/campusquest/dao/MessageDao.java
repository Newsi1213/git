package com.campusquest.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class MessageDao extends BaseDao {

    public static class Row {
        public long id;
        public long conversationId;
        public long senderId;
        public String senderNickname;
        public String msgType;
        public String content;
        public String imagePath;
        public String createdAt;
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Row row = new Row();
        row.id = rs.getLong("id");
        row.conversationId = rs.getLong("conversation_id");
        row.senderId = rs.getLong("sender_id");
        row.senderNickname = rs.getString("sender_nickname");
        row.msgType = rs.getString("msg_type");
        row.content = rs.getString("content");
        row.imagePath = rs.getString("image_path");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        row.createdAt = ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
        return row;
    }

    public long insertText(Connection conn, long conversationId, long senderId, String content)
            throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO messages(conversation_id, sender_id, msg_type, content) VALUES(?,?,'TEXT',?)",
                conversationId, senderId, content);
    }

    public long insertImage(Connection conn, long conversationId, long senderId, String imagePath)
            throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO messages(conversation_id, sender_id, msg_type, image_path) VALUES(?,?,'IMAGE',?)",
                conversationId, senderId, imagePath);
    }

    public Row findById(Connection conn, long id) throws SQLException {
        return queryOne(conn,
                "SELECT m.*, u.nickname sender_nickname FROM messages m JOIN users u ON m.sender_id = u.id"
                        + " WHERE m.id = ?",
                this::mapRow, id);
    }

    /**
     * afterId 大于 0 时只取该 id 之后的新消息（升序）；否则取最近一页历史消息（也按升序返回）。
     */
    public List<Row> list(Connection conn, long conversationId, long afterId, int offset, int size)
            throws SQLException {
        if (afterId > 0) {
            return queryList(conn,
                    "SELECT m.*, u.nickname sender_nickname FROM messages m JOIN users u ON m.sender_id = u.id"
                            + " WHERE m.conversation_id = ? AND m.id > ? ORDER BY m.id ASC LIMIT ?",
                    this::mapRow, conversationId, afterId, size);
        }
        return queryList(conn,
                "SELECT * FROM (SELECT m.*, u.nickname sender_nickname"
                        + " FROM messages m JOIN users u ON m.sender_id = u.id"
                        + " WHERE m.conversation_id = ? ORDER BY m.id DESC LIMIT ? OFFSET ?) x"
                        + " ORDER BY x.id ASC",
                this::mapRow, conversationId, size, offset);
    }

    public long count(Connection conn, long conversationId) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM messages WHERE conversation_id = ?", conversationId);
    }
}
