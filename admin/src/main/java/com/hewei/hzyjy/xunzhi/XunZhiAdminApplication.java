package com.hewei.hzyjy.xunzhi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.hewei.hzyjy.xunzhi.**.dao.mapper")
@EnableScheduling
@EnableAsync
public class XunZhiAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(XunZhiAdminApplication.class, args);
    }

    /** Visible readiness marker for local IDE startup and log-based smoke checks. */
    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady(ApplicationReadyEvent event) {
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port");
        if (port == null || port.isBlank()) {
            port = event.getApplicationContext().getEnvironment().getProperty("server.port", "8002");
        }
        org.slf4j.LoggerFactory.getLogger(XunZhiAdminApplication.class)
                .info("=== 后端启动成功 XUNZHI_ADMIN_STARTED === 服务已就绪：http://localhost:{}/", port);
    }
}
