package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Lưu file đính kèm trên đĩa, dưới thư mục app.uploads.dir (Docker: volume /app/uploads).
 * Tên file do hệ thống sinh (UUID), không dùng tên người dùng gửi, nên không thể ghi / đọc ra ngoài thư mục.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.uploads.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Ghi file, trả về đường dẫn tương đối để lưu vào CSDL. */
    public String save(String folder, byte[] content, String extension) {
        String relative = folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file đính kèm", e);
        }
        return relative;
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw ApiException.notFound("File đính kèm không còn trên máy chủ");
        }
    }

    public void delete(String relative) {
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException ignored) {
            // File mồ côi trên đĩa không ảnh hưởng dữ liệu; lần dọn sau xử lý
        }
    }

    private Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.badRequest("Đường dẫn file không hợp lệ");
        }
        return p;
    }
}
