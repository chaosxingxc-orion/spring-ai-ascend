package com.huawei.ascend.examples.workmate.diag;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.security.SecurityPolicyService;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NetworkCheckService {

    private final SecurityPolicyService securityPolicyService;
    private final WorkmateMcpProperties mcpProperties;

    public NetworkCheckService(SecurityPolicyService securityPolicyService, WorkmateMcpProperties mcpProperties) {
        this.securityPolicyService = securityPolicyService;
        this.mcpProperties = mcpProperties;
    }

    public NetworkCheckReport runChecks() {
        String proxyMode = resolveProxyMode();
        List<NetworkCheckResult> checks = new ArrayList<>();
        checks.add(ping("http://localhost:8080/actuator/health"));
        checks.add(ping("https://www.example.com"));
        for (McpServerConfig server : mcpProperties.servers()) {
            if (server.enabled() && server.url() != null && !server.url().isBlank()) {
                checks.add(ping(server.url()));
            }
        }
        return new NetworkCheckReport(proxyMode, checks);
    }

    private NetworkCheckResult ping(String url) {
        long start = System.currentTimeMillis();
        boolean policyAllowed = securityPolicyService.isUrlAllowed(url);
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            boolean ok = code >= 200 && code < 500;
            String detail = policyAllowed ? "HTTP " + code : "HTTP " + code + " · blocked by domain policy";
            return new NetworkCheckResult(url, ok, latency, detail, policyAllowed);
        } catch (Exception ex) {
            String detail = policyAllowed ? ex.getMessage() : ex.getMessage() + " · blocked by domain policy";
            return new NetworkCheckResult(
                    url, false, System.currentTimeMillis() - start, detail, policyAllowed);
        }
    }

    private static String resolveProxyMode() {
        String httpProxy = System.getenv("HTTP_PROXY");
        if (httpProxy == null || httpProxy.isBlank()) {
            httpProxy = System.getenv("http_proxy");
        }
        if (httpProxy == null || httpProxy.isBlank()) {
            return "direct";
        }
        try {
            URI uri = URI.create(httpProxy);
            Proxy.Type type = Proxy.Type.HTTP;
            InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 8080);
            return type + "://" + address.getHostString() + ":" + address.getPort();
        } catch (Exception ex) {
            return "configured:" + httpProxy;
        }
    }
}
