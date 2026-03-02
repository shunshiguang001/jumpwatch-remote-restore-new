package com.example.jumpwatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
/**
 * 项目名称: jumpwatch-remote-restore
 * 类名称: MonitorApplication
 *
 * @author blx
 * @date 2026-03-02
 * @version 7.0
 * @description 服务监控启动类
 */

@Configuration
@ConfigurationProperties(prefix = "jumpwatch")
public class JumpWatchConfig {
    private String sshUser = "appoper";
    private int cmdTimeoutSec = 15;
    private boolean systemdEnabled = true;
    private boolean journaldEnabled = false;
    private Map<String,String> commandPaths;
    private Map<String, HostEntry> hosts;

    public static class HostEntry {
        private String ip;
        private List<String> allowedUnits;
        private Map<String,String> allowedLogs;
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public List<String> getAllowedUnits() { return allowedUnits; }
        public void setAllowedUnits(List<String> allowedUnits) { this.allowedUnits = allowedUnits; }
        public Map<String, String> getAllowedLogs() { return allowedLogs; }
        public void setAllowedLogs(Map<String, String> allowedLogs) { this.allowedLogs = allowedLogs; }
    }

    public String getSshUser() { return sshUser; }
    public void setSshUser(String sshUser) { this.sshUser = sshUser; }
    public int getCmdTimeoutSec() { return cmdTimeoutSec; }
    public void setCmdTimeoutSec(int cmdTimeoutSec) { this.cmdTimeoutSec = cmdTimeoutSec; }
    public boolean isSystemdEnabled() { return systemdEnabled; }
    public void setSystemdEnabled(boolean systemdEnabled) { this.systemdEnabled = systemdEnabled; }
    public boolean isJournaldEnabled() { return journaldEnabled; }
    public void setJournaldEnabled(boolean journaldEnabled) { this.journaldEnabled = journaldEnabled; }
    public Map<String, String> getCommandPaths() { return commandPaths; }
    public void setCommandPaths(Map<String, String> commandPaths) { this.commandPaths = commandPaths; }
    public Map<String, HostEntry> getHosts() { return hosts; }
    public void setHosts(Map<String, HostEntry> hosts) { this.hosts = hosts; }
}
