---
title: Aberrant Mobs — project
type: overview
layer: store
tags: [overview]
---
# Aberrant Mobs

## What this is

A protocol for monsters on NeoForge 1.21.1, `com.chunkworks.aberrantmobs`, repo
`minecraft-aberrant-mobs`. A creature is a datapack profile plus a Blockbench project;
the protocol owns the rig, the body, the mind (a decision tree in JSON over senses, with
verbs in Java), the world verbs and the loot. The first creature is nfx's Face-Stealer,
a centipede eleven blocks long that lives deep underground, stalks, digs, grabs and eats.
The full design, agreed with Rusty on 2026-09-14, is the plan at
`~/.claude/plans/wiggly-cuddling-adleman.md`; it is being built in seven phases.

## Shape

Vanilla Wheels' layout: `domain` (JDK only, JUnit) <- `main` <- `gametest` (the tests
and the photo booth, a mod of their own). Written to the 6.031 bar: specs on every
operation, RI and AF on every ADT, immutable values, tests partitioned by the spec.

Phase 1 (done): `CreatureProfile` codec + the `aberrantmobs:creature` datapack registry;
one entity type `Aberrant`; `domain/BbRig` reads the project into a `Rig` of bones
(`Bone`, `Pose`, `Xform`, `Quat`); `client/RigLibrary` + `Skin` + `RigDrawer` +
`AberrantRenderer` draw it at rest. The model ships as received
(`assets/aberrantmobs/aberrantmobs/model/face_stealer.bbmodel`; a copy in
`tools/reference/`).

## How it is verified

`./gradlew test` (17 JUnit tests, the reader proved against the saved file),
`runGameTestServer` (1 gametest), `runPhotoBooth` (4 checks, three frames). The booth
world is normal difficulty with spawning off: a monster is discarded in peaceful.

## Decisions

See `decisions/`.

## Next

Phase 2: the body -- `Trail`, `Undulation`, `ChainPose`, `Legs` (planted feet), `Clip` /
`Animator`; multipart segments; the carapace and its weak spot.
