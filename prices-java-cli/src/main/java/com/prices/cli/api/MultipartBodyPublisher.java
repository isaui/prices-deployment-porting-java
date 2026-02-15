package com.prices.cli.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.http.HttpRequest;

public class MultipartBodyPublisher {
    
    private final List<byte[]> parts = new ArrayList<>();
    private final String boundary;

    private MultipartBodyPublisher(String boundary) {
        this.boundary = boundary;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String boundary = "---ContentBoundary" + UUID.randomUUID().toString();
        private final List<byte[]> parts = new ArrayList<>();

        public Builder boundary(String boundary) {
            this.boundary = boundary;
            return this;
        }

        public Builder addPart(String name, String value) {
            parts.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(value.getBytes(StandardCharsets.UTF_8));
            parts.add(("\r\n").getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public Builder addFile(String name, Path path, String contentType) throws IOException {
            String filename = path.getFileName().toString();
            parts.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(Files.readAllBytes(path)); // Note: Reading entire file into memory for simplicity, like Go cli does for small zips
            parts.add(("\r\n").getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public HttpRequest.BodyPublisher build() {
            parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            
            // Combine all byte arrays
            int totalSize = 0;
            for (byte[] part : parts) {
                totalSize += part.length;
            }
            
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, result, offset, part.length);
                offset += part.length;
            }
            
            return HttpRequest.BodyPublishers.ofByteArray(result);
        }
    }
}
