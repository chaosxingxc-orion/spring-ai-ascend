package com.huawei.ascend.examples.runtime.middleware.mcp.localtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        classes = LocalTimeMcpServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalTimeMcpServerApplicationTest {
    @LocalServerPort
    private int port;

    @Test
    void localMcpServerListsAndCallsDateTool() throws Exception {
        String listResponse = post("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """).body();
        String callResponse = post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_current_date","arguments":{}}}
                """).body();
        String machineInfoResponse = post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_machine_info","arguments":{}}}
                """).body();

        assertThat(listResponse).contains("get_current_date", "get_current_time", "get_machine_info");
        assertThat(callResponse).contains("structuredContent", "date");
        assertThat(machineInfoResponse).contains("structuredContent", "osName", "javaVersion");
    }

    @Test
    void localMcpServerAcceptsInitializedNotificationWithoutId() throws Exception {
        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                """);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isBlank();
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
