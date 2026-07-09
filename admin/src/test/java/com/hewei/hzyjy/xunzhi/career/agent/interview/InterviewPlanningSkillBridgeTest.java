package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewPlanningSkillBridgeTest {

    @Test
    void jdAlignmentPromptIncludesRuntimeSkillGuidance() {
        CapturingAiGateway aiGateway = new CapturingAiGateway("{\"score\":7,\"decision\":\"NEXT\",\"feedback\":\"ok\"}");
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, ClasspathCareerSkillRegistry.withBuiltIns());

        service.plan("interview:s1", CvBO.builder().id(1L).summary("Java Redis").build(), "Java Spring Redis");

        assertTrue(aiGateway.requests.stream().anyMatch(prompt -> prompt.contains("Runtime Skill: jd-alignment")));
        assertTrue(aiGateway.requests.stream().anyMatch(prompt -> prompt.contains("输出纯 JSON")));
    }

    @Test
    void reflectionPromptIncludesQuestionProbingSkillGuidance() {
        CapturingAiGateway aiGateway = new CapturingAiGateway("{\"score\":4,\"decision\":\"PROBE\",\"feedback\":\"Need details.\"}");
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, ClasspathCareerSkillRegistry.withBuiltIns());

        service.reflect("interview:s2", "How did you use Redis?", "I used it.", CvBO.builder().id(2L).summary("Redis project").build());

        assertTrue(aiGateway.requests.stream().anyMatch(prompt -> prompt.contains("Runtime Skill: question-probing")));
        assertTrue(aiGateway.requests.stream().anyMatch(prompt -> prompt.contains("STAR 模型追问法")));
    }

    private static class CapturingAiGateway implements AiGateway {
        private final String content;
        private final List<String> requests = new ArrayList<>();

        private CapturingAiGateway(String content) {
            this.content = content;
        }

        @Override
        public AiGatewayResult chat(com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest request) {
            requests.add(request.systemPrompt() + "\n" + request.userPrompt());
            return AiGatewayResult.builder()
                    .content(content)
                    .provider("test")
                    .build();
        }
    }
}
