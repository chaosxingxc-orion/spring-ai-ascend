package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.StudioSkillFileContentResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillFileEntryResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class StudioSkillFileService {

    private static final int MAX_LISTED_FILES = 500;
    private static final long MAX_READ_BYTES = 512_000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "markdown", "yaml", "yml", "json", "txt", "py", "sh", "bash", "js", "ts", "tsx", "jsx", "css",
            "html", "htm", "xml", "xsd", "csv", "toml", "ini", "cfg", "conf", "sql", "java", "go", "rs");

    private final SkillRegistry skillRegistry;

    public StudioSkillFileService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public List<StudioSkillFileEntryResponse> listFiles(String skillId) {
        Path root = resolveSkillRoot(skillId);
        List<StudioSkillFileEntryResponse> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isHiddenRelative(root, path))
                    .forEach(path -> files.add(toEntry(root, path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list skill files for " + skillId, ex);
        }
        files.sort(Comparator.comparing(StudioSkillFileEntryResponse::path));
        if (files.size() > MAX_LISTED_FILES) {
            return List.copyOf(files.subList(0, MAX_LISTED_FILES));
        }
        return List.copyOf(files);
    }

    public StudioSkillFileContentResponse readFile(String skillId, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("File path required");
        }
        Path root = resolveSkillRoot(skillId);
        Path file = resolveRelativePath(root, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Skill file not found: " + relativePath);
        }
        boolean editable = isDraftPath(root);
        try {
            long size = Files.size(file);
            if (!isTextReadable(file, size)) {
                return new StudioSkillFileContentResponse(relativePath, "", false, true, editable);
            }
            byte[] bytes = Files.readAllBytes(file);
            boolean truncated = bytes.length > MAX_READ_BYTES;
            if (truncated) {
                byte[] slice = new byte[(int) MAX_READ_BYTES];
                System.arraycopy(bytes, 0, slice, 0, slice.length);
                bytes = slice;
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            return new StudioSkillFileContentResponse(relativePath, content, truncated, false, editable);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read skill file " + relativePath, ex);
        }
    }

    private Path resolveSkillRoot(String skillId) {
        String safeId = OfficeImportValidator.requireSafeId(skillId, "Skill");
        SkillRegistryEntry entry =
                skillRegistry.findEntry(safeId).orElseThrow(() -> new SkillNotFoundException(safeId));
        return entry.sourceDir();
    }

    private static Path resolveRelativePath(Path root, String relativePath) {
        Path normalized = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Invalid skill file path: " + relativePath);
        }
        // Defense against symlink escape: a normalized path can stay textually under root yet point
        // (via a symlink) outside it. If the target exists, verify its real path is still inside root.
        try {
            if (Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Path realTarget = normalized.toRealPath();
                Path realRoot = root.toRealPath();
                if (!realTarget.startsWith(realRoot)) {
                    throw new IllegalArgumentException("Invalid skill file path: " + relativePath);
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid skill file path: " + relativePath);
        }
        return normalized;
    }

    private static boolean isDraftPath(Path root) {
        return root.toString().replace('\\', '/').contains("/office-drafts/");
    }

    private static StudioSkillFileEntryResponse toEntry(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        try {
            long size = Files.size(file);
            return new StudioSkillFileEntryResponse(relative, size, isTextReadable(file, size));
        } catch (IOException ex) {
            return new StudioSkillFileEntryResponse(relative, 0, false);
        }
    }

    private static boolean isHiddenRelative(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTextReadable(Path file, long size) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return size <= 64_000;
        }
        String ext = name.substring(dot + 1);
        return TEXT_EXTENSIONS.contains(ext);
    }
}
