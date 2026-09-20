# Manual spawning and hearing — local 1.4.0

Session verification on 2026-09-20. This is an unreleased local candidate.

- The pre-fix server run failed four of five new manual-spawn tests: clean profile
  summon names were absent and surface egg use reported success. Valid underground
  egg use passed.
- Eight of nine new hearing tests failed against the old model.
- The final source passed **136 JUnit tests and 57 real-server GameTests** via
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-watch-fs test runGameTestServer jar`.
- Actual item use, command parsing/completion, permission checks, legacy NBT syntax,
  valid underground lifetime and real block destruction 500 blocks away are covered.
- The full suite exposed six existing fixture failures. A temporary
  `EntityLeaveLevelEvent` stack trace identified vanilla `Mob.checkDespawn` discarding
  freshly created fixtures before their first tick when unrelated mock players were
  far away. Fixture creatures now explicitly persist; production despawn behavior
  is unchanged. The diagnostic subscriber was removed before the final passing run.

No client booth was run for these behavior/text changes. Block-targeted egg use is
covered; the source-fluid branch reuses the same validation but has not received a
separate runtime regression case. Natural encounter frequency across a multiplayer
world and long-distance pursuit through arbitrary terrain are not measured by these
tests. Hearing needs an existing loaded creature and ticking terrain to approach.

No tag, push, pack update or deployment is included in this work.
