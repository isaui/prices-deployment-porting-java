package com.prices.api.services.deployment.stages;

import com.prices.api.constants.Constants;
import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
public class ExtractStage implements PipelineStage {

    @Override
    public String name() {
        return "Extract Artifact";
    }

    @Override
    public void execute(DeploymentContext ctx) throws Exception {
        // 1. Create deployment directory
        Path deployDir = Paths.get(Constants.DEPLOYMENTS_BASE_DIR, ctx.getProjectSlug());
        ctx.setExtractedPath(deployDir);

        // Remove existing directory if exists (redeploy case)
        if (Files.exists(deployDir)) {
            deleteDirectory(deployDir);
        }

        Files.createDirectories(deployDir);
        ctx.addLog("Created deployment directory: " + deployDir);

        // 2. Extract zip from ArtifactData
        if (ctx.getArtifactData() == null || ctx.getArtifactData().length == 0) {
            throw new RuntimeException("No artifact data provided");
        }

        int fileCount = 0;
        try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(new ByteArrayInputStream(ctx.getArtifactData()))) {
            ZipArchiveEntry entry;
            while ((entry = zipIn.getNextZipEntry()) != null) {
                Path destPath = deployDir.resolve(entry.getName());
                // Protect against Zip Slip
                if (!destPath.normalize().startsWith(deployDir.normalize())) {
                    throw new IOException("Malicious zip entry found: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    Files.createDirectories(destPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipIn.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    fileCount++;
                }
            }
        }

        ctx.addLog(String.format("Extracted %d files to: %s", fileCount, deployDir));

        // 3. Validate required folders exist
        Path frontendPath = deployDir.resolve("frontend");
        Path backendPath = deployDir.resolve("backend");

        boolean frontendExists = Files.exists(frontendPath) && Files.isDirectory(frontendPath);
        boolean backendExists = Files.exists(backendPath) && Files.isDirectory(backendPath);

        if (!frontendExists && !backendExists) {
            throw new RuntimeException("Invalid project structure: neither frontend/ nor backend/ folder found");
        }

        if (frontendExists) {
            ctx.addLog("Found frontend/ folder");
        }
        if (backendExists) {
            ctx.addLog("Found backend/ folder");
        }
    }

    @Override
    public void rollback(DeploymentContext ctx) {
        if (ctx.getExtractedPath() == null) {
            ctx.addLog("No extracted path to clean up");
            return;
        }

        ctx.addLog("Removing deployment directory: " + ctx.getExtractedPath());
        try {
            deleteDirectory(ctx.getExtractedPath());
            ctx.addLog("Cleaned up extracted files: " + ctx.getExtractedPath());
        } catch (IOException e) {
            ctx.addLog("Warning: failed to remove directory: " + e.getMessage());
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
    }
}
