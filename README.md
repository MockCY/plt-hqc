# ARVELLO 微信健身小程序后端

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

不要让应用使用 `root`，也不要把公网 MySQL 密码写入代码或提交到仓库。

在 MySQL 管理终端依次执行：

```text
database/01-schema.sql
database/02-seed.sql
database/03-create-app-user.sql.example
database/04-features.sql
```

第三个文件先替换其中的随机密码。如果 Java 和 MySQL 在同一台服务器，应用账号限制为 `localhost`，并在云安全组中关闭公网 `3306`。

## 4. 配置与启动

必要环境变量见 [.env.example](.env.example)。Spring Boot 不会自动读取 `.env` 文件，生产环境应在系统服务、Docker 或部署平台中注入变量。

PowerShell 示例：

```powershell
$env:DB_URL='jdbc:mysql://47.111.172.151:3306/hqc_plt?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的应用数据库密码'
$env:WECHAT_APP_ID='你的小程序AppID'
$env:WECHAT_APP_SECRET='你的小程序AppSecret'
.\mvnw.cmd spring-boot:run
```

测试与打包：

```powershell
.\mvnw.cmd test
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
| GET | `/api/courses` | 否 | 课程列表，支持 `type`、`query` |
| GET | `/api/courses/{id}` | 否 | 课程详情与训练动作步骤 |
| GET | `/api/exercises` | 否 | 动作列表，支持 `bodyPart`、`query` |
| GET | `/api/plans/catalog` | 否 | 可选训练计划列表 |
| GET | `/api/plans/current` | 是 | 当前训练计划 |
| PUT | `/api/plans/{id}/select` | 是 | 选择并保存当前计划 |
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

## 7. 上线前检查

- 在微信公众平台配置 `request` 合法域名和用户隐私保护指引。
- 服务通过 Nginx/Caddy 暴露 HTTPS，只开放 `443`，Java 的 `8080` 仅供反向代理访问。
- AppSecret、数据库密码通过环境变量注入，定期轮换。
- 不开放公网 `3306`；应用使用最小权限账号。
- 对登录和手机号接口增加网关限流、访问日志和告警。
- 先在微信开发者工具和真机测试；快速验证组件以真机结果为准。
