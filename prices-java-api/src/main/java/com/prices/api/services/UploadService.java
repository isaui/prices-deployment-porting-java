package com.prices.api.services;

import java.nio.file.Path;

public interface UploadService {
    
    /**
     * Initialize upload session for a project
     * @param projectSlug project slug (used as upload ID)
     * @param fileName original file name
     * @param totalSize total file size in bytes
     * @param totalChunks number of chunks
     * @return upload session info
     */
    UploadSession initUpload(String projectSlug, String fileName, long totalSize, int totalChunks);
    
    /**
     * Upload a chunk
     * @return number of chunks uploaded so far
     */
    int uploadChunk(String projectSlug, int index, byte[] chunkData) throws UploadException;
    
    /**
     * Finalize upload - reassemble chunks
     * @return path to final file
     */
    Path finalizeUpload(String projectSlug) throws UploadException;
    
    /**
     * Get upload session status
     */
    UploadSession getStatus(String projectSlug);
    
    /**
     * Get final path for deployment
     */
    Path getFinalPath(String projectSlug);
    
    /**
     * Cleanup session after deployment
     */
    void cleanupSession(String projectSlug);
    
    /**
     * Upload session info
     */
    record UploadSession(
        String projectSlug,
        String fileName,
        long totalSize,
        int totalChunks,
        int uploadedChunks,
        boolean finalized,
        Path finalPath
    ) {}
    
    class UploadException extends Exception {
        public UploadException(String message) {
            super(message);
        }
    }
}
