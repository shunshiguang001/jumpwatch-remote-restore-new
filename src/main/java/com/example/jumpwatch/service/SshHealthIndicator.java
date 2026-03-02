package com.example.jumpwatch.service;


import com.example.jumpwatch.config.MonitorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SshHealthIndicator implements HealthIndicator {
    @Autowired
    private SshService sshService;

    @Autowired
    private MonitorConfig monitorConfig;

    @Override
    public Health health() {
        if (monitorConfig.getHosts() == null || monitorConfig.getHosts().isEmpty()) {
            return Health.down().withDetail("Error", "配置读取失败，请检查配置文件格式").build();
        }
        try {
            // 取配置中的第一台机器进行连通性测试
            String firstIp = monitorConfig.getHosts().keySet().iterator().next();
            // 执行一个最简单的命令：whoami
            String result = sshService.execute(firstIp, "whoami");

            if (result != null && result.contains("appoper")) {
                return Health.up()
                        .withDetail("SSH Connection", "Success")
                        .withDetail("Target Host", firstIp)
                        .withDetail("User", result.trim())
                        .build();
            }
            return Health.down().withDetail("Error", "Unexpected result: " + result).build();
        } catch (Exception e) {
            // 如果 SSH 失败（比如密钥不对），这里会捕获并显示在健康检查中
            return Health.down(e).withDetail("SSH Status", "Authentication Failed or Connection Timeout").build();
        }
    }
}
