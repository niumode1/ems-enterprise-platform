package com.company.ems.project;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local disk storage for self-hosted deployments. MinIO is reserved as a later
 * adapter; the interface keeps document code storage-agnostic.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.local-path:./data/files}") String localPath) {
        this.root = Path.of(localPath).toAbsolutePath().normalize();
    }

    @Override
    public String store(String namespace, String originalName, InputStream inputStream) throws IOException {
        String extension = extension(originalName);
        String safeNamespace = (namespace == null ? "misc" : namespace).replaceAll("[^a-zA-Z0-9_-]", "-");
        Path dir = root.resolve(safeNamespace);
        Files.createDirectories(dir);
        String key = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = dir.resolve(key);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return safeNamespace + "/" + key;
    }

    @Override
    public Resource load(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法存储路径");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        try {
            Path target = root.resolve(storageKey).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // orphaned files are cleaned by the retention job; deletion is best effort
        }
    }

    private String extension(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot).toLowerCase();
        return ext.length() <= 12 && ext.matches("\\.[a-z0-9]+") ? ext : "";
    }
}
