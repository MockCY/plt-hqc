# ARVELLO 微信健身小程序后端

传感器接入与部署见 [SENSOR_INTEGRATION.md](SENSOR_INTEGRATION.md)。新版传感器功能需先执行
`database/30-sensor-iot.sql`、`database/31-sensor-keyless.sql` 和 `database/33-sensor-v4.sql`；设备首次连接自动登记，无需设备密钥。
固件 `4.0.0` 使用协议 `schemaVersion: 4`，后端同时保留 V3 接收。新建数据库的 `01-schema.sql` 已包含 V4 字段，不要重复执行 `33`。现有数据库必须先执行 `33` 再启动新版服务，应用不会自动迁移。详见 [V4 协议及统计规则](SENSOR_V4.md)。
课程观看时间与传感器训练时间分别统计。

这是 `we-plt` 健身微信小程序的 Java 后端，采用 Spring Boot + JDBC + MySQL。

鉴权没有引入 Spring Security、JWT、JPA 或 Flyway。服务端使用微信 `openid` 识别用户，再签发随机会话令牌；数据库只保存令牌的 SHA-256 摘要。

## 1. 登录链路

普通微信登录：

1. 小程序调用 `uni.login()`，得到一次性的 `code`。
2. 小程序把 `code` 发送到 `POST /api/auth/login`。
3. Java 服务用 AppID、AppSecret 和 `code` 调用微信 `code2Session`，取得 `openid`。
4. 服务按 `openid` 创建或查找用户，返回自己的 `token`。

手机号快速验证：

1. 用户点击 `open-type="getPhoneNumber"` 按钮，事件中得到一次性的手机号 `code`。
2. 再调用一次 `uni.login()` 取得新的登录 `code`。
3. 将两者分别作为 `phoneCode`、`loginCode` 发送到 `POST /api/auth/wechat-phone`。
4. Java 服务分别向微信换取手机号和 `openid`，绑定后返回自己的 `token`。

`openid` 是用户在当前小程序下的稳定身份标识；`phone` 是可选的业务联系方式。数据库不使用手机号作为微信用户的唯一身份。

## 2. 环境要求

- JDK 21
- MySQL 8.0+
- Maven 3.9+，或直接使用项目自带的 `mvnw.cmd`
- 已认证的微信小程序 AppID、AppSecret
- 已购买并开通的“手机号快速验证组件”资源包

## 3. 初始化数据库

动作难度“拉伸”更名为“挑战”需执行 `database/32-exercise-challenge-level.sql`，更新已有动作的难度字段。

不要让应用使用 `root`，也不要把公网 MySQL 密码写入代码或提交到仓库。

在 MySQL 管理终端依次执行：

```text
database/01-schema.sql
database/02-seed.sql
database/03-create-app-user.sql.example
database/04-features.sql
database/05-media-storage.sql
database/06-devices.sql
database/07-course-detail.sql
database/08-admin-console.sql
database/16-plan-presentation.sql
```

上面的顺序用于新数据库，设备表会直接使用精简后的型号、SN 和二维码结构，不要执行一次性升级脚本 `09` 至 `13`。

已经执行过 `12-device-qr-and-sn.sql` 的数据库需额外执行一次
`database/13-device-models-and-cleanup.sql`。该脚本把分类转换为型号，保留设备 ID、SN、二维码和账号绑定，
并删除设备名称、分类、连接状态、床型、弹簧、购买时间、启用状态和排序等废弃字段。随后执行
`database/14-sequential-device-sn.sql`，为每个型号增加受数据库行锁保护的 SN 流水号。所有已有数据库还需执行
`database/15-online-presence.sql`，用于按天去重记录在线用户。
计划页升级后还需执行 `database/16-plan-presentation.sql`，用于增加计划封面、展示标签、单次时长和完成收益字段。
设备品牌与第三方设备升级需执行 `database/17-device-brand-and-source.sql`，用于增加品牌、设备名称和设备来源，并允许用户登记自定义的第三方型号。
随后执行 `database/18-device-brand-integrity.sql`，用于修复旧服务写入的空品牌并增加品牌完整性约束。
用户绑定与状态管理升级需执行 `database/21-user-binding-and-status.sql`，用于将手机号绑定收紧为唯一约束，执行前需确认脚本中的重复手机号审计查询没有返回记录。

设备型号品牌升级需执行一次 `database/22-device-model-brand.sql`。三个枫系列型号归属 Manhart，新 SN 前缀为 MNW01、MNW02、MNW03；其他现有型号归属 ARVELLO。已有设备 SN 和流水号保持不变。新型号必须选择品牌，Manhart 前缀以 MN 开头，ARVELLO 前缀以 AV 开头；新增和批量新增设备只能选择对应品牌的型号。

用户在线状态与历史记录升级需先执行 `database/23-user-presence-history.sql`，再启动新版服务并更新后台。该脚本只新增连接历史表，不回填上线前的历史。后台用户管理每 15 秒刷新，可筛选在线 / 离线，并查看每次连接的上线时间、离线时间和时长（北京时间）。同一用户任一连接活跃即在线，多端连接分别记录，时长不应直接相加作为用户总时长。正常关闭立即记离线；超过 75 秒未收到心跳的连接按最后心跳加 75 秒记为超时离线，该时间为推定值。数据库持久化心跳，服务重启后遗留连接也按此规则过期。

设备解绑状态与时间升级需执行 `database/24-device-binding-times.sql` 后再部署服务。该脚本补齐当前设备的真实绑定时间；解绑、换绑或注销账号时保留设备最近绑定及解绑时间。过去已经删除的绑定关系无法回填。设备管理可分别筛选未绑定、已绑定和设备已解绑，时间以北京时间展示。

版本早于 `12-device-qr-and-sn.sql` 的旧数据库，应按编号依次执行尚未运行的 `09` 至 `15` 升级脚本。
执行 `09` 前先用脚本内的审计语句确认历史设备没有重复绑定。

第三个文件先替换其中的随机密码。如果 Java 和 MySQL 在同一台服务器，应用账号限制为 `localhost`，并在云安全组中关闭公网 `3306`。

训练时长统计升级需先执行 `database/27-training-activity.sql`，再运行新版后端。该脚本通过 `CREATE TABLE IF NOT EXISTS` 新增 `training_activity` 表，用于保存实际训练秒数，不修改已有训练记录；仅重新编译或重启后端不会自动建表（`spring.sql.init.mode=never`）。漏执行时，训练统计和 `/api/workout-records/activity` 会报缺表错误。

也可在 `server` 目录使用 Java 21 运行 `tools/TrainingActivityMigration.java`，classpath 需包含项目使用的 MySQL Connector/J 和 SnakeYAML：先传 `--check` 检查，传 `--apply` 执行缺失的建表脚本并验证字段。连接配置读取 `application.yml`，优先使用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量。

## 4. 配置与启动

动作详情配置升级需执行 `database/24-exercise-guidance.sql`，新增重点部位图片与文字、弹簧组数数组、动作要点、常见错误和动作指令音频。新增字段默认未配置，不从训练组数推算弹簧组数。管理端和小程序通过原有动作接口读写这些字段；训练目标字段保留兼容旧课程，但动作详情不再展示。

必要环境变量见 [.env.example](.env.example)。Spring Boot 不会自动读取 `.env` 文件，生产环境应在系统服务、Docker 或部署平台中注入变量。

PowerShell 示例：

```powershell
$env:DB_URL='jdbc:mysql://47.111.172.151:3306/hqc_plt?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的应用数据库密码'
$env:WECHAT_APP_ID='你的小程序AppID'
$env:WECHAT_APP_SECRET='你的小程序AppSecret'
$env:WECHAT_MINI_PROGRAM_CODE_ENV_VERSION='release'
$env:ADMIN_USERNAME='admin'
$env:ADMIN_PASSWORD='请使用足够长的随机密码'
.\mvnw.cmd spring-boot:run
```

测试使用独立的 MySQL 数据库，不再使用 H2。测试启动时会重建测试表，禁止把测试连接指向正式业务库：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/hqc_plt_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:TEST_DB_USERNAME='root'
$env:TEST_DB_PASSWORD='测试数据库密码'
.\mvnw.cmd test
```

打包与启动：

```powershell
.\mvnw.cmd package
java -jar target\ARVELLO.jar
```

健康检查：`GET http://127.0.0.1:8080/api/health`。

## 5. 小程序端接入

可直接参考 [examples/uniapp-auth.js](examples/uniapp-auth.js)。登录成功后把 `token` 存入本地，后续请求统一添加：

```http
Authorization: Bearer <token>
```

线上小程序必须使用已备案、已配置到微信公众平台“服务器域名”的 HTTPS 域名，不能请求 `127.0.0.1` 或裸 IP。AppSecret 只放在 Java 服务端。

## 6. API

| 方法 | 路径 | 登录 | 用途 |
|---|---|---:|---|
| GET | `/api/health` | 否 | 服务及数据库健康检查 |
| POST | `/api/auth/login` | 否 | 微信 `code` 登录 |
| POST | `/api/auth/wechat-phone` | 否 | 微信登录并绑定快速验证手机号 |
| POST | `/api/auth/logout` | 是 | 注销当前会话 |
| WebSocket | `/ws/presence` | 是 | 小程序在线状态与心跳连接 |
| GET | `/api/media/files/**` | 否 | 通过现有 `/api/` 代理读取媒体文件 |
| GET | `/api/courses` | 否 | 课程列表，支持 `type`、`query` |
| GET | `/api/courses/{id}` | 否 | 课程详情与训练动作步骤 |
| GET | `/api/exercises` | 否 | 动作列表，支持 `bodyPart`、`query` |
| GET | `/api/plans/catalog` | 否 | 可选训练计划列表 |
| GET | `/api/plans/current` | 是 | 当前训练计划；未选择时返回 `204` |
| PUT | `/api/plans/{id}/select` | 是 | 选择并保存当前计划 |
| GET | `/api/devices` | 是 | 查询当前账号绑定的全部设备 |
| GET | `/api/devices/current` | 是 | 兼容旧版：查询最近绑定的一台设备；未绑定时返回 `204` |
| POST | `/api/devices/bind` | 是 | 使用自有设备 SN 码绑定到当前账号 |
| POST | `/api/devices/third-party` | 是 | 创建第三方设备，系统自动生成内部 SN 并绑定当前账号 |
| DELETE | `/api/devices/{deviceId}` | 是 | 解绑当前账号拥有的指定设备 |
| DELETE | `/api/devices/current` | 是 | 兼容旧版：仅单台绑定可用，多台时返回 `409` |
| POST | `/api/workout-records` | 是 | 写入训练记录 |
| GET | `/api/workout-records` | 是 | 训练记录列表 |
| GET | `/api/workout-records/stats` | 是 | 训练统计 |
| GET | `/api/me` | 是 | 个人资料与设置 |
| PUT | `/api/me` | 是 | 更新昵称、头像 |
| PUT | `/api/me/settings` | 是 | 更新提醒、声音设置 |
| DELETE | `/api/me` | 是 | 注销账号并删除个人数据 |
| GET/PUT/DELETE | `/api/favorites` | 是 | 查询、添加或取消动作与课程收藏 |
| GET/POST/DELETE | `/api/custom-courses` | 是 | 管理自定义课程 |
| GET/POST | `/api/feedback` | 是 | 查询和提交问题反馈 |
| GET/POST | `/api/campaigns/{code}` | 是 | 查询训练营状态并打卡 |

完整请求样例见 [examples/api.http](examples/api.http)。

## 7. 管理后台

### 多设备绑定升级

一个用户可以绑定多台品牌或第三方设备，每台设备只允许绑定一个用户。
执行 `database/29-multiple-user-devices.sql` 将绑定表主键改为 `(user_id, device_id)`，保留
`device_id` 唯一索引、所有已有绑定及其时间。新库的 `01-schema.sql` 和 `06-devices.sql` 已使用该结构。

升级顺序：先停止旧后端的绑定写入，再执行迁移、启动新版后端，最后更新小程序。
旧后端会按用户替换绑定，不可在多设备数据写入后继续运行或直接回滚；如需回滚，先处理多设备数据。
`tools/MultipleDeviceMigration.java` 默认只检查结构，加 `--apply` 执行迁移并核对已有绑定和时间是否保留。

- `GET /api/devices`：返回当前用户的全部绑定，按绑定时间倒序。
- `DELETE /api/devices/{deviceId}`：仅解绑当前用户拥有的指定设备，其他设备不受影响。
- `POST /api/devices/bind` 和 `/third-party`：追加绑定；重复绑定同一设备保留原绑定时间。
- 旧 `GET /api/devices/current` 返回最近绑定的一台设备。旧 `DELETE /api/devices/current` 仅在绑定一台时生效，多台时返回 `409 DEVICE_ID_REQUIRED`，避免误解绑。

后台设备列表仍按设备展示所属用户，同一个用户可以出现在多行。账号注销会释放该用户的所有设备。

管理接口统一位于 `/api/admin/**`，使用独立管理员会话，不接受小程序用户令牌。首次启动前设置
`ADMIN_USERNAME` 和 `ADMIN_PASSWORD`，应用会在尚无管理员账号时创建首个账号。创建完成后，密码仅以
BCrypt 摘要保存在数据库中。

后台支持数据概览、用户查询、课程与动作维护、训练计划与训练营维护、训练记录查询、反馈处理、
媒体上传和操作日志。设备管理支持单台新增和单次最多 100 台的批量新增，批量设备会在同一事务中生成连续 SN；
还可勾选最多 100 台自有设备，导出包含品牌、型号、SN 和完整设备标签图片的 Excel 文件。
生产环境不要保留空的 `ADMIN_PASSWORD`。

设备标签统一使用微信官方接口 `getwxacodeunlimit` 生成小程序码，参数固定为
`scene=device-bind`，页面参数留空并由微信打开小程序首页。用户微信扫码后，小程序读取 `scene` 并直接打开 SN 绑定页，
因此不需要配置 Nginx `/device-bind` 路由，也不需要在微信公众平台配置“扫普通链接二维码打开小程序”。

所有设备标签共用同一个小程序码，区别仅在标签下方的唯一 SN。服务进程会缓存小程序码，重启后的首次标签下载会重新调用微信接口。
生产环境保持 `WECHAT_MINI_PROGRAM_CODE_ENV_VERSION=release`；体验版联调时可临时设为 `trial`，并确保目标页面已存在于对应版本中。

## 8. 上线前检查

- 在微信公众平台分别配置 `request` 与 `socket` 合法域名，并配置用户隐私保护指引。
- 反向代理需要将 `/api/` 和 `/ws/` 转发到 Java 服务，其中 `/ws/` 必须传递 `Upgrade` 与 `Connection` 请求头；媒体不需要单独配置 `/media/`。
- 服务通过 Nginx/Caddy 暴露 HTTPS，只开放 `443`，Java 的 `8080` 仅供反向代理访问。
- AppSecret、数据库密码通过环境变量注入，定期轮换。
- 不开放公网 `3306`；应用使用最小权限账号。
- 对登录和手机号接口增加网关限流、访问日志和告警。
- 先在微信开发者工具和真机测试；快速验证组件以真机结果为准。
