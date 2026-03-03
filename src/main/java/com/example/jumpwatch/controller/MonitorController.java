package com.example.jumpwatch.controller;

import com.example.jumpwatch.config.JumpWatchConfig;
import com.example.jumpwatch.config.MonitorConfig;
import com.example.jumpwatch.service.SSHExecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 项目名称: jumpwatch-remote-restore
 * 类名称: MonitorController
 *
 * 说明：
 * - 已移除 JSch 的 SshService 依赖，统一改为 sshj 的 SSHExecService
 * - 由于前端/接口仍用 ip 作为参数，这里通过 jumpwatch.hosts 反查 hostAlias，再调用 SSHExecService.exec(alias, cmd)
 *
 * @author blx
 * @date 2026-03-02
 * @version 7.2
 * @description 监控接口
 */
@RestController
@RequestMapping("/api")
public class MonitorController {

    @Autowired
    private SSHExecService sshExecService;

    @Autowired
    private MonitorConfig monitorConfig;

    @Autowired
    private JumpWatchConfig jumpWatchConfig;
    @GetMapping("/config")
    public Map getConfig() {
        return monitorConfig.getHosts();
    }

    @GetMapping("/status")
    public String getStatus(@RequestParam String ip) throws Exception {
        MonitorConfig.HostDetail hostDetail = monitorConfig.getHosts().get(ip);
        if (hostDetail == null) return "错误：主机配置未加载";

        String hostAlias = resolveHostAliasByIp(ip);

        StringBuilder sb = new StringBuilder();
        sb.append("〖系统运行状态〗\n");

        // 磁盘统计：优化对齐格式
        String baseCmd = "top -bn1 | grep 'Cpu(s)' | awk '{print \"CPU使用率: \" 100-$8 \"%\"}'; "
                + "free -h | grep Mem | awk '{print \"内存占用: 已用\"$3 \"/总量\"$2}'; "
                + "df -h /home | tail -1 | awk '{printf \"/home分区: 总计%-6s, 可用%-6s (使用率%s)\\n\", $2, $4, $5}'; "
                + "df -h /app | tail -1 | awk '{printf \"/app 分区: 总计%-6s, 可用%-6s (使用率%s)\\n\", $2, $4, $5}';";

        sb.append(sshExecService.exec(hostAlias, baseCmd));

        if (hostDetail.getUnits() != null) {
            sb.append("\n〖服务部署及存活详情〗\n");
            sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n", "服务模块名称", "运行状态", "最后更新时间", "包大小"));
            sb.append("--------------------------------------------------------------------------------\n");

            for (MonitorConfig.UnitDetail unit : hostDetail.getUnits()) {
                if (unit == null) continue;

                if (unit.getJarPath() != null && unit.getJarName() != null) {
                    // ⚠️ 这里依旧是拼接命令（jarName/jarPath 来自配置文件，不来自请求参数）
                    String cmd = "if ps -ef | grep \"" + unit.getJarName() + "\" | grep -v grep > /dev/null; then echo \"OK\"; else echo \"DOWN\"; fi; "
                            + "FILE=$(ls -t " + unit.getJarPath() + unit.getJarName() + "*.jar 2>/dev/null | head -n 1); "
                            + "if [ -n \"$FILE\" ]; then stat --format='%y|%s' \"$FILE\" | awk -F'|' '{split($1, a, \".\"); printf \"%s|%.2f MB\", a[1], $2/1024/1024}'; fi";

                    String result = sshExecService.exec(hostAlias, cmd).trim();
                    String[] lines = result.split("\n");

                    String status = lines.length > 0 ? lines[0].trim() : "DOWN";
                    String fileRaw = lines.length > 1 ? lines[1].trim() : "";

                    if (fileRaw.isEmpty()) {
                        sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n",
                                unit.getName(), status, "未找到JAR包", "-"));
                    } else {
                        String[] parts = fileRaw.split("\\|");
                        sb.append(String.format("%-25s | %-8s | %-19s | %-10s\n",
                                unit.getName(), status, parts[0], parts[1]));
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 安全版日志查询：
     * - 远端只做 tail 固定行数（不拼 keyword，不做 grep）
     * - keyword 过滤、上下文 before/after 在 Java 内完成（杜绝命令注入）
     *
     * 示例：
     * /api/logs?ip=1.2.3.4&unit=xxx
     * /api/logs?ip=1.2.3.4&unit=xxx&keyword=ERROR&lines=800&before=10&after=10
     */
    @GetMapping("/logs")
    public String getLogs(
            @RequestParam String ip,
            @RequestParam String unit,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "300") int lines,
            @RequestParam(defaultValue = "30") int before,
            @RequestParam(defaultValue = "30") int after
    ) throws Exception {

        MonitorConfig.HostDetail hostDetail = monitorConfig.getHosts().get(ip);
        if (hostDetail == null || hostDetail.getUnits() == null) {
            return "错误：主机或服务配置未加载";
        }

        String logPath = hostDetail.getUnits().stream()
                .filter(u -> u != null && unit.equals(u.getName()))
                .map(MonitorConfig.UnitDetail::getLogPath)
                .findFirst()
                .orElse(null);

        if (logPath == null || logPath.isBlank()) {
            return "日志路径未配置";
        }

        // 参数限幅：防止一次读太多、或者上下文过大
        int safeLines = clamp(lines, 1, 2000);
        int safeBefore = clamp(before, 0, 200);
        int safeAfter = clamp(after, 0, 200);

        String kw = keyword == null ? "" : keyword.trim();
        if (kw.length() > 200) {
            return "keyword 太长（最多 200 字符）";
        }

        String hostAlias = resolveHostAliasByIp(ip);

        // 远端只执行固定 tail，不拼 keyword
        String cmd = "tail -n " + safeLines + " " + shQuote(logPath);
        String raw = sshExecService.exec(hostAlias, cmd);

        List<String> allLines = Arrays.asList(raw.split("\\R", -1));
        List<String> outLines = grepWithContext(allLines, kw, safeBefore, safeAfter);

        return String.join("\n", outLines);
    }

    /**
     * 通过 jumpwatch.hosts 反查 hostAlias（因为接口仍然用 ip 传参）
     * 若找不到，直接抛错（提示你补齐 jumpwatch.hosts 配置）
     */
    private String resolveHostAliasByIp(String ip) {
        Map<String, JumpWatchConfig.HostEntry> hosts = jumpWatchConfig.getHosts();
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalStateException("jumpwatch.hosts 未配置或未加载");
        }
        for (Map.Entry<String, JumpWatchConfig.HostEntry> e : hosts.entrySet()) {
            if (e.getValue() != null && ip.equals(e.getValue().getIp())) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("找不到该 ip 对应的 jumpwatch.hosts 条目: " + ip);
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(Math.max(v, min), max);
    }

    /**
     * shell 单引号安全引用：' -> '"'"'
     * 防止 logPath 里出现空格/特殊字符导致命令解析异常
     */
    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Java 侧关键词匹配（contains）+ 上下文截取
     * 说明：这里不走正则，避免正则注入/性能问题；需要正则的话再升级。
     */
    private static List<String> grepWithContext(List<String> lines, String keyword, int before, int after) {
        if (keyword == null || keyword.isBlank()) return lines;

        List<String> out = new ArrayList<>();
        int n = lines.size();

        for (int i = 0; i < n; i++) {
            if (!lines.get(i).contains(keyword)) continue;

            int start = Math.max(0, i - before);
            int end = Math.min(n - 1, i + after);

            for (int j = start; j <= end; j++) {
                out.add(lines.get(j));
            }
            out.add("----"); // 分隔符（你不喜欢可以删）
        }
        return out;
    }
}