# ARVELLO Docker 部署

当前默认使用 [Dozzle 轻量日志网页](logs/README.md)，内存上限为 128 MB，适合现有
2 GB 服务器查看后端日志。自动部署会启动 Dozzle 并停止旧 ELK，保留已有日志卷。
后端仍将 ECS JSON 日志写入持久卷 `arvello-backend-logs`。
原 [ELK 配置](elk/README.md) 保留供以后扩容时参考。

## GitHub Actions 自动部署

仓库的 `Deploy Backend` 工作流需要以下 GitHub Actions Secrets：

- `SERVER_HOST`：服务器地址
- `SERVER_USER`：SSH 用户名（当前部署目录按 `admin` 用户配置）
- `SERVER_SSH_KEY`：SSH 私钥
- `DEPLOY_ENV`：完整的生产环境变量文件内容

在仓库的 `Settings > Secrets and variables > Actions` 中创建 `DEPLOY_ENV`，内容可从
`deploy/.env.example` 复制，并将所有占位值替换为真实生产配置。例如：

```properties
DB_URL=jdbc:mysql://host.docker.internal:3306/hqc_plt?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=请替换为真实数据库密码
WECHAT_APP_ID=请替换为真实小程序AppID
WECHAT_APP_SECRET=请替换为真实小程序AppSecret
SESSION_TTL=30d
ADMIN_USERNAME=admin
ADMIN_PASSWORD=请替换为真实后台密码
ADMIN_SESSION_TTL=12h
MEDIA_HOST_PATH=./data/media
MEDIA_MAX_FILE_SIZE=500MB
MEDIA_MAX_REQUEST_SIZE=500MB
```

工作流会临时生成 `.env` 并上传到 `/home/admin/deploy/.env`，不会将密钥提交到仓库。
工作流启动 `arvello-backend` 和独立的 Dozzle 日志服务，不依赖管理后台镜像。
Dozzle 镜像在 GitHub Actions 下载，与后端镜像一同上传，服务器无需访问镜像仓库。

## 准备

服务器需要安装 Docker Engine 和 Docker Compose 插件。将整个 `server` 目录上传到服务器，例如 `/opt/arvello/server`。

进入部署目录并创建生产环境变量文件：

```bash
cd /opt/arvello/server/deploy
cp .env.example .env
chmod 600 .env
vi .env
```

不要把 `.env` 提交到代码仓库或发送给他人。

如果 MySQL 与 Docker 在同一台 ECS，请把 `.env` 中数据库主机改为宿主机网关：

```properties
DB_URL=jdbc:mysql://host.docker.internal:3306/hqc_plt?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

如果 MySQL 在另一台服务器，则继续使用数据库服务器的公网或私网地址。

## 构建并启动

已有数据库依次执行 `database/14-sequential-device-sn.sql`、`database/15-online-presence.sql` 和
`database/16-plan-presentation.sql` 和 `database/17-device-brand-and-source.sql`，分别补齐设备序列号、每日在线用户、计划展示字段以及设备品牌与来源字段。

Docker 会在构建镜像时使用 Java 21 完成测试与打包，服务器无需单独安装 Java 或 Maven：

```bash
cd /opt/arvello/server/deploy
docker compose up -d --build
```

## 检查

```bash
docker compose ps
docker compose logs -f --tail=200
curl http://127.0.0.1:8080/api/health
curl -I http://127.0.0.1:8080/api/media/files/images/fitness/course-fullbody.jpg
```

## 更新版本

上传新代码后执行：

```bash
cd /opt/arvello/server/deploy
docker compose up -d --build
```

## 停止或重启

```bash
docker compose restart
docker compose down
```

容器端口只绑定到服务器的 `127.0.0.1:8080`，由 Nginx 将 HTTPS 请求反向代理到该地址。不要向公网开放 8080 端口。
