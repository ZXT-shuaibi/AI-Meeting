package com.hewei.hzyjy.xunzhi.career.resume.render;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.StringWriter;
import java.util.Map;

public class ResumeTemplateService {

    private static final String TEMPLATE_NAME = "career-resume/cv.md.ftl";

    private final Configuration configuration;

    public ResumeTemplateService() {
        this.configuration = createConfiguration();
    }

    public String renderMarkdown(CvBO cv, ResumeMarkdownConfig config, ResumeTemplateFieldMapper mapper) {
        try {
            Map<String, Object> dataModel = mapper.toTemplateData(cv);
            ResumeMarkdownConfig effectiveConfig = config == null ? ResumeMarkdownConfig.defaults() : config;
            dataModel.put("format", effectiveConfig.format());
            dataModel.put("headingOffset", effectiveConfig.headingOffset());
            dataModel.put("compactList", effectiveConfig.compactList());
            dataModel.put("includeHeaderBlock", effectiveConfig.includeHeaderBlock());

            Template template = configuration.getTemplate(TEMPLATE_NAME, "UTF-8");
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString().trim();
        } catch (ResumeRenderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResumeRenderException("Resume markdown template render failed: " + ex.getMessage(), ex);
        }
    }

    private Configuration createConfiguration() {
        try {
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            TemplateLoader primary = new ClassTemplateLoader(ResumeTemplateService.class, "/templates");
            TemplateLoader context = contextClassLoader == null ? null : new ClassTemplateLoader(contextClassLoader, "/templates");
            cfg.setTemplateLoader(context == null
                    ? primary
                    : new MultiTemplateLoader(new TemplateLoader[]{primary, context}));
            return cfg;
        } catch (Exception ex) {
            throw new ResumeRenderException("Initialize resume markdown template engine failed", ex);
        }
    }
}
