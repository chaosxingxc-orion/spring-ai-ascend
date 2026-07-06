package com.huawei.ascend.edp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * EDPAgent Todo 数据层（单一文件）。
 *
 * <p>命名说明（v2 §10.2）：「Catalog」是 todo 内部 {@code entries.catalog_id} 字段的语义，
 * 留在字段层；承载它的容器类用 Todo 命名。故本类从 {@code EdpaTodoCatalog} 重命名为 {@code EdpaTodolist}，
 * 直接映射 scenario-config.yaml 的 {@code todolist} 一级配置段。</p>
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>加载 scenario-config.yaml 的 {@code todolist} 一级配置段（entries + dynamic_paths）。</li>
 *     <li>向后兼容：未配置 {@code todolist} 时，从旧版 {@code todolist_steps} 生成 entries。</li>
 *     <li>提供 O(1) 的 catalog_id 查询和路径规则访问，供 Rail 层注入 prompt 和参数补全使用。</li>
 *     <li>启动期校验：catalog_id 唯一、depends_on 引用存在、skip_steps 引用存在、依赖图无环。</li>
 * </ul>
 *
 * <p>{@link TodoEntry} 和 {@link DynamicPath} 作为本类的 static inner class，不单独拆分文件。
 * {@code catalog_id} 作为 entry 的内部主键字段名保留（字段层语义）。</p>
 */
public class EdpaTodolist {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpaTodolist.class);

    /**
     * 任务定义列表（保持配置顺序）。
     */
    private final List<TodoEntry> entries;

    /**
     * 动态路径规则列表。
     */
    private final List<DynamicPath> dynamicPaths;

    /**
     * catalog_id 到 TodoEntry 的索引，O(1) 查询。
     */
    private final Map<String, TodoEntry> index;

    /**
     * 从 ActRuleConfig POJO 构造（governance 数据源）。
     *
     * @param rawEntries  ActRuleConfig.TodolistEntry 列表
     * @param rawPaths    ActRuleConfig.TodolistPath 列表
     * @throws IllegalArgumentException 校验失败（catalog_id 重复、引用不存在、依赖图有环）
     */
    public EdpaTodolist(List<ActRuleConfig.TodolistEntry> rawEntries,
                        List<ActRuleConfig.TodolistPath> rawPaths) {
        Objects.requireNonNull(rawEntries, "todolist entries must not be null");
        this.entries = new ArrayList<>(rawEntries.size());
        this.dynamicPaths = new ArrayList<>();
        for (ActRuleConfig.TodolistEntry e : rawEntries) {
            this.entries.add(new TodoEntry(
                    e.getCatalogId(),
                    e.getContent(),
                    e.getDescription(),
                    e.getDependsOn(),
                    e.getSkill()));
        }
        if (rawPaths != null) {
            for (ActRuleConfig.TodolistPath p : rawPaths) {
                this.dynamicPaths.add(new DynamicPath(
                        p.getPathId(),
                        p.getDescription(),
                        p.getTrigger(),
                        p.getSkipSteps(),
                        p.getRedirect()));
            }
        }
        this.index = buildIndex(this.entries);
        validateReferences();
        validateAcyclic();
        LOGGER.info("EdpaTodolist loaded from governance actrule: entries={}, dynamicPaths={}",
                entries.size(), dynamicPaths.size());
    }

    private static Map<String, TodoEntry> buildIndex(List<TodoEntry> entries) {
        Map<String, TodoEntry> index = new LinkedHashMap<>();
        for (TodoEntry entry : entries) {
            if (index.put(entry.getCatalogId(), entry) != null) {
                throw new IllegalArgumentException(
                        "Duplicate catalog_id in todolist.entries: " + entry.getCatalogId());
            }
        }
        return index;
    }

    public List<TodoEntry> getEntries() {
        return entries;
    }

    public List<DynamicPath> getDynamicPaths() {
        return dynamicPaths;
    }

    public TodoEntry findByCatalogId(String catalogId) {
        return index.get(catalogId);
    }

    /**
     * 是否存在动态路径规则。
     *
     * @return 存在 dynamic_paths 返回 true
     */
    public boolean hasDynamicPaths() {
        return !dynamicPaths.isEmpty();
    }

    /**
     * 校验 depends_on 和 dynamic_paths.skip_steps 引用的 catalog_id 都存在。
     */
    private void validateReferences() {
        for (TodoEntry entry : entries) {
            for (String dep : entry.getDependsOn()) {
                if (!index.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "todolist.entries[" + entry.getCatalogId() + "].depends_on references unknown catalog_id: " + dep);
                }
            }
        }
        for (DynamicPath path : dynamicPaths) {
            for (String skip : path.getSkipSteps()) {
                if (!index.containsKey(skip)) {
                    throw new IllegalArgumentException(
                            "todolist.dynamic_paths[" + path.getPathId() + "].skip_steps references unknown catalog_id: " + skip);
                }
            }
        }
    }

    /**
     * 校验 depends_on 构成的有向图无环（Kahn's 算法）。
     */
    private void validateAcyclic() {
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (TodoEntry entry : entries) {
            graph.putIfAbsent(entry.getCatalogId(), new HashSet<>());
            inDegree.putIfAbsent(entry.getCatalogId(), 0);
        }
        for (TodoEntry entry : entries) {
            for (String dep : entry.getDependsOn()) {
                // 边 dep -> entry：dep 完成后 entry 才可执行
                graph.computeIfAbsent(dep, k -> new HashSet<>()).add(entry.getCatalogId());
                inDegree.merge(entry.getCatalogId(), 1, Integer::sum);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : graph.getOrDefault(current, Collections.emptySet())) {
                int remain = inDegree.merge(next, -1, Integer::sum);
                if (remain == 0) {
                    queue.add(next);
                }
            }
        }

        if (visited != entries.size()) {
            throw new IllegalArgumentException(
                    "todolist.entries dependency graph has a cycle (visited " + visited + " of " + entries.size() + ")");
        }
    }

    /**
     * 任务定义（catalog_id 为内部主键字段，含完整字段）。
     */
    public static class TodoEntry {
        private final String catalogId;
        private final String content;
        private final String description;
        private final List<String> dependsOn;
        private final String skill;

        public TodoEntry(String catalogId, String content, String description, List<String> dependsOn, String skill) {
            this.catalogId = catalogId;
            this.content = content;
            this.description = description;
            this.dependsOn = dependsOn == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(dependsOn));
            this.skill = skill;
        }

        public String getCatalogId() { return catalogId; }
        public String getContent() { return content; }
        public String getDescription() { return description; }
        public List<String> getDependsOn() { return dependsOn; }
        public String getSkill() { return skill; }
    }

    /**
     * 动态路径规则（LLM 根据业务结果自主判断是否切换）。
     */
    public static class DynamicPath {
        private final String pathId;
        private final String description;
        private final String trigger;
        private final List<String> skipSteps;
        private final String redirect;

        public DynamicPath(String pathId, String description, String trigger, List<String> skipSteps, String redirect) {
            this.pathId = pathId;
            this.description = description;
            this.trigger = trigger;
            this.skipSteps = skipSteps == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(skipSteps));
            this.redirect = redirect;
        }

        public String getPathId() { return pathId; }
        public String getDescription() { return description; }
        public String getTrigger() { return trigger; }
        public List<String> getSkipSteps() { return skipSteps; }
        public String getRedirect() { return redirect; }
    }
}
