package com.example.jumpwatch.controller;

import com.example.jumpwatch.config.JumpWatchConfig;
import com.example.jumpwatch.service.SSHExecService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JumpWatchConfig cfg;
    private final SSHExecService ssh;
    public ApiController(JumpWatchConfig cfg, SSHExecService ssh){
        this.cfg = cfg; this.ssh = ssh;
    }

    @GetMapping("/health")
    public String health(){ return "ok"; }

    @GetMapping("/hosts")
    public Map<String,Object> hosts(){
        var h = cfg.getHosts();
        return Map.of("ok", true, "count", h==null?0:h.size(), "hosts", h==null?Map.of():h);
    }

    @GetMapping("/service/status")
    public Map<String,String> serviceStatus(@RequestParam String host, @RequestParam String unit) throws Exception {
        if(host==null || host.isBlank() || unit==null || unit.isBlank())
            throw new IllegalArgumentException("host & unit required");
        return ssh.systemdStatus(host, unit);
    }

    @GetMapping("/process/pids")
    public Map<String,Object> pids(@RequestParam String host, @RequestParam String pattern) throws Exception {
        if(host==null || host.isBlank() || pattern==null || pattern.isBlank())
            throw new IllegalArgumentException("host & pattern required");
        List<Map<String,Object>> list = ssh.grepPids(host, pattern);
        return Map.of("ok", true, "count", list.size(), "pids", list);
    }

    @GetMapping(path="/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> logs(@RequestParam String host,
                             @RequestParam(required = false) String unit,
                             @RequestParam(name="file", required = false) String file){
        if(host==null || host.isBlank())
            throw new IllegalArgumentException("host required");
        if((unit==null || unit.isBlank()) && (file==null || file.isBlank()))
            throw new IllegalArgumentException("unit or file required");
        return ssh.streamLogs(host, unit, file);
    }

    @GetMapping("/debug/bind")
    public Map<String,Object> debugBind() {
        return Map.of(
                "hostsNull", cfg.getHosts()==null,
                "hostCount", cfg.getHosts()==null?0:cfg.getHosts().size()
        );
    }
}
