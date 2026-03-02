# JumpWatch Remote (简易版监控系统)
# 可以查看日志-支持300行，文件检索关键字上下文30行匹配
# 服务器状态查看，包含cpu 内存 磁盘 只针对home盘和app盘
# 禁止盗用
构建：`mvn -DskipTests clean package`   
运行：
```bash
# 定版环境
nohup java -jar jumpwatch-remote-0.0.7.jar   --spring.config.location=classpath:/application.yml > app.log 2>&1 &
# 预演环境
nohup java -jar jumpwatch-remote-0.0.7.jar   --spring.config.location=classpath:/application-uat.yml  app.log 2>&1 &
```
注意：远端 PATH 可能为空，`command-paths` 已写为绝对路径。

测试push