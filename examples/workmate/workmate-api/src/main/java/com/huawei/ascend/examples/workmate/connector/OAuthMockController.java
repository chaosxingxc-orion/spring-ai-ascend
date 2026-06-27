package com.huawei.ascend.examples.workmate.connector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** G23 — Mock OAuth authorize page for redirect walkthrough / dogfood. */
@RestController
@ConditionalOnProperty(prefix = "workmate.oauth", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
class OAuthMockController {

    @GetMapping(value = "/oauth/mock-authorize", produces = MediaType.TEXT_HTML_VALUE)
    public String mockAuthorize(
            @RequestParam String state,
            @RequestParam(name = "connector") String connectorId) {
        String name = ConnectorCatalog.displayName(connectorId);
        String deepLink =
                "workmate://oauth/callback?state=" + escape(state) + "&code=demo-mock-auth-code";
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8"/>
                  <title>WorkMate Mock OAuth — %s</title>
                  <style>
                    body { font-family: system-ui, sans-serif; max-width: 32rem; margin: 3rem auto; padding: 0 1rem; }
                    h1 { font-size: 1.25rem; }
                    code { background: #f4f4f5; padding: 0.15rem 0.35rem; border-radius: 4px; }
                    .btn { display: inline-block; margin-top: 1rem; padding: 0.5rem 1rem;
                      background: #111; color: #fff; text-decoration: none; border-radius: 8px; }
                    .hint { color: #6e6e73; font-size: 0.9rem; margin-top: 1.5rem; }
                  </style>
                </head>
                <body>
                  <h1>Mock 授权 — %s</h1>
                  <p>这是 WorkMate dogfood 用的模拟 OAuth 提供方。点击下方按钮模拟用户同意授权。</p>
                  <p>授权码（可手动粘贴）：<code id="code">demo-mock-auth-code</code></p>
                  <p>state：<code>%s</code></p>
                  <a class="btn" href="%s">在 WorkMate 桌面版中完成</a>
                  <p class="hint">Web 用户请复制授权码，回到连接器弹窗粘贴后点击「完成授权并连接」。</p>
                  <script>
                    document.getElementById('code').addEventListener('click', function () {
                      navigator.clipboard.writeText('demo-mock-auth-code');
                    });
                  </script>
                </body>
                </html>
                """
                .formatted(name, name, escape(state), deepLink);
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
