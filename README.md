# JumpWatch Remote (恢复版)
构建：`mvn -DskipTests clean package`
运行：
```bash
java -jar target/jumpwatch-remote-0.0.7.jar   --spring.config.location=file:./src/main/resources/application.yml   --server.address=0.0.0.0
```
注意：远端 PATH 可能为空，`command-paths` 已写为绝对路径。
