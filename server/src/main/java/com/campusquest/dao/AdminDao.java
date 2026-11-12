package com.campusquest.dao;

import com.campusquest.entity.Admin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminDao extends BaseDao {

    public Admin findByUsername(Connection conn, String username) throws SQLException {
        return queryOne(conn, "SELECT * FROM admins WHERE username = ?", new RowMapper<Admin>() {
            @Override
            public Admin map(ResultSet rs) throws SQLException {
                Admin a = new Admin();
                a.setId(rs.getLong("id"));
                a.setUsername(rs.getString("username"));
                a.setPasswordHash(rs.getString("password_hash"));
                a.setCreatedAt(rs.getTimestamp("created_at"));
                return a;
            }
        }, username);
    }
}
