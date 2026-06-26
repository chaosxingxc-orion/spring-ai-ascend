# Local SkillHub 样例教程

## 测试目标

验证 SkillHub 的本地目录模式：

1. Runtime 从本地 `skills/` 目录发现所有包含 `SKILL.md` 的 skill。
2. `/sample/skillhub/skills` 只返回摘要，不加载完整 instructions。
3. `/sample/skillhub/skills/{skillId}` 返回完整 skill 定义，并带上 OpenJiuwen 可注册的本地路径。
4. `/sample/skillhub/skills/{skillId}/package` 返回 zip 包，模拟后续下载/安装场景。
5. Agent 执行前，`OpenJiuwenSkillHubInstaller` 调用 `BaseAgent.registerSkill(...)` 注册本地 skill。

## 准备环境

需要一个可用的 OpenAI-compatible LLM API。DeepSeek 可按下面方式配置：

```powershell
$env:SAA_SAMPLE_LLM_API_KEY="<your api key>"
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE="https://api.deepseek.com"
$env:SAA_SAMPLE_LLM_MODEL="deepseek-chat"
```

样例自带一个本地 skill：

```text
examples/agent-runtime-middleware-skillhub-local/skills/date-helper/SKILL.md
```

如需测试公开 skill，可以先把公开仓库里的 `SKILL.md` 下载到 `skills/<skill-id>/SKILL.md`，不需要改 Java 代码。

## 启动样例

在仓库根目录执行：

```powershell
$env:SERVER_PORT="19091"
Push-Location examples/agent-runtime-middleware-skillhub-local
..\..\mvnw.cmd spring-boot:run
Pop-Location
```

## 验证摘要列表

```bash
curl --noproxy '*' http://127.0.0.1:19091/sample/skillhub/skills
```

预期能看到 `date-helper`，响应只包含 `skillId`、`name`、`description`、`tags`、`metadata` 等摘要字段。

## 验证完整定义

```bash
curl --noproxy '*' http://127.0.0.1:19091/sample/skillhub/skills/date-helper
```

预期响应包含完整 `instructions`，并且 metadata 中包含：

```json
{
  "openjiuwen.skill.path": "skills/date-helper"
}
```

这个字段由 example provider 生成，OpenJiuwen adapter 会用它调用 `BaseAgent.registerSkill(...)`。

## 验证技能包下载

```bash
curl --noproxy '*' -o date-helper.zip \
  http://127.0.0.1:19091/sample/skillhub/skills/date-helper/package
```

预期 `date-helper.zip` 非空，解压后能看到 `SKILL.md`。

## 验证 Agent 执行

```bash
curl --noproxy '*' -X POST http://127.0.0.1:19091/sample/skillhub/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"请用日期助手告诉我今天是什么日期"}'
```

观察点：

- 应用日志出现 `installed openjiuwen skill`。
- 响应中 `agentOutputs` 来自 OpenJiuwen 默认 Runner。
- Java 代码没有手工 override Agent 执行逻辑；skill 是通过 provider + installer 链路安装的。

## 自动化验证

```powershell
.\mvnw -f examples/agent-runtime-middleware-skillhub-local/pom.xml verify
```

自动化测试覆盖本地目录 provider 的 summary、definition、package 行为。curl 步骤用于验证用户视角的端到端体验。
