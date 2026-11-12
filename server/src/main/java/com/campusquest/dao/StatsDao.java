package com.campusquest.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class StatsDao extends BaseDao {

    public static class DayCount {
        public String date;
        public int count;
    }

    public static class CategoryCount {
        public String category;
        public int count;
    }

    public static class TopRow {
        public long taskId;
        public String title;
        public String category;
        public int points;
        public int applyCount;
    }

    public int countInProgress(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE status = 'IN_PROGRESS'"
                + " AND (publisher_id = ? OR acceptor_id = ?)", userId, userId);
    }

    public int countToConfirm(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE publisher_id = ? AND status = 'PENDING_CONFIRM'",
                userId);
    }

    public int countPublishedActive(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE publisher_id = ?"
                + " AND status IN ('WAITING','IN_PROGRESS') AND offline = 0", userId);
    }

    public int countPublishedWaiting(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE publisher_id = ?"
                + " AND status = 'WAITING' AND offline = 0", userId);
    }

    public List<DayCount> dailyCounts(Connection conn) throws SQLException {
        return queryList(conn, "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') d, COUNT(*) c FROM tasks"
                        + " WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"
                        + " GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY d",
                rs -> {
                    DayCount row = new DayCount();
                    row.date = rs.getString("d");
                    row.count = rs.getInt("c");
                    return row;
                });
    }

    public List<CategoryCount> categoryCounts(Connection conn) throws SQLException {
        return queryList(conn, "SELECT category, COUNT(*) c FROM tasks WHERE offline = 0"
                        + " GROUP BY category ORDER BY c DESC",
                rs -> {
                    CategoryCount row = new CategoryCount();
                    row.category = rs.getString("category");
                    row.count = rs.getInt("c");
                    return row;
                });
    }

    public List<TopRow> topTasks(Connection conn, int limit) throws SQLException {
        return queryList(conn, "SELECT t.id, t.title, t.category, t.points, COUNT(a.id) apply_count"
                        + " FROM tasks t LEFT JOIN applications a ON a.task_id = t.id"
                        + " WHERE t.offline = 0 GROUP BY t.id, t.title, t.category, t.points"
                        + " ORDER BY apply_count DESC, t.id DESC LIMIT ?",
                rs -> {
                    TopRow row = new TopRow();
                    row.taskId = rs.getLong("id");
                    row.title = rs.getString("title");
                    row.category = rs.getString("category");
                    row.points = rs.getInt("points");
                    row.applyCount = rs.getInt("apply_count");
                    return row;
                }, limit);
    }

    public int personalPublished(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE publisher_id = ?"
                + " AND status <> 'CANCELLED' AND offline = 0", userId);
    }

    public int personalCompleted(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE publisher_id = ? AND status = 'COMPLETED'", userId);
    }

    public int personalAccepted(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM tasks WHERE acceptor_id = ? AND status = 'COMPLETED'", userId);
    }

    public int personalIncome(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT IFNULL(SUM(change_amount), 0) FROM point_logs"
                + " WHERE user_id = ? AND change_amount > 0", userId);
    }

    public int personalExpense(Connection conn, long userId) throws SQLException {
        return queryInt(conn, "SELECT IFNULL(-SUM(change_amount), 0) FROM point_logs"
                + " WHERE user_id = ? AND change_amount < 0", userId);
    }
}
