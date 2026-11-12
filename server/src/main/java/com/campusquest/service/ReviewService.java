package com.campusquest.service;

import com.campusquest.dao.ReviewDao;
import com.campusquest.dao.TaskDao;
import com.campusquest.entity.Review;
import com.campusquest.entity.Task;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.TextUtil;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 双方互评：已完成任务可评价对方一次，好评 +3 信用，差评 -3 信用。
 */
public class ReviewService {

    private final TaskDao taskDao = new TaskDao();
    private final ReviewDao reviewDao = new ReviewDao();
    private final CreditService creditService = new CreditService();
    private final NotificationService notificationService = new NotificationService();

    public Map<String, Object> review(long taskId, long fromUserId, String rating, String comment) {
        rating = TextUtil.trim(rating);
        comment = TextUtil.trim(comment);
        if (!"GOOD".equals(rating) && !"BAD".equals(rating)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择好评或差评");
        }
        if (comment.length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价内容最多 100 个字");
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (!"COMPLETED".equals(task.getStatus())) {
                throw new BizException(ErrorCode.REVIEW_NOT_READY, "任务完成后才能评价");
            }
            boolean isPublisher = task.getPublisherId() == fromUserId;
            boolean isAcceptor = task.getAcceptorId() != null && task.getAcceptorId() == fromUserId;
            if (!isPublisher && !isAcceptor) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有任务双方可以评价");
            }
            if (reviewDao.findByTaskAndFrom(conn, taskId, fromUserId) != null) {
                throw new BizException(ErrorCode.DUPLICATE_REVIEW, "你已经评价过这个任务");
            }
            long toUserId = isPublisher ? task.getAcceptorId() : task.getPublisherId();
            reviewDao.insert(conn, taskId, fromUserId, toUserId, rating, comment);
            String reason = "GOOD".equals(rating) ? "GOOD_REVIEW" : "BAD_REVIEW";
            int delta = "GOOD".equals(rating) ? 3 : -3;
            int newCredit = creditService.change(conn, toUserId, delta, reason, taskId,
                    "GOOD".equals(rating) ? "收到好评" : "收到差评");
            taskDao.commit(conn);

            notificationService.notify(toUserId, "REVIEW_RECEIVED", "收到新的评价",
                    "任务「" + task.getTitle() + "」收到一条" + ("GOOD".equals(rating) ? "好评" : "差评")
                            + "，信用分变动 " + (delta > 0 ? "+" : "") + delta + "。",
                    taskId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("rating", rating);
            data.put("credit", newCredit);
            data.put("change", delta);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "评价失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    /** 查询本人对某任务的评价，没有则返回 null。 */
    public Review findMyReview(long taskId, long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            return reviewDao.findByTaskAndFrom(conn, taskId, userId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取评价失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
