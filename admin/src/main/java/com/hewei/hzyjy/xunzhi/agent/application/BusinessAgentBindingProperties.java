package com.hewei.hzyjy.xunzhi.agent.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xunzhi-agent.agent-binding")
public class BusinessAgentBindingProperties {

    private String generalAgentChat;

    private String interviewQuestionExtraction;

    private String interviewAnswerEvaluation;

    private String interviewDemeanor;

    private String interviewQuestionAsking;

    private String resumeAnalysis;

    private String resumeReview;

    private String resumeTailor;

    private String jdAlignment;

    private String interviewCoordination;

    private String interviewReflection;

    public String resolveAgentName(BusinessAgentScene scene) {
        if (scene == null) {
            return null;
        }
        String configured = switch (scene) {
            case GENERAL_AGENT_CHAT -> generalAgentChat;
            case INTERVIEW_QUESTION_EXTRACTION -> interviewQuestionExtraction;
            case INTERVIEW_ANSWER_EVALUATION -> interviewAnswerEvaluation;
            case INTERVIEW_DEMEANOR -> interviewDemeanor;
            case INTERVIEW_QUESTION_ASKING -> interviewQuestionAsking;
            case RESUME_ANALYSIS -> resumeAnalysis;
            case RESUME_REVIEW -> resumeReview;
            case RESUME_TAILOR -> resumeTailor;
            case JD_ALIGNMENT -> jdAlignment;
            case INTERVIEW_COORDINATION -> interviewCoordination;
            case INTERVIEW_REFLECTION -> interviewReflection;
        };
        return configured == null || configured.isBlank() ? scene.getDefaultAgentName() : configured;
    }
}
