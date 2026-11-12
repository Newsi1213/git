package com.campusquest.service;

import com.campusquest.dao.UserDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分排行榜：积分降序，积分相同按完成任务数降序，再按注册顺序。
 */
public class LeaderboardService {

    private final UserDao userDao = new UserDao();

    public Map<String, Object> board(long currentUserId, int limit) {
        int size = Math.max(1, Math.min(100, limit));
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            List<Map<String, Object>> list = new ArrayList<>();
            List<UserDao.LeaderRow> rows = userDao.listLeaderboard(conn, size);
            for (int i = 0; i < rows.size(); i++) {
                UserDao.LeaderRow row = rows.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", i + 1);
                item.put("userId", row.id);
                item.put("nickname", row.nickname);
                item.put("credit", row.credit);
                item.put("points", row.points);
                item.put("completed", row.completed);
                list.add(item);
            }

            Map<String, Object> me = null;
            UserDao.LeaderRow mine = userDao.findLeaderRow(conn, currentUserId);
            if (mine != null) {
                long ahead = userDao.countAhead(conn, currentUserId, mine.points, mine.completed);
                me = new LinkedHashMap<>();
                me.put("rank", ahead + 1);
                me.put("userId", mine.id);
                me.put("nickname", mine.nickname);
                me.put("credit", mine.credit);
                me.put("points", mine.points);
                me.put("completed", mine.completed);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", list);
            data.put("me", me);
            return data;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取排行榜失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
