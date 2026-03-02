package com.example.jumpwatch.service;

import com.example.jumpwatch.config.JumpWatchConfig;
import com.example.jumpwatch.config.JumpWatchConfig.HostEntry;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;

@Service
public class SSHExecService {
    private final JumpWatchConfig cfg;
    public SSHExecService(JumpWatchConfig cfg){ this.cfg = cfg; }

    private HostEntry hostEntry(String alias){
        Map<String,HostEntry> m = cfg.getHosts();
        if(m==null || !m.containsKey(alias)) throw new IllegalArgumentException("unknown host: "+alias);
        return m.get(alias);
    }

    private String cmdPath(String key, String defaultName){
        Map<String,String> m = cfg.getCommandPaths();
        String p = (m!=null)? m.get(key): null;
        return (p!=null && !p.isBlank()) ? p : defaultName;
    }

    private String runOnce(String ip, String cmd) throws Exception {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        try {
            ssh.connect(ip);
            ssh.authPublickey(cfg.getSshUser());
            try (Session s = ssh.startSession()) {
                Session.Command c = s.exec(cmd);
                c.join(Duration.ofSeconds(cfg.getCmdTimeoutSec()).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                String out = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String err = new String(c.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                if (c.getExitStatus()!=null && c.getExitStatus()!=0) {
                    throw new IllegalStateException("remote exit "+c.getExitStatus()+": "+err);
                }
                return out;
            }
        } finally { try { ssh.disconnect(); } catch(Exception ignore){} }
    }

    public Map<String,String> systemdStatus(String hostAlias, String unit) throws Exception {
        if(!cfg.isSystemdEnabled()) throw new IllegalStateException("systemd disabled by config");
        HostEntry h = hostEntry(hostAlias);
        if(h.getAllowedUnits()==null || !h.getAllowedUnits().contains(unit))
            throw new IllegalArgumentException("unit not allowed");

        String systemctl = cmdPath("systemctl","systemctl");
        String cmd = systemctl + " show " + unit + " -p ActiveState -p SubState -p MainPID";
        String out = runOnce(h.getIp(), cmd);
        Map<String,String> res = new HashMap<>();
/*        for(String line: out.split("\R")){
            if(line.contains("=")){
                String[] kv = line.split("=",2);
                res.put(kv[0], kv[1]);
            }
        }*/

        // 使用 \R（匹配任意换行），注意需要双反斜杠
        for (String line : out.split("\\R")) {
            if (line.contains("=")) {
                String[] kv = line.split("=", 2);
                res.put(kv[0], kv[1]);
            }
        }

        return res;
    }

    public List<Map<String,Object>> grepPids(String hostAlias, String pattern) throws Exception {
        HostEntry h = hostEntry(hostAlias);
        String pgrep = cmdPath("pgrep","pgrep");
        String cmd = pgrep + " -fl " + pattern;
        String out = runOnce(h.getIp(), cmd);
        List<Map<String,Object>> list = new ArrayList<>();
        for(String line: out.split("\\R")){
            if(line.isBlank()) continue;
            String[] parts = line.trim().split("\\s+",2);
            try {
                long pid = Long.parseLong(parts[0]);
                String cmdline = (parts.length>1? parts[1]:"");
                Map<String,Object> m = new HashMap<>();
                m.put("pid", pid);
                m.put("cmd", cmdline);
                list.add(m);
            }catch(NumberFormatException ignore){}
        }
        return list;
    }

    public Flux<String> streamLogs(String hostAlias, String unit, String fileAliasOrPath){
        HostEntry h = hostEntry(hostAlias);
        final String ip = h.getIp();
        String cmd;
        String tail = cmdPath("tail","tail");
        String journalctl = cmdPath("journalctl","journalctl");

        if(unit!=null && !unit.isBlank()){
            if(!cfg.isJournaldEnabled()) return Flux.error(new IllegalStateException("journald streaming disabled by config"));
            if(h.getAllowedUnits()==null || !h.getAllowedUnits().contains(unit))
                return Flux.error(new IllegalArgumentException("unit not allowed"));
            cmd = journalctl + " -fu " + unit;
        }else if(fileAliasOrPath!=null && !fileAliasOrPath.isBlank()){
            String path = (h.getAllowedLogs()!=null && h.getAllowedLogs().containsKey(fileAliasOrPath))
                    ? h.getAllowedLogs().get(fileAliasOrPath) : fileAliasOrPath;
            if(!path.startsWith("/")) return Flux.error(new IllegalArgumentException("log path must be absolute"));
            cmd = tail + " -F " + path;
        }else{
            return Flux.error(new IllegalArgumentException("unit or file required"));
        }

        return Flux.create(sink -> {
            final SSHClient ssh = new SSHClient();
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            try {
                ssh.connect(ip);
                ssh.authPublickey(cfg.getSshUser());
                final Session s = ssh.startSession();
                final Session.Command c = s.exec(cmd);
                final InputStream in = c.getInputStream();

                var exec = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "ssh-log-stream"); t.setDaemon(true); return t;
                });
                exec.submit(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null && !sink.isCancelled()) {
                            sink.next(line);
                        }
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        try { c.close(); } catch(Exception ignore){}
                        try { s.close(); } catch(Exception ignore){}
                        try { ssh.disconnect(); } catch(Exception ignore){}
                    }
                });

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
