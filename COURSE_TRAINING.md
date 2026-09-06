# Course Training Configuration

## Training Tags And Display Advice

Before deploying the tags/advice changes, apply `database/28-course-training-tags.sql`.
It adds nullable `courses.training_tags` and `course_exercises.recommended_plays`
without changing existing groups or assigning sample values. Applied to the
configured `hqc_plt` database on 2026-09-06 to fix the reported missing-column
error. Both column definitions were verified. The running local backend's
`GET /api/courses/1` returned HTTP 200 with both new fields after migration.

`tools/CourseTrainingTagsMigration.java` checks these fields using the configured
datasource and adds only missing columns with `--apply`. It validates existing
types and nullability, and does not rewrite course data. Run it from `server`
with MySQL Connector/J and SnakeYAML on the Java classpath. New database instances
still need migration 28 before using the updated backend.

Course Management accepts `trainingTags` as newline-separated labels (up to 200
characters total). Each entry in `exercises` accepts optional `recommendedPlays`
(integer 1-999). The course editor labels it Suggested Count. It is display-only:
three suggested plays can coexist with five configured training groups, as
explicitly requested. It does not drive advancement, group counts or session
signatures. Empty advice/tags are not shown in the mini-app. Clearing a tag field
with an empty string removes the labels; omitted tags from older clients preserve
the current value on update. Action suggestions can be cleared with null.

The mini-app displays configured advice alongside purpose labels in one tag row.
The group list and overall completion indicator retain actual progress. Side,
spring count and within-group repetitions remain stored in `training_sets`, but
are no longer displayed in the training workspace.

Verified the mapper write/read/public-API contract in an H2 integration test
(explicit JSON conversion models MySQL JSON assignment), validation bounds,
unchanged saved progress, and a real preview administrator form save followed by
mini-app reload. The isolated preview runs at ports 5196 and 5197; it is not a
production backend connection. Release backend, administrator and mini-app after
the migration. WeChat native device verification remains pending.

## Database Update

Before starting the updated backend against an existing database, run
`database/26-course-training.sql` with the usual database migration account.
The script is repeatable and adds `courses.introduction`, `courses.audience`,
and `course_exercises.training_sets`. It does not change existing content.
Applied to the configured remote `hqc_plt` database on 2026-09-05 at the user's
request. All three nullable columns and removal of the temporary migration
procedure were verified successfully.

New schemas include these fields in `database/01-schema.sql`. Existing exercise
guidance fields still require the existing `24-exercise-guidance.sql` migration.

## Course API

Admin course create/update accepts `introduction`, `audience`, and ordered
`exercises`. Each exercise references an existing library entry:

```json
{
  "exercises": [
    {
      "exerciseId": 1,
      "sets": [
        { "side": "左侧", "repetitions": 8, "springCount": 2 },
        { "side": "右侧", "repetitions": 8, "springCount": 3 }
      ]
    }
  ]
}
```

The public course detail returns these sets alongside each library exercise's
video, cover, cue, safety tip, instruction audio, and background music.
Each set completes when its exercise video emits `ended`. The video does not
loop; the next set starts a fresh playback. The final set marks the action
complete and waits for the user to start the next action. There is no manual
complete-set command or duration-based completion. Pausing, replaying or seeking
does not itself complete a set; reaching the video's end does.
The course's administrator-configured `durationMinutes` is an estimate only.
Actual active seconds are still recorded independently for workout history.
`springCount` means the number of spring groups, not pounds.
Repetitions are administrator-configured targets, not automatically counted
body movements. Zero springs means zero spring groups.

Limits: 100 exercises per course, 50 sets per exercise,
1-999 repetitions, and 0-12 spring groups. Sides are 双侧, 左侧, or 右侧.
Older clients may still send `exerciseIds`; existing set settings are preserved
for those actions, but missing configuration or invalid repetition counts must
be corrected in the administrator editor. Publishing requires a published
exercise with a configured video for every action. The nullable legacy
`durationSeconds` field remains readable for compatibility but no longer drives
training; new administrator saves omit it. No database schema migration is
needed for this change. Existing records with zero repetitions remain readable
and display an unconfigured state until an administrator enters a valid count.
Legacy timer-session progress is invalidated by the new session signature;
saved workout history is retained. Custom-course actions use the action library's
suggested group count; unconfigured repetitions/sides are not invented.

Guidance is not frontend sample copy: `exercises.cue` and `exercises.safety_tip`
come from Action Management. Course introduction, audience, and estimated minutes
come from Course Management. `course_exercises.training_sets` stores each group's
side, repetitions and springs. `admin/scripts/preview-course-training.mjs` is a
separate in-memory preview fixture, not the production API or database.
The training workspace's Course Introduction region shows only course
introduction (falling back to summary) and audience. Action cues and safety tips
remain in action details and are not mixed into this course-level region.

## Active Training Statistics

Apply `database/27-training-activity.sql` before deploying this version of the
backend. This repeatable migration adds `training_activity`; it does not modify
existing workout records. Applied to the configured remote `hqc_plt` database on
2026-09-06 at the user's request using `tools/TrainingActivityMigration.java`.
All six columns, the five-column primary key, and the cascading user foreign key
were verified successfully. The backend and mini-app still need deployment for
the new statistics behavior to become available online.

The mini-app sends cumulative active seconds to `POST /api/workout-records/activity`
every 15 seconds, and flushes pending values when pausing, leaving, or opening
records. Both course timers and exercise video playback count before completion.
Pauses, buffering, background time, and seeking do not add training time.
Pending uploads persist on the device per account and retry on the next sync.
The server takes the maximum for each account, activity, session start, and date,
so retrying or receiving older requests cannot inflate the total.

`totalMinutes` floors the sum of active seconds divided by 60. Fractions of a
minute accumulate across sessions. `trainingDays` counts distinct dates with any
active training; `consecutiveDays` uses those dates starting today or yesterday.
New activity is split at midnight in Asia/Shanghai. `completedCount` remains a
count of saved workouts at least 80% complete, for existing clients and screens.
The records summary uses `trainingDays` and the label 累计训练天数.

Legacy workout durations remain included until the same course/session has active
time uploads. A completed workout matching an uploaded session is excluded from
the time sum to prevent double counting. The list still shows saved completed
workouts; unfinished training contributes to the summary. Historical viewing time
that was never uploaded cannot be reconstructed.

Validation: `node --test we-plt/scripts/course-training.test.mjs
we-plt/scripts/training-activity.test.mjs`, backend `WorkoutActivityTest`, Web and
WeChat production builds. `we-plt/scripts/capture-training-activity.mjs` exercises
partial training in the isolated preview using ports 5194 and 5195.

## Saved Course Progress

Progress is saved synchronously on the current device, separated by user ID,
course type, and course ID. It includes each action's set, elapsed seconds,
completion state, video position, actual active duration, and transition state.
Pausing, exiting, hiding the app, and page unload stop the timer and save progress.
Selecting an action starts or resumes that action without completing earlier ones.
After the last set, the next action requires explicit confirmation. If an earlier
action was skipped, it remains available as the next unfinished action.

Changing exercise order, videos, or set configuration invalidates old progress.
The local progress is cleared only after a completed workout is saved successfully.
Progress does not sync across devices and is lost when app storage is cleared.

## Local Preview And Validation

- Mini-app preview: `http://127.0.0.1:5190/?tab=course`.
- Admin preview: `http://127.0.0.1:5191/#/courses`.
- Run `admin/scripts/preview-course-training.mjs` from the admin directory using Node 22+.
- Preview data is held in memory and shared between the two preview endpoints.
  Any username/password can sign into this isolated preview. It does not proxy to
  the production API. Restarting the preview resets the course fixture.
- Frontend state tests: `node --test we-plt/scripts/course-training.test.mjs` from the workspace root.
- Backend tests: `mvnw.cmd test` with JDK 21 from the server directory.

On 2026-09-06, all 12 frontend tests and 4 backend activity tests passed; Web and
WeChat production builds passed. Playwright verified that leaving an unfinished
course after 180 active seconds adds 3 minutes, leaves completedCount at zero,
and counts one training day. Paused time did not increase the total. Screenshots
at widths 320, 393, and 1280 were inspected with no summary text overflow or page
errors. Real-device WeChat playback lifecycle testing remains a release check.
