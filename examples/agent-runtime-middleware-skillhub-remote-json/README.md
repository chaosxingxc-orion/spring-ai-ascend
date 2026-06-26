# Remote JSON SkillHub Example

本样例验证“远端 SkillHub”形态：一个进程作为 HTTP SkillHub，按 JSON catalog 暴露 skill 摘要、完整定义和 zip 包；另一个进程作为 runtime，通过 `SkillHubProvider` 的 HTTP 实现读取远端 SkillHub，并把 skill 安装到 OpenJiuwen Agent。

完整 step-by-step 教程见 [TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 终端 1：启动远端 SkillHub

```powershell
$env:SERVER_PORT="19101"
$env:SAMPLE_SKILLHUB_ROLE="hub"
Push-Location examples/agent-runtime-middleware-skillhub-remote-json
..\..\mvnw.cmd spring-boot:run
Pop-Location
```

验证远端 SkillHub：

```bash
curl --noproxy '*' http://127.0.0.1:19101/hub/skills
curl --noproxy '*' http://127.0.0.1:19101/hub/skills/date-helper
curl --noproxy '*' -o date-helper.zip http://127.0.0.1:19101/hub/skills/date-helper/package
```

## 终端 2：启动 runtime

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

验证 runtime 经 HTTP Provider 访问远端 SkillHub：

```bash
curl --noproxy '*' http://127.0.0.1:19102/sample/skillhub/skills
curl --noproxy '*' http://127.0.0.1:19102/sample/skillhub/skills/date-helper
curl --noproxy '*' -o date-helper-runtime.zip http://127.0.0.1:19102/sample/skillhub/skills/date-helper/package
```

触发一次 Agent 执行：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:19102/sample/skillhub/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"请用日期助手回答今天是什么日期"}'
```

观察点：

- hub 端只暴露 `/hub/**`，不创建 OpenJiuwen Agent。
- runtime 端只配置 `sample.remote-skillhub.base-url`，不直接读 hub 的 JSON 文件。
- runtime 日志包含 `installed openjiuwen skill`，说明远端 definition 被转换成 OpenJiuwen 本地 skill 注册。

## JSON catalog 格式

目录文件：`skills/catalog.json`。

```json
{
  "skills": [{
    "skillId": "date-helper",
    "name": "Date Helper",
    "description": "Use this skill for date, calendar, and deadline wording.",
    "path": "date-helper",
    "tags": ["date", "calendar"],
    "toolDependencies": [],
    "metadata": {"source": "remote-json"}
  }]
}
```

`path` 指向 `skills/` 下的本地目录；hub 负责把它包装成远端 definition/package。生产环境可以把这个 JSON catalog 换成数据库、对象存储或 Nacos Skill Registry。
