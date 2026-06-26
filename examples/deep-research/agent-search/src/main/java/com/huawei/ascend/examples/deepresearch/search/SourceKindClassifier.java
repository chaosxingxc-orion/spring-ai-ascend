package com.huawei.ascend.examples.deepresearch.search;

import com.huawei.ascend.examples.deepresearch.search.WebSearchProvider.SourceKind;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies a result URL into {@link SourceKind} buckets (official / blog /
 * news / forum) by host pattern. The classification feeds verify-agent's
 * authoritative-source preference — see TOPOLOGY §3.2.
 */
public final class SourceKindClassifier {

    private static final Set<String> VENDOR_OFFICIAL_HOSTS = Set.of(
            "volcengine.com",
            "bailian.aliyun.com",
            "bigmodel.cn",
            "moonshot.cn",
            "deepseek.com",
            "cloud.baidu.com",
            "cloud.tencent.com",
            "openai.com",
            "anthropic.com");

    private static final Set<String> BLOG_HOSTS = Set.of(
            "csdn.net", "juejin.cn", "zhihu.com", "jianshu.com", "infoq.cn", "medium.com");

    private static final Set<String> NEWS_HOSTS = Set.of(
            "36kr.com", "huxiu.com", "tmtpost.com", "techcrunch.com", "wired.com", "venturebeat.com");

    private static final Set<String> FORUM_HOSTS = Set.of(
            "v2ex.com", "reddit.com", "stackoverflow.com", "news.ycombinator.com");

    private SourceKindClassifier() {
    }

    public static SourceKind classify(String url) {
        String host = hostOf(url);
        if (host == null) {
            return SourceKind.BLOG;
        }
        if (matchesAny(host, VENDOR_OFFICIAL_HOSTS) || isOfficialDocsLike(host)) {
            return SourceKind.OFFICIAL;
        }
        if (matchesAny(host, NEWS_HOSTS)) {
            return SourceKind.NEWS;
        }
        if (matchesAny(host, FORUM_HOSTS)) {
            return SourceKind.FORUM;
        }
        if (matchesAny(host, BLOG_HOSTS)) {
            return SourceKind.BLOG;
        }
        return SourceKind.BLOG;
    }

    private static boolean isOfficialDocsLike(String host) {
        return host.startsWith("docs.") || host.startsWith("developer.") || host.startsWith("api.");
    }

    private static boolean matchesAny(String host, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}