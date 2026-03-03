package com.example.jumpwatch.service;

import com.example.jumpwatch.config.JumpWatchConfig;
import com.example.jumpwatch.config.JumpWatchConfig.HostEntry;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 项目名称: jumpwatch-remote-restore
 * 类名称: SSHExecService
 *
 * 说明：
 * - 统一使用 sshj 执行远程命令/拉取日志
 * - 提供通用 exec(hostAlias, command) 方法，便于 Controller 统一调用
 *
 * @author blx
 * @date 2026-03-02
 * @version 7.0
 */
@Service
public class SSHExecService {
    private final JumpWatchConfig cfg;

    public SSHExecService(JumpWatchConfig cfg) {
        this.cfg = cfg;
    }

    private HostEntry hostEntry(String alias) {
        Map<String, HostEntry> m = cfg.getHosts();
        if (m == null || !m.containsKey(alias)) throw new IllegalArgumentException("unknown host: " + alias);
        return m.get(alias);
    }

    private String cmdPath(String key, String defaultName) {
        Map<String, String> m = cfg.getCommandPaths();
        String p = (m != null) ? m.get(key) : null;
        return (p != null && !p.isBlank()) ? p : defaultName;
    }

    private SSHClient newClient() {
        SSHClient ssh = new SSHClient();
        // TODO：生产环境建议替换为严格 HostKey 校验（known_hosts / 指纹）
        ssh.addHostKeyVerifier(new PromiscuousVerifier());

        // 连接/读写超时（防止卡死）
        ssh.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        ssh.setTimeout((int) Duration.ofSeconds(Math.max(5, cfg.getCmdTimeoutSec()) + 10).toMillis());
        return ssh;
    }

    /**
     * 通用执行：按 hostAlias（jumpwatch.hosts 的 key）执行一条命令，返回 stdout + stderr（如有）。
     */
    public String exec(String hostAlias, String command) throws Exception {
        HostEntry h = hostEntry(hostAlias);
        return runOnce(h.getIp(), command);
    }

    /**
     * 低层执行：按 IP 执行命令。
     */
    private String runOnce(String ip, String cmd) throws Exception {
        try (SSHClient ssh = newClient()) {
            ssh.connect(ip);
            ssh.authPublickey(cfg.getSshUser());

            try (Session s = ssh.startSession();
                 Session.Command c = s.exec(cmd)) {

                c.join(Math.max(1, cfg.getCmdTimeoutSec()), TimeUnit.SECONDS);

                boolean finished = !c.isOpen();

                String out = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String err = new String(c.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

                if (!finished) {
                    throw new IllegalStateException("SSH command timeout");
                }

                Integer exit = c.getExitStatus();
                if (exit != null && exit != 0) {
                    throw new IllegalStateException("SSH command failed: " + err);
                }

                if (err != null && !err.isBlank()) {
                    throw new IllegalStateException("SSH error: " + err);
                }

                return out;
            }
        }
    }

    public Map<String, String> systemdStatus(String hostAlias, String unit) throws Exception {
        if (!cfg.isSystemdEnabled()) throw new IllegalStateException("systemd disabled by config");
        HostEntry h = hostEntry(hostAlias);
        if (h.getAllowedUnits() == null || !h.getAllowedUnits().contains(unit))
            throw new IllegalArgumentException("unit not allowed");

        String systemctl = cmdPath("systemctl", "systemctl");
        String cmd = systemctl + " show " + unit + " -p ActiveState -p SubState -p MainPID";
        String out = runOnce(h.getIp(), cmd);

        Map<String, String> res = new HashMap<>();
        for (String line : out.split("\\R")) {
            if (line.contains("=")) {
                String[] kv = line.split("=", 2);
                res.put(kv[0], kv[1]);
            }
        }
        return res;
    }

    public List<Map<String, Object>> grepPids(String hostAlias, String pattern) throws Exception {
        HostEntry h = hostEntry(hostAlias);
        String pgrep = cmdPath("pgrep", "pgrep");

        // 注意：pattern 来自调用方，建议上层做字符集校验/白名单；这里先保持原逻辑
        String cmd = pgrep + " -fl " + pattern;

        String out = runOnce(h.getIp(), cmd);
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split("\\s+", 2);
            try {
                long pid = Long.parseLong(parts[0]);
                String cmdline = (parts.length > 1 ? parts[1] : "");
                Map<String, Object> m = new HashMap<>();
                m.put("pid", pid);
                m.put("cmd", cmdline);
                list.add(m);
            } catch (NumberFormatException ignore) {
            }
        }
        return list;
    }

    /**
     * 流式日志：journalctl -fu unit 或 tail -F file
     */
    public Flux<String> streamLogs(String hostAlias, String unit, String fileAliasOrPath) {
        HostEntry h = hostEntry(hostAlias);
        final String ip = h.getIp();
        String cmd;

        String tail = cmdPath("tail", "tail");
        String journalctl = cmdPath("journalctl", "journalctl");

        if (unit != null && !unit.isBlank()) {
            if (!cfg.isJournaldEnabled())
                return Flux.error(new IllegalStateException("journald streaming disabled by config"));
            if (h.getAllowedUnits() == null || !h.getAllowedUnits().contains(unit))
                return Flux.error(new IllegalArgumentException("unit not allowed"));
            cmd = journalctl + " -fu " + unit;
        } else if (fileAliasOrPath != null && !fileAliasOrPath.isBlank()) {
            String path = (h.getAllowedLogs() != null && h.getAllowedLogs().containsKey(fileAliasOrPath))
                    ? h.getAllowedLogs().get(fileAliasOrPath) : fileAliasOrPath;
            if (!path.startsWith("/")) return Flux.error(new IllegalArgumentException("log path must be absolute"));
            cmd = tail + " -F " + path;
        } else {
            return Flux.error(new IllegalArgumentException("unit or file required"));
        }

        return Flux.create(sink -> {
            final SSHClient ssh = newClient();
            final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ssh-log-stream");
                t.setDaemon(true);
                return t;
            });

            // 用于 onCancel/cleanup
            final Holder<Session.Command> cmdHolder = new Holder<>();
            final Holder<Session> sessionHolder = new Holder<>();

            Runnable cleanup = () -> {
                try { if (cmdHolder.value != null) cmdHolder.value.close(); } catch (Exception ignore) {}
                try { if (sessionHolder.value != null) sessionHolder.value.close(); } catch (Exception ignore) {}
                try { ssh.disconnect(); } catch (Exception ignore) {}
                try { ssh.close(); } catch (Exception ignore) {}
                exec.shutdownNow();
            };
            sink.onCancel((Disposable) cleanup::run);
            sink.onDispose((Disposable) cleanup::run);
            try {
                ssh.connect(ip);
                ssh.authPublickey(cfg.getSshUser());

                final Session s = ssh.startSession();
                sessionHolder.value = s;

                final Session.Command c = s.exec(cmd);
                cmdHolder.value = c;

                final InputStream in = c.getInputStream();

                exec.submit(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while (!sink.isCancelled() && (line = br.readLine()) != null) {
                            sink.next(line);
                        }
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        cleanup.run();
                    }
                });

            } catch (Exception e) {
                cleanup.run();
                sink.error(e);
            }
        });
    }

    /**
     * 极简 Holder（避免引入额外依赖）
     */
    private static final class Holder<T> {
        private T value;
    }
}
