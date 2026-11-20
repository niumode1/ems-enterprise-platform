package com.company.ems.project;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    String store(String namespace, String originalName, InputStream inputStream) throws IOException;

    default String store(Long projectId, String originalName, InputStream inputStream) throws IOException {
        return store("project-" + projectId, originalName, inputStream);
    }

    Resource load(String storageKey);

    void delete(String storageKey);
}
