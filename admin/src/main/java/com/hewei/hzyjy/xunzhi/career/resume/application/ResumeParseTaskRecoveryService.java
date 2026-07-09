package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.config.CareerAsyncTaskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseTaskRecoveryService {

    private final ResumeApplicationService resumeApplicationService;
    private final CareerAsyncTaskProperties properties;

    @Scheduled(fixedDelayString = "${xunzhi-agent.career.async-task.recovery.fixed-delay-millis:30000}")
    public void recoverStaleParseTasks() {
        CareerAsyncTaskProperties.Recovery recovery = properties.getRecovery();
        if (recovery == null || !recovery.isEnabled()) {
            return;
        }
        try {
            int recovered = resumeApplicationService.recoverStaleParseTasks(
                    Duration.ofSeconds(Math.max(1, recovery.getStaleAfterSeconds())),
                    recovery.getBatchSize()
            );
            if (recovered > 0) {
                log.info("Recovered stale resume parse tasks. count={}", recovered);
            }
        } catch (Exception ex) {
            log.warn("Stale resume parse task recovery failed.", ex);
        }
    }
}
