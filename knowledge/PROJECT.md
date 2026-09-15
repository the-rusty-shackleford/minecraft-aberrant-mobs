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

Phase 2 (in progress): the body -- `Trail`, `Undulation`, `ChainPose`, `LegGait`, `Body`
(pure, partitioned); the entity's trail and wave per side, the renderer laying the chain
from the interpolated head; `AberrantPart` per segment on the server, placed on the chain
each tick; `Carapace` and the synced crack, damage routed by segment, the glow drawn on
the cracked segment; `RigStore` reads the model from the jar for the server. Still to do
in phase 2: `Legs` (feet planted on the world, most down at all times) and the authored
`Clip`s with `Animator`.

## How it is verified

`./gradlew test` (46 JUnit tests, the reader proved against the saved file),
`runGameTestServer` (4 gametests: the profile and the creature, the parts along the body
after a walk, only the crack takes a blow, a scripted walk), `runPhotoBooth` (7 checks:
side, quarter, face, a walking strip, from above). The booth world is normal difficulty
with spawning off: a monster is discarded in peaceful.

## Decisions

See `decisions/`.

## Next

Phase 2's rest: planted feet and the clips. Then phase 3, crawl and dig.
