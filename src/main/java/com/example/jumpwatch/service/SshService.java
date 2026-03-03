/*
package com.example.jumpwatch.service;


import com.example.jumpwatch.config.MonitorConfig;
import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;

*/
/**
 * 项目名称: jumpwatch-remote-restore
 * 类名称: MonitorApplication
 *
 * @author blx
 * @date 2026-03-02
 * @version 7.0
 * @description 服务监控启动类
 *//*


@Service
public class SshService {
    @Autowired
    private MonitorConfig config;

    public String execute(String ip, String command) throws Exception {
        JSch jsch = new JSch();

        // 获取当前用户目录下的私钥路径
        String privateKeyPath = System.getProperty("user.home") + "/.ssh/id_rsa";

        // 新版 JSch 会自动识别多种私钥格式 (RSA, ED25519, OpenSSH等)
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession("appoper", ip, 22);
        session.setConfig("StrictHostKeyChecking", "no");
        // 如果是 OpenSSH 格式，新版 JSch 可能需要显式开启某些算法（可选）
        session.setConfig("PubkeyAcceptedAlgorithms", "ssh-ed25519,ssh-rsa,rsa-sha2-256,rsa-sha2-512");

        session.connect(5000);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        InputStream in = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n");
        }

        channel.disconnect();
        session.disconnect();
        return result.toString();
    }

}
*/
