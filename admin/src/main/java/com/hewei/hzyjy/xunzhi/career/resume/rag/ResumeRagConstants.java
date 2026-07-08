package com.hewei.hzyjy.xunzhi.career.resume.rag;

public final class ResumeRagConstants {

    private ResumeRagConstants() {
    }

    public static final String CHUNK_TYPE_OVERVIEW = "overview";
    public static final String CHUNK_TYPE_SUMMARY = "summary";
    public static final String CHUNK_TYPE_SKILLS = "skills";
    public static final String CHUNK_TYPE_EXPERIENCE = "experience";
    public static final String CHUNK_TYPE_PROJECT = "project";
    public static final String CHUNK_TYPE_EDUCATION = "education";

    public static final String META_CHUNK_TYPE = "chunk_type";
    public static final String META_RESUME_ID = "resume_id";
    public static final String META_CV_TYPE = "cv_type";
    public static final String META_CHUNK_INDEX = "chunk_index";
    public static final String META_INDUSTRIES = "industries";
    public static final String META_COMPANIES = "companies";
    public static final String META_SKILL_NAMES = "skill_names";
    public static final String META_ROLES = "roles";

    public static final String CACHE_KEY_HYDE = "xunzhi-agent:career:rag:hyde:";
    public static final String CACHE_KEY_MQ = "xunzhi-agent:career:rag:mq:";
}
