package com.huawei.ascend.examples.workmate.team;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * generator-verifier 可编程验收（与 LLM 校验互补）。确保首轮草稿常因硬性指标被驳回，从而触发迭代。
 */
final class GeneratorAcceptanceChecker {

    private static final Pattern CHINESE_RANGE =
            Pattern.compile("(\\d+)\\s*[-–—~到]\\s*(\\d+)\\s*个?汉?字?");
    private static final Pattern CHINESE_TARGET =
            Pattern.compile("(?:约|大约|左右)?\\s*(\\d+)\\s*个?汉?字");
    private static final Pattern QUOTED_PHRASE = Pattern.compile("「([^」]+)」");
    private static final Pattern FILE_STUB =
            Pattern.compile("已生成文件|已写入|write 工具|创建了文件", Pattern.CASE_INSENSITIVE);

    private GeneratorAcceptanceChecker() {}

    record Result(boolean passed, String feedback) {}

    static Result check(String userMessage, String draft, String acceptanceCriteria) {
        if (draft == null || draft.isBlank()) {
            return new Result(false, "草稿为空，请输出完整正文。");
        }
        String body = stripMarkdownNoise(draft);
        List<String> issues = new ArrayList<>();

        int minChars = 0;
        int maxChars = Integer.MAX_VALUE;
        Integer target = parseChineseTarget(userMessage);
        if (acceptanceCriteria != null && !acceptanceCriteria.isBlank()) {
            Matcher range = CHINESE_RANGE.matcher(acceptanceCriteria);
            if (range.find()) {
                minChars = Integer.parseInt(range.group(1));
                maxChars = Integer.parseInt(range.group(2));
            } else if (target == null) {
                Matcher single = CHINESE_TARGET.matcher(acceptanceCriteria);
                if (single.find()) {
                    target = Integer.parseInt(single.group(1));
                }
            }
            Matcher phraseMatcher = QUOTED_PHRASE.matcher(acceptanceCriteria);
            while (phraseMatcher.find()) {
                String phrase = phraseMatcher.group(1).trim();
                if (!phrase.isBlank() && !body.contains(phrase)) {
                    issues.add("正文须包含「" + phrase + "」");
                }
            }
        }
        if (target != null && minChars == 0 && maxChars == Integer.MAX_VALUE) {
            minChars = Math.max(1, target - 25);
            maxChars = target + 25;
        }

        int chineseCount = countChineseCharacters(body);
        if (minChars > 0 || maxChars < Integer.MAX_VALUE) {
            if (chineseCount < minChars) {
                issues.add("汉字不足：当前 " + chineseCount + " 字，至少需要 " + minChars + " 字");
            } else if (chineseCount > maxChars) {
                issues.add("汉字过多：当前 " + chineseCount + " 字，最多 " + maxChars + " 字");
            }
        }

        if (body.length() < 40 && FILE_STUB.matcher(draft).find()) {
            issues.add("勿仅说明已写文件；请在回复正文中给出完整草稿。");
        }

        if (hasDuplicateBlock(body)) {
            issues.add("正文存在大段重复，请只保留一段完整表述。");
        }

        if (issues.isEmpty()) {
            return new Result(true, null);
        }
        return new Result(false, String.join("\n", issues));
    }

    private static Integer parseChineseTarget(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        Matcher m = CHINESE_TARGET.matcher(userMessage);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static int countChineseCharacters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                count++;
            }
        }
        return count;
    }

    private static String stripMarkdownNoise(String draft) {
        String text = draft;
        text = text.replaceAll("```[\\s\\S]*?```", " ");
        text = text.replaceAll("#+\\s*", "");
        text = text.replaceAll("\\*+", "");
        text = text.replaceAll("`[^`]+`", " ");
        return text.trim();
    }

    static String dedupeRepeatedBody(String draft) {
        if (draft == null || draft.isBlank()) {
            return "";
        }
        String trimmed = stripMarkdownNoise(draft);
        int len = trimmed.length();
        if (len < 40) {
            return draft.trim();
        }
        int half = len / 2;
        String first = trimmed.substring(0, half).trim();
        String second = trimmed.substring(half).trim();
        if (first.equals(second)) {
            return first;
        }
        return draft.trim();
    }

    private static boolean hasDuplicateBlock(String body) {
        if (body == null || body.length() < 80) {
            return false;
        }
        int half = body.length() / 2;
        return body.substring(0, half).trim().equals(body.substring(half).trim());
    }
}
