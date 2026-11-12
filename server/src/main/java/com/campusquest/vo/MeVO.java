package com.campusquest.vo;

public class MeVO {
    public long userId;
    public String nickname;
    public String phoneMasked;
    public int points;
    public int credit;
    public int unreadCount;
    public String avatarPath;
    public String createdAt;

    public MeVO(long userId, String nickname, String phoneMasked, int points, int credit, int unreadCount,
                String avatarPath, String createdAt) {
        this.userId = userId;
        this.nickname = nickname;
        this.phoneMasked = phoneMasked;
        this.points = points;
        this.credit = credit;
        this.unreadCount = unreadCount;
        this.avatarPath = avatarPath;
        this.createdAt = createdAt;
    }
}
