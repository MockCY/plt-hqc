# ARVELLO Docker 部署

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
