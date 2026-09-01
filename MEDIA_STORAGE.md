# 本地媒体存储

媒体文件不写入 JAR，也不写入数据库。后端默认保存到运行目录下的 `data/media`，生产环境通过 `MEDIA_ROOT` 指向独立数据盘。

推荐生产配置：

```text
MEDIA_ROOT=/data/arvello/media
MEDIA_MAX_FILE_SIZE=500MB
MEDIA_MAX_REQUEST_SIZE=500MB
```

上传接口为 `POST /api/media/upload`，使用登录令牌，提交 `multipart/form-data`：

- `file`：图片或视频文件
- `kind`：`image` 或 `video`

成功后返回 `/media/...` 访问路径。数据库只保存该路径。部署时必须备份媒体目录，并确保运行后端的用户拥有写权限。

已有数据库需要依次执行 `database/05-media-storage.sql` 和 `database/07-course-detail.sql`。后者会为课程观看人数及动作图片/视频补充字段。Docker Compose 会把 `MEDIA_HOST_PATH` 挂载到容器内 `/data/arvello/media`。
