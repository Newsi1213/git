package com.campusquest.service;

import com.campusquest.dao.BaseDao;
import com.campusquest.dao.PointLogDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.PageResult;
import com.campusquest.vo.PointLogVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分变动统一入口：所有 point_logs 写入都经由本类，保证余额与流水一致。
 */
public class PointService extends BaseDao {

    /** 扣减积分。余额不足返回 false，调用方负责转成业务错误。 */
    public boolean spend(Connection conn, long userId, int amount, Long taskId, String remark) throws SQLException {
        int rows = update(conn, "UPDATE users SET points = points - ? WHERE id = ? AND points >= ?",
                amount, userId, amount);
        if (rows == 0) {
            return false;
        }
        writeLog(conn, userId, -amount, "PUBLISH", taskId, remark);
        return true;
    }

    /** 退回积分（取消任务）。 */
    public void refund(Connection conn, long userId, int amount, Long taskId, String remark) throws SQLException {
        update(conn, "UPDATE users SET points = points + ? WHERE id = ?", amount, userId);
        writeLog(conn, userId, amount, "REFUND", taskId, remark);
    }

    /** 增加积分（任务收入、充值等）。 */
    public void earn(Connection conn, long userId, int amount, String type, Long taskId, String remark)
            throws SQLException {
        update(conn, "UPDATE users SET points = points + ? WHERE id = ?", amount, userId);
        writeLog(conn, userId, amount, type, taskId, remark);
    }

    public int balance(Connection conn, long userId) throws SQLException {
        Integer value = queryInt(conn, "SELECT points FROM users WHERE id = ?", userId);
        return value == null ? 0 : value;
    }

    private void writeLog(Connection conn, long userId, int change, String type, Long taskId, String remark)
            throws SQLException {
        int balance = balance(conn, userId);
        update(conn,
                "INSERT INTO point_logs(user_id, change_amount, balance_after, type, related_task_id, remark)"
                        + " VALUES(?,?,?,?,?,?)",
                userId, change, balance, type, taskId, remark);
    }

    private final PointLogDao pointLogDao = new PointLogDao();

    /** 分页查询积分流水，可按类型过滤。 */
    public PageResult<PointLogVO> logs(long userId, String type, int page, int size) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = pointLogDao.countByUser(conn, userId, type);
            List<PointLogVO> list = new ArrayList<>();
            for (PointLogDao.Row row : pointLogDao.listByUser(conn, userId, type, (page - 1) * size, size)) {
                PointLogVO vo = new PointLogVO();
                vo.id = row.id;
                vo.changeAmount = row.changeAmount;
                vo.balanceAfter = row.balanceAfter;
                vo.type = row.type;
                vo.taskTitle = row.taskTitle;
                vo.remark = row.remark;
                vo.createdAt = row.createdAt;
                list.add(vo);
            }
            return PageResult.of(page, size, total, list);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取积分流水失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    /** 模拟充值：1 元 = 10 积分，单次 1-10000 元。 */
    public Map<String, Object> recharge(long userId, int amountYuan) {
        if (amountYuan < 1 || amountYuan > 10000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "充值金额需在 1-10000 元之间");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            begin(conn);
            earn(conn, userId, amountYuan * 10, "RECHARGE", null, "模拟充值 " + amountYuan + " 元");
            int balance = balance(conn, userId);
            commit(conn);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("added", amountYuan * 10);
            data.put("points", balance);
            return data;
        } catch (BizException e) {
            rollback(conn);
            throw e;
        } catch (Exception e) {
            rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "充值失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
