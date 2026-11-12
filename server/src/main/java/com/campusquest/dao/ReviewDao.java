package com.campusquest.dao;

import com.campusquest.entity.Review;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ReviewDao extends BaseDao {

    public static final RowMapper<Review> MAPPER = new RowMapper<Review>() {
        @Override
        public Review map(ResultSet rs) throws SQLException {
            Review r = new Review();
            r.setId(rs.getLong("id"));
            r.setTaskId(rs.getLong("task_id"));
            r.setFromUserId(rs.getLong("from_user_id"));
            r.setToUserId(rs.getLong("to_user_id"));
            r.setRating(rs.getString("rating"));
            r.setComment(rs.getString("comment"));
            r.setCreatedAt(rs.getTimestamp("created_at"));
            return r;
        }
    };

    public long insert(Connection conn, long taskId, long fromUserId, long toUserId, String rating, String comment)
            throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO reviews(task_id, from_user_id, to_user_id, rating, comment)"
                        + " VALUES(?,?,?,?,?)",
                taskId, fromUserId, toUserId, rating, comment);
    }

    public Review findByTaskAndFrom(Connection conn, long taskId, long fromUserId) throws SQLException {
        return queryOne(conn, "SELECT * FROM reviews WHERE task_id = ? AND from_user_id = ?",
                MAPPER, taskId, fromUserId);
    }

    public List<Review> listByTask(Connection conn, long taskId) throws SQLException {
        return queryList(conn, "SELECT * FROM reviews WHERE task_id = ? ORDER BY id ASC", MAPPER, taskId);
    }
}
