package com.prices.cli.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", "target", "build", "dist", ".git", ".idea", ".vscode", ".prices"
    );

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
                    
                    // Skip hidden dirs (start with .) except .env.example if needed, but here we exclude hidden dirs generally
                    if (dirName.startsWith(".") && !dirName.equals(".env.example")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    if (EXCLUDED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
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
