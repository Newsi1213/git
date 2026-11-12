package com.campusquest.dao;

import com.campusquest.entity.Task;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class TaskDao extends BaseDao {

    public static final RowMapper<Task> TASK_MAPPER = new RowMapper<Task>() {
        @Override
        public Task map(ResultSet rs) throws SQLException {
            Task t = new Task();
            t.setId(rs.getLong("id"));
            t.setTaskNo(rs.getString("task_no"));
            t.setCategory(rs.getString("category"));
            t.setTitle(rs.getString("title"));
            t.setDescription(rs.getString("description"));
            t.setPoints(rs.getInt("points"));
            t.setLocationDesc(rs.getString("location_desc"));
            t.setTimeDesc(rs.getString("time_desc"));
            t.setPublisherId(rs.getLong("publisher_id"));
            long acceptor = rs.getLong("acceptor_id");
            t.setAcceptorId(rs.wasNull() ? null : acceptor);
            t.setStatus(rs.getString("status"));
            t.setCreatedAt(rs.getTimestamp("created_at"));
            t.setAcceptedAt(rs.getTimestamp("accepted_at"));
            t.setDeliveredAt(rs.getTimestamp("delivered_at"));
            t.setConfirmDeadline(rs.getTimestamp("confirm_deadline"));
            t.setFinishedAt(rs.getTimestamp("finished_at"));
            t.setOffline(rs.getInt("offline"));
            t.setOfflineReason(rs.getString("offline_reason"));
            t.setOfflineAt(rs.getTimestamp("offline_at"));
            return t;
        }
    };

    public Task findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, "SELECT * FROM tasks WHERE id = ?", TASK_MAPPER, id);
    }

    public Task findByIdForUpdate(Connection conn, long id) throws SQLException {
        return queryOne(conn, "SELECT * FROM tasks WHERE id = ? FOR UPDATE", TASK_MAPPER, id);
    }

    public String nextTaskNo(Connection conn) throws SQLException {
        String prefix = "CQ-" + new SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + "-";
        String max = queryOne(conn, "SELECT MAX(task_no) FROM tasks WHERE task_no LIKE ?",
                new RowMapper<String>() {
                    @Override
                    public String map(ResultSet rs) throws SQLException {
                        return rs.getString(1);
                    }
                }, prefix + "%");
        int seq = 1;
        if (max != null && max.length() >= prefix.length() + 3) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return prefix + String.format("%03d", seq);
    }

    public long insert(Connection conn, Task t) throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO tasks(task_no, category, title, description, points, location_desc, time_desc,"
                        + " publisher_id, status) VALUES(?,?,?,?,?,?,?,?,'WAITING')",
                t.getTaskNo(), t.getCategory(), t.getTitle(), t.getDescription(), t.getPoints(),
                t.getLocationDesc(), t.getTimeDesc(), t.getPublisherId());
    }

    public int updateStatus(Connection conn, long id, String status) throws SQLException {
        return update(conn, "UPDATE tasks SET status = ? WHERE id = ?", status, id);
    }

    public List<TaskCardVOBuilder> listLobby(Connection conn, String category, String keyword, String sort,
                                             boolean onlyWaiting, int offset, int size) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.task_no, t.category, t.title, t.points, t.status, t.publisher_id,"
                        + " t.created_at, u.nickname publisher_nickname, u.credit publisher_credit,"
                        + " (SELECT COUNT(*) FROM applications a WHERE a.task_id = t.id) application_count"
                        + " FROM tasks t JOIN users u ON t.publisher_id = u.id WHERE t.offline = 0");
        List<Object> params = new ArrayList<>();
        if (onlyWaiting) {
            sql.append(" AND t.status = 'WAITING'");
        } else {
            sql.append(" AND t.status <> 'CANCELLED'");
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND t.category = ?");
            params.add(category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (t.title LIKE ? OR t.description LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if ("points_desc".equals(sort)) {
            sql.append(" ORDER BY t.points DESC, t.id DESC");
        } else if ("points_asc".equals(sort)) {
            sql.append(" ORDER BY t.points ASC, t.id DESC");
        } else {
            sql.append(" ORDER BY t.created_at DESC, t.id DESC");
        }
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        final List<TaskCardVOBuilder> list = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TaskCardVOBuilder b = new TaskCardVOBuilder();
                    b.id = rs.getLong("id");
                    b.taskNo = rs.getString("task_no");
                    b.category = rs.getString("category");
                    b.title = rs.getString("title");
                    b.points = rs.getInt("points");
                    b.status = rs.getString("status");
                    b.publisherId = rs.getLong("publisher_id");
                    b.publisherNickname = rs.getString("publisher_nickname");
                    b.publisherCredit = rs.getInt("publisher_credit");
                    b.applicationCount = rs.getLong("application_count");
                    Timestamp ts = rs.getTimestamp("created_at");
                    b.createdAt = ts == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
                    list.add(b);
                }
            }
        }
        return list;
    }

    public long countLobby(Connection conn, String category, String keyword, boolean onlyWaiting) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tasks t WHERE t.offline = 0");
        List<Object> params = new ArrayList<>();
        if (onlyWaiting) {
            sql.append(" AND t.status = 'WAITING'");
        } else {
            sql.append(" AND t.status <> 'CANCELLED'");
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND t.category = ?");
            params.add(category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (t.title LIKE ? OR t.description LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        return queryLong(conn, sql.toString(), params.toArray());
    }

    public List<MyTaskRow> listMinePublished(Connection conn, long userId, String status, int offset, int size)
            throws SQLException {
        String sql = "SELECT t.*, u.nickname counterpart_nickname, rv.rating my_review_rating,"
                + " (SELECT COUNT(*) FROM applications a WHERE a.task_id=t.id) application_count,"
                + " GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), t.confirm_deadline)) remaining_seconds"
                + " FROM tasks t LEFT JOIN users u ON t.acceptor_id=u.id"
                + " LEFT JOIN reviews rv ON rv.task_id=t.id AND rv.from_user_id=?"
                + " WHERE t.publisher_id=? AND t.offline=0"
                + (status == null || status.isEmpty() ? "" : " AND t.status=?")
                + " ORDER BY t.created_at DESC, t.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        if (status != null && !status.isEmpty()) {
            params.add(status);
        }
        params.add(size);
        params.add(offset);
        return queryMyTasks(conn, sql, params);
    }

    public long countMinePublished(Connection conn, long userId, String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tasks WHERE publisher_id=? AND offline=0"
                + (status == null || status.isEmpty() ? "" : " AND status=?");
        if (status == null || status.isEmpty()) {
            return queryLong(conn, sql, userId);
        }
        return queryLong(conn, sql, userId, status);
    }

    public List<MyTaskRow> listMineAccepted(Connection conn, long userId, String status, int offset, int size)
            throws SQLException {
        String sql = "SELECT t.*, u.nickname counterpart_nickname, rv.rating my_review_rating,"
                + " (SELECT COUNT(*) FROM applications a WHERE a.task_id=t.id) application_count,"
                + " GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), t.confirm_deadline)) remaining_seconds"
                + " FROM tasks t JOIN users u ON t.publisher_id=u.id"
                + " LEFT JOIN reviews rv ON rv.task_id=t.id AND rv.from_user_id=?"
                + " WHERE t.acceptor_id=? AND t.offline=0"
                + (status == null || status.isEmpty() ? "" : " AND t.status=?")
                + " ORDER BY t.accepted_at DESC, t.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        if (status != null && !status.isEmpty()) {
            params.add(status);
        }
        params.add(size);
        params.add(offset);
        return queryMyTasks(conn, sql, params);
    }

    public long countMineAccepted(Connection conn, long userId, String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tasks WHERE acceptor_id=? AND offline=0"
                + (status == null || status.isEmpty() ? "" : " AND status=?");
        if (status == null || status.isEmpty()) {
            return queryLong(conn, sql, userId);
        }
        return queryLong(conn, sql, userId, status);
    }

    private List<MyTaskRow> queryMyTasks(Connection conn, String sql, List<Object> params) throws SQLException {
        List<MyTaskRow> list = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MyTaskRow row = new MyTaskRow();
                    row.id = rs.getLong("id");
                    row.taskNo = rs.getString("task_no");
                    row.category = rs.getString("category");
                    row.title = rs.getString("title");
                    row.points = rs.getInt("points");
                    row.status = rs.getString("status");
                    row.counterpartNickname = rs.getString("counterpart_nickname");
                    row.applicationCount = rs.getLong("application_count");
                    row.createdAt = fmtTs(rs.getTimestamp("created_at"));
                    row.acceptedAt = fmtTs(rs.getTimestamp("accepted_at"));
                    row.confirmDeadline = fmtTs(rs.getTimestamp("confirm_deadline"));
                    long remain = rs.getLong("remaining_seconds");
                    row.remainingSeconds = rs.wasNull() ? null : remain;
                    row.myReviewRating = rs.getString("my_review_rating");
                    row.offline = rs.getInt("offline");
                    list.add(row);
                }
            }
        }
        return list;
    }

    private String fmtTs(java.sql.Timestamp ts) {
        return ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
    }

    /** 管理端任务列表行。 */
    public static class AdminTaskRow {
        public long id;
        public String taskNo;
        public String category;
        public String title;
        public int points;
        public String status;
        public int offline;
        public String offlineReason;
        public String offlineAt;
        public String publisherNickname;
        public String createdAt;
    }

    public List<AdminTaskRow> adminList(Connection conn, String keyword, Integer offline, int offset, int size)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT t.*, u.nickname publisher_nickname FROM tasks t"
                        + " JOIN users u ON t.publisher_id = u.id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendAdminFilter(sql, params, keyword, offline);
        sql.append(" ORDER BY t.id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        return queryList(conn, sql.toString(), this::mapAdminTask, params.toArray());
    }

    public long adminCount(Connection conn, String keyword, Integer offline) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM tasks t JOIN users u ON t.publisher_id = u.id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendAdminFilter(sql, params, keyword, offline);
        return queryLong(conn, sql.toString(), params.toArray());
    }

    private void appendAdminFilter(StringBuilder sql, List<Object> params, String keyword, Integer offline) {
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (t.title LIKE ? OR t.task_no LIKE ? OR u.nickname LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (offline != null) {
            sql.append(" AND t.offline = ?");
            params.add(offline);
        }
    }

    private AdminTaskRow mapAdminTask(ResultSet rs) throws SQLException {
        AdminTaskRow row = new AdminTaskRow();
        row.id = rs.getLong("id");
        row.taskNo = rs.getString("task_no");
        row.category = rs.getString("category");
        row.title = rs.getString("title");
        row.points = rs.getInt("points");
        row.status = rs.getString("status");
        row.offline = rs.getInt("offline");
        row.offlineReason = rs.getString("offline_reason");
        row.offlineAt = fmtTs(rs.getTimestamp("offline_at"));
        row.publisherNickname = rs.getString("publisher_nickname");
        row.createdAt = fmtTs(rs.getTimestamp("created_at"));
        return row;
    }

    public int setOffline(Connection conn, long id, String reason) throws SQLException {
        return update(conn,
                "UPDATE tasks SET offline = 1, offline_reason = ?, offline_at = NOW()"
                        + " WHERE id = ? AND offline = 0",
                reason, id);
    }

    public int setOnline(Connection conn, long id) throws SQLException {
        return update(conn,
                "UPDATE tasks SET offline = 0, offline_reason = NULL, offline_at = NULL"
                        + " WHERE id = ? AND offline = 1",
                id);
    }

    /** 查询用的临时结构，由 Service 转成 VO。 */
    public static class TaskCardVOBuilder {
        public long id;
        public String taskNo;
        public String category;
        public String title;
        public int points;
        public String status;
        public long publisherId;
        public String publisherNickname;
        public int publisherCredit;
        public long applicationCount;
        public String createdAt;
    }

    public static class MyTaskRow {
        public long id;
        public String taskNo;
        public String category;
        public String title;
        public int points;
        public String status;
        public String counterpartNickname;
        public long applicationCount;
        public String createdAt;
        public String acceptedAt;
        public String confirmDeadline;
        public Long remainingSeconds;
        public String myReviewRating;
        public int offline;
    }
}
