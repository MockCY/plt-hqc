# Dozzle 轻量日志网页

直接读取后端容器的 stdout/stderr，在网页查看实时日志、搜索文本和下载日志。
`log.info()`、`log.warn()`、`log.error()` 无需修改即可显示。只展示名称匹配
`arvello-backend` 的容器，容器操作、终端和 MCP 功能均关闭。

Dozzle 不依赖 Elasticsearch、Logstash、Filebeat、数据库或额外的业务日志代码。
容器内存上限 128 MB，Go 运行时内存软限制 96 MiB；实际内存随日志量和并发访问变化。
这比完整 ELK 更适合当前总内存约 1.8 GB 的服务器，但仍需观察整机剩余内存。

## 自动部署

推送 `main` 后，现有 `Deploy Backend` 工作流会自动：

1. 构建后端并从 GHCR 下载固定版本的 Dozzle 镜像。
2. 将两个镜像打进同一个归档，上传到服务器并加载，服务器不再拉取 Dozzle 镜像。
3. 停止已配置的旧 ELK 容器，保留索引、日志和采集进度卷。
4. 启动后端及独立的 `arvello-logs` Compose 项目，检查后端与日志网页的健康状态。

无需配置新的 `.env`、密码、ES 索引或 Kibana 数据视图。

## 打开网页

部署成功后，在自己的电脑终端执行（替换为实际 SSH 用户和地址）：

```bash
ssh -N -L 9999:127.0.0.1:9999 admin@SERVER_HOST
```

私钥或端口与默认不同则按现有 SSH 连接方式增加 `-i`、`-p` 参数。
保持终端连接，浏览器打开 `http://127.0.0.1:9999`，选择 `arvello-backend`。
可以看到近期控制台日志及不断新增的日志；无需额外登录网页，访问由 SSH 身份验证保护。
不方便建立隧道时仍可在服务器执行 `docker logs -f --tail=200 arvello-backend`。

网页只监听服务器回环地址，不能使用公网 IP 直接访问。Dozzle 读取 Docker socket，
即使 socket 挂载标记为 `ro` 也不构成 Docker API 权限隔离，因此不要把 9999 端口
开放到公网，或将此网页反向代理为无认证的公共页面。

## 日志范围

Dozzle 显示的是 Docker 保留的容器控制台日志，不是长期索引。后端 Docker 日志按
20 MB 轮转，最多保留 3 个文件，旧日志会被覆盖。发布时重建并删除旧后端容器后，
旧容器的控制台日志也不再能通过 Dozzle 查看；持久卷内的应用 JSON 日志仍按原来的
7 天、约 1 GB 规则保留，但不会被 Dozzle 读取。

Dozzle 的数据卷仅持久保存自身设置，不是日志备份。需要跨版本长期查询时再使用
独立日志服务器或托管服务。

## 检查与手动启动

```bash
cd /home/admin/deploy/logs
docker compose ps
docker compose logs --tail=100 dozzle
curl --fail http://127.0.0.1:9999/healthcheck
docker stats --no-stream
```

手动部署且镜像已由工作流上传时：

```bash
cd /home/admin/deploy
docker compose --env-file elk/.env -f elk/compose.yml stop
docker compose -f logs/compose.yml up -d --no-build --pull never
```

没有部署过 ELK 时省略停止 ELK 的命令。镜像尚未加载且服务器能够访问 GHCR 时，
可在 `logs` 目录直接执行 `docker compose up -d` 下载并启动。

启动失败时先检查上述容器日志与 `free -h`，不要同时重新启动旧 ELK。
停止日志网页使用 `docker compose stop`，不影响后端和已有应用日志文件。
