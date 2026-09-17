# 本地媒体存储

媒体文件不写入 JAR，也不写入数据库。后端默认保存到运行目录下的 `data/media`，生产环境通过 `MEDIA_ROOT` 指向独立数据盘。

推荐生产配置：

```text
MEDIA_ROOT=/data/arvello/media
MEDIA_MAX_FILE_SIZE=500MB
MEDIA_MAX_REQUEST_SIZE=500MB
MEDIA_VIDEO_TRANSCODING_ENABLED=true
MEDIA_VIDEO_TRANSCODING_TIMEOUT_SECONDS=900
```

上传接口为 `POST /api/media/upload`，使用登录令牌，提交 `multipart/form-data`：

- `file`：图片、视频或音频文件
- `kind`：`image`、`video` 或 `audio`

成功后返回 `/api/media/files/...` 访问路径。数据库只保存该路径。部署时必须备份媒体目录，并确保运行后端的用户拥有写权限。

视频上传后会在服务器本地通过 FFmpeg 统一处理为适合小程序弱网播放的 MP4：H.264、AAC、最高 720p、30fps、约 1.8Mbps 峰值码率，并把 MP4 索引移动到文件开头。这样视频画面与原声共用同一时间轴，也能更快开始播放。Docker 镜像已包含 FFmpeg；非 Docker 部署需要自行安装 FFmpeg，或将 `FFMPEG_COMMAND` 指向可执行文件。管理后台内置的 Nginx 已把上传请求超时设为 900 秒；如果生产环境前面还有一层 Nginx、Caddy 或云网关，也要把上游响应超时设置为不低于视频处理超时。

已有数据库需要依次执行 `database/05-media-storage.sql` 和 `database/07-course-detail.sql`。后者会为课程观看人数及动作图片/视频补充字段。Docker Compose 会把 `MEDIA_HOST_PATH` 挂载到容器内 `/data/arvello/media`。
