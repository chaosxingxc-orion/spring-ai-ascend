# Local SkillHub Example

本样例验证 `SkillHubProvider` 的本地目录接入方式。它启动一个常驻 Spring Boot 进程，用户可以用 curl 查看技能摘要、加载完整技能定义，并让 OpenJiuwen Agent 在执行前通过 `OpenJiuwenSkillHubInstaller` 注册本地 skill。

完整 step-by-step 教程见 [TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 启动

```powershell
$env:SAA_SAMPLE_LLM_API_KEY="<your api key>"
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE="https://api.deepseek.com"
$env:SAA_SAMPLE_LLM_MODEL="deepseek-chat"
$env:SERVER_PORT="19091"
Push-Location examples/agent-runtime-middleware-skillhub-local
..\..\mvnw.cmd spring-boot:run
Pop-Location
```

## 查看技能摘要

```bash
curl --noproxy '*' http://127.0.0.1:19091/sample/skillhub/skills
```

预期能看到 `date-helper` 的轻量摘要。此时只需要知道 skillId、name、description、tags 和 metadata，不需要把完整 `SKILL.md` 放入上下文。

## 加载完整技能

```bash
curl --noproxy '*' http://127.0.0.1:19091/sample/skillhub/skills/date-helper
```

预期返回完整 instructions，并在 metadata 中包含：

```json
{
  "openjiuwen.skill.path": "skills/date-helper"
}
```

OpenJiuwen adapter 会读取这个路径并调用 `BaseAgent.registerSkill(...)`。

## 下载技能包

```bash
curl --noproxy '*' -o date-helper.zip \
  http://127.0.0.1:19091/sample/skillhub/skills/date-helper/package
```

这个端点对应 `SkillHubProvider.loadSkillPackage(...)`，返回一个 `application/zip` 包，里面包含 `SKILL.md` 和同目录下的参考文件。测试团队可以用这个接口验证“摘要列表”和“完整包下载”是两条不同链路。

## 触发一次 Agent 执行

```bash
curl --noproxy '*' -X POST http://127.0.0.1:19091/sample/skillhub/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"请用日期助手告诉我今天是什么日期"}'
```

观察点：

- 日志包含 `installed openjiuwen skill`。
- 响应中的 `agentOutputs` 来自 OpenJiuwen 默认 Runner。
- 这个样例不实现 MCP tool，也不托管远端 Skill Registry；它只演示 SkillHub 的 summary/definition/provider/installer 链路。

## 使用 GitHub 上的公开 skill 目录

如果需要把 GitHub 上的公开 skill 拉到本地验证，不要改 Java 代码，只要把对应目录放进本样例的 `skills/` 下即可。例如：

下面命令请在 `examples/agent-runtime-middleware-skillhub-local` 目录下执行：

```bash
mkdir -p skills/openai-docs
curl -L -o skills/openai-docs/SKILL.md \
  https://raw.githubusercontent.com/openai/skills/main/skills/.curated/openai-docs/SKILL.md

mkdir -p skills/anthropic-skill-creator
curl -L -o skills/anthropic-skill-creator/SKILL.md \
  https://raw.githubusercontent.com/anthropics/skills/main/skills/skill-creator/SKILL.md

mkdir -p skills/huggingface-papers
curl -L -o skills/huggingface-papers/SKILL.md \
  https://raw.githubusercontent.com/huggingface/skills/main/skills/huggingface-papers/SKILL.md
```

这些命令演示“远端公开 skill 落地成本地 skill 目录”的流程。实际测试时建议确认对应仓库许可证；本样例会自动把每个包含 `SKILL.md` 的子目录作为一个 skill 暴露出来，并支持 summary、definition、zip package 三条链路。
