package com.campusquest.util;

public final class ErrorCode {
    public static final int OK = 0;
    public static final int PARAM_INVALID = 1001;
    public static final int NOT_LOGIN = 1002;
    public static final int FORBIDDEN = 1003;
    public static final int NOT_FOUND = 1004;
    public static final int METHOD_NOT_ALLOWED = 1005;

    public static final int PHONE_EXISTS = 2001;
    public static final int PHONE_INVALID = 2002;
    public static final int LOGIN_FAILED = 2003;
    public static final int ADMIN_LOGIN_FAILED = 2004;

    public static final int POINTS_NOT_ENOUGH = 3001;
    public static final int CREDIT_PUBLISH_LOW = 3002;
    public static final int CREDIT_ACCEPT_LOW = 3003;
    public static final int STATUS_NOT_ALLOWED = 3004;
    public static final int CANNOT_APPLY_OWN = 3005;
    public static final int DUPLICATE_APPLY = 3006;
    public static final int TASK_TAKEN = 3007;
    public static final int APPLICATION_INVALID = 3008;
    public static final int DUPLICATE_REVIEW = 3009;
    public static final int REVIEW_NOT_READY = 3010;
    public static final int DUPLICATE_APPEAL = 3011;
    public static final int APPEAL_HANDLED = 3012;

    public static final int FILE_TYPE_INVALID = 4001;
    public static final int FILE_TOO_LARGE = 4002;
    public static final int FILE_MISSING = 4003;
    public static final int FILE_SAVE_FAILED = 4004;

    public static final int SERVER_ERROR = 5000;
    public static final int DB_ERROR = 5001;

    private ErrorCode() {
    }

    public static int httpStatus(int code) {
        if (code == NOT_LOGIN) {
            return 401;
        }
        if (code == FORBIDDEN) {
            return 403;
        }
        if (code == NOT_FOUND) {
            return 404;
        }
        if (code == METHOD_NOT_ALLOWED) {
            return 405;
        }
        return 200;
    }
}
