package com.example.jumpwatch.controller;

import com.example.jumpwatch.config.MonitorConfig;
import com.example.jumpwatch.service.SshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MonitorController {

    @Autowired
    private SshService sshService;
    @Autowired
    private MonitorConfig monitorConfig;

    @GetMapping("/config")
    public Map<String, MonitorConfig.HostDetail> getConfig() {
        return monitorConfig.getHosts();
    }

    @GetMapping("/status")
    public String getStatus(@RequestParam String ip) throws Exception {
        MonitorConfig.HostDetail hostDetail = monitorConfig.getHosts().get(ip);
        if (hostDetail == null) return "错误：主机配置未加载";

        StringBuilder sb = new StringBuilder();
        sb.append("【系统运行状态】\n");

        // 磁盘统计：优化对齐格式
        String baseCmd = "top -bn1 | grep 'Cpu(s)' | awk '{print \"CPU使用率: \" 100-$8 \"%\"}'; " +
                "free -h | grep Mem | awk '{print \"内存占用: 已用\"$3 \"/总量\"$2}'; " +
                "df -h /home | tail -1 | awk '{printf \"/home分区: 总计%-6s, 可用%-6s (使用率%s)\\n\", $2, $4, $5}'; " +
                "df -h /app  | tail -1 | awk '{printf \"/app 分区: 总计%-6s, 可用%-6s (使用率%s)\\n\", $2, $4, $5}';";

        sb.append(sshService.execute(ip, baseCmd));

        if (hostDetail.getUnits() != null) {
            sb.append("\n【服务部署及存活详情】\n");
            // 增加运行状态列并保持对齐
            sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n", "服务模块名称", "运行状态", "最后更新时间", "包大小"));
            sb.append("--------------------------------------------------------------------------------\n");

            for (MonitorConfig.UnitDetail unit : hostDetail.getUnits()) {
                if (unit.getJarPath() != null && unit.getJarName() != null) {
                    // 存活检查与文件信息同步获取
                    String cmd = "if ps -ef | grep \"" + unit.getJarName() + "\" | grep -v grep > /dev/null; then echo \"OK\"; else echo \"DOWN\"; fi; " +
                            "FILE=$(ls -t " + unit.getJarPath() + unit.getJarName() + "*.jar 2>/dev/null | head -n 1); " +
                            "if [ -n \"$FILE\" ]; then stat --format='%y|%s' \"$FILE\" | awk -F'|' '{split($1, a, \".\"); printf \"%s|%.2f MB\", a[1], $2/1024/1024}'; fi";

                    String result = sshService.execute(ip, cmd).trim();
                    String[] lines = result.split("\n");
                    String status = lines.length > 0 ? lines[0].trim() : "DOWN";
                    String fileRaw = lines.length > 1 ? lines[1].trim() : "";

                    if (fileRaw.isEmpty()) {
                        sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n", unit.getName(), status, "未找到JAR包", "-"));
                    } else {
                        String[] parts = fileRaw.split("\\|");
                        sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n", unit.getName(), status, parts[0], parts[1]));
                    }
                }
            }
        }
        return sb.toString();
    }

    @GetMapping("/logs")
    public String getLogs(@RequestParam String ip, @RequestParam String unit, @RequestParam(required = false) String keyword) throws Exception {
        MonitorConfig.HostDetail hostDetail = monitorConfig.getHosts().get(ip);
        String logPath = hostDetail.getUnits().stream()
                .filter(u -> u.getName().equals(unit)).map(MonitorConfig.UnitDetail::getLogPath)
                .findFirst().orElse(null);
        if (logPath == null) return "日志路径未配置";
        // 默认 tail 300 行，关键字搜索则使用 grep 上下文(30行)
        String cmd = (keyword == null || keyword.isEmpty()) ? "tail -n 300 " + logPath : "grep -i -C 30 --color=never '" + keyword + "' " + logPath + " | tail -n 500";
        return sshService.execute(ip, cmd);
    }
}