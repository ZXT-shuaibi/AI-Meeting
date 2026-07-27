package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonResponseCleaner;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeTagType;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/** 解析完成后由大模型生成可复用的简历基础标签，失败时不影响简历主链路。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagResumeAutoTagService {
    private final AiGateway aiGateway;
    private final RagResumeTagMapper tagMapper;

    /**
     * 由实验室用户显式发起一次 AI 预评。
     *
     * <p>自动建标签发生在简历解析链路，不能让用户判断何时已经完成；这个入口把
     * 评测动作放回到“查看简历”的页面。点击即重新调用模型；只有模型已返回有效
     * 结果后才替换原标签，因此超时、拒答或空结果不会抹掉用户已有的人工补充。</p>
     */
    public List<RagResumeTagDO> evaluateOnDemand(CvBO cv) {
        if (cv == null || cv.getId() == null || cv.getUserId() == null) {
            throw new ClientException("简历结构化数据不完整，暂时无法进行 AI 预评");
        }
        Map<RagResumeTagType, List<String>> tags = extract(cv);
        if (tags.values().stream().flatMap(Collection::stream).findAny().isEmpty()) {
            throw new ClientException("AI 预评未返回可用标签，请稍后重试");
        }
        // 先完成模型响应的完整校验，再清空旧值；避免失败的 AI 调用破坏已确认标签。
        tagMapper.delete(Wrappers.lambdaQuery(RagResumeTagDO.class)
                .eq(RagResumeTagDO::getOwnerUserId, cv.getUserId())
                .eq(RagResumeTagDO::getResumeId, cv.getId()));
        List<RagResumeTagDO> generated = new ArrayList<>();
        tags.forEach((type, values) -> values.stream().distinct().forEach(value -> {
            RagResumeTagDO row = new RagResumeTagDO();
            row.setOwnerUserId(cv.getUserId());
            row.setResumeId(cv.getId());
            row.setTagType(type);
            row.setTagValue(value);
            tagMapper.insert(row);
            generated.add(row);
        }));
        log.info("AI 简历预评完成，resumeId={}, tagCount={}", cv.getId(), generated.size());
        return generated;
    }

    public void generateAndReplace(CvBO cv) {
        if (cv == null || cv.getId() == null || cv.getUserId() == null) return;
        try {
            Long existingCount = tagMapper.selectCount(Wrappers.lambdaQuery(RagResumeTagDO.class)
                    .eq(RagResumeTagDO::getOwnerUserId, cv.getUserId())
                    .eq(RagResumeTagDO::getResumeId, cv.getId()));
            if (existingCount != null && existingCount > 0) {
                // 标签可能已经由用户在实验室补充或修订；表结构尚未区分来源前，优先保护人工结论。
                log.info("简历已有可复用标签，跳过自动覆盖，resumeId={}, tagCount={}", cv.getId(), existingCount);
                return;
            }
            Map<RagResumeTagType, List<String>> tags = extract(cv);
            if (tags.values().stream().flatMap(Collection::stream).findAny().isEmpty()) {
                // 模型超时、拒答或只返回空数组时，绝不能用空结果覆盖已经由用户确认的标签。
                log.warn("简历自动标签未提取到有效内容，保留既有标签，resumeId={}", cv.getId());
                return;
            }
            tagMapper.delete(Wrappers.lambdaQuery(RagResumeTagDO.class)
                    .eq(RagResumeTagDO::getOwnerUserId, cv.getUserId())
                    .eq(RagResumeTagDO::getResumeId, cv.getId()));
            tags.forEach((type, values) -> values.stream().distinct().forEach(value -> {
                RagResumeTagDO row = new RagResumeTagDO();
                row.setOwnerUserId(cv.getUserId()); row.setResumeId(cv.getId()); row.setTagType(type); row.setTagValue(value);
                tagMapper.insert(row);
            }));
        } catch (Exception ex) {
            log.warn("简历自动标签生成失败，不影响解析结果，resumeId={}", cv.getId(), ex);
        }
    }

    private Map<RagResumeTagType, List<String>> extract(CvBO cv) {
        String content = aiGateway.chat(AiPromptRequest.builder().sceneCode("RESUME_RAG_TAGGING")
                .systemPrompt("Return one strict JSON object only. Keys: roles,directions,projects,skills,experiences. Each value is a concise string array, max 3,8,8,15,8 respectively. Extract only facts from the resume. No markdown, no commentary, no invented facts.")
                .userPrompt(JSON.toJSONString(cv)).metadata(Map.of("resumeId", String.valueOf(cv.getId()), "userId", String.valueOf(cv.getUserId()))).build()).content();
        JSONObject json = AgentResponseParser.jsonObject(CareerJsonResponseCleaner.cleanJsonResponse(content)).orElse(new JSONObject());
        Map<RagResumeTagType, List<String>> result = new LinkedHashMap<>();
        result.put(RagResumeTagType.ROLE, values(json, "roles", 3));
        result.put(RagResumeTagType.DIRECTION, values(json, "directions", 8));
        result.put(RagResumeTagType.PROJECT, values(json, "projects", 8));
        result.put(RagResumeTagType.SKILL, values(json, "skills", 15));
        result.put(RagResumeTagType.EXPERIENCE, values(json, "experiences", 8));
        return result;
    }

    private List<String> values(JSONObject json, String key, int limit) {
        return Optional.ofNullable(json.getList(key, String.class)).orElse(List.of()).stream().filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isBlank()).map(value -> value.substring(0, Math.min(100, value.length())))
                .distinct().limit(limit).toList();
    }
}
