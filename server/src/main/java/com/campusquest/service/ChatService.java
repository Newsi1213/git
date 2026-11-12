package com.campusquest.service;

import com.campusquest.dao.ConversationDao;
import com.campusquest.dao.MessageDao;
import com.campusquest.util.BizException;
import com.campusquest.util.DBUtil;
import com.campusquest.util.ErrorCode;
import com.campusquest.util.FileUtil;
import com.campusquest.util.TextUtil;
import com.campusquest.vo.ConversationVO;
import com.campusquest.vo.MessageVO;

import javax.servlet.http.Part;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatService {

    private final ConversationDao conversationDao = new ConversationDao();
    private final MessageDao messageDao = new MessageDao();

    public List<ConversationVO> listMyConversations(long userId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            List<ConversationDao.Row> rows = conversationDao.listByUser(conn, userId);
            List<ConversationVO> list = new ArrayList<>();
            for (ConversationDao.Row row : rows) {
                ConversationVO vo = new ConversationVO();
                vo.id = row.id;
                vo.taskId = row.taskId;
                vo.taskNo = row.taskNo;
                vo.taskTitle = row.taskTitle;
                vo.taskStatus = row.taskStatus;
                boolean isPublisher = row.publisherId == userId;
                vo.otherUserId = isPublisher ? row.acceptorId : row.publisherId;
                vo.otherNickname = isPublisher ? row.acceptorNickname : row.publisherNickname;
                vo.otherCredit = isPublisher ? row.acceptorCredit : row.publisherCredit;
                vo.lastMessage = row.lastContent != null ? row.lastContent
                        : (row.lastImagePath != null ? "[图片]" : "暂无消息");
                vo.lastMessageAt = row.lastAt;
                list.add(vo);
            }
            return list;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取会话列表失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public Map<String, Object> listMessages(long conversationId, long userId, long afterId, int page, int size) {
        page = Math.max(1, page);
        size = Math.max(1, Math.min(100, size));
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            ConversationDao.Row conv = requireMember(conn, conversationId, userId);
            List<MessageDao.Row> rows = messageDao.list(conn, conversationId, afterId, (page - 1) * size, size);
            List<MessageVO> list = new ArrayList<>();
            long lastId = 0;
            for (MessageDao.Row row : rows) {
                MessageVO vo = toVO(row, userId);
                list.add(vo);
                if (row.id > lastId) {
                    lastId = row.id;
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", conversationId);
            data.put("taskId", conv.taskId);
            data.put("taskTitle", conv.taskTitle);
            data.put("otherNickname", conv.publisherId == userId ? conv.acceptorNickname : conv.publisherNickname);
            data.put("total", messageDao.count(conn, conversationId));
            data.put("lastId", lastId);
            data.put("list", list);
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "读取聊天记录失败");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public MessageVO sendText(long conversationId, long userId, String content) {
        content = TextUtil.trim(content);
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息内容不能为空");
        }
        if (content.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单条消息最多 500 个字");
        }
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conversationDao.findById(conn, conversationId);
            requireMember(conn, conversationId, userId);
            long id = messageDao.insertText(conn, conversationId, userId, content);
            return toVO(messageDao.findById(conn, id), userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "发送消息失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    public MessageVO sendImage(long conversationId, long userId, Part part) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            requireMember(conn, conversationId, userId);
            if (part == null || part.getSize() <= 0) {
                throw new BizException(ErrorCode.FILE_MISSING, "请选择要发送的图片");
            }
            String path = FileUtil.saveImage(part);
            long id = messageDao.insertImage(conn, conversationId, userId, path);
            return toVO(messageDao.findById(conn, id), userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DB_ERROR, "发送图片失败，请稍后再试");
        } finally {
            DBUtil.closeQuietly(conn);
        }
    }

    private ConversationDao.Row requireMember(Connection conn, long conversationId, long userId) throws Exception {
        ConversationDao.Row conv = conversationDao.findById(conn, conversationId);
        if (conv == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (conv.publisherId != userId && conv.acceptorId != userId) {
            throw new BizException(ErrorCode.FORBIDDEN, "你不是该会话的参与方");
        }
        return conv;
    }

    private MessageVO toVO(MessageDao.Row row, long userId) {
        MessageVO vo = new MessageVO();
        vo.id = row.id;
        vo.conversationId = row.conversationId;
        vo.senderId = row.senderId;
        vo.senderNickname = row.senderNickname;
        vo.msgType = row.msgType;
        vo.content = row.content;
        vo.imagePath = row.imagePath;
        vo.createdAt = row.createdAt;
        vo.mine = row.senderId == userId;
        return vo;
    }
}
