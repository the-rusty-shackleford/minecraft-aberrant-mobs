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

Phase 2 (done): the body -- `Trail`, `Undulation`, `ChainPose`, `LegGait`, `Body`
(pure, partitioned); the entity's trail and wave per side, the renderer laying the chain
from the interpolated head; `AberrantPart` per segment on the server, placed on the chain
each tick; `Carapace` and the synced crack, damage routed by segment, the glow drawn on
the cracked segment; `RigStore` reads the model from the jar for the server. Then the
feet: `Cells` (the one way the domain reads blocks; `LevelCells` in main), `Legs` (a
foot per leg planted on the level, stepped in the gait's metachronal order with at most
40 % swinging and a pair never together, swings shorter and strides longer with speed,
the surface found by a bisected cast on floor, wall or ceiling), `Body` deriving each
leg's segment, hip and rest foot from the file's pivots and cubes, the renderer aiming
each leg at its foot (`Legs.aim`, a lever: lift in the leg's plane then yaw). And the
clips: `Clip` (keys slerped, cues at ticks), `Animator` (one clip over the body's pose),
`FaceStealerClips` (coil, pounce, strike, grab, bite, flinch, death, with their cues);
the server starts a clip by name over synced data, both sides advance, the server acts
on cues (recorded for now; sounds and the dig's blocks hang on them later).

Phase 3 (done): crawl and dig. `Cell`; `Cells.face` (a bisected cast to a face);
`Crawl` (the head an axis point held its clearance off the axis face it clings to:
turns within the face at 25 degrees a tick, climbs a wall it meets, wraps over an edge
onto the ledge's face, bores into rock when it may dig, holds for the dig, falls and
attaches when it has nothing; `Rules(clearance, bore, lookahead)`); `Tunnel` (the
section within the bore's radius of the head's run, rock only, never beside hard or
fluid); `Burrow` (A* over cells, air 1, unsupported air 4, rock 5 hunting or 14
stalking, hard and fluid and rock beside them never, a 4000-expansion budget); `Leap`
(a launch velocity landing exactly under the game's integration). In main: the entity
runs on the crawl with no gravity, no physics, no pushing, its box centred on the axis,
its face synced as its up; the scripted walk crawls on along whatever face after a
corner; `setCrawlTarget` follows a burrowed way, replanned every 20 ticks; the strike
clip's cue cuts the readied section (`DigWorld`, loud or quiet); `pounce` flies by the
leap and lands on the first face it flies into; `LevelCells` marks bedrock, obsidian,
block entities, `#aberrantmobs:undiggable` and the wither-immune as hard.

Phase 4 (done): mind and ears. `domain/mind`: `Senses` (a fixed vocabulary of numbers,
flags and points; absent is NaN/false/null), `Memory` (mode, timers counting to zero,
points, seed; saved), `Intent`, `Cond` (a parsed boolean grammar over senses and
timers), `Node` (select, sequence, when, act, enter, timer, wait = patience, cooldown),
`Tree`, `Mind.tick` (aged, evaluated, one entry followed), `TreeJson` (the grammar,
refusing a typo naming its path). `Hearing` (a sound per source, the loudest for its
distance wins, error = distance / 2 / loudness, exact within 24, a sideways offset
re-rolled every 600 ticks, forgotten after 2400). In main: the profile's `mind` field
(gson bridged to the domain, verbs from `Verbs`), `Verbs` (hold, wander, approach,
chase, flee, dig, climb, pounce; grab/release/bite named for phase 5), `SensesReader`
(target = nearest survival/adventure player within `sight`, in sight by the mob's own
sensing, known 600 ticks, eye contact by cones, underground = no sky), `Ears`
(`VanillaGameEvent` → sounds: step 1, sprint 2, block 6, blast 20, sneaking 0, other
mobs and its own digging nothing), the entity's `think` (senses → mind → verb begun,
ticked, ended; the mode synced; the memory in NBT). The Face-Stealer's tree: roam,
prowl, stalk, hunt, flee.

Phase 5 (done): grab, bite, the coil before the pounce, the face. The target rides the
creature at the maw (`positionRider`, never a driver; `Grip` cancels a dismount while
held; pinched on the grab clip's cue); the bite clip's cue hurts the held one by
`aberrantmobs:devoured` (a damage type tagged bypasses_armor/effects/enchantments/
shield/cooldown, no_knockback, NOT bypasses_invulnerability), a finite million through
`hurt`, so a `LivingDamageEvent.Pre` listener (Miracle Bringer) or a totem still saves
them and the creature knows (`grab.survived` → release, no_bite 600, flee); a dead
player's `GameProfile` becomes the face it wears (synced name + id, saved; the client
draws the skin's face and hat over the mask cube's front, `RigDrawer.drawQuad`). The
pounce verb plays the coil and leaps as it ends; the tree holds it with a `pouncing`
timer. Verbs grab/release/bite do their work.

## How it is verified

`./gradlew test` (90 JUnit tests, the reader proved against the saved file),
`runGameTestServer` (15 gametests: the profile and the creature, the parts along the body
after a walk, only the crack takes a blow, most feet on the floor mid-walk, a clip's
cues on their ticks, a scripted walk, floor-wall-ceiling, a coherent tunnel round
bedrock to a target, a pounce landing where aimed, a distant step → prowl toward a vague
bearing and a near noise exact, a block break heard through the world, a player seen
underground → stalk out of view → eye contact → hunt, the grab holds at the maw and the
bite devours and takes the face, a blessed player survives and is let go and it flees,
hunting in sight it coils then pounces), `runPhotoBooth` (20 checks: side,
quarter, face, a walking strip, from above, each clip playing on the client at its key
frames, drawn after the death, the stolen face known to the client, the client held at
the maw, on the wall, dug in). The booth world is normal difficulty with spawning off: a
monster is discarded in peaceful.

## Decisions

See `decisions/`.

## Next

Phase 6, habitat, loot, sounds: spawn placement in the rock beside deep caves, the
biome modifier, persistence once it knows you, the loot table (Chitin, Cracked Carapace,
Stolen Face with the profile), sounds.json from freesound CC0 on the cues, a playtest
through real caves.
