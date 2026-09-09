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
4. 从部署环境生成日志网页登录凭据，启动后端及独立的 `arvello-logs` Compose 项目。
5. 更新 Nginx 日志代理片段，检查配置并重载，检查后端与日志网页的健康状态。

默认使用部署环境中的 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 登录；也可在 `DEPLOY_ENV`
中设置独立的 `LOG_VIEWER_USERNAME`、`LOG_VIEWER_PASSWORD`。日志账户独立于后端数据库，
在后台修改账号密码不会自动同步，需要更新部署环境并重新部署。

`init-auth.py` 通过 Docker Compose 解析环境配置，并通过 stdin 将密码传入 Dozzle
生成器，不把明文密码放进命令参数或输出。服务器仅生成 `users.yml` 密码摘要文件，
权限为 600，禁止提交仓库。每次部署都会重新生成并重建日志容器。

## 打开网页

浏览器直接访问 `https://manhart.top/logs/`，登录后选择 `arvello-backend`，即可
查看近期和实时日志，不需要 SSH 隧道。登录会话最长 12 小时。

Dozzle 仍只将 9999 端口绑定到服务器回环地址，由现有 HTTPS Nginx 代理访问。
在 `/etc/nginx/conf.d/mahate.conf` 的 HTTPS server 块中一次性添加：

```nginx
include /etc/nginx/snippets/arvello-logs.conf;
```

当前服务器已配置此入口。后续部署自动更新该片段；新增服务器时需安装 Nginx、
配置域名证书并添加同样的 include。`nginx-location.conf` 保留 `/logs` 前缀，
关闭代理缓冲以保证日志实时推送。

Dozzle 读取 Docker socket；`ro` 挂载不构成 Docker API 权限隔离。不要将 9999
端口开放到公网，也不要关闭网页认证。服务器终端仍可直接执行
`docker logs -f --tail=200 arvello-backend` 查看日志。

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
curl --fail http://127.0.0.1:9999/logs/healthcheck
docker stats --no-stream
```

手动部署且镜像已由工作流上传时：

```bash
cd /home/admin/deploy
docker compose --env-file elk/.env -f elk/compose.yml stop
python3 logs/init-auth.py
docker compose -f logs/compose.yml up -d --no-build --pull never --force-recreate
```

没有部署过 ELK 时省略停止 ELK 的命令。镜像尚未加载且服务器能够访问 GHCR 时，
可先在 `logs` 目录执行 `docker compose pull`，再初始化账户并启动。

启动失败时先检查上述容器日志与 `free -h`，不要同时重新启动旧 ELK。
停止日志网页使用 `docker compose stop`，不影响后端和已有应用日志文件。
