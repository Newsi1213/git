package com.campusquest.service;

import com.campusquest.dao.NotificationDao;
import com.campusquest.dao.StatsDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StatsService {

    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            "EXPRESS", "TAKEOUT", "ERRAND", "MOVING", "CLEANING", "OTHER");

    private final StatsDao statsDao = new StatsDao();
    private final NotificationDao notificationDao = new NotificationDao();

    public Map<String, Object> summary(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("inProgress", statsDao.countInProgress(conn, userId));
            data.put("toConfirm", statsDao.countToConfirm(conn, userId));
            data.put("publishedActive", statsDao.countPublishedActive(conn, userId));
            data.put("publishedWaiting", statsDao.countPublishedWaiting(conn, userId));
            Integer unread = notificationDao.countUnread(conn, userId);
            data.put("unreadCount", unread == null ? 0 : unread);
            return data;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取工作台摘要失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> personal(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("published", statsDao.personalPublished(conn, userId));
            data.put("completed", statsDao.personalCompleted(conn, userId));
            data.put("accepted", statsDao.personalAccepted(conn, userId));
            data.put("income", statsDao.personalIncome(conn, userId));
            data.put("expense", statsDao.personalExpense(conn, userId));
            return data;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取个人统计失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> overview() {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Map<String, Integer> dayMap = new HashMap<>();
            for (StatsDao.DayCount row : statsDao.dailyCounts(conn)) {
                dayMap.put(row.date, row.count);
            }
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> days = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                String date = today.minusDays(i).toString();
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", date);
                Integer count = dayMap.get(date);
                day.put("count", count == null ? 0 : count);
                days.add(day);
            }

            Map<String, Integer> categoryMap = new HashMap<>();
            int totalTasks = 0;
            for (StatsDao.CategoryCount row : statsDao.categoryCounts(conn)) {
                categoryMap.put(row.category, row.count);
                totalTasks += row.count;
            }
            int[] counts = new int[CATEGORY_ORDER.size()];
            for (int i = 0; i < CATEGORY_ORDER.size(); i++) {
                Integer count = categoryMap.get(CATEGORY_ORDER.get(i));
                counts[i] = count == null ? 0 : count;
            }
            int[] percents = distributePercent(counts, totalTasks);
            List<Map<String, Object>> categories = new ArrayList<>();
            for (int i = 0; i < CATEGORY_ORDER.size(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", CATEGORY_ORDER.get(i));
                item.put("count", counts[i]);
                item.put("percent", percents[i]);
                categories.add(item);
            }

            List<Map<String, Object>> top = new ArrayList<>();
            for (StatsDao.TopRow row : statsDao.topTasks(conn, 5)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("taskId", row.taskId);
                item.put("title", row.title);
                item.put("category", row.category);
                item.put("points", row.points);
                item.put("applyCount", row.applyCount);
                top.add(item);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("days", days);
            data.put("totalTasks", totalTasks);
            data.put("categories", categories);
            data.put("top", top);
            return data;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取看板数据失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    /** 用最大余数法分配百分比，保证六类合计正好 100。 */
    private int[] distributePercent(int[] counts, int total) {
        int[] percents = new int[counts.length];
        if (total <= 0) {
            return percents;
        }
        double[] fractions = new double[counts.length];
        int used = 0;
        for (int i = 0; i < counts.length; i++) {
            double raw = counts[i] * 100.0 / total;
            percents[i] = (int) Math.floor(raw);
            fractions[i] = raw - percents[i];
            used += percents[i];
        }
        int remain = 100 - used;
        boolean[] bumped = new boolean[counts.length];
        while (remain > 0) {
            int best = -1;
            for (int i = 0; i < counts.length; i++) {
                if (bumped[i]) {
                    continue;
                }
                if (best < 0 || fractions[i] > fractions[best]) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            percents[best]++;
            bumped[best] = true;
            remain--;
        }
        return percents;
    }
}
