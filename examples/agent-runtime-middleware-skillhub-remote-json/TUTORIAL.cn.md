# Remote JSON SkillHub 样例教程

## 测试目标

验证 SkillHub 的远端目录模式：

1. 远端 Hub 返回全部 skills 的摘要。
2. 远端 Hub 返回某个 skill 的完整定义。
3. 远端 Hub 返回某个 skill 的 zip 包。
4. Runtime 通过 HTTP `SkillHubProvider` 访问远端 Hub。
5. OpenJiuwen adapter 将远端 definition 中的 `openjiuwen.skill.path` 注册到 Agent。

## 运行步骤

1. 启动 Hub：

```powershell
$env:SERVER_PORT="19101"
$env:SAMPLE_SKILLHUB_ROLE="hub"
Push-Location examples/agent-runtime-middleware-skillhub-remote-json
..\..\mvnw.cmd spring-boot:run
Pop-Location
```

2. 验证 Hub：

```bash
curl --noproxy '*' http://127.0.0.1:19101/hub/skills
curl --noproxy '*' http://127.0.0.1:19101/hub/skills/date-helper
curl --noproxy '*' -o date-helper.zip http://127.0.0.1:19101/hub/skills/date-helper/package
```

3. 启动 Runtime：

```powershell
$env:SAA_SAMPLE_LLM_API_KEY="<your api key>"
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE="https://api.deepseek.com"
$env:SAA_SAMPLE_LLM_MODEL="deepseek-chat"
$env:SERVER_PORT="19102"
$env:SAMPLE_SKILLHUB_ROLE="runtime"
$env:SAMPLE_REMOTE_SKILLHUB_BASE_URL="http://127.0.0.1:19101/hub"
Push-Location examples/agent-runtime-middleware-skillhub-remote-json
..\..\mvnw.cmd spring-boot:run
Pop-Location
```

4. 验证 Runtime 访问远端 Hub：

```bash
curl --noproxy '*' http://127.0.0.1:19102/sample/skillhub/skills
curl --noproxy '*' http://127.0.0.1:19102/sample/skillhub/skills/date-helper
curl --noproxy '*' -o date-helper-runtime.zip \
  http://127.0.0.1:19102/sample/skillhub/skills/date-helper/package
```

5. 触发一次 Agent 执行：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:19102/sample/skillhub/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"请用日期助手回答今天是什么日期"}'
```

## 预期结果

- `/hub/skills` 和 `/sample/skillhub/skills` 都能看到相同的 `date-helper`。
- `/sample/skillhub/skills/date-helper` 的 metadata 包含 `openjiuwen.skill.path`。
- `date-helper-runtime.zip` 非空，说明 Runtime 不是只读摘要，也能按需下载完整 skill 包。
- runtime 端执行 Agent 时日志包含 `installed openjiuwen skill`。

## 自动化验证

```powershell
.\mvnw -f examples/agent-runtime-middleware-skillhub-remote-json/pom.xml verify
```

自动化测试覆盖三件事：

1. JSON catalog provider 可以返回 summary、definition、package。
2. HTTP provider 可以通过远端 Hub API 读取同样的数据。
3. OpenJiuwen installer 可以把远端 definition 中的 `openjiuwen.skill.path` 注册进 Agent。

## 常见问题

- 如果 runtime 启动失败，先确认 `--sample.remote-skillhub.base-url` 指向 hub 的 `/hub` 前缀。
- 如果 package 下载为空，检查 `skills/catalog.json` 的 `path` 是否指向包含 `SKILL.md` 的目录。
