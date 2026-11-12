package com.campusquest.service;

import com.campusquest.dao.ApplicationDao;
import com.campusquest.dao.ConversationDao;
import com.campusquest.dao.TaskDao;
import com.campusquest.dao.UserDao;
import com.campusquest.entity.Application;
import com.campusquest.entity.Task;
import com.campusquest.entity.User;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.PageResult;
import com.campusquest.util.TextUtil;
import com.campusquest.vo.MyTaskVO;
import com.campusquest.vo.TaskCardVO;
import com.campusquest.vo.TaskDetailVO;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskService {

    private static final List<String> CATEGORIES = Arrays.asList(
            "EXPRESS", "TAKEOUT", "ERRAND", "MOVING", "CLEANING", "OTHER");

    private final TaskDao taskDao = new TaskDao();
    private final UserDao userDao = new UserDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final ConversationDao conversationDao = new ConversationDao();
    private final PointService pointService = new PointService();
    private final ReviewService reviewService = new ReviewService();

    public Map<String, Object> publish(long userId, String category, String title, String description, int points,
                                       String locationDesc, String timeDesc) {
        category = TextUtil.trim(category);
        title = TextUtil.trim(title);
        description = TextUtil.trim(description);
        locationDesc = TextUtil.trim(locationDesc);
        timeDesc = TextUtil.trim(timeDesc);

        if (!CATEGORIES.contains(category)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择任务分类");
        }
        if (title.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题不能为空，最多 60 个字");
        }
        if (title.length() > 60) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题最多 60 个字");
        }
        if (description.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "描述不能为空");
        }
        if (points < 1 || points > 999) {
            throw new BizException(ErrorCode.PARAM_INVALID, "积分预算需在 1 到 999 之间");
        }
        if (locationDesc.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "地点说明不能为空");
        }
        if (locationDesc.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "地点说明最多 255 个字");
        }
        if (timeDesc.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间说明不能为空");
        }
        if (timeDesc.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间说明最多 255 个字");
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
            }
            if (user.getCredit() < 90) {
                throw new BizException(ErrorCode.CREDIT_PUBLISH_LOW,
                        "信用分低于 90，暂不能发布任务，当前信用分 " + user.getCredit());
            }
            taskDao.begin(conn);
            Task task = new Task();
            task.setCategory(category);
            task.setTitle(title);
            task.setDescription(description);
            task.setPoints(points);
            task.setLocationDesc(locationDesc);
            task.setTimeDesc(timeDesc);
            task.setPublisherId(userId);
            task.setTaskNo(taskDao.nextTaskNo(conn));
            long taskId = taskDao.insert(conn, task);
            if (!pointService.spend(conn, userId, points, taskId, "发布任务 " + task.getTaskNo())) {
                int balance = pointService.balance(conn, userId);
                throw new BizException(ErrorCode.POINTS_NOT_ENOUGH,
                        "积分不足，当前余额 " + balance + "，本次需要 " + points);
            }
            int balance = pointService.balance(conn, userId);
            taskDao.commit(conn);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("taskNo", task.getTaskNo());
            data.put("points", points);
            data.put("balance", balance);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "发布任务失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public PageResult<TaskCardVO> lobby(String category, String keyword, String sort, boolean onlyWaiting,
                                        int page, int size) {
        category = TextUtil.trim(category);
        keyword = TextUtil.trim(keyword);
        if (!category.isEmpty() && !CATEGORIES.contains(category)) {
            category = "";
        }
        if (keyword.length() > 40) {
            keyword = keyword.substring(0, 40);
        }
        if (!"points_desc".equals(sort) && !"points_asc".equals(sort)) {
            sort = "latest";
        }
        page = Math.max(1, page);
        size = Math.max(1, Math.min(50, size));

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            long total = taskDao.countLobby(conn, category, keyword, onlyWaiting);
            List<TaskDao.TaskCardVOBuilder> rows = taskDao.listLobby(conn, category, keyword, sort, onlyWaiting,
                    (page - 1) * size, size);
            List<TaskCardVO> list = new ArrayList<>();
            for (TaskDao.TaskCardVOBuilder b : rows) {
                TaskCardVO vo = new TaskCardVO();
                vo.id = b.id;
                vo.taskNo = b.taskNo;
                vo.category = b.category;
                vo.title = b.title;
                vo.points = b.points;
                vo.status = b.status;
                vo.publisherId = b.publisherId;
                vo.publisherNickname = b.publisherNickname;
                vo.publisherCredit = b.publisherCredit;
                vo.applicationCount = b.applicationCount;
                vo.createdAt = b.createdAt;
                list.add(vo);
            }
            return PageResult.of(page, size, total, list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取任务列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public PageResult<MyTaskVO> mine(long userId, String type, String status, int page, int size) {
        if (!"published".equals(type) && !"accepted".equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不正确");
        }
        status = TextUtil.trim(status);
        if (!status.isEmpty() && !Arrays.asList("WAITING", "IN_PROGRESS", "PENDING_CONFIRM", "COMPLETED",
                "CANCELLED").contains(status)) {
            status = "";
        }
        page = Math.max(1, page);
        size = Math.max(1, Math.min(50, size));
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            boolean published = "published".equals(type);
            long total = published ? taskDao.countMinePublished(conn, userId, status)
                    : taskDao.countMineAccepted(conn, userId, status);
            List<TaskDao.MyTaskRow> rows = published
                    ? taskDao.listMinePublished(conn, userId, status, (page - 1) * size, size)
                    : taskDao.listMineAccepted(conn, userId, status, (page - 1) * size, size);
            List<MyTaskVO> list = new ArrayList<>();
            for (TaskDao.MyTaskRow r : rows) {
                MyTaskVO vo = new MyTaskVO();
                vo.id = r.id;
                vo.taskNo = r.taskNo;
                vo.category = r.category;
                vo.title = r.title;
                vo.points = r.points;
                vo.status = r.status;
                vo.counterpartNickname = r.counterpartNickname;
                vo.applicationCount = r.applicationCount;
                vo.createdAt = r.createdAt;
                vo.acceptedAt = r.acceptedAt;
                vo.confirmDeadline = r.confirmDeadline;
                vo.remainingSeconds = r.remainingSeconds;
                vo.myReviewRating = r.myReviewRating;
                vo.offline = r.offline;
                list.add(vo);
            }
            return PageResult.of(page, size, total, list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取我的任务失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> deliver(long taskId, long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (task.getAcceptorId() == null || task.getAcceptorId() != userId) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有接单者可以标记已送达");
            }
            if (!"IN_PROGRESS".equals(task.getStatus())) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持标记已送达");
            }
            int rows = taskDao.update(conn,
                    "UPDATE tasks SET status='PENDING_CONFIRM', delivered_at=NOW(),"
                            + " confirm_deadline=DATE_ADD(NOW(), INTERVAL 24 HOUR)"
                            + " WHERE id=? AND status='IN_PROGRESS' AND acceptor_id=?",
                    taskId, userId);
            if (rows == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持标记已送达");
            }
            String deadline = taskDao.queryOne(conn,
                    "SELECT DATE_FORMAT(confirm_deadline, '%Y-%m-%d %H:%i:%s') FROM tasks WHERE id=?",
                    new com.campusquest.dao.BaseDao.RowMapper<String>() {
                        @Override
                        public String map(java.sql.ResultSet rs) throws java.sql.SQLException {
                            return rs.getString(1);
                        }
                    }, taskId);
            taskDao.commit(conn);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("status", "PENDING_CONFIRM");
            data.put("confirmDeadline", deadline);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "标记送达失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public TaskDetailVO detail(long taskId, long viewerId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Task task = taskDao.findById(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            User publisher = userDao.findById(conn, task.getPublisherId());
            User acceptor = task.getAcceptorId() == null ? null : userDao.findById(conn, task.getAcceptorId());

            TaskDetailVO vo = new TaskDetailVO();
            vo.id = task.getId();
            vo.taskNo = task.getTaskNo();
            vo.category = task.getCategory();
            vo.title = task.getTitle();
            vo.description = task.getDescription();
            vo.points = task.getPoints();
            vo.locationDesc = task.getLocationDesc();
            vo.timeDesc = task.getTimeDesc();
            vo.publisherId = task.getPublisherId();
            vo.publisherNickname = publisher == null ? "" : publisher.getNickname();
            vo.publisherCredit = publisher == null ? 100 : publisher.getCredit();
            vo.acceptorId = task.getAcceptorId();
            vo.acceptorNickname = acceptor == null ? null : acceptor.getNickname();
            vo.acceptorCredit = acceptor == null ? null : acceptor.getCredit();
            vo.status = task.getStatus();
            vo.createdAt = fmt(task.getCreatedAt());
            vo.acceptedAt = fmt(task.getAcceptedAt());
            vo.deliveredAt = fmt(task.getDeliveredAt());
            vo.confirmDeadline = fmt(task.getConfirmDeadline());
            vo.finishedAt = fmt(task.getFinishedAt());
            vo.offline = task.getOffline();
            vo.offlineReason = task.getOfflineReason();
            vo.applicationCount = applicationDao.countByTask(conn, taskId);
            if ("PENDING_CONFIRM".equals(task.getStatus())) {
                Integer remain = taskDao.queryInt(conn,
                        "SELECT GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), confirm_deadline)) FROM tasks WHERE id=?",
                        taskId);
                vo.remainingSeconds = remain == null ? 0L : remain.longValue();
            }

            Application mine = applicationDao.findByTaskAndApplicant(conn, taskId, viewerId);
            vo.myApplicationStatus = mine == null ? null : mine.getStatus();

            boolean isPublisher = task.getPublisherId() == viewerId;
            boolean isAcceptor = task.getAcceptorId() != null && task.getAcceptorId() == viewerId;
            boolean waiting = "WAITING".equals(task.getStatus());
            vo.canApply = waiting && !isPublisher
                    && (mine == null || "ABANDONED".equals(mine.getStatus()) || "REJECTED".equals(mine.getStatus()));
            vo.canConfirmApplication = waiting && isPublisher;
            vo.canDeliver = "IN_PROGRESS".equals(task.getStatus()) && isAcceptor;
            vo.canComplete = "PENDING_CONFIRM".equals(task.getStatus()) && isPublisher;
            vo.canCancel = waiting && isPublisher;
            vo.canAbandon = "IN_PROGRESS".equals(task.getStatus()) && isAcceptor;
            com.campusquest.entity.Review myReview = "COMPLETED".equals(task.getStatus())
                    ? reviewService.findMyReview(taskId, viewerId) : null;
            vo.myReviewRating = myReview == null ? null : myReview.getRating();
            vo.canReview = "COMPLETED".equals(task.getStatus()) && (isPublisher || isAcceptor) && myReview == null;
            if (isPublisher || isAcceptor) {
                ConversationDao.Row conv = conversationDao.findByTask(conn, taskId);
                vo.conversationId = conv == null ? 0 : conv.id;
            }
            return vo;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取任务详情失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    private String fmt(java.sql.Timestamp ts) {
        return ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
    }
}
