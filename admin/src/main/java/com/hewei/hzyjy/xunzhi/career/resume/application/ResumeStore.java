package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface ResumeStore {

    CvBO save(CvBO cv);

    Optional<CvBO> findById(Long resumeId);

    default Optional<CvBO> findByIdAndUserId(Long resumeId, Long userId) {
        if (resumeId == null || userId == null) {
            return Optional.empty();
        }
        return findById(resumeId).filter(cv -> userId.equals(cv.getUserId()));
    }

    default List<CvBO> findByUserId(Long userId) {
        return List.of();
    }

    default Set<String> findResumeIdStringsByUserId(Long userId) {
        return findByUserId(userId).stream()
                .map(CvBO::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }
}
