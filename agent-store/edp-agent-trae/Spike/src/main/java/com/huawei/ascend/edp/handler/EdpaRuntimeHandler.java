package com.huawei.ascend.edp.handler;

import com.huawei.ascend.edp.config.EdpAgentConfig;
import com.huawei.ascend.edp.config.EdpAgentConfigLoader;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.EdpConfigLoader;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EDPAgent 运行时适配器。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>作为 {@link OpenJiuwenAgentRuntimeHandler} 的 EDPAgent 实现，接入 agent-runtime 的 A2A 执行链路。</li>
 *     <li>加载 EDPAgent 标准 YAML 配置和 EDP 专有配置，并合成为 {@link DeepAgentConfig}。</li>
 *     <li>创建并增强 DeepAgent，注册 EDPAgent 的业务工具和业务 Rails。</li>
 *     <li>向 agent-runtime 暴露可执行的 OpenJiuwen {@link BaseAgent} 实例。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #EdpaRuntimeHandler()}：构造 handler，并向父类声明固定 agentId。</li>
 *     <li>{@link #init(String, String)}：初始化配置、DeepAgent、业务工具和 Rails。</li>
 *     <li>{@link #isHealthy()}：供运行时健康检查使用。</li>
 *     <li>{@link #getDeepAgent()}：供测试或诊断读取当前 DeepAgent 实例。</li>
 *     <li>{@link #getEdpConfig()}：供测试或诊断读取当前 EDP 专有配置。</li>
 * </ul>
 *
 * <p>被 agent-runtime 调用的覆写接口：</p>
 * <ul>
 *     <li>{@link #createOpenJiuwenAgent(AgentExecutionContext)}：返回真正参与执行的 OpenJiuwen Agent。</li>
 *     <li>{@link #openJiuwenRails(AgentExecutionContext)}：返回 EDPAgent 业务 Rails。</li>
 * </ul>
 */
public class EdpaRuntimeHandler extends OpenJiuwenAgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpaRuntimeHandler.class);

    /**
     * EDPAgent 在 agent-runtime 中注册和路由使用的固定 agentId。
     */
    private static final String AGENT_ID = "edp-agent";

    /**
     * DeepAgent 外观对象，负责创建和持有底层 OpenJiuwen BaseAgent。
     */
    private DeepAgent deepAgent;

    /**
     * EDPAgent 标准配置，来自 edp-agent.yaml。
     */
    private EdpAgentConfig agentConfig;

    /**
     * EDPAgent 专有配置，来自 edp-config.yaml。
     */
    private EdpConfig edpConfig;

    /**
     * 构造 EDPAgent runtime handler。
     *
     * <p>输入参数：无。</p>
     * <p>输出参数：无直接返回值；构造完成后父类持有 agentId=edp-agent。</p>
     */
    public EdpaRuntimeHandler() {
        // 父类依赖 agentId 完成 A2A 请求到具体 AgentRuntimeHandler 的路由。
        super(AGENT_ID);
    }

    /**
     * 初始化 EDPAgent。
     *
     * <p>作用：</p>
     * <ul>
     *     <li>加载标准 agent YAML 配置。</li>
     *     <li>加载 EDP 专有配置。</li>
     *     <li>合成 DeepAgentConfig 并创建 DeepAgent。</li>
     *     <li>注册 EDPAgent 业务工具和业务 Rails。</li>
     *     <li>触发 DeepAgent 初始化，确保后续 A2A 请求可直接执行。</li>
     * </ul>
     *
     * @param yamlPath 标准 agent YAML 配置路径，通常指向 edp-agent.yaml
     * @param configPath EDP 专有配置路径，通常指向 edp-config.yaml
     */
    public void init(String yamlPath, String configPath) {
        LOGGER.info("EdpaRuntimeHandler init start, yamlPath={}, configPath={}", yamlPath, configPath);

        // 第一步：加载标准 Agent 配置。该配置提供模型、Prompt、框架开关等通用信息。
        agentConfig = EdpAgentConfigLoader.load(Path.of(yamlPath));

        // 第二步：加载 EDP 专有配置。该配置提供 todolist、limits、llm_sampling、话术等业务信息。
        edpConfig = EdpConfigLoader.load(Path.of(configPath));
        Path yamlDir = Path.of(yamlPath).toAbsolutePath().normalize().getParent();

        // 第三步：把标准配置和 EDP 专有配置合成为 DeepAgent 可识别的配置对象。
        DeepAgentConfig deepAgentConfig = buildDeepAgentConfig(agentConfig, edpConfig, yamlDir);

        // 第四步：通过 OpenJiuwen HarnessFactory 创建 DeepAgent。
        deepAgent = HarnessFactory.createDeepAgent(deepAgentConfig);

        // 第五步：注册 Skill 目录，确保 OpenJiuwen SkillUtil 可以生成 Skill prompt。
        registerSkills(yamlDir);

        // 第六步：注册 EDPAgent 内置业务工具和业务 Rails。
        EdpaAgentEnhancer.enhance(deepAgent, edpConfig, agentConfig);

        // 第七步：强制完成 DeepAgent 初始化，避免首次请求时延迟初始化失败。
        deepAgent.ensureInitialized();

        LOGGER.info("EdpaRuntimeHandler init completed, agentId={}, deepAgent initialized={}",
                AGENT_ID, deepAgent.isInitialized());
    }

    /**
     * 注册 edp-agent.yaml 中声明的 Skill 目录。
     *
     * @param yamlDir edp-agent.yaml 所在目录
     */
    private void registerSkills(Path yamlDir) {
        EdpAgentConfig.Skills skills = agentConfig != null ? agentConfig.getSkills() : null;
        if (skills == null || skills.getDirectories().isEmpty()) {
            LOGGER.info("Skill load skipped: no skills.directories configured");
            return;
        }
        ensureSkillSysOperationId();
        for (String directory : skills.getDirectories()) {
            Path skillRoot = resolveSkillDirectory(yamlDir, directory);
            boolean exists = Files.exists(skillRoot);
            LOGGER.info("Skill load probe: configuredDirectory={}, resolvedDirectory={}, exists={}",
                    directory, skillRoot, exists);
            if (exists) {
                deepAgent.getAgent().registerSkill(skillRoot.toString());
            }
        }
        boolean hasSkill = deepAgent.getAgent().getSkillUtil() != null && deepAgent.getAgent().getSkillUtil().hasSkill();
        int skillCount = hasSkill ? deepAgent.getAgent().getSkillUtil().getSkillManager().count() : 0;
        List<String> skillNames = hasSkill ? deepAgent.getAgent().getSkillUtil().getSkillManager().getNames() : List.of();
        LOGGER.info("Skill load completed: hasSkill={}, skillCount={}, skillNames={}, mode={}",
                hasSkill, skillCount, skillNames, skills.getMode());
    }

    /**
     * 为 OpenJiuwen SkillUtil 初始化提供 sysOperationId。
     */
    private void ensureSkillSysOperationId() {
        Object config = deepAgent.getAgent().getConfig();
        if (config instanceof ReActAgentConfig reactConfig && reactConfig.getSysOperationId() == null) {
            reactConfig.setSysOperationId(AGENT_ID);
        }
    }

    /**
     * 解析 Skill 目录。相对路径按 edp-agent.yaml 所在目录解析。
     */
    private Path resolveSkillDirectory(Path yamlDir, String directory) {
        Path path = Path.of(directory);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return yamlDir.resolve(path).normalize();
    }

    /**
     * 构造 DeepAgentConfig。
     *
     * <p>作用：</p>
     * <ul>
     *     <li>从 edp-agent.yaml 提取模型、Prompt、框架选项。</li>
     *     <li>从 edp-config.yaml 提取 llm_sampling 采样参数。</li>
     *     <li>兼容 OpenJiuwen Java SDK 当前使用的多组字段名。</li>
     * </ul>
     *
     * @param agentConfig 标准 agent 配置，提供模型、Prompt、框架选项
     * @param edpConfig EDP 专有配置，提供采样参数等业务补充配置
     * @return DeepAgentConfig，供 HarnessFactory 创建 DeepAgent
     */
    private DeepAgentConfig buildDeepAgentConfig(EdpAgentConfig agentConfig, EdpConfig edpConfig, Path yamlDir) {
        EdpAgentConfig.Model model = agentConfig.getModel();
        EdpAgentConfig.Options options = agentConfig.getFramework() != null ? agentConfig.getFramework().getOptions() : null;
        EdpAgentConfig.Prompt prompt = agentConfig.getPrompt();
        EdpConfig.LlmSampling sampling = edpConfig != null ? edpConfig.getLlmSampling() : null;

        Map<String, Object> modelMap = new LinkedHashMap<>();
        Map<String, Object> backendMap = new LinkedHashMap<>();

        // 关键判断：模型配置为空时不写入模型和后端信息，交由 DeepAgent/SDK 默认逻辑处理。
        if (model != null) {
            // model/model_name 同时写入，用于兼容不同 SDK 层的字段读取习惯。
            modelMap.put("model", model.getName());
            modelMap.put("model_name", model.getName());

            // 关键判断：只有 edp-config.yaml 中声明了 llm_sampling 时才覆盖默认采样参数。
            if (sampling != null) {
                modelMap.put("temperature", sampling.getTemperature());
                modelMap.put("top_p", sampling.getTopP());
            }

            // provider/client_provider 同时写入，用于兼容后端 client provider 字段命名差异。
            backendMap.put("provider", model.getProvider());
            backendMap.put("client_provider", model.getProvider());

            // apiKey/api_key、baseUrl/apiBase/api_base 同时写入，用于兼容 Java/Python 风格字段名。
            backendMap.put("apiKey", model.getApiKey());
            backendMap.put("api_key", model.getApiKey());
            backendMap.put("baseUrl", model.getBaseUrl());
            backendMap.put("apiBase", model.getBaseUrl());
            backendMap.put("api_base", model.getBaseUrl());
        }

        return DeepAgentConfig.builder()
                // Prompt 为空时传空字符串，避免 null 进入下游 Prompt 构造逻辑。
                .systemPrompt(prompt != null ? prompt.getSystem() : "")
                // maxIterations 必须为正数；未配置或非法时使用 spike 默认值 15。
                .maxIterations(options != null && options.getMaxIterations() > 0 ? options.getMaxIterations() : 15)
                // enableTaskLoop 未配置时默认为 false。
                .enableTaskLoop(options != null && options.isEnableTaskLoop())
                .skillDirectories(resolvedSkillDirectories(agentConfig, yamlDir))
                .skillMode(agentConfig.getSkills() != null ? agentConfig.getSkills().getMode() : "all")
                .model(modelMap)
                .backend(backendMap)
                .build();
    }

    /**
     * 返回按 edp-agent.yaml 所在目录解析后的 Skill 目录，确保 SkillUseRail 与 EDP 手工注册使用同一目录。
     */
    private List<String> resolvedSkillDirectories(EdpAgentConfig config, Path yamlDir) {
        EdpAgentConfig.Skills skills = config != null ? config.getSkills() : null;
        if (skills == null || skills.getDirectories().isEmpty()) {
            return List.of();
        }
        return skills.getDirectories().stream()
                .map(directory -> resolveSkillDirectory(yamlDir, directory).toString())
                .toList();
    }

    /**
     * 创建 OpenJiuwen Agent 执行实例。
     *
     * <p>作用：agent-runtime 在处理 A2A 请求时调用该方法获取真正执行请求的 {@link BaseAgent}。</p>
     *
     * @param context 当前 A2A/Agent 执行上下文
     * @return DeepAgent 内部持有的 BaseAgent 实例
     */
    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        LOGGER.info("createOpenJiuwenAgent called, returning DeepAgent instance, agentId={}", AGENT_ID);
        // DeepAgent 是外观对象，OpenJiuwenAgentRuntimeHandler 需要的是底层 BaseAgent。
        return deepAgent.getAgent();
    }

    /**
     * 返回每次请求需要临时安装的 OpenJiuwen Rails。
     *
     * <p>EDPAgent 业务 Rails 已在初始化阶段通过 {@link EdpaAgentEnhancer#enhance} 注册到持久化 BaseAgent，
     * 这里不能再次返回同一批业务 Rails，否则每次 A2A 请求都会累积重复 callback。</p>
     *
     * @param context 当前 A2A/Agent 执行上下文
     * @return 每请求临时 Rails 列表，当前为空
     */
    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        LOGGER.info("openJiuwenRails called, EDPAgent business rails already installed during init; returning no per-request rails");
        return List.of();
    }

    /**
     * 健康检查接口。
     *
     * <p>作用：向运行时或测试代码报告当前 handler 是否可服务。</p>
     *
     * @return true 表示 DeepAgent 已创建且初始化完成；false 表示尚未初始化或初始化失败
     */
    @Override
    public boolean isHealthy() {
        // 关键判断：deepAgent 非空且 SDK 初始化成功，才认为当前 handler 健康。
        return deepAgent != null && deepAgent.isInitialized();
    }

    /**
     * 获取当前 DeepAgent。
     *
     * <p>作用：主要供 spike 测试、问题定位和诊断读取当前运行时对象。</p>
     *
     * @return 当前 DeepAgent；未初始化时可能为 null
     */
    public DeepAgent getDeepAgent() {
        return deepAgent;
    }

    /**
     * 获取当前 EDP 专有配置。
     *
     * <p>作用：主要供 spike 测试、问题定位和诊断读取配置解析结果。</p>
     *
     * @return 当前 EdpConfig；未初始化时可能为 null
     */
    public EdpConfig getEdpConfig() {
        return edpConfig;
    }
}
