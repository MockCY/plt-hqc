# Course Training Configuration

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
        { "side": "左侧", "durationSeconds": 60, "repetitions": 8, "springCount": 2 },
        { "side": "右侧", "durationSeconds": 60, "repetitions": 8, "springCount": 3 }
      ]
    }
  ]
}
```

The public course detail returns these sets alongside each library exercise's
video, cover, cue, safety tip, instruction audio, and background music.
Training time comes from the set configuration; the video loops independently.
`springCount` means the number of spring groups, not pounds.
Zero repetitions selects a timed set. Zero springs means zero spring groups.

Limits: 100 exercises per course, 50 sets per exercise, 1-3600 seconds per set,
0-999 repetitions, and 0-12 spring groups. Sides are 双侧, 左侧, or 右侧.
Older clients may still send `exerciseIds`; existing set settings are preserved
for those actions. Legacy courses without set data use their existing duration
(60 seconds when absent), with zero repetition and spring recommendations.
Configure their recommendations in the editor before publishing updated content.

## Training Progress

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

Builds and automated tests passed. Browser visual and interaction verification
was blocked: the browser tools rejected the local preview URL, and the desktop
automation tool stopped because it could not reliably determine the browser URL.
Manual mobile verification is still required before release.
