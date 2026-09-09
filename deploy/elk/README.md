# ARVELLO ELK 日志采集

链路：Spring Boot ECS JSON 文件 → Filebeat → Logstash → Elasticsearch → Kibana。
采集应用启动、业务代码通过 SLF4J 输出的日志及异常堆栈；不会自动生成每条 HTTP
请求的访问日志，也不采集 MySQL、Nginx 或其他容器的日志。
异常堆栈在一条 JSON 记录内，无需拼接多行。日志内不应主动输出令牌、请求体或密钥。

ELK 使用独立 Compose 项目 `arvello-elk`，通过只读共享卷 `arvello-backend-logs`
读取后端日志。后端部署工作流会同步本目录，但不自动启动 ELK；日常后端发布不会
重建 ELK 容器。生产密钥仅保存在本目录 `.env`，不放进后端 `DEPLOY_ENV`。

## 服务器准备

适用于同机 Linux Docker Engine + Compose v2。ELK 容器合计内存上限约 4 GB，
还需为 Filebeat、系统、后端和 MySQL 留空间，建议整机至少 8 GB 内存并预留 30 GB
可用磁盘，实际磁盘需求取决于日志量。本配置为单节点，无副本，不提供高可用。

```bash
sudo sysctl -w vm.max_map_count=262144
printf 'vm.max_map_count=262144\n' | sudo tee /etc/sysctl.d/90-arvello-elasticsearch.conf
```

先部署包含日志卷的新后端镜像。在已有 Actions 部署服务器上：

```bash
cd /home/admin/deploy
docker compose up -d --no-build arvello-backend
docker volume inspect arvello-backend-logs
docker compose exec arvello-backend sh -c 'test -s /app/logs/application.json'
```

上面的命令要求先通过现有工作流构建、上传并加载新版镜像；只有旧镜像时，请先发布。
源码部署则在 `server/deploy` 执行 `docker compose up -d --build arvello-backend`。
镜像会以 UID 10001 创建日志目录，由 Docker 初始化共享卷权限。

## 首次启动

```bash
cd /home/admin/deploy/elk
cp .env.example .env
chmod 600 .env
# 执行四次，将四个不同的随机值分别填入 .env 的四个变量。
openssl rand -hex 32
vi .env
docker compose config --quiet
docker compose up -d
docker compose ps -a
docker compose logs --tail=100 setup filebeat logstash
```

所有密码使用不少于 32 位的十六进制字符串；Kibana 加密密钥也使用 64 位随机
十六进制字符串，持久保存，不要在每次发布时重新生成。初始化容器 `setup` 正常
状态为 `Exited (0)`，负责建立专用写入账户、Kibana 系统账户和索引生命周期。
若初始化失败，修复后执行 `docker compose up -d --force-recreate setup`，再执行
`docker compose up -d`。Elasticsearch 首次启动可能需要数分钟。

仅 `127.0.0.1:5601` 和 `127.0.0.1:9200` 暴露到宿主机，Logstash 的 5044 端口
不发布。服务间使用隔离的 Docker 内部网络和 HTTP；跨主机部署时需要另行配置 TLS。
不要把这些端口改成公网监听。

## 查询和验收

在本机建立 SSH 隧道（替换服务器地址和用户）：

```bash
ssh -L 5601:127.0.0.1:5601 admin@SERVER_HOST
```

浏览器访问 `http://127.0.0.1:5601`，首次使用 `elastic` 和 `.env` 中的
`ELASTIC_PASSWORD` 登录。日常使用请在 Kibana 创建仅对 `arvello-logs-*` 具有
`read` / `view_index_metadata` 和 Discover 读取权限的账户。

在 Stack Management → Data Views 创建 `arvello-logs-*`，时间字段选
`@timestamp`。进入 Discover，将时间范围设为最近 15 分钟，可查询：

```text
service.name : "ARVELLO"
log.level : "ERROR"
log.logger : "com.qinglian.fitness.common.GlobalExceptionHandler"
```

在服务器确认已有真实应用日志写入索引（curl 会交互提示输入 elastic 密码）：

```bash
curl --fail -u elastic 'http://127.0.0.1:9200/_cluster/health?pretty'
curl --fail -u elastic 'http://127.0.0.1:9200/arvello-logs-*/_count?pretty'
curl --fail -u elastic 'http://127.0.0.1:9200/arvello-logs-*/_search?size=1&sort=@timestamp:desc&pretty'
curl --fail -u elastic 'http://127.0.0.1:9200/arvello-logs-*/_ilm/explain?pretty'
```

确认检索结果包含真实应用的 `service.name`、`log.level`、`message`；有异常时应
包含 `error.stack_trace`。Filebeat 指纹识别默认在文件至少 1024 字节后开始读取，
正常启动日志即可达到阈值。健康检查接口本身不会必然产生应用日志。

## 保留与维护

- 后端文件：每个 50 MB，最多 7 天，总量约 1 GB（轮转清理时执行上限），不压缩，
  Filebeat 同时读取活动文件和轮转文件。控制台日志另保留 3 个 20 MB 文件。
- Elasticsearch：单主分片，达到 10 GB 或 1 天时滚动，滚动后 14 天删除，实际
  日志保留通常约 14～15 天；ILM 异步执行，不能作为磁盘硬上限。
- Filebeat 读取进度和 1 GB 磁盘队列、Logstash 1 GB 持久队列均使用独立卷。
  停机超过源日志保留窗口且队列已满时，旧日志仍可能丢失；重试可能产生重复记录。
- 磁盘占满、认证失败、JSON 解码失败时，检查 `docker compose logs filebeat logstash`
  和索引状态。Logstash 死信队列位于其数据卷内，需要运维检查和处理映射失败记录。

更新配置并重建相应服务：

```bash
docker compose up -d --force-recreate filebeat logstash kibana
```

修改索引模板只影响后续新索引；修改 ILM 策略后需重新运行 `setup`。
已有 Elasticsearch 数据卷上的 `elastic` 密码不会随环境变量自动修改，轮换时先
调用 Elasticsearch 修改密码，再同步 `.env` 并重建相关服务。

停止 ELK：`docker compose down`，卷保留。恢复：`docker compose up -d`。
不要使用 `down -v`，它会删除索引和采集进度；备份日志应使用 Elasticsearch snapshot。
ELK 停机时后端继续写本地日志，不依赖采集服务启动。
