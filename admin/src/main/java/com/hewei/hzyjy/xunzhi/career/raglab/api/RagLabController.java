package com.hewei.hzyjy.xunzhi.career.raglab.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagDatasetReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagExperimentReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.ReplaceResumeTagsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagJudgementsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentDatasetService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabAccessService;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDatasetDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RestController @RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/rag-experiments")
public class RagLabController {
    private final RagLabAccessService accessService;
    private final RagExperimentDatasetService datasetService;
    private final RagExperimentDatasetMapper datasetMapper;
    private final RagResumeTagMapper tagMapper;
    private final RagExperimentService experimentService;
    private final CareerResumeMapper resumeMapper;
    private final CareerResumeParseTaskMapper parseTaskMapper;
    private final ResumeObjectStorage resumeObjectStorage;

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
        return Results.success(datasetService.create(userId, request.getName(), request.getDescription(), request.getResumeIds()));
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

    /** 仅列出可加入测试集的已解析简历；管理员可审阅全量数据，普通用户只能看到自己的数据。 */
    @GetMapping("/resumes")
    public Result<List<CareerResumeDO>> parsedResumes(@CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        return Results.success(resumeMapper.selectList(Wrappers.lambdaQuery(CareerResumeDO.class)
                .eq(!accessService.isAdministrator(username), CareerResumeDO::getUserId, userId)
                .orderByDesc(CareerResumeDO::getCreateTime)));
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
        datasetService.replaceTags(owner, resumeId, request.getRoles(), request.getDirections());
        return Results.success();
    }

    /** 为 PDF 标注台提供原上传文件。仅返回 owner 范围内最近一次解析成功且保留快照的文件。 */
    @GetMapping("/resumes/{resumeId}/source")
    public ResponseEntity<byte[]> sourcePdf(@PathVariable Long resumeId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        Long owner = resumeOwner(resumeId);
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
        if (source == null || source.length == 0) {
            throw new ClientException("原始 PDF 不存在或未保留可读取的文件副本");
        }
        String filename = task.getOriginalFilename() == null ? "resume.pdf" : task.getOriginalFilename();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        task.getContentType() == null ? "application/pdf" : task.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(source);
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
        return Results.success(experimentService.detail(experimentId, experiment.getOwnerUserId()));
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
        CareerResumeDO resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getUserId() == null) {
            throw new ClientException("简历不存在或已删除");
        }
        return resume.getUserId();
    }
}
