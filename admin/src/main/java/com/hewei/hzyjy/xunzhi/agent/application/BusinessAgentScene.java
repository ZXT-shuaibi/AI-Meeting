package com.hewei.hzyjy.xunzhi.agent.application;

import java.util.Arrays;
import java.util.List;

public enum BusinessAgentScene {

    GENERAL_AGENT_CHAT("general-agent-chat", "general-agent-chat"),
    INTERVIEW_QUESTION_EXTRACTION("interview-question-extraction", "interview-question-extraction"),
    INTERVIEW_ANSWER_EVALUATION("interview-answer-evaluation", "interview-answer-evaluation"),
    INTERVIEW_DEMEANOR("interview-demeanor", "interview-demeanor"),
    INTERVIEW_QUESTION_ASKING("interview-question-asking", "interview-question-asking"),
    RESUME_ANALYSIS("resume-analysis", "resume-analysis"),
    RESUME_REVIEW("resume-review", "resume-review"),
    RESUME_TAILOR("resume-tailor", "resume-tailor"),
    JD_ALIGNMENT("jd-alignment", "jd-alignment"),
    INTERVIEW_COORDINATION("interview-coordination", "interview-coordination"),
    INTERVIEW_REFLECTION("interview-reflection", "interview-reflection");

    private final String code;

    private final String defaultAgentName;

    private final List<String> candidateAgentNames;

    BusinessAgentScene(String code, String defaultAgentName, String... aliasAgentNames) {
        this.code = code;
        this.defaultAgentName = defaultAgentName;
        this.candidateAgentNames = Arrays.asList(buildCandidateNames(defaultAgentName, aliasAgentNames));
    }

    public String getCode() {
        return code;
    }

    public String getDefaultAgentName() {
        return defaultAgentName;
    }

    public List<String> getCandidateAgentNames() {
        return candidateAgentNames;
    }

    private static String[] buildCandidateNames(String defaultAgentName, String... aliasAgentNames) {
        String[] names = new String[aliasAgentNames.length + 1];
        names[0] = defaultAgentName;
        System.arraycopy(aliasAgentNames, 0, names, 1, aliasAgentNames.length);
        return names;
    }
}
