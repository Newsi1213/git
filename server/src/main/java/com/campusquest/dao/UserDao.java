package com.campusquest.dao;

import com.campusquest.entity.User;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDao extends BaseDao {

    private final RowMapper<User> mapper = new RowMapper<User>() {
        @Override
        public User map(ResultSet rs) throws SQLException {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setPhone(rs.getString("phone"));
            u.setNickname(rs.getString("nickname"));
            u.setPasswordHash(rs.getString("password_hash"));
            u.setPoints(rs.getInt("points"));
            u.setCredit(rs.getInt("credit"));
            u.setAvatarPath(rs.getString("avatar_path"));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            return u;
        }
    };

    public User findByPhone(Connection conn, String phone) throws SQLException {
        return queryOne(conn, "SELECT * FROM users WHERE phone = ?", mapper, phone);
    }

    public User findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, "SELECT * FROM users WHERE id = ?", mapper, id);
    }

    public long insert(Connection conn, String phone, String nickname, String passwordHash) throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO users(phone, nickname, password_hash, points, credit) VALUES(?,?,?,100,100)",
                phone, nickname, passwordHash);
    }

    public int updatePoints(Connection conn, long userId, int points) throws SQLException {
        return update(conn, "UPDATE users SET points = ? WHERE id = ?", points, userId);
    }

    public int updateCredit(Connection conn, long userId, int credit) throws SQLException {
        return update(conn, "UPDATE users SET credit = ? WHERE id = ?", credit, userId);
    }

    public int updateAvatar(Connection conn, long userId, String avatarPath) throws SQLException {
        return update(conn, "UPDATE users SET avatar_path = ? WHERE id = ?", avatarPath, userId);
    }

    /** 排行榜行：积分降序、完成任务数降序、id 升序。 */
    public static class LeaderRow {
        public long id;
        public String nickname;
        public int credit;
        public int points;
        public int completed;
    }

    private LeaderRow mapLeader(ResultSet rs) throws SQLException {
        LeaderRow row = new LeaderRow();
        row.id = rs.getLong("id");
        row.nickname = rs.getString("nickname");
        row.credit = rs.getInt("credit");
        row.points = rs.getInt("points");
        row.completed = rs.getInt("completed");
        return row;
    }

    private static final String LEADER_COLUMNS =
            "SELECT u.id, u.nickname, u.credit, u.points,"
                    + " (SELECT COUNT(*) FROM tasks t WHERE t.acceptor_id = u.id AND t.status = 'COMPLETED') completed"
                    + " FROM users u";

    public java.util.List<LeaderRow> listLeaderboard(Connection conn, int limit) throws SQLException {
        return queryList(conn, LEADER_COLUMNS + " ORDER BY u.points DESC, completed DESC, u.id ASC LIMIT ?",
                this::mapLeader, limit);
    }

    public LeaderRow findLeaderRow(Connection conn, long userId) throws SQLException {
        return queryOne(conn, LEADER_COLUMNS + " WHERE u.id = ?", this::mapLeader, userId);
    }

    /** 严格排在我前面的人数。 */
    public long countAhead(Connection conn, long userId, int points, int completed) throws SQLException {
        return queryLong(conn,
                "SELECT COUNT(*) FROM users u WHERE"
                        + " (u.points > ?)"
                        + " OR (u.points = ? AND (SELECT COUNT(*) FROM tasks t WHERE t.acceptor_id = u.id"
                        + " AND t.status = 'COMPLETED') > ?)"
                        + " OR (u.points = ? AND (SELECT COUNT(*) FROM tasks t WHERE t.acceptor_id = u.id"
                        + " AND t.status = 'COMPLETED') = ? AND u.id < ?)",
                points, points, completed, points, completed, userId);
    }
}
