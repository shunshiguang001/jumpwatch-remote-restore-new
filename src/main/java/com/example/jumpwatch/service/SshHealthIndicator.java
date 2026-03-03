package com.example.jumpwatch.service;

import com.example.jumpwatch.config.JumpWatchConfig;
import com.example.jumpwatch.config.MonitorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SshHealthIndicator implements HealthIndicator {

    @Autowired
    private SSHExecService sshExecService;

    @Autowired
    private JumpWatchConfig jumpWatchConfig;

    @Autowired
    private MonitorConfig monitorConfig;

    @Override
    public Health health() {
        // 1) 配置检查
        if (monitorConfig.getHosts() == null || monitorConfig.getHosts().isEmpty()) {
            return Health.down().withDetail("error", "monitor.hosts 未配置").build();
        }
        if (jumpWatchConfig.getHosts() == null || jumpWatchConfig.getHosts().isEmpty()) {
            return Health.down().withDetail("error", "jumpwatch.hosts 未配置").build();
        }

        // 2) 取第一台机器做连通性测试
        String firstKey = monitorConfig.getHosts().keySet().iterator().next();
        String ip = monitorConfig.getHosts().get(firstKey).getIp();
        String alias = resolveHostAliasByIp(ip);

        try {
            String result = sshExecService.exec(alias, "whoami");
            return Health.up()
                    .withDetail("ssh", "ok")
                    .withDetail("host", ip)
                    .withDetail("whoami", result == null ? "" : result.trim())
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("host", ip)
                    .build();
        }
    }

    private String resolveHostAliasByIp(String ip) {
        for (Map.Entry<String, JumpWatchConfig.HostEntry> e : jumpWatchConfig.getHosts().entrySet()) {
            JumpWatchConfig.HostEntry he = e.getValue();
            if (he != null && ip != null && ip.equals(he.getIp())) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("找不到该 ip 对应的 jumpwatch.hosts: " + ip);
    }
}