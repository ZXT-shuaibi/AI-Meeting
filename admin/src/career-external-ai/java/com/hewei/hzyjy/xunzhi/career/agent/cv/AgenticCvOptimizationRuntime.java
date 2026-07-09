package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AgenticCvOptimizationRuntime {

    private final AgenticCvOptimizationAgent agent;
    private final AiTracePublisher tracePublisher;

    public AgenticCvOptimizationRuntime(AgenticCvOptimizationAgent agent) {
        this(agent, null);
    }

    public CvOptimizationResult optimize(String memoryId, CvBO cv, String jobDescription, List<String> referenceTemplates) {
        String traceId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        if (tracePublisher != null) {
            tracePublisher.started(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent",
                    "cv=" + cv + ", jd=" + jobDescription);
        }
        try {
            CvBO optimized = agent.optimizeCv(memoryId, cv, jobDescription, referenceTemplates == null ? List.of() : referenceTemplates);
            AgenticScope scope = agent.getAgenticScope(memoryId);
            CvReview review = scope == null ? null : (CvReview) scope.readState("cvReview", null);
            int iterations = scope == null ? 0 : Math.max(1, scope.agentInvocations(AgenticCvReviewAgent.class).size());
            boolean scoreGatePassed = review != null && review.score() > AgenticCvOptimizationAgent.SCORE_GATE;
            String failureReason = scoreGatePassed ? null : "Score gate not reached after LangChain4j agentic loop";
            CvOptimizationResult result = CvOptimizationResult.builder()
                    .cv(optimized)
                    .bestReview(review)
                    .iterations(iterations)
                    .scoreGatePassed(scoreGatePassed)
                    .failureReason(failureReason)
                    .reviewHistory(review == null ? List.of() : List.of(review))
                    .build();
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent",
                        start, String.valueOf(result));
            }
            return result;
        } catch (RuntimeException ex) {
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent", start, ex);
            }
            throw ex;
        }
    }
}
