package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.*;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.*;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeTagType;
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

    public RagExperimentDatasetDO create(Long owner, String name, String description, List<Long> resumeIds) {
        RagExperimentDatasetDO dataset = new RagExperimentDatasetDO();
        dataset.setOwnerUserId(owner); dataset.setName(name.trim()); dataset.setDescription(description);
        datasetMapper.insert(dataset);
        int index = 0;
        for (Long resumeId : new LinkedHashSet<>(resumeIds)) {
            if (resumeId == null) continue;
            RagExperimentDatasetItemDO item = new RagExperimentDatasetItemDO();
            item.setDatasetId(dataset.getId()); item.setOwnerUserId(owner); item.setResumeId(resumeId); item.setDisplayOrder(++index);
            itemMapper.insert(item);
        }
        if (index == 0) throw new ClientException("测试集至少需要一份简历");
        return dataset;
    }

    public void replaceTags(Long owner, Long resumeId, List<String> roles, List<String> directions) {
        tagMapper.delete(Wrappers.lambdaQuery(RagResumeTagDO.class).eq(RagResumeTagDO::getOwnerUserId, owner).eq(RagResumeTagDO::getResumeId, resumeId));
        save(owner, resumeId, RagResumeTagType.ROLE, roles); save(owner, resumeId, RagResumeTagType.DIRECTION, directions);
    }
    private void save(Long owner, Long resumeId, RagResumeTagType type, List<String> values) {
        Optional.ofNullable(values).orElse(List.of()).stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).map(s -> s.substring(0, Math.min(100, s.length()))).distinct().forEach(value -> {
            RagResumeTagDO tag = new RagResumeTagDO(); tag.setOwnerUserId(owner); tag.setResumeId(resumeId); tag.setTagType(type); tag.setTagValue(value); tagMapper.insert(tag);
        });
    }
}
