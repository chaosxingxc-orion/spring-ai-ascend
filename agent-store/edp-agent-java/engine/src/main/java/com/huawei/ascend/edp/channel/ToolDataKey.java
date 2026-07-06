package com.huawei.ascend.edp.channel;

/**
 * ToolDataChannel 四元组隔离键。
 *
 * 按 tenantId + agentId + contextId + taskId 隔离数据。
 */
public class ToolDataKey {

    private final String tenantId;
    private final String agentId;
    private final String contextId;
    private final String taskId;

    public ToolDataKey(String tenantId, String agentId, String contextId, String taskId) {
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.contextId = contextId;
        this.taskId = taskId;
    }

    public String getTenantId() { return tenantId; }
    public String getAgentId() { return agentId; }
    public String getContextId() { return contextId; }
    public String getTaskId() { return taskId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolDataKey)) return false;
        ToolDataKey that = (ToolDataKey) o;
        return tenantId.equals(that.tenantId) && agentId.equals(that.agentId)
                && contextId.equals(that.contextId) && taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        int result = tenantId.hashCode();
        result = 31 * result + agentId.hashCode();
        result = 31 * result + contextId.hashCode();
        result = 31 * result + taskId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ToolDataKey{tenant=" + tenantId + ",agent=" + agentId
                + ",context=" + contextId + ",task=" + taskId + "}";
    }
}
