package com.bank.financial.research.web;

/**
 * A deliberately tiny Markdown→HTML renderer for the web playground preview. It
 * covers exactly what the report types' {@code toMarkdown()} emits — ATX headings,
 * bold spans, dash/star bullet lists, horizontal rules,
 * blank-line paragraphs — and HTML-escapes everything else so report text can
 * never inject markup. No third-party dependency: the playground is meant to run
 * from a single offline classpath.
 */
public final class MdHtml {

    private MdHtml() {
    }

    /** Render a Markdown document to a self-contained HTML fragment. */
    public static String render(String md) {
        if (md == null || md.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(md.length() * 2);
        String[] lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean inList = false;
        for (String raw : lines) {
            String line = raw.stripTrailing();
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                inList = closeList(out, inList);
                continue;
            }
            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                inList = closeList(out, inList);
                out.append("<hr/>\n");
                continue;
            }
            if (trimmed.startsWith("### ")) {
                inList = closeList(out, inList);
                out.append("<h3>").append(inline(trimmed.substring(4))).append("</h3>\n");
                continue;
            }
            if (trimmed.startsWith("## ")) {
                inList = closeList(out, inList);
                out.append("<h2>").append(inline(trimmed.substring(3))).append("</h2>\n");
                continue;
            }
            if (trimmed.startsWith("# ")) {
                inList = closeList(out, inList);
                out.append("<h1>").append(inline(trimmed.substring(2))).append("</h1>\n");
                continue;
            }
            if (trimmed.startsWith("> ")) {
                inList = closeList(out, inList);
                out.append("<blockquote>").append(inline(trimmed.substring(2))).append("</blockquote>\n");
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    out.append("<ul>\n");
                    inList = true;
                }
                out.append("<li>").append(inline(trimmed.substring(2))).append("</li>\n");
                continue;
            }
            inList = closeList(out, inList);
            out.append("<p>").append(inline(trimmed)).append("</p>\n");
        }
        closeList(out, inList);
        return out.toString();
    }

    private static boolean closeList(StringBuilder out, boolean inList) {
        if (inList) {
            out.append("</ul>\n");
        }
        return false;
    }

    /** Escape HTML, then apply inline **bold** on the escaped text. */
    private static String inline(String text) {
        String escaped = escape(text);
        StringBuilder sb = new StringBuilder(escaped.length());
        int i = 0;
        boolean bold = false;
        while (i < escaped.length()) {
            if (i + 1 < escaped.length() && escaped.charAt(i) == '*' && escaped.charAt(i + 1) == '*') {
                sb.append(bold ? "</strong>" : "<strong>");
                bold = !bold;
                i += 2;
            } else {
                sb.append(escaped.charAt(i));
                i++;
            }
        }
        if (bold) {
            sb.append("</strong>");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
