# ARVELLO sensor integration

## Implemented behavior

The existing `devices` table represents beds/equipment. `sensor_devices` represents ESP32 units.
User ownership remains in `user_device_selections`. Hardware bindings and sensor workouts are separate
from course playback activity. The profile now labels the existing minutes as viewing time; the API adds
`watchMinutes` while retaining `totalMinutes` for existing clients. Historical course totals keep their
existing calculation, including the fallback for older completed course records.

A sensor workout starts on the first received valid `moving=true`. Every subsequent motion report
updates `last_motion_at`; still reports update counts but do not extend the elapsed time. At 180 seconds
without motion, the session is completed with `ended_at=last_motion_at`. A scheduler runs every 10 seconds,
and ingestion also checks expiry before accepting a new motion report. Pauses shorter than 180 seconds
belong to the same session. End-of-session waiting is excluded. Duration is last motion minus first motion.
Values are stored as UTC `DATETIME(3)` and returned as ISO UTC timestamps.

Latest state is updated once per accepted report. Historical storage contains workout sessions, not
every accelerometer sample. Repeated `(deviceId,bootId,sequence)` uploads are acknowledged idempotently.
Old boot uploads are rejected; a reboot closes the prior session. Counter resets require a new bootId.
The first reading of a new boot establishes a baseline: counts completed before that reading cannot
be reliably attributed to a server-side training session. Continuous online use reports motion before
the first counted repetition, preserving the first repetition in that session.

## Deployment order

1. Back up the existing business database and execute `database/30-sensor-iot.sql`, followed by
   `database/31-sensor-keyless.sql` in that database. The latter makes legacy hash columns nullable;
   existing rows and hashes are preserved, but the keyless service does not use the hashes.
   The application does not automatically apply migrations. Do not run the integration-test fixture SQL
   against production. The additions are also appended to `01-schema.sql` for schema maintenance.
2. Build and deploy the existing Spring Boot server. Keep proxy paths intact under `/api/`.
3. Devices register automatically on their first upload or binding request. No key provisioning is needed.
4. Compile and flash the same firmware to each ESP32 of the supported board model.
5. Build the mini-program and test BLE on a real Android/iOS device. Browser preview cannot use WeChat BLE.

The migrations were applied to the configured hqc_plt database on 2026-09-07 after backups.
Backend deployment, flashing and physical hardware acceptance have not been performed.

## APIs

| Method and path | Authentication | Purpose |
| --- | --- | --- |
| GET `/api/v1/sensor/health` | None | Service health |
| POST `/api/v1/sensor/readings` | No device authentication; X-Device-Id identifies the device | Auto-register, persist latest status, update session, return 202 |
| POST `/api/v1/sensor/claim-confirm` | X-Device-Id + one-use challenge | Confirm a BLE-delivered binding request |
| POST `/api/admin/sensors` | Admin Bearer token | Optional idempotent registration; return public ID and label, no secrets |
| POST `/api/v1/device-bed-bindings/challenges` | User Bearer token | Check ownership and device ID, issue 120-second challenge |
| POST `/api/v1/device-bed-bindings` | User Bearer token | Consume device-confirmed challenge and bind |
| DELETE `/api/v1/device-bed-bindings/{id}` | User Bearer token | Unbind, close session, retain history |
| GET `/api/v1/beds/{bedSn}/sensor/latest` | Owning user | Latest status and latest session |
| GET `/api/v1/beds/{bedSn}/sensor/sessions?before={id}` | Owning user | History, 20 items and nextCursor |

Registration is automatic. The optional administrator registration body remains
`{"deviceId":"ARVELLO-80A5419205D4"}` and returns public metadata only. Plain device IDs and public
`ARVELLO:DEVICE:AVS-80A5-4192-05D4` labels can optionally restrict which nearby sensor is selected.
Users can leave the label field empty and connect directly through BLE.

The device upload key has been removed at the user's request. Knowing a device ID is sufficient to
impersonate uploads. TLS still verifies the server certificate and protects transport, but does not
authenticate the device. The one-use BLE binding exchange coordinates setup; it is not cryptographic
proof of hardware possession without a device credential. User login, bed ownership and uniqueness
checks remain enforced. There is no permanent secret to compile or flash per device.

Challenge body: `{"deviceId":"...","deviceCode":"...","bedSn":"..."}`.
The response contains `challengeId`. Mini-program sends `prove_binding` over BLE; ESP32 posts
`{"challengeId":"..."}` to `claim-confirm`, then notifies `confirmed`. The mini-program submits the same
`challengeId` to the binding endpoint. Database unique constraints enforce one active sensor per bed and
one active bed per sensor. Binding creation locks the user, bed and sensor and rechecks ownership.

Upload example (new bootId is required):

```json
{
  "schemaVersion": 3,
  "deviceId": "ARVELLO-80A5419205D4",
  "bootId": "66cb62365e2f4757b4c4117a8c759e2c",
  "sequence": 1,
  "uptimeMs": 5000,
  "sensorOk": true,
  "moving": false,
  "standby": false,
  "countType": "continuous_cycle",
  "repetitionCount": 0,
  "motionAxis": "X",
  "motionAxisG": 0.01,
  "accelG": [0, 0, 1],
  "gyroDps": [0, 0, 0],
  "activityG": 0.01
}
```

The response uses HTTP 202 with `ok`, `deviceId`, `receivedAt`. Business errors retain the existing
sensor `{ok:false,code,message,requestId}` format. Unknown reading fields are ignored. Failed sensor readings
must use `sensorOk=false,moving=null`; acceleration/gyro fields may be omitted in that case.

## UI

Open My Devices, choose a bed, then Sensor / Device Training. The dedicated page shows binding,
motion status, training elapsed time, repetition count and paginated history. Unbound beds retain their
history. Reading and polling stop when the page is hidden. The BLE flow validates the returned permanent
ID against an optional label, supports 20-byte UTF-8 fragments, WiFi failure/retry and network reprovisioning.

More than 10 seconds without data means offline, not proof of standby. A last `standby=true` reading
is displayed as "last state: standby", since a subsequent power loss during sleep is not observable.
Hardware shuts WiFi down after 15 minutes without detected activity. This is separate from session expiry.

## Limits and hardware acceptance

- Server timestamps approximate observed motion. Network delay, dropped reports and offline movement
  cannot be reconstructed as accurate start/end timestamps from the current protocol.
- MPU6500 thresholds classify carriage movement; they do not verify exercise quality or distinguish
  every accidental movement. Confirm counting accuracy and the first/last motion on the actual bed.
- NVS saves count at 30-second checkpoints and before standby/manual reset. Sudden power loss can lose
  increments since the last checkpoint. No claim of zero-loss persistence is made.
- No `activeDurationMs` is needed for the approved elapsed-span definition.
- The current version includes reprovisioning and unbinding; factory-reset and raw
  sensor analytics are not part of this release.
- Test wrong password, Bluetooth off/disconnect, Chinese SSID, two beds, duplicate binding, unplugging,
  reconnection, 179-second pause, 180-second pause, 15-minute standby, and wake on movement on real hardware.

## Administrator API

All routes below require an administrator login through the existing admin interceptor:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/admin/sensors` | Paginated sensor list; query/state/binding/bedId/userId filters |
| GET | `/api/admin/sensors/{id}` | Current binding, status and latest telemetry |
| GET | `/api/admin/sensors/{id}/bindings` | Paginated binding history |
| PUT | `/api/admin/sensors/{id}/status` | Set status to ACTIVE or DISABLED |
| DELETE | `/api/admin/sensors/{id}/bindings/{bindingId}` | Unbind the exact current binding; stale binding returns 409 |
| GET | `/api/admin/sensor-workouts` | Paginated training sessions and summary across all filtered rows |
| GET | `/api/admin/sensor-workouts/{id}` | Single session detail, including after completion |

Workout filters: query, status, sensorId, bedId, userId, from and to (inclusive Beijing dates,
YYYY-MM-DD). Pagination uses page/pageSize, default 1/20. Stored times remain UTC.
Session duration ends at last motion, excluding the trailing idle timeout. Counts are session deltas;
the sensor detail count is the device cumulative value. Latest telemetry is not raw sample history.

Disable and unbind close active sessions at last motion, retain history, and write an admin audit in the
same transaction. Disable retains the binding. No additional migration is required beyond sensor migrations 30/31.
The admin interface separates device training from course viewing and links from users and beds.

## Verification commands

Backend Java test classes were removed at the project owner's request. Maven no longer runs the
sensor authentication, training-window or database regression tests. Compile/package validation
should run in a separate checkout when the IDE backend is running; do not clean its active target directory.
Client protocol tests: Node 22 `--test we-plt/scripts/sensor-protocol.test.mjs`.
Responsive browser tests: `we-plt/scripts/capture-sensor.mjs` against the local H5 preview on port 5194,
with mocked sensor API responses. These screenshots do not prove physical BLE connectivity.
