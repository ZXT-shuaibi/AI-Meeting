package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentNotificationOutboxDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentNotificationOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** 成功运行的通知 Outbox。仅发送场景、运行编号和脱敏结果摘要，不携带简历、JD、回答或 Prompt。 */
@Slf4j @Service @RequiredArgsConstructor
public class AgentNotificationOutboxService {
    private final AgentNotificationOutboxMapper outboxMapper;
    private final McpNotificationClient notificationClient;

    public void enqueueCompletion(AgentRunDO run) {
        if (run == null || run.getRunId() == null || run.getUserId() == null) return;
        String key = "completion:" + run.getRunId();
        if (outboxMapper.selectCount(Wrappers.<AgentNotificationOutboxDO>lambdaQuery().eq(AgentNotificationOutboxDO::getDeliveryKey, key)) > 0) return;
        AgentNotificationOutboxDO item = new AgentNotificationOutboxDO();
        item.setDeliveryKey(key); item.setRunId(run.getRunId()); item.setUserId(run.getUserId()); item.setChannel("MCP_NOTIFICATION");
        item.setStatus("PENDING"); item.setTitle("Agent 任务已完成：" + run.getSceneCode()); item.setAttemptCount(0);
        item.setPayloadJson(JSON.toJSONString(Map.of("runId", run.getRunId(), "sceneCode", run.getSceneCode(), "resultSummary", safe(run.getResultSummary()))));
        outboxMapper.insert(item); deliver(item);
    }

    /** 管理端只读查看最近投递，不返回外部响应正文或任何业务原文。 */
    public List<AgentNotificationOutboxDO> listRecent(int limit) {
        return outboxMapper.selectList(Wrappers.<AgentNotificationOutboxDO>lambdaQuery()
                .orderByDesc(AgentNotificationOutboxDO::getCreateTime).last("LIMIT " + Math.min(100, Math.max(1, limit))));
    }
    private void deliver(AgentNotificationOutboxDO item) {
        try {
            item.setAttemptCount(item.getAttemptCount() + 1);
            McpNotificationResult result = notificationClient.send(Map.of("tool", "career.notify_completion", "arguments", JSON.parseObject(item.getPayloadJson(), Map.class)));
            item.setStatus(result.delivered() ? "SENT" : result.enabled() ? "FAILED" : "SKIPPED");
            item.setErrorSummary(result.errorSummary()); if (result.delivered()) item.setDeliveredAt(new Date()); outboxMapper.updateById(item);
        } catch (Exception ex) { item.setStatus("FAILED"); item.setErrorSummary("MCP 通知桥调用异常"); outboxMapper.updateById(item); log.warn("MCP 通知发送失败，已保留 Outbox 记录。runId={}", item.getRunId(), ex); }
    }
    private String safe(String value) { return value == null ? "" : value.substring(0, Math.min(240, value.length())); }
}
