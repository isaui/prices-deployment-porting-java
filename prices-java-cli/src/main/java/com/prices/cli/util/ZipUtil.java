package com.prices.cli.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    // Always excluded - never needed in deployment
    private static final Set<String> ALWAYS_EXCLUDED_DIRS = Set.of(
            "node_modules", "target", ".git", ".idea", ".vscode", ".prices",
            "bin", ".gradle", ".settings", "out", "__pycache__", ".pytest_cache"
    );
    
    // Only excluded if parent has package.json (will be rebuilt)
    private static final Set<String> BUILD_OUTPUT_DIRS = Set.of("build", "dist");

    /**
     * Create archive from separate frontend and backend directories.
     * The zip will contain frontend/ and backend/ at the root, regardless of the original dir names.
     */
    public static Path createProjectArchive(Path frontendDir, Path backendDir) throws IOException {
        Path zipPath = Files.createTempFile("prices-deploy-", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            addDirectoryToZip(zos, frontendDir, "frontend");
            addDirectoryToZip(zos, backendDir, "backend");
        }

        return zipPath;
    }

    private static void addDirectoryToZip(ZipOutputStream zos, Path sourceDir, String targetPrefix) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(sourceDir)) {
                    return FileVisitResult.CONTINUE;
                }

                String dirName = dir.getFileName().toString();

                if (dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ALWAYS_EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (BUILD_OUTPUT_DIRS.contains(dirName)) {
                    Path parentPackageJson = dir.getParent().resolve("package.json");
                    if (Files.exists(parentPackageJson)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }

                Path relativePath = sourceDir.relativize(file);
                String zipEntryName = targetPrefix + "/" + relativePath.toString().replace("\\", "/");

                ZipEntry zipEntry = new ZipEntry(zipEntryName);
                zos.putNextEntry(zipEntry);
                Files.copy(file, zos);
                zos.closeEntry();

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path createProjectArchive(Path sourceDir) throws IOException {
        Path zipPath = Files.createTempFile("prices-deploy-", ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(sourceDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    String dirName = dir.getFileName().toString();
                    
                    // Skip hidden dirs
                    if (dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    // Always skip these
                    if (ALWAYS_EXCLUDED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    // Skip build/dist only if parent has package.json (will be rebuilt)
                    if (BUILD_OUTPUT_DIRS.contains(dirName)) {
                        Path parentPackageJson = dir.getParent().resolve("package.json");
                        if (Files.exists(parentPackageJson)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        // No package.json = pre-built static site, include it
                    }
                    
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Skip hidden files
                    if (file.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Create zip entry
                    Path relativePath = sourceDir.relativize(file);
                    String zipEntryName = relativePath.toString().replace("\\", "/");
                    
                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        
        return zipPath;
    }
}
