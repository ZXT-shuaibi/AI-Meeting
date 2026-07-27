package com.hewei.hzyjy.xunzhi.career.raglab.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagDatasetReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagExperimentReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.ReplaceResumeTagsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagJudgementsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagEvidenceAnnotationsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagDatasetItemsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagDatasetReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentDatasetService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabAccessService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabResumeCandidateService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagResumeAutoTagTaskService;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDatasetDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeAutoTagTaskResult;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderService;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** RAG 实验室的测试集、标签及访问状态接口。 */
@Slf4j
@RestController @RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/rag-experiments")
public class RagLabController {
    private final RagLabAccessService accessService;
    private final RagLabResumeCandidateService resumeCandidateService;
    private final RagExperimentDatasetService datasetService;
    private final RagExperimentDatasetMapper datasetMapper;
    private final RagResumeTagMapper tagMapper;
    private final RagExperimentService experimentService;
    private final CareerResumeMapper resumeMapper;
    private final CareerResumeParseTaskMapper parseTaskMapper;
    private final ResumeObjectStorage resumeObjectStorage;
    private final ResumeRenderService resumeRenderService;
    private final RagResumeAutoTagTaskService ragResumeAutoTagTaskService;

    @GetMapping("/access")
    public Result<Map<String, Object>> access(@CurrentUser Long userId, @CurrentUser String username) {
        boolean administrator = accessService.isAdministrator(username);
        return Results.success(Map.of("allowed", accessService.canAccess(userId, username), "administrator", administrator));
    }

    @GetMapping("/datasets")
    public Result<List<RagExperimentDatasetDO>> datasets(@CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        return Results.success(datasetMapper.selectList(Wrappers.lambdaQuery(RagExperimentDatasetDO.class)
                .eq(!accessService.isAdministrator(username), RagExperimentDatasetDO::getOwnerUserId, userId)
                .orderByDesc(RagExperimentDatasetDO::getCreateTime)));
    }

    @PostMapping("/datasets")
    public Result<RagExperimentDatasetDO> createDataset(@Valid @RequestBody CreateRagDatasetReqDTO request, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        return Results.success(datasetService.create(
                userId,
                accessService.isAdministrator(username),
                request.getName(),
                request.getDescription(),
                request.getResumeIds()));
    }

    /** 编辑测试集名称与说明；候选简历仍通过专门接口修改，便于保留操作边界。 */
    @PutMapping("/datasets/{datasetId}")
    public Result<Void> updateDataset(@PathVariable Long datasetId,
                                      @Valid @RequestBody UpdateRagDatasetReqDTO request,
                                      @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDatasetDO dataset = datasetService.requireDataset(datasetId);
        accessService.requireOwnerScope(userId, username, dataset.getOwnerUserId());
        datasetService.updateMetadata(datasetId, request.getName(), request.getDescription());
        return Results.success();
    }

    @GetMapping("/datasets/{datasetId}")
    public Result<Map<String, Object>> datasetDetail(@PathVariable Long datasetId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDatasetDO dataset = datasetService.requireDataset(datasetId);
        accessService.requireOwnerScope(userId, username, dataset.getOwnerUserId());
        List<Map<String, Object>> items = datasetService.listItems(datasetId).stream().map(item -> {
            CareerResumeDO resume = resumeMapper.selectById(item.getResumeId());
            return Map.<String, Object>of(
                    "resumeId", item.getResumeId(),
                    "displayOrder", item.getDisplayOrder(),
                    "name", resume == null || resume.getName() == null ? "未知简历" : resume.getName(),
                    "title", resume == null || resume.getTitle() == null ? "" : resume.getTitle());
        }).toList();
        return Results.success(Map.of("dataset", dataset, "items", items));
    }

    @PutMapping("/datasets/{datasetId}/items")
    public Result<Void> updateDatasetItems(@PathVariable Long datasetId,
                                           @Valid @RequestBody UpdateRagDatasetItemsReqDTO request,
                                           @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDatasetDO dataset = datasetService.requireDataset(datasetId);
        accessService.requireOwnerScope(userId, username, dataset.getOwnerUserId());
        datasetService.replaceItems(datasetId, userId, accessService.isAdministrator(username), request.getResumeIds());
        return Results.success();
    }

    /** 返回测试集中已存在的基础标签，供实验页直接点选相关性规则。 */
    @GetMapping("/datasets/{datasetId}/tag-options")
    public Result<List<RagResumeTagDO>> datasetTagOptions(@PathVariable Long datasetId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDatasetDO dataset = datasetService.requireDataset(datasetId);
        accessService.requireOwnerScope(userId, username, dataset.getOwnerUserId());
        List<Long> resumeIds = datasetService.listItems(datasetId).stream().map(item -> item.getResumeId()).toList();
        if (resumeIds.isEmpty()) return Results.success(List.of());
        return Results.success(tagMapper.selectList(Wrappers.lambdaQuery(RagResumeTagDO.class)
                .eq(RagResumeTagDO::getOwnerUserId, dataset.getOwnerUserId())
                .in(RagResumeTagDO::getResumeId, resumeIds)));
    }

    /** 仅列出可加入测试集的已解析简历；管理员可审阅全量数据，普通用户只能看到自己的数据。 */
    @GetMapping("/resumes")
    public Result<List<CareerResumeDO>> parsedResumes(@CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        List<CareerResumeDO> resumes = resumeMapper.selectList(Wrappers.lambdaQuery(CareerResumeDO.class)
                .eq(!accessService.isAdministrator(username), CareerResumeDO::getUserId, userId)
                .orderByDesc(CareerResumeDO::getCreateTime));
        return Results.success(resumeCandidateService.deduplicateCompletedPdfCandidates(resumes));
    }

    @GetMapping("/resumes/{resumeId}/tags")
    public Result<List<RagResumeTagDO>> tags(@PathVariable Long resumeId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        Long owner = resumeOwner(resumeId);
        accessService.requireOwnerScope(userId, username, owner);
        return Results.success(tagMapper.selectList(Wrappers.lambdaQuery(RagResumeTagDO.class)
                .eq(RagResumeTagDO::getOwnerUserId, owner)
                .eq(RagResumeTagDO::getResumeId, resumeId)));
    }

    @PutMapping("/resumes/{resumeId}/tags")
    public Result<Void> replaceTags(@PathVariable Long resumeId, @RequestBody ReplaceResumeTagsReqDTO request, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        Long owner = resumeOwner(resumeId);
        accessService.requireOwnerScope(userId, username, owner);
        datasetService.replaceTags(owner, resumeId, request.getRoles(), request.getDirections(),
                request.getProjects(), request.getSkills(), request.getExperiences());
        return Results.success();
    }

    /** 为 PDF 标注台提供原上传文件。仅返回 owner 范围内最近一次解析成功且保留快照的文件。 */
    @GetMapping("/resumes/{resumeId}/source")
    public ResponseEntity<byte[]> sourcePdf(@PathVariable Long resumeId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        CareerResumeDO resume = requireResume(resumeId);
        Long owner = resume.getUserId();
        accessService.requireOwnerScope(userId, username, owner);
        CareerResumeParseTaskDO task = parseTaskMapper.selectOne(Wrappers.lambdaQuery(CareerResumeParseTaskDO.class)
                .eq(CareerResumeParseTaskDO::getUserId, owner)
                .eq(CareerResumeParseTaskDO::getResumeId, resumeId)
                .eq(CareerResumeParseTaskDO::getStatus, "COMPLETED")
                .orderByDesc(CareerResumeParseTaskDO::getCompleteTime).last("LIMIT 1"));
        if (task == null) {
            throw new ClientException("原始 PDF 不存在或尚未解析完成");
        }
        byte[] source = task.getFileSnapshot();
        if ((source == null || source.length == 0) && resumeObjectStorage.enabled() && task.getStorageKey() != null && !task.getStorageKey().isBlank()) {
            try (InputStream input = resumeObjectStorage.get(task.getStorageKey())) {
                source = input == null ? new byte[0] : input.readAllBytes();
            } catch (Exception ex) {
                throw new ClientException("原始 PDF 从对象存储读取失败");
            }
        }
        String contentType = task.getContentType() == null ? "application/pdf" : task.getContentType();
        String filename = task.getOriginalFilename() == null ? "resume.pdf" : task.getOriginalFilename();
        boolean renderedFallback = false;
        if (source == null || source.length == 0) {
            ResumeRenderArtifact fallback = renderStructuredResumePdf(resume);
            if (fallback != null && fallback.bytes().length > 0) {
                source = fallback.bytes();
                contentType = fallback.contentType();
                filename = fallback.filename();
                renderedFallback = true;
            }
        }
        if (source == null || source.length == 0) {
            throw new ClientException("原始 PDF 不存在或未保留可读取的文件副本");
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        if (renderedFallback) {
            response.header("X-Rag-Lab-Pdf-Source", "rendered-fallback");
        }
        return response.body(source);
    }

    @GetMapping
    public Result<List<RagExperimentDO>> experiments(@CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        return Results.success(accessService.isAdministrator(username) ? experimentService.listAll() : experimentService.list(userId));
    }

    @PostMapping
    public Result<RagExperimentDO> createExperiment(@Valid @RequestBody CreateRagExperimentReqDTO request,
                                                     @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        // 管理员全量管理时，实验归属数据集 owner；普通用户仍只能操作自己的实验空间。
        return Results.success(experimentService.createAndRun(userId, accessService.isAdministrator(username), request));
    }

    @GetMapping("/{experimentId}")
    public Result<Map<String, Object>> experimentDetail(@PathVariable Long experimentId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDO experiment = experimentService.requireExperiment(experimentId);
        accessService.requireOwnerScope(userId, username, experiment.getOwnerUserId());
        // 评测页不能只返回内部 resumeId：人工给 0/1/3 真值时必须能识别具体 PDF。
        // 这里仅补充展示字段，不改变评分实体、实验快照或用户隔离规则。
        Map<String, Object> detail = new java.util.LinkedHashMap<>(experimentService.detail(experimentId, experiment.getOwnerUserId()));
        @SuppressWarnings("unchecked")
        List<com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentJudgementDO> judgements =
                (List<com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentJudgementDO>) detail.get("judgements");
        List<Map<String, Object>> judgementViews = judgements.stream().map(judgement -> {
            CareerResumeDO resume = resumeMapper.selectById(judgement.getResumeId());
            Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("resumeId", judgement.getResumeId());
            value.put("resumeName", resume == null || resume.getName() == null ? "未知简历" : resume.getName());
            value.put("resumeTitle", resume == null ? "" : Objects.toString(resume.getTitle(), ""));
            value.put("automaticScore", judgement.getAutomaticScore());
            value.put("finalScore", judgement.getFinalScore());
            value.put("ruleSource", judgement.getRuleSource());
            value.put("overrideReason", judgement.getOverrideReason());
            return value;
        }).toList();
        detail.put("judgements", judgementViews);
        return Results.success(detail);
    }

    @PutMapping("/{experimentId}/judgements")
    public Result<Void> overrideJudgements(@PathVariable Long experimentId, @Valid @RequestBody UpdateRagJudgementsReqDTO request,
                                           @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDO experiment = experimentService.requireExperiment(experimentId);
        accessService.requireOwnerScope(userId, username, experiment.getOwnerUserId());
        experimentService.overrideJudgements(experimentId, experiment.getOwnerUserId(), request.getItems());
        return Results.success();
    }

    /**
     * 用户在实验室查看简历时主动触发 AI 预评，而不是把外部模型调用藏在页面加载中。
     * 已有标签会原样返回，确保人工补充不会被重复点击意外覆盖。
     */
    @PostMapping("/resumes/{resumeId}/tags/evaluate")
    public Result<RagResumeAutoTagTaskResult> evaluateTags(@PathVariable Long resumeId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        CareerResumeDO resume = requireResume(resumeId);
        Long owner = resume.getUserId();
        accessService.requireOwnerScope(userId, username, owner);
        if (resume.getCvJson() == null || resume.getCvJson().isBlank()) {
            throw new ClientException("简历尚未完成结构化解析，暂时无法进行 AI 预评");
        }
        try {
            CvBO cv = JSON.parseObject(resume.getCvJson(), CvBO.class);
            if (cv == null) throw new ClientException("简历结构化数据为空，暂时无法进行 AI 预评");
            cv.setId(resumeId);
            cv.setUserId(owner);
            return Results.success(ragResumeAutoTagTaskService.submit(owner, resumeId, cv));
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI 简历预评读取结构化数据失败，resumeId={}", resumeId, ex);
            throw new ClientException("简历结构化数据异常，暂时无法进行 AI 预评");
        }
    }

    /** 查询异步预评任务；前端只轮询这个轻量接口，不再长时间占用一次 HTTP 请求。 */
    @GetMapping("/resumes/{resumeId}/tags/evaluate/{taskId}")
    public Result<RagResumeAutoTagTaskResult> tagEvaluationTask(@PathVariable Long resumeId,
                                                                  @PathVariable String taskId,
                                                                  @CurrentUser Long userId,
                                                                  @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        CareerResumeDO resume = requireResume(resumeId);
        accessService.requireOwnerScope(userId, username, resume.getUserId());
        return Results.success(ragResumeAutoTagTaskService.query(resume.getUserId(), resumeId, taskId));
    }

    /**
     * 保存对 Top-K 每个具体证据片段的“有效 / 无效”核验，用于计算 Chunk 命中准确率。
     */
    @PutMapping("/{experimentId}/evidence-annotations")
    public Result<Void> updateEvidenceAnnotations(@PathVariable Long experimentId,
                                                  @Valid @RequestBody UpdateRagEvidenceAnnotationsReqDTO request,
                                                  @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDO experiment = experimentService.requireExperiment(experimentId);
        accessService.requireOwnerScope(userId, username, experiment.getOwnerUserId());
        experimentService.updateEvidenceAnnotations(experimentId, experiment.getOwnerUserId(), request.getItems());
        return Results.success();
    }

    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long leftId, @RequestParam Long rightId,
                                               @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        RagExperimentDO left = experimentService.requireExperiment(leftId);
        RagExperimentDO right = experimentService.requireExperiment(rightId);
        accessService.requireOwnerScope(userId, username, left.getOwnerUserId());
        accessService.requireOwnerScope(userId, username, right.getOwnerUserId());
        if (!Objects.equals(left.getOwnerUserId(), right.getOwnerUserId())) {
            throw new ClientException("只能比较同一用户实验空间内的两次实验");
        }
        return Results.success(experimentService.compare(leftId, rightId, left.getOwnerUserId()));
    }

    private Long resumeOwner(Long resumeId) {
        return requireResume(resumeId).getUserId();
    }

    private CareerResumeDO requireResume(Long resumeId) {
        CareerResumeDO resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getUserId() == null) {
            throw new ClientException("简历不存在或已删除");
        }
        return resume;
    }

    /**
     * 老记录可能早于“解析任务保留原文件快照”的能力，不能因此让已有的结构化简历不可标注。
     * 此处只在原 PDF 与对象存储均不可读时，用已保存的结构化数据生成一份只读预览 PDF。
     */
    private ResumeRenderArtifact renderStructuredResumePdf(CareerResumeDO resume) {
        if (resume == null || resume.getCvJson() == null || resume.getCvJson().isBlank()) {
            return null;
        }
        try {
            CvBO cv = JSON.parseObject(resume.getCvJson(), CvBO.class);
            return cv == null ? null : resumeRenderService.renderPdf(cv);
        } catch (Exception ex) {
            log.warn("RAG 实验室无法从结构化简历生成 PDF 预览，resumeId={}", resume.getId(), ex);
            return null;
        }
    }
}
