package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryResumeStore implements ResumeStore {

    private final AtomicLong idGenerator = new AtomicLong(100000);
    private final ConcurrentMap<Long, CvBO> resumes = new ConcurrentHashMap<>();

    @Override
    public CvBO save(CvBO cv) {
        Long id = cv.getId() == null ? idGenerator.incrementAndGet() : cv.getId();
        CvBO saved = cv.toBuilder().id(id).build();
        resumes.put(id, saved);
        return saved;
    }

    @Override
    public Optional<CvBO> findById(Long resumeId) {
        return Optional.ofNullable(resumes.get(resumeId));
    }

    @Override
    public Optional<CvBO> findByIdAndUserId(Long resumeId, Long userId) {
        if (resumeId == null || userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resumes.get(resumeId))
                .filter(cv -> userId.equals(cv.getUserId()));
    }

    @Override
    public List<CvBO> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return resumes.values().stream()
                .filter(cv -> userId.equals(cv.getUserId()))
                .toList();
    }
}
