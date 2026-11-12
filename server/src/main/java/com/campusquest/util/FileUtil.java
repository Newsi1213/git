package com.campusquest.util;

import javax.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class FileUtil {

    /** 运行期上传根目录，与 FileServlet 共用。 */
    public static final Path UPLOAD_ROOT = Paths.get("E:\\MyApp\\uploads").toAbsolutePath().normalize();

    /** 图片大小上限 5MB。 */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private static final List<String> IMAGE_EXTS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    private FileUtil() {
    }

    public static File uploadRoot() {
        File dir = UPLOAD_ROOT.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 保存上传图片，返回形如 uploads/2026/11/09/uuid.jpg 的相对路径。
     * 大小超过 5MB 抛 4002；扩展名不在白名单或文件内容不是图片抛 4001。
     */
    public static String saveImage(Part part) throws IOException {
        if (part == null || part.getSize() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传图片");
        }
        if (part.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "图片不能超过 5MB");
        }
        String submitted = part.getSubmittedFileName();
        String ext = extensionOf(submitted);
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_INVALID, "仅支持 jpg、jpeg、png、gif、webp 格式的图片");
        }
        byte[] data = readAll(part.getInputStream());
        if (!isImageData(data, ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_INVALID, "文件内容不是有效图片，仅支持 jpg、jpeg、png、gif、webp");
        }
        String dayPath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        Path dir = UPLOAD_ROOT.resolve(dayPath);
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Files.write(dir.resolve(fileName), data);
        return "uploads/" + dayPath + "/" + fileName;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toByteArray();
    }

    /** 校验文件头，防止把 .exe 等改名为图片后缀绕过白名单。 */
    private static boolean isImageData(byte[] data, String ext) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8
                    && (data[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N'
                    && data[3] == 'G' && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
        }
        if ("gif".equals(ext)) {
            return data.length >= 4 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
        }
        if ("webp".equals(ext)) {
            return data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
        }
        return false;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }
}
