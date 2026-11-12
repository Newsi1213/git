package com.campusquest.dao;

import com.campusquest.entity.Application;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ApplicationDao extends BaseDao {

    public static final RowMapper<Application> MAPPER = new RowMapper<Application>() {
        @Override
        public Application map(ResultSet rs) throws SQLException {
            Application a = new Application();
            a.setId(rs.getLong("id"));
            a.setTaskId(rs.getLong("task_id"));
            a.setApplicantId(rs.getLong("applicant_id"));
            a.setStatus(rs.getString("status"));
            a.setCreatedAt(rs.getTimestamp("created_at"));
            a.setAbandonedAt(rs.getTimestamp("abandoned_at"));
            a.setPenaltyApplied(rs.getInt("penalty_applied"));
            return a;
        }
    };

    public Application findByTaskAndApplicant(Connection conn, long taskId, long applicantId) throws SQLException {
        return queryOne(conn, "SELECT * FROM applications WHERE task_id = ? AND applicant_id = ?",
                MAPPER, taskId, applicantId);
    }

    public Application findById(Connection conn, long id) throws SQLException {
        return queryOne(conn, "SELECT * FROM applications WHERE id = ?", MAPPER, id);
    }

    public int countByTask(Connection conn, long taskId) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM applications WHERE task_id = ?", taskId);
    }

    public long insert(Connection conn, long taskId, long applicantId) throws SQLException {
        return insertReturnKey(conn,
                "INSERT INTO applications(task_id, applicant_id, status, created_at) VALUES(?,?,'PENDING',NOW())",
                taskId, applicantId);
    }

    public int reactivate(Connection conn, long applicationId) throws SQLException {
        return update(conn,
                "UPDATE applications SET status='PENDING', created_at=NOW(), abandoned_at=NULL WHERE id=?",
                applicationId);
    }

    public int setStatus(Connection conn, long applicationId, String status) throws SQLException {
        return update(conn, "UPDATE applications SET status=? WHERE id=?", status, applicationId);
    }

    public int selectOne(Connection conn, long taskId, long applicationId) throws SQLException {
        return update(conn,
                "UPDATE applications SET status='SELECTED' WHERE id=? AND task_id=? AND status='PENDING'",
                applicationId, taskId);
    }

    public int rejectOthers(Connection conn, long taskId, long exceptApplicationId) throws SQLException {
        return update(conn,
                "UPDATE applications SET status='REJECTED' WHERE task_id=? AND id<>? AND status='PENDING'",
                taskId, exceptApplicationId);
    }

    public int rejectAllPending(Connection conn, long taskId) throws SQLException {
        return update(conn, "UPDATE applications SET status='REJECTED' WHERE task_id=? AND status='PENDING'",
                taskId);
    }

    public int markAbandoned(Connection conn, long taskId, long applicantId, int penaltyApplied)
            throws SQLException {
        return update(conn,
                "UPDATE applications SET status='ABANDONED', abandoned_at=NOW(), penalty_applied=?"
                        + " WHERE task_id=? AND applicant_id=?",
                penaltyApplied, taskId, applicantId);
    }

    public List<ApplicationRow> listByTask(Connection conn, long taskId) throws SQLException {
        List<ApplicationRow> list = new ArrayList<>();
        String sql = "SELECT a.*, u.nickname, u.credit FROM applications a JOIN users u ON a.applicant_id=u.id"
                + " WHERE a.task_id=? ORDER BY a.created_at ASC, a.id ASC";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApplicationRow row = new ApplicationRow();
                    row.id = rs.getLong("id");
                    row.taskId = rs.getLong("task_id");
                    row.applicantId = rs.getLong("applicant_id");
                    row.status = rs.getString("status");
                    row.applicantNickname = rs.getString("nickname");
                    row.applicantCredit = rs.getInt("credit");
                    java.sql.Timestamp ts = rs.getTimestamp("created_at");
                    row.createdAt = ts == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
                    list.add(row);
                }
            }
        }
        return list;
    }

    public static class ApplicationRow {
        public long id;
        public long taskId;
        public long applicantId;
        public String status;
        public String applicantNickname;
        public int applicantCredit;
        public String createdAt;
    }
}
