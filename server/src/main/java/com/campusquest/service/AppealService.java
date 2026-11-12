package com.campusquest.service;

import com.campusquest.dao.AppealDao;
import com.campusquest.dao.TaskDao;
import com.campusquest.entity.Task;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.FileUtil;
import com.campusquest.util.TextUtil;
import com.campusquest.vo.AppealVO;

import javax.servlet.http.Part;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信用申诉：仅针对放弃任务产生的扣分记录，每任务一次，必须上传截图。
 */
public class AppealService {

    private final AppealDao appealDao = new AppealDao();
    private final TaskDao taskDao = new TaskDao();

    public Map<String, Object> submit(long userId, long taskId, String content, Part screenshot) {
        content = TextUtil.trim(content);
        if (TextUtil.isBlank(content)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写申诉理由");
        }
        if (content.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申诉理由最多 500 个字");
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Long taskIdValue = taskId;
            Integer punishCount = taskDao.queryInt(conn,
                    "SELECT COUNT(*) FROM credit_logs WHERE user_id = ? AND related_task_id = ?"
                            + " AND reason = 'ABANDON' AND change_amount < 0",
                    userId, taskIdValue);
            if (punishCount == null || punishCount == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该任务没有可申诉的扣分记录");
            }
            Task task = taskDao.findById(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (appealDao.findByUserTask(conn, userId, taskId) != null) {
                throw new BizException(ErrorCode.DUPLICATE_APPEAL, "该任务已提交过申诉");
            }
            if (screenshot == null || screenshot.getSize() <= 0) {
                throw new BizException(ErrorCode.FILE_MISSING, "请上传申诉截图");
            }
            String path = FileUtil.saveImage(screenshot);
            long appealId = appealDao.insert(conn, userId, taskId, content, path);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("appealId", appealId);
            data.put("status", "PENDING");
            data.put("screenshotPath", path);
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "提交申诉失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public List<AppealVO> listMine(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            List<AppealVO> list = new ArrayList<>();
            for (AppealDao.Row row : appealDao.listByUser(conn, userId)) {
                AppealVO vo = new AppealVO();
                vo.id = row.id;
                vo.taskId = row.taskId;
                vo.taskTitle = row.taskTitle;
                vo.content = row.content;
                vo.screenshotPath = row.screenshotPath;
                vo.status = row.status;
                vo.rejectReason = row.rejectReason;
                vo.createdAt = row.createdAt;
                vo.handledAt = row.handledAt;
                list.add(vo);
            }
            return list;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取我的申诉失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
