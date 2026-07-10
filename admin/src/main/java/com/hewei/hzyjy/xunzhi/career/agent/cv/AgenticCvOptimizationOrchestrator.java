package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class AgenticCvOptimizationOrchestrator extends CvOptimizationOrchestrator {

    private final AgenticCvOptimizationRuntime runtime;

    public AgenticCvOptimizationOrchestrator(
            CvReviewer reviewer,
            ScoredCvTailor tailor,
            AgenticCvOptimizationRuntime runtime) {
        super(reviewer, tailor);
        this.runtime = runtime;
    }

    @Override
    public CvOptimizationResult optimize(CvBO cv, String jobDescription, List<String> referenceTemplates, int maxIterations) {
        if (runtime == null) {
            return super.optimize(cv, jobDescription, referenceTemplates, maxIterations);
        }
        try {
            String memoryId = cv == null || cv.getId() == null ? "resume:agentic:transient" : "resume:" + cv.getId();
            return runtime.optimize(memoryId, cv, jobDescription, referenceTemplates);
        } catch (Exception ex) {
            log.warn("LangChain4j Agentic CV optimization failed, falling back to local orchestrator", ex);
            return super.optimize(cv, jobDescription, referenceTemplates, maxIterations);
        }
    }

    @Override
    public CvOptimizationResult optimize(
            CvBO cv,
            String jobDescription,
            List<String> referenceTemplates,
            int maxIterations,
            Consumer<CvReview> progressCallback) {
        if (runtime == null) {
            return super.optimize(cv, jobDescription, referenceTemplates, maxIterations, progressCallback);
        }
        try {
            String memoryId = cv == null || cv.getId() == null ? "resume:agentic:transient" : "resume:" + cv.getId();
            return runtime.optimize(memoryId, cv, jobDescription, referenceTemplates, progressCallback);
        } catch (Exception ex) {
            log.warn("LangChain4j Agentic CV optimization failed, falling back to local orchestrator", ex);
            return super.optimize(cv, jobDescription, referenceTemplates, maxIterations, progressCallback);
        }
    }
}
