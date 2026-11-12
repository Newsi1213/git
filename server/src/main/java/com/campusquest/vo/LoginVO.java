package com.campusquest.vo;

public class LoginVO {
    public long userId;
    public String nickname;
    public int points;
    public int credit;
    public String role = "STUDENT";

    public LoginVO(long userId, String nickname, int points, int credit) {
        this.userId = userId;
        this.nickname = nickname;
        this.points = points;
        this.credit = credit;
    }
}
