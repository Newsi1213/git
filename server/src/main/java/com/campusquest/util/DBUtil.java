package com.campusquest.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库连接工具。单机项目，直接使用 DriverManager，不引入连接池依赖。
 */
public final class DBUtil {

    private static final String URL =
            "jdbc:mysql://localhost:3306/campus_quest?useUnicode=true&characterEncoding=utf8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL 驱动未找到：" + e.getMessage());
        }
    }

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void closeQuietly(AutoCloseable... resources) {
        if (resources == null) {
            return;
        }
        for (AutoCloseable r : resources) {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 阶段 2 自测用：连接数据库并打印版本。 */
    public static void main(String[] args) throws Exception {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                System.out.println("MySQL version: " + rs.getString(1));
                System.out.println("Database: " + conn.getCatalog());
            }
        }
    }
}
