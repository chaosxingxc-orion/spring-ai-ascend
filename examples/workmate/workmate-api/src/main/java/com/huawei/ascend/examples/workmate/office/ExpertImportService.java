package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ExpertImportRequest;
import com.huawei.ascend.examples.workmate.office.dto.ExpertSummaryResponse;
import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class ExpertImportService {

    private static final String PROMPT_FILE = "prompt.md";
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_ZIP_ENTRY_BYTES = 1_048_576L; // 1 MiB per entry
    private static final long MAX_ZIP_TOTAL_BYTES = 5_242_880L; // 5 MiB uncompressed total

    private final OfficeImportPaths importPaths;
    private final ExpertRegistry expertRegistry;

    public ExpertImportService(OfficeImportPaths importPaths, ExpertRegistry expertRegistry) {
        this.importPaths = importPaths;
        this.expertRegistry = expertRegistry;
    }

    public ImportValidationResponse validate(ExpertImportRequest request) {
        try {
            normalizeAgentImport(request);
            return new ImportValidationResponse(true, "OK");
        } catch (IllegalArgumentException ex) {
            return new ImportValidationResponse(false, ex.getMessage());
        }
    }

    public ExpertSummaryResponse importExpert(ExpertImportRequest request) {
        ExpertImportRequest normalized = normalizeAgentImport(request);
        if (expertRegistry.findExpert(normalized.id()).isPresent()) {
            throw new IllegalArgumentException("Expert already exists: " + normalized.id());
        }
        Path expertDir = importPaths.expertDir(normalized.id());
        try {
            Files.createDirectories(expertDir);
            Files.writeString(expertDir.resolve(PROMPT_FILE), normalized.promptContent() + System.lineSeparator());
            Files.writeString(expertDir.resolve("expert.yaml"), renderExpertYaml(normalized));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write imported expert " + normalized.id(), ex);
        }
        expertRegistry.reloadImports(importPaths.expertsDir());
        return ExpertSummaryResponse.from(expertRegistry.requireExpert(normalized.id()));
    }

    public ExpertSummaryResponse importExpertZip(InputStream zipStream) {
        ExpertImportRequest request = parseExpertZip(zipStream);
        return importExpert(request);
    }

    public ExpertImportRequest parseExpertZip(InputStream zipStream) {
        String yamlText = null;
        String promptText = null;
        int entryCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("Zip has too many entries (>" + MAX_ZIP_ENTRIES + ")");
                }
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                String base = slash >= 0 ? name.substring(slash + 1) : name;
                // Read with a hard per-entry cap so a single huge (or zip-bomb) entry cannot exhaust heap.
                byte[] bytes = readCapped(zip, MAX_ZIP_ENTRY_BYTES, name);
                totalBytes += bytes.length;
                if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Zip uncompressed size exceeds limit");
                }
                if ("expert.yaml".equalsIgnoreCase(base) || "expert.yml".equalsIgnoreCase(base)) {
                    yamlText = new String(bytes, StandardCharsets.UTF_8);
                } else if (PROMPT_FILE.equalsIgnoreCase(base)) {
                    promptText = new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read expert zip archive", ex);
        }
        if (yamlText == null || yamlText.isBlank()) {
            throw new IllegalArgumentException("Zip must contain expert.yaml");
        }
        if (promptText == null || promptText.isBlank()) {
            throw new IllegalArgumentException("Zip must contain prompt.md");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = SafeYaml.loader().load(yamlText);
        if (meta == null) {
            throw new IllegalArgumentException("Invalid expert.yaml");
        }
        ExpertImportRequest request = new ExpertImportRequest(
                stringField(meta, "id"),
                stringField(meta, "name"),
                stringField(meta, "description"),
                stringFieldOrDefault(meta, "expertType", "agent"),
                stringFieldOrDefault(meta, "category", "custom"),
                tagsField(meta),
                promptText,
                stringFieldOrDefault(meta, "defaultInitPrompt", ""));
        return normalizeAgentImport(request);
    }

    private static byte[] readCapped(InputStream in, long maxBytes, String entryName) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Zip entry too large: " + entryName);
            }
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    private static String stringField(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String stringFieldOrDefault(Map<String, Object> meta, String key, String fallback) {
        String value = stringField(meta, key);
        return value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> tagsField(Map<String, Object> meta) {
        Object raw = meta.get("tags");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of("imported");
    }

    public ExpertImportRequest normalizeAgentImport(ExpertImportRequest request) {
        String id = OfficeImportValidator.requireSafeId(request.id(), "Expert");
        String name = OfficeImportValidator.requireText(request.name(), "Expert name");
        String description = OfficeImportValidator.requireText(request.description(), "Expert description");
        String expertType = request.expertType() == null || request.expertType().isBlank()
                ? "agent"
                : request.expertType().trim();
        if (!expertType.equals("agent") && !expertType.equals("team")) {
            throw new IllegalArgumentException("expertType must be agent or team");
        }
        if ("team".equals(expertType)) {
            throw new IllegalArgumentException("Team expert import is not supported in G26 MVP");
        }
        String prompt = OfficeImportValidator.requireText(request.promptContent(), "Prompt content");
        String category = request.category() == null || request.category().isBlank()
                ? "custom"
                : request.category().trim();
        List<String> tags = request.tags() == null ? List.of("imported") : List.copyOf(request.tags());
        String defaultInitPrompt = request.defaultInitPrompt() == null ? "" : request.defaultInitPrompt().trim();
        return new ExpertImportRequest(id, name, description, expertType, category, tags, prompt, defaultInitPrompt);
    }

    private static String renderExpertYaml(ExpertImportRequest request) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("id", request.id());
        yaml.put("name", request.name());
        yaml.put("description", request.description());
        yaml.put("expertType", request.expertType());
        yaml.put("promptFile", PROMPT_FILE);
        yaml.put("category", request.category());
        yaml.put("tags", request.tags());
        yaml.put("source", "imported");
        if (!request.defaultInitPrompt().isBlank()) {
            yaml.put("defaultInitPrompt", request.defaultInitPrompt());
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(yaml);
    }
}
