package com.example.jumpwatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "monitor")
public class MonitorConfig {
    private String user;
    private Map<String, HostDetail> hosts;

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public Map<String, HostDetail> getHosts() { return hosts; }
    public void setHosts(Map<String, HostDetail> hosts) { this.hosts = hosts; }

    public static class HostDetail {
        private String ip;
        private List<UnitDetail> units;
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public List<UnitDetail> getUnits() { return units; }
        public void setUnits(List<UnitDetail> units) { this.units = units; }
    }

    public static class UnitDetail {
        private String name, logPath, jarPath, jarName;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLogPath() { return logPath; }
        public void setLogPath(String logPath) { this.logPath = logPath; }
        public String getJarPath() { return jarPath; }
        public void setJarPath(String jarPath) { this.jarPath = jarPath; }
        public String getJarName() { return jarName; }
        public void setJarName(String jarName) { this.jarName = jarName; }
    }
}