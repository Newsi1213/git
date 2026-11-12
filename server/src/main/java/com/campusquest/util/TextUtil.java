package com.campusquest.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TextUtil {

    public static final String SALT = "CQ_SALT_2026";

    private TextUtil() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((value + SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    public static boolean isPhone(String value) {
        return value != null && value.matches("^\\d{11}$");
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
