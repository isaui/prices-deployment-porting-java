package com.prices.api.services.impl;

import com.prices.api.services.UploadService;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.prices.api.constants.Constants.*;

@Singleton
@Slf4j
public class UploadServiceImpl implements UploadService {

    private static final Path UPLOAD_DIR = Paths.get(UPLOADS_DIR);
    
    private final Map<String, InternalSession> activeSessions = new ConcurrentHashMap<>();

    public UploadServiceImpl() {
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            log.error("Failed to create upload directory", e);
        }
    }

    @Override
    public UploadSession initUpload(String projectSlug, String fileName, long totalSize, int totalChunks) {
        // Cancel any existing upload for this project
        cleanupSession(projectSlug);
        
        Path uploadPath = UPLOAD_DIR.resolve(projectSlug);
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            log.error("Failed to create upload directory for {}", projectSlug, e);
            return null;
        }
        
        InternalSession session = new InternalSession();
        session.projectSlug = projectSlug;
        session.fileName = fileName;
        session.totalSize = totalSize;
        session.totalChunks = totalChunks;
        session.uploadedChunks = new HashSet<>();
        session.createdAt = System.currentTimeMillis();
        session.uploadPath = uploadPath;
        
        activeSessions.put(projectSlug, session);
        
        log.info("Initialized upload for project {} ({} chunks, {} bytes)", 
            projectSlug, totalChunks, totalSize);
        
        return toPublicSession(session);
    }

    @Override
    public int uploadChunk(String projectSlug, int index, byte[] chunkData) throws UploadException {
        InternalSession session = activeSessions.get(projectSlug);
        if (session == null) {
            throw new UploadException("Upload session not found or expired");
        }
        
        if (index < 0 || index >= session.totalChunks) {
            throw new UploadException("Invalid chunk index: " + index);
        }
        
        Path chunkPath = session.uploadPath.resolve("chunk_" + index);
        try {
            Files.write(chunkPath, chunkData);
            session.uploadedChunks.add(index);
        } catch (IOException e) {
            throw new UploadException("Failed to save chunk: " + e.getMessage());
        }
        
        return session.uploadedChunks.size();
    }

    @Override
    public Path finalizeUpload(String projectSlug) throws UploadException {
        InternalSession session = activeSessions.get(projectSlug);
        if (session == null) {
            throw new UploadException("Upload session not found");
        }
        
        if (session.uploadedChunks.size() != session.totalChunks) {
            throw new UploadException(String.format("Missing chunks: %d/%d uploaded", 
                session.uploadedChunks.size(), session.totalChunks));
        }
        
        Path finalPath = UPLOAD_DIR.resolve(projectSlug + ".zip");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(finalPath))) {
            for (int i = 0; i < session.totalChunks; i++) {
                Path chunkPath = session.uploadPath.resolve("chunk_" + i);
                Files.copy(chunkPath, out);
            }
        } catch (IOException e) {
            throw new UploadException("Failed to reassemble file: " + e.getMessage());
        }
        
        // Cleanup chunk files
        cleanupChunks(session);
        
        session.finalPath = finalPath;
        session.finalized = true;
        
        log.info("Finalized upload for project {} -> {}", projectSlug, finalPath);
        
        return finalPath;
    }

    @Override
    public UploadSession getStatus(String projectSlug) {
        InternalSession session = activeSessions.get(projectSlug);
        if (session == null) {
            return null;
        }
        return toPublicSession(session);
    }

    @Override
    public Path getFinalPath(String projectSlug) {
        InternalSession session = activeSessions.get(projectSlug);
        if (session == null || !session.finalized) {
            return null;
        }
        return session.finalPath;
    }

    @Override
    public void cleanupSession(String projectSlug) {
        InternalSession session = activeSessions.remove(projectSlug);
        if (session != null) {
            cleanupChunks(session);
            if (session.finalPath != null) {
                try {
                    Files.deleteIfExists(session.finalPath);
                } catch (IOException e) {
                    log.warn("Failed to cleanup final file {}: {}", session.finalPath, e.getMessage());
                }
            }
            log.info("Cleaned up upload session for project {}", projectSlug);
        }
    }

    /**
     * Scheduled task to cleanup expired sessions (runs every 10 minutes)
     */
    @Scheduled(fixedDelay = "10m")
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (Map.Entry<String, InternalSession> entry : activeSessions.entrySet()) {
            InternalSession session = entry.getValue();
            if (now - session.createdAt > UPLOAD_SESSION_EXPIRY_MS) {
                cleanupSession(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            log.info("Cleaned up {} expired upload sessions", cleaned);
        }
    }

    /**
     * Get active session count (for monitoring)
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    private void cleanupChunks(InternalSession session) {
        if (session.uploadPath != null) {
            try {
                for (int i = 0; i < session.totalChunks; i++) {
                    Files.deleteIfExists(session.uploadPath.resolve("chunk_" + i));
                }
                Files.deleteIfExists(session.uploadPath);
            } catch (IOException e) {
                log.warn("Failed to cleanup chunks for {}: {}", session.projectSlug, e.getMessage());
            }
        }
    }

    private UploadSession toPublicSession(InternalSession session) {
        return new UploadSession(
            session.projectSlug,
            session.fileName,
            session.totalSize,
            session.totalChunks,
            session.uploadedChunks.size(),
            session.finalized,
            session.finalPath
        );
    }

    private static class InternalSession {
        String projectSlug;
        String fileName;
        long totalSize;
        int totalChunks;
        Set<Integer> uploadedChunks;
        long createdAt;
        Path uploadPath;
        Path finalPath;
        boolean finalized;
    }
}
