# 固件 4.0.0 / 协议 V4

现有数据库先执行 `database/33-sensor-v4.sql`；尚未接入传感器的旧库先执行 `30-sensor-iot.sql`、`31-sensor-keyless.sql`，再执行 `33`。新库直接使用已合并 V4 的 `01-schema.sql`，不重复执行 `33`。应用 `spring.sql.init.mode=never`，编译或重启不会执行迁移。

2026-09-10 已在当前配置的 `hqc_plt` 执行第 33 号迁移，原 19 条训练记录保留为 V3；三张升级表的字段、可空约束和会话唯一索引均校验通过。该数据库无需重复执行 `33`。执行前完整备份为 `../.runtime/database-backups/hqc-plt-before-sensor-v4-20260910-215236.sql`，同目录 `.sha256` 和 `.migration.txt` 保存校验值及执行记录。

## 设备注册和上报

设备接口携带 `X-Device-Id`，其值必须和 JSON 的 `deviceId` 一致。沿用项目开发期的设备标识机制；这不是设备密钥认证。用户和管理端查询接口仍需要各自登录。

`POST /api/v1/device/register` 不要求用户登录。请求字段 `deviceId`（`ARVELLO-` 加 12 位大写 MAC）、`serialNumber`、`model`、`firmwareVersion` 全部必填，返回 `200`、`ok: true`、`deviceId`、`deviceCode` 和 `schemaVersion: 4`。同一设备的传感器 SN 不允许改变；注册及实时上报更新型号和当前固件版本，历史摘要仅补齐缺失元数据。

`POST /api/v1/sensor/readings` 返回 `202`，按 `schemaVersion` 和 `recordType` 分别校验：

| 类型 | V4 必填字段 |
| --- | --- |
| 公共 | `schemaVersion: 4`、`recordType`、`deviceId`、`serialNumber`、`model`、`firmwareVersion`、`bootId` |
| `telemetry` | V3 的 `sequence`、`uptimeMs`、`sensorOk`、`moving`、`standby`、`countType: continuous_cycle`、`repetitionCount` 以及传感器正常时的三轴运动字段；另加 `trainingState`、`sessionId`、`activeDurationMs`、`sessionRepetitionCount`、`averagePeriodMs`、`minPeriodMs`、`maxPeriodMs`、`timeValid`、`sessionStartedAt`、`sessionLastMotionAt`、`startUptimeMs`、`endUptimeMs` |
| `training_summary` | `sessionId`、`timeValid`、`startTime`、`endTime`、`startUptimeMs`、`endUptimeMs`、`activeDurationMs`、`repetitionCount`、`averagePeriodMs`、`minPeriodMs`、`maxPeriodMs`、`endReason` |

`trainingState` 为 `idle`、`active` 或 `paused`。空闲时 `sessionId` 为空，会话计数、时长及时间字段为 `0`，`timeValid` 为 `false`。训练中 `sessionId` 必须非空，每次训练全设备生命周期唯一。推荐 `<deviceId>-<随机bootId>-<会话序号>`。

`telemetry.repetitionCount` 是设备累计计数（固件可跨开机保存）；`sessionRepetitionCount` 和 `training_summary.repetitionCount` 才是本次训练计数。正常传感器必须有完整三轴数据；异常时 `moving` 必须为 `null`。同一开机周期实时序号去重，并拒收计数倒退；升级/降级协议必须更换 `bootId`，同一开机周期混用协议返回 `409 PROTOCOL_CHANGED`。历史摘要不受“旧开机周期实时数据拒收”影响。固件补救旧缓存时可附带 `uptimeEstimated`、`legacyStartTime` 和 `legacyEndTime`，这些来源线索随摘要保存在数据库中，不用于替代通过校验的训练日期或归属。

可选实时字段为 `faultMask`、`cachedSessionCount`、`batteryAvailable`、`batteryPercent`、`batteryVoltage`、`charging` 和 `batteryFull`。未配置电池时 `batteryAvailable: false`，省略或传 `null` 电池测量字段；百分比存在时必须为 `0..100`，不能使用 `0` 或 `-1` 代替未知。

## 时间、归属和统计

历法时间字段是 UTC Unix **秒**，运行时长及节奏字段是**毫秒**。未校时时历法时间为 `0` 且 `timeValid: false`；后端数据库保存未知时间为 `NULL`，不会解释为 1970 年。`endUptimeMs >= startUptimeMs`，有效运动时长不得超过两者差值。校时后的历法时间跨度与运行时间跨度允许最多 2 秒取整误差。节奏为全 `0`（无样本）或 `minPeriodMs <= averagePeriodMs <= maxPeriodMs`。

同一 `(sensor_id, device_session_id)` 只保存一条训练：实时消息更新其进行状态，最终摘要覆盖为完整统计。摘要提交事务成功后才回应：

```json
{"ok":true,"accepted":true,"sessionId":"原始会话ID","duplicate":false}
```

重复摘要返回同一 `sessionId` 和 `duplicate: true`，不累加次数或时长；相同会话的统计发生冲突返回 `409 SUMMARY_CONFLICT`。只允许未知历法时间补充为有效时间。固件应收到匹配会话 ID 的 `accepted: true` 才删除缓存，不能只凭任意 HTTP 2xx 清空摘要。

归属按整个训练时间落入的历史传感器绑定区间确认。实时未校时记录可根据服务器接收时间和运行时长建立 `ESTIMATED` 时间；孤立的离线未知时间摘要保存为 `ownershipStatus: PENDING`，不归入当前账号。横跨解绑/重新绑定边界的训练同样待核实。核心床解绑、账号注销和传感器解绑都会结束有效传感器绑定，避免后来使用者继承之前的训练。

V4 `durationMs` 采用 `activeDurationMs`，暂停不增加有效时长。静止 5 分钟完成会话，随后到达的摘要仍更新原行；V3 保留原有 3 分钟及首末运动跨度口径。已完成记录仅在往返次数大于 0 或 V4 有效时长至少 5 秒时出现在训练历史和统计。未知或仅估算历法时间的训练可计入已确认归属账号的总时长/次数，但不计算训练日期或连续天数。

## 查询字段

用户最新状态和管理端在线超时统一为 **90 秒**；暂停状态返回 `PAUSED`。电池未知保持 `null`/缺省。

用户最新状态包含 `serialNumber`、`model`、`firmwareVersion` 及 V4 状态/会话/电池/故障字段。管理端设备列表包含固件元数据及 `trainingState`；设备详情 `telemetry` 包含 V4 运动、电量、故障和缓存信息。

用户与管理端训练查询保留 `durationMs`、`repetitionCount`、`startedAt`、`lastMotionAt`、`endedAt`、`status`、`endReason`，新增 `schemaVersion`、`deviceSessionId`、`activeDurationMs`、`trainingState`、`timeValid`、`timeQuality`、`ownershipStatus`、`averagePeriodMs`、`minPeriodMs`、`maxPeriodMs`、`summaryReceived`。时间字段可能为 `null`。`timeQuality` 为 `SERVER`（V3）、`DEVICE`、`ESTIMATED` 或 `UNKNOWN`；未能确认归属的记录仅在管理端显示。
