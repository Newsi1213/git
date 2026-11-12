package com.campusquest.service;

import com.campusquest.dao.BaseDao;
import com.campusquest.dao.CreditLogDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.PageResult;
import com.campusquest.vo.CreditLogVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 信用分变动统一入口：所有 credit_logs 写入都经由本类，信用分限制在 0-100。
 */
public class CreditService extends BaseDao {

    /** 调整信用分，返回调整后的信用分。delta 为期望变动值，实际变动受 0-100 边界约束。 */
    public int change(Connection conn, long userId, int delta, String reason, Long taskId, String remark)
            throws SQLException {
        int current = credit(conn, userId);
        int next = Math.max(0, Math.min(100, current + delta));
        int applied = next - current;
        if (applied != 0) {
            update(conn, "UPDATE users SET credit = ? WHERE id = ?", next, userId);
        }
        String finalRemark = remark;
        if (applied == 0 && delta != 0) {
            finalRemark = (remark == null ? "" : remark + " ") + "已封顶";
        }
        update(conn,
                "INSERT INTO credit_logs(user_id, change_amount, reason, related_task_id, remark)"
                        + " VALUES(?,?,?,?,?)",
                userId, applied, reason, taskId, finalRemark);
        return next;
    }

    public int credit(Connection conn, long userId) throws SQLException {
        Integer value = queryInt(conn, "SELECT credit FROM users WHERE id = ?", userId);
        return value == null ? 100 : value;
    }

    private final CreditLogDao creditLogDao = new CreditLogDao();

    /** 分页查询信用变动记录，附带任务标题与申诉状态。 */
    public PageResult<CreditLogVO> logs(long userId, int page, int size) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = creditLogDao.countByUser(conn, userId);
            List<CreditLogVO> list = new ArrayList<>();
            for (CreditLogDao.Row row : creditLogDao.listByUser(conn, userId, (page - 1) * size, size)) {
                CreditLogVO vo = new CreditLogVO();
                vo.id = row.id;
                vo.changeAmount = row.changeAmount;
                vo.reason = row.reason;
                vo.taskId = row.taskId;
                vo.taskTitle = row.taskTitle;
                vo.remark = row.remark;
                vo.createdAt = row.createdAt;
                vo.appealId = row.appealId;
                vo.appealStatus = row.appealStatus;
                list.add(vo);
            }
            return PageResult.of(page, size, total, list);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取信用记录失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
