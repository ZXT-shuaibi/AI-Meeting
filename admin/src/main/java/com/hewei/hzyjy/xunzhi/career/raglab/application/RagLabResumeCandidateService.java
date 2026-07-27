package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 实验室候选简历的展示准备服务。
 *
 * <p>上传记录和历史简历保持原样。此服务仅在“可选 PDF”列表中按原始 PDF 内容的 MD5 折叠同一用户
 * 重复上传的文件，保留输入列表中排在前面的最新简历，既减少人工选择噪声，也不破坏简历优化历史。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLabResumeCandidateService {

    private final CareerResumeParseTaskMapper parseTaskMapper;
    private final ResumeObjectStorage resumeObjectStorage;

    public List<CareerResumeDO> deduplicateCompletedPdfCandidates(List<CareerResumeDO> candidates) {
        List<CareerResumeDO> result = new ArrayList<>();
        Set<String> fingerprints = new LinkedHashSet<>();
        for (CareerResumeDO resume : candidates) {
            if (resume == null || resume.getId() == null || resume.getUserId() == null) {
                continue;
            }
            CareerResumeParseTaskDO task = latestCompletedTask(resume);
            if (task == null) {
                continue;
            }
            String fingerprint = fingerprint(task);
            // 无法回读原文件时不进行猜测性去重，避免误隐藏用户可以选择的简历。
            String ownerScopedFingerprint = fingerprint == null ? null : resume.getUserId() + ":" + fingerprint;
            if (ownerScopedFingerprint == null || fingerprints.add(ownerScopedFingerprint)) {
                result.add(resume);
            }
        }
        return result;
    }

    private CareerResumeParseTaskDO latestCompletedTask(CareerResumeDO resume) {
        return parseTaskMapper.selectOne(Wrappers.lambdaQuery(CareerResumeParseTaskDO.class)
                .eq(CareerResumeParseTaskDO::getUserId, resume.getUserId())
                .eq(CareerResumeParseTaskDO::getResumeId, resume.getId())
                .eq(CareerResumeParseTaskDO::getStatus, "COMPLETED")
                .orderByDesc(CareerResumeParseTaskDO::getCompleteTime)
                .last("LIMIT 1"));
    }

    private String fingerprint(CareerResumeParseTaskDO task) {
        byte[] source = task.getFileSnapshot();
        if ((source == null || source.length == 0)
                && resumeObjectStorage.enabled()
                && task.getStorageKey() != null
                && !task.getStorageKey().isBlank()) {
            try (InputStream input = resumeObjectStorage.get(task.getStorageKey())) {
                source = input == null ? null : input.readAllBytes();
            } catch (Exception ex) {
                log.warn("RAG 实验室读取 PDF 计算 MD5 失败，将保留该候选项，resumeId={}", task.getResumeId(), ex);
            }
        }
        if (source == null || source.length == 0) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(source));
        } catch (Exception ex) {
            log.warn("RAG 实验室计算 PDF MD5 失败，将保留该候选项，resumeId={}", task.getResumeId(), ex);
            return null;
        }
    }
}
