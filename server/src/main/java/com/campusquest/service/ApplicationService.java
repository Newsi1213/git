package com.campusquest.service;

import com.campusquest.dao.ApplicationDao;
import com.campusquest.dao.NotificationDao;
import com.campusquest.dao.TaskDao;
import com.campusquest.dao.UserDao;
import com.campusquest.entity.Application;
import com.campusquest.entity.Task;
import com.campusquest.entity.User;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApplicationService {

    private final TaskDao taskDao = new TaskDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final UserDao userDao = new UserDao();
    private final NotificationService notificationService = new NotificationService();
    private final PointService pointService = new PointService();
    private final CreditService creditService = new CreditService();

    public Map<String, Object> apply(long taskId, long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (!"WAITING".equals(task.getStatus())) {
                if ("IN_PROGRESS".equals(task.getStatus()) || "PENDING_CONFIRM".equals(task.getStatus())) {
                    throw new BizException(ErrorCode.TASK_TAKEN, "该任务已被其他同学接单");
                }
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持申请接单");
            }
            if (task.getPublisherId() == userId) {
                throw new BizException(ErrorCode.CANNOT_APPLY_OWN, "不能申请自己发布的任务");
            }
            User user = userDao.findById(conn, userId);
            if (user == null) {
                throw new BizException(ErrorCode.NOT_LOGIN, "登录已失效，请重新登录");
            }
            if (user.getCredit() < 85) {
                throw new BizException(ErrorCode.CREDIT_ACCEPT_LOW,
                        "信用分低于 85，不能接单，当前信用分 " + user.getCredit());
            }
            Application existing = applicationDao.findByTaskAndApplicant(conn, taskId, userId);
            long applicationId;
            if (existing == null) {
                applicationId = applicationDao.insert(conn, taskId, userId);
            } else if ("PENDING".equals(existing.getStatus()) || "SELECTED".equals(existing.getStatus())) {
                throw new BizException(ErrorCode.DUPLICATE_APPLY, "你已经申请过这个任务");
            } else {
                applicationId = existing.getId();
                applicationDao.reactivate(conn, applicationId);
            }
            long count = applicationDao.countByTask(conn, taskId);
            taskDao.commit(conn);

            notificationService.notify(task.getPublisherId(), "APPLICATION_RECEIVED", "收到新的接单申请",
                    user.getNickname() + " 申请了你的任务「" + task.getTitle() + "」，当前共 " + count + " 人申请。",
                    taskId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("applicationId", applicationId);
            data.put("applicationCount", count);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "申请接单失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public List<ApplicationDao.ApplicationRow> listApplications(long taskId, long publisherId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            Task task = taskDao.findById(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (task.getPublisherId() != publisherId) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有发布者可以查看申请列表");
            }
            return applicationDao.listByTask(conn, taskId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取申请列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> confirm(long taskId, long publisherId, long applicationId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (task.getPublisherId() != publisherId) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有发布者可以确认接单");
            }
            if ("IN_PROGRESS".equals(task.getStatus()) || "PENDING_CONFIRM".equals(task.getStatus())
                    || "COMPLETED".equals(task.getStatus())) {
                throw new BizException(ErrorCode.TASK_TAKEN, "该任务已被其他同学接单");
            }
            if (!"WAITING".equals(task.getStatus())) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持确认接单");
            }
            Application application = applicationDao.findById(conn, applicationId);
            if (application == null || application.getTaskId() != taskId) {
                throw new BizException(ErrorCode.APPLICATION_INVALID, "该申请已失效");
            }
            if (!"PENDING".equals(application.getStatus())) {
                throw new BizException(ErrorCode.APPLICATION_INVALID, "该申请已失效");
            }
            int rows = taskDao.update(conn,
                    "UPDATE tasks SET status='IN_PROGRESS', acceptor_id=?, accepted_at=NOW()"
                            + " WHERE id=? AND status='WAITING'",
                    application.getApplicantId(), taskId);
            if (rows == 0) {
                throw new BizException(ErrorCode.TASK_TAKEN, "该任务已被其他同学接单");
            }
            if (applicationDao.selectOne(conn, taskId, applicationId) == 0) {
                throw new BizException(ErrorCode.APPLICATION_INVALID, "该申请已失效");
            }
            applicationDao.rejectOthers(conn, taskId, applicationId);
            taskDao.update(conn,
                    "INSERT INTO conversations(task_id, publisher_id, acceptor_id) VALUES(?,?,?)"
                            + " ON DUPLICATE KEY UPDATE acceptor_id=VALUES(acceptor_id)",
                    taskId, publisherId, application.getApplicantId());
            taskDao.commit(conn);

            notificationService.notify(application.getApplicantId(), "APPLICATION_CONFIRMED", "申请已通过",
                    "你申请的任务「" + task.getTitle() + "」已被确认，请按约定完成并点击已送达。", taskId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("status", "IN_PROGRESS");
            data.put("acceptorId", application.getApplicantId());
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "确认接单失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> cancel(long taskId, long publisherId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (task.getPublisherId() != publisherId) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有发布者可以取消任务");
            }
            if (!"WAITING".equals(task.getStatus())) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "只有待接单的任务可以取消");
            }
            int rows = taskDao.update(conn,
                    "UPDATE tasks SET status='CANCELLED' WHERE id=? AND status='WAITING'", taskId);
            if (rows == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "只有待接单的任务可以取消");
            }
            pointService.refund(conn, publisherId, task.getPoints(), taskId, "取消任务退回 " + task.getTaskNo());
            int balance = pointService.balance(conn, publisherId);
            applicationDao.rejectAllPending(conn, taskId);
            taskDao.commit(conn);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("status", "CANCELLED");
            data.put("refund", task.getPoints());
            data.put("balance", balance);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "取消任务失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> abandon(long taskId, long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            taskDao.begin(conn);
            Task task = taskDao.findByIdForUpdate(conn, taskId);
            if (task == null || task.getOffline() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
            }
            if (task.getAcceptorId() == null || task.getAcceptorId() != userId) {
                throw new BizException(ErrorCode.FORBIDDEN, "只有接单者可以放弃任务");
            }
            if (!"IN_PROGRESS".equals(task.getStatus())) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持放弃");
            }
            Integer seconds = taskDao.queryInt(conn,
                    "SELECT TIMESTAMPDIFF(SECOND, accepted_at, NOW()) FROM tasks WHERE id=?", taskId);
            Application mine = applicationDao.findByTaskAndApplicant(conn, taskId, userId);
            boolean penaltyApplied = mine != null && mine.getPenaltyApplied() == 1;
            boolean late = seconds != null && seconds > 1800;
            int rows = taskDao.update(conn,
                    "UPDATE tasks SET status='WAITING', acceptor_id=NULL, accepted_at=NULL,"
                            + " delivered_at=NULL, confirm_deadline=NULL"
                            + " WHERE id=? AND status='IN_PROGRESS' AND acceptor_id=?",
                    taskId, userId);
            if (rows == 0) {
                throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "当前任务状态不支持放弃");
            }
            applicationDao.markAbandoned(conn, taskId, userId, (late || penaltyApplied) ? 1 : 0);
            int penalty = 0;
            int credit;
            if (late && !penaltyApplied) {
                credit = creditService.change(conn, userId, -2, "ABANDON", taskId, "接单超过 30 分钟放弃");
                penalty = 2;
            } else {
                credit = creditService.credit(conn, userId);
            }
            taskDao.commit(conn);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("status", "WAITING");
            data.put("within30", !late);
            data.put("penalty", penalty);
            data.put("credit", credit);
            return data;
        } catch (BizException e) {
            taskDao.rollback(conn);
            throw e;
        } catch (Exception e) {
            taskDao.rollback(conn);
            throw new BizException(ErrorCode.DB_ERROR, "放弃任务失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }
}
