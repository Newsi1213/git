package com.campusquest.servlet;

import com.campusquest.util.FileUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * 读取 E:/MyApp/uploads 下的图片，拒绝路径穿越。
 */
public class FileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            resp.sendError(404);
            return;
        }
        String relative = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        File root = FileUtil.uploadRoot();
        File target = new File(root, relative);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath + File.separator)) {
            resp.sendError(404);
            return;
        }
        if (!target.isFile()) {
            resp.sendError(404);
            return;
        }
        String name = target.getName().toLowerCase();
        resp.setContentType(contentType(name));
        resp.setContentLengthLong(target.length());
        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(target.toPath(), out);
        }
    }

    private String contentType(String name) {
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
