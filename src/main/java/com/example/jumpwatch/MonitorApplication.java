package com.example.jumpwatch;

import com.example.jumpwatch.config.MonitorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;



@SpringBootApplication
public class MonitorApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(MonitorApplication.class, args);
        MonitorConfig config = context.getBean(MonitorConfig.class);

        System.out.println(">>> 检查配置加载 <<<");
        System.out.println("用户: " + config.getUser());
        System.out.println("Hosts是否为空: " + (config.getHosts() == null));
        if (config.getHosts() != null) {
            System.out.println("已读取机器数: " + config.getHosts().size());
        }
        System.out.println(">>>>>>>>>>>>>>>>>>");
    }
}