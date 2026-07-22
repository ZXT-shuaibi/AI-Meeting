package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.*;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.*;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeTagType;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** 测试集及可复用岗位/方向自由标签的写入服务。 */
@Service @RequiredArgsConstructor
public class RagExperimentDatasetService {
    private final RagExperimentDatasetMapper datasetMapper;
    private final RagExperimentDatasetItemMapper itemMapper;
    private final RagResumeTagMapper tagMapper;
    private final CareerResumeMapper resumeMapper;
    private final CareerResumeParseTaskMapper parseTaskMapper;

    public RagExperimentDatasetDO create(Long owner, String name, String description, List<Long> resumeIds) {
        LinkedHashSet<Long> uniqueResumeIds = new LinkedHashSet<>();
        Optional.ofNullable(resumeIds).orElse(List.of()).stream().filter(Objects::nonNull).forEach(uniqueResumeIds::add);
        if (uniqueResumeIds.isEmpty()) {
            throw new ClientException("测试集至少需要一份已解析简历");
        }
        // 先完整校验，避免插入一个不含合法样本的半成品测试集。
        uniqueResumeIds.forEach(resumeId -> requireOwnedParsedResume(owner, resumeId));
        RagExperimentDatasetDO dataset = new RagExperimentDatasetDO();
        dataset.setOwnerUserId(owner); dataset.setName(name.trim()); dataset.setDescription(description);
        datasetMapper.insert(dataset);
        int index = 0;
        for (Long resumeId : uniqueResumeIds) {
            RagExperimentDatasetItemDO item = new RagExperimentDatasetItemDO();
            item.setDatasetId(dataset.getId()); item.setOwnerUserId(owner); item.setResumeId(resumeId); item.setDisplayOrder(++index);
            itemMapper.insert(item);
        }
        return dataset;
    }

    public void replaceTags(Long owner, Long resumeId, List<String> roles, List<String> directions) {
        requireOwnedParsedResume(owner, resumeId);
        tagMapper.delete(Wrappers.lambdaQuery(RagResumeTagDO.class).eq(RagResumeTagDO::getOwnerUserId, owner).eq(RagResumeTagDO::getResumeId, resumeId));
        save(owner, resumeId, RagResumeTagType.ROLE, roles); save(owner, resumeId, RagResumeTagType.DIRECTION, directions);
    }

    /** 返回测试集归属，供控制器统一落实管理员全量/普通用户自有范围的访问约束。 */
    public RagExperimentDatasetDO requireDataset(Long datasetId) {
        RagExperimentDatasetDO dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new ClientException("RAG 测试集不存在或已删除");
        }
        return dataset;
    }

    public List<RagExperimentDatasetItemDO> listItems(Long datasetId) {
        return itemMapper.selectList(Wrappers.lambdaQuery(RagExperimentDatasetItemDO.class)
                .eq(RagExperimentDatasetItemDO::getDatasetId, datasetId)
                .orderByAsc(RagExperimentDatasetItemDO::getDisplayOrder));
    }

    public CareerResumeDO requireOwnedParsedResume(Long owner, Long resumeId) {
        CareerResumeDO resume = resumeMapper.selectById(resumeId);
        if (resume == null || !Objects.equals(owner, resume.getUserId())) {
            throw new ClientException("只能选择本人已解析成功的简历");
        }
        boolean parsed = parseTaskMapper.exists(Wrappers.lambdaQuery(CareerResumeParseTaskDO.class)
                .eq(CareerResumeParseTaskDO::getUserId, owner)
                .eq(CareerResumeParseTaskDO::getResumeId, resumeId)
                .eq(CareerResumeParseTaskDO::getStatus, "COMPLETED"));
        if (!parsed) {
            throw new ClientException("简历尚未解析完成，暂不能加入 RAG 测试集");
        }
        return resume;
    }
    private void save(Long owner, Long resumeId, RagResumeTagType type, List<String> values) {
        Optional.ofNullable(values).orElse(List.of()).stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).map(s -> s.substring(0, Math.min(100, s.length()))).distinct().forEach(value -> {
            RagResumeTagDO tag = new RagResumeTagDO(); tag.setOwnerUserId(owner); tag.setResumeId(resumeId); tag.setTagType(type); tag.setTagValue(value); tagMapper.insert(tag);
        });
    }
}
