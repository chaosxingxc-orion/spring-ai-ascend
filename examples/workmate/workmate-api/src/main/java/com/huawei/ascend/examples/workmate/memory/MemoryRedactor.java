package com.huawei.ascend.examples.workmate.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MemoryRedactor {

    private static final Pattern SECRET =
            Pattern.compile("(password|token|secret|api[_-]?key|authorization|credential)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+?86)?1[3-9]\\d{9}(?![0-9])");

    public List<String> sanitizeEntries(List<String> entries) {
        List<String> out = new ArrayList<>();
        for (String entry : entries) {
            String sanitized = sanitizeLine(entry);
            if (!sanitized.isBlank() && sanitized.length() <= 240) {
                out.add(sanitized);
            }
        }
        return out;
    }

    public String sanitizeLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String line = text.trim();
        if (SECRET.matcher(line).find()) {
            return "";
        }
        line = EMAIL.matcher(line).replaceAll("[REDACTED_EMAIL]");
        line = PHONE.matcher(line).replaceAll("[REDACTED_PHONE]");
        if (line.length() > 240) {
            line = line.substring(0, 237) + "…";
        }
        return line;
    }

    public String sanitizeTranscript(String transcript) {
        if (transcript == null) {
            return "";
        }
        String[] lines = transcript.split("\\R");
        StringBuilder out = new StringBuilder();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("assistant:") && line.length() > 600) {
                line = line.substring(0, 597) + "…";
            }
            out.append(sanitizeLine(line.replaceFirst("^(user|assistant):\\s*", ""))).append('\n');
        }
        return out.toString().trim();
    }
}
