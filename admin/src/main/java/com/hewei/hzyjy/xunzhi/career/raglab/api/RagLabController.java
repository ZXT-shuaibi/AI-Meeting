package com.hewei.hzyjy.xunzhi.career.raglab.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagDatasetReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.ReplaceResumeTagsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentDatasetService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabAccessService;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDatasetDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** RAG 实验室的测试集、标签及访问状态接口。 */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/rag-experiments")
public class RagLabController {
    private final RagLabAccessService accessService;
    private final RagExperimentDatasetService datasetService;
    private final RagExperimentDatasetMapper datasetMapper;
    private final RagResumeTagMapper tagMapper;

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

    @GetMapping("/resumes/{resumeId}/tags")
    public Result<List<RagResumeTagDO>> tags(@PathVariable Long resumeId, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        return Results.success(tagMapper.selectList(Wrappers.lambdaQuery(RagResumeTagDO.class)
                .eq(!accessService.isAdministrator(username), RagResumeTagDO::getOwnerUserId, userId)
                .eq(RagResumeTagDO::getResumeId, resumeId)));
    }

    @PutMapping("/resumes/{resumeId}/tags")
    public Result<Void> replaceTags(@PathVariable Long resumeId, @RequestBody ReplaceResumeTagsReqDTO request, @CurrentUser Long userId, @CurrentUser String username) {
        accessService.requireLabAccess(userId, username);
        datasetService.replaceTags(userId, resumeId, request.getRoles(), request.getDirections());
        return Results.success();
    }
}
