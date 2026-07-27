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

    /**
     * 创建归属于候选简历实际所有者的测试集。管理员能查看全量简历，但测试集不能跨用户混合，
     * 否则标签、人工真值和历史实验会突破现有 ownerUserId 的数据隔离边界。
     */
    public RagExperimentDatasetDO create(Long requesterUserId, boolean administrator,
                                         String name, String description, List<Long> resumeIds) {
        LinkedHashSet<Long> uniqueResumeIds = new LinkedHashSet<>();
        Optional.ofNullable(resumeIds).orElse(List.of()).stream().filter(Objects::nonNull).forEach(uniqueResumeIds::add);
        if (uniqueResumeIds.isEmpty()) {
            throw new ClientException("测试集至少需要一份已解析简历");
        }
        // 先完整校验，避免插入一个不含合法样本的半成品测试集。
        Long owner = resolveDatasetOwner(requesterUserId, administrator, uniqueResumeIds);
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

    /**
     * 管理员创建测试集时，按所选简历的真实 owner 建立实验空间；普通用户仍只能选自己的简历。
     * 先比较全部 owner，再检查解析状态，避免跨用户选择时产生不必要的数据库查询。
     */
    private Long resolveDatasetOwner(Long requesterUserId, boolean administrator, Collection<Long> resumeIds) {
        List<CareerResumeDO> resumes = new ArrayList<>();
        Long owner = null;
        for (Long resumeId : resumeIds) {
            CareerResumeDO resume = resumeMapper.selectById(resumeId);
            if (resume == null || resume.getUserId() == null) {
                throw new ClientException("\u7b80\u5386\u4e0d\u5b58\u5728\u6216\u5df2\u5220\u9664");
            }
            if (!administrator && !Objects.equals(requesterUserId, resume.getUserId())) {
                throw new ClientException("\u5f53\u524d\u8d26\u53f7\u65e0\u6743\u4f7f\u7528\u8be5\u7b80\u5386\u521b\u5efa RAG \u6d4b\u8bd5\u96c6");
            }
            if (owner != null && !Objects.equals(owner, resume.getUserId())) {
                throw new ClientException("\u540c\u4e00\u4e2a RAG \u6d4b\u8bd5\u96c6\u53ea\u80fd\u9009\u62e9\u540c\u4e00\u7528\u6237\u7684\u7b80\u5386");
            }
            owner = resume.getUserId();
            resumes.add(resume);
        }
        for (CareerResumeDO resume : resumes) {
            boolean parsed = parseTaskMapper.exists(Wrappers.lambdaQuery(CareerResumeParseTaskDO.class)
                    .eq(CareerResumeParseTaskDO::getUserId, resume.getUserId())
                    .eq(CareerResumeParseTaskDO::getResumeId, resume.getId())
                    .eq(CareerResumeParseTaskDO::getStatus, "COMPLETED"));
            if (!parsed) {
                throw new ClientException("\u7b80\u5386\u5c1a\u672a\u89e3\u6790\u5b8c\u6210\uff0c\u6682\u4e0d\u80fd\u52a0\u5165 RAG \u6d4b\u8bd5\u96c6");
            }
        }
        return owner;
    }

    public void replaceTags(Long owner, Long resumeId, List<String> roles, List<String> directions,
                            List<String> projects, List<String> skills, List<String> experiences) {
        requireOwnedParsedResume(owner, resumeId);
        tagMapper.delete(Wrappers.lambdaQuery(RagResumeTagDO.class).eq(RagResumeTagDO::getOwnerUserId, owner).eq(RagResumeTagDO::getResumeId, resumeId));
        save(owner, resumeId, RagResumeTagType.ROLE, roles); save(owner, resumeId, RagResumeTagType.DIRECTION, directions);
        save(owner, resumeId, RagResumeTagType.PROJECT, projects); save(owner, resumeId, RagResumeTagType.SKILL, skills);
        save(owner, resumeId, RagResumeTagType.EXPERIENCE, experiences);
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
