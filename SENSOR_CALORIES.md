# 传感器卡路里估算

卡路里只来源于 `sensor_workout_sessions`，不与课程完成或观看记录相加，因此同一次训练播放课程不会重复计入。
已有历史会话在读取时也会获得估算结果。升级已有数据库需先执行 `database/36-user-body-measurements.sql`，
增加用户身高、体重字段后再启动新服务；新建数据库的 `01-schema.sql` 已包含这些字段。

## 估算口径

这是基于训练时长、往返频率及体重的产品估算，尚未经过设备能量消耗校准，不能视为实测热量。
优先使用用户在“我的”保存的体重；未填写体重或无用户归属时，假设体重 **60 kg**。
身高作为身体资料保存，本公式不使用身高。计算使用：

```text
minutes = 训练时长毫秒 / 60000
repetitions = end_count - start_count
MET = 3 + 3 × min(repetitions / minutes / 30, 1)
weightKg = users.weight_kg，未填写时为 60
estimatedCalories = round(MET × 3.5 × weightKg / 200 × minutes, 1)
```

3–6 MET 及每分钟 30 次往返封顶是启发式强度映射，不代表传感器能测得个人代谢强度。
例如体重 60 kg、训练 10 分钟、往返 100 次，结果为 **42.0 kcal**；同样时长、200 次为 **52.5 kcal**。
体重 80 kg、训练 10 分钟、往返 100 次则为 **56.0 kcal**。
显示一位小数是接口格式约定，不代表测量准确度。

V4 使用设备上报的有效运动时长 `active_duration_ms`，不包含暂停时间；V3 使用开始到最后运动的时间跨度。
没有正时长、没有完成至少一次往返、起始计数为负的会话返回 0。
往返频率过高最多按 6 MET 计算，避免计数异常无限提高强度。
每个会话先四舍五入到一位小数，再累计，确保明细与总数一致。进行中的会话使用已上报时长和次数。

所有计算复用 `SensorSql.xml` 的 `estimatedCalories` SQL 片段，列表、详情及汇总使用同一口径。
每次查询均使用会话原用户当前保存的体重，因此**修改体重后，历史消耗也会同步重估**；
本功能不保存每次训练时的体重快照，默认体重也不会写入用户身体资料。

## API

| 接口 | 新增字段 | 说明 |
| --- | --- | --- |
| `GET /api/v1/sensor/stats` | `todayEstimatedCalories`、`totalEstimatedCalories` | 当前用户当日及累计估算消耗 |
| `GET /api/v1/sensor/sessions` | `items[].estimatedCalories` | 当前用户每次训练的估算消耗 |
| 设备训练历史、设备 latest 的 `session` | `estimatedCalories` | 同一会话的估算消耗 |
| `GET /api/admin/sensor-workouts` | `items[].estimatedCalories`、`summary.estimatedCalories`、`summary.todayEstimatedCalories` | 明细、筛选全集累计、筛选全集当日 |
| `GET /api/admin/sensor-workouts/{id}` | `estimatedCalories` | 管理员训练详情 |

以上字段均为 JSON 数字，单位 kcal；空记录返回 0，不返回未知值或字符串。
`sensor/stats` 及所有单次会话还返回 `calorieWeightKg`（数值）和 `calorieWeightDefaulted`（布尔值），
分别说明当前使用的估算体重及是否使用默认值。管理员汇总可能包含多个用户，不提供单一体重字段。
用户统计按会话保存的 `user_id` 归属，包含其全部设备及解绑之前的历史，不依赖当前选择的设备。
无用户归属的会话不会计入某个用户的统计，管理员仍可查看。

“今日”沿用训练时长的 **Asia/Shanghai 会话开始日**口径：北京时间零点包含、次日零点排除。
跨午夜的整个会话计入开始日，不按日内分摊；`time_valid = 0` 的会话只计累计、不计今日。
管理员两个 summary 字段与列表使用相同的用户、设备、传感器、状态、搜索词及日期筛选，并累计所有分页；
`todayEstimatedCalories` 再与今日范围取交集，筛选只包含历史日期时为 0。

## 验证

`SensorCaloriesTest` 使用 H2 的 MySQL 兼容模式执行生产 MyBatis SQL，并覆盖计算示例、频率封顶、
异常输入、V3/V4 时长来源、逐会话舍入、北京时间边界、未校时时间、历史归属、管理员筛选与分页全集汇总，
以及保存体重后历史会话、详情、列表与汇总的一致重估。
此测试使用内存数据库，不读取部署数据库配置。
