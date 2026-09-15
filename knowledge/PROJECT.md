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
onto the ledge's face, bores into rock when it may dig, holds for the dig, takes the
nearest other face a wish off its face lies along (the playtest found it held on a wall
four blocks from the player, wanting what was out on the floor), falls and attaches
when it has nothing; `Rules(clearance, bore, lookahead)`); `Tunnel` (the
section within the bore's radius of the head's run, rock only, never beside hard or
fluid); `Burrow` (A* over cells, air 1, unsupported air 4, rock 5 hunting or 14
stalking, hard and fluid never nor rock within the body's margin of them, air by a face
within the body's hold of it cheap -- both from the crawl's rules, D-0011 -- a
4000-expansion budget as a backstop; its bound charges the rock depth about the target, `rockDepth`, and a
per-search `Table` reads each cell once and holds the search's state in primitive
arrays with a heap of its own -- before these, 2026-09-15, a hunt to a point ten blocks
into a hill never planned within the budget, see D-0010); `Leap`
(a launch velocity landing exactly under the game's integration); `Burrow.planNearest`
gives the way to the nearest reachable cell when the target is cut off, and the entity
never falls back to a straight line (the playtest found it held on a pool for good). The
entity cuts rock only while the way itself runs through rock within a strike's reach, so
a way that climbs a wall does not dig its foot (the booth found it cutting the foot of a
hill its way went over, then standing blocked for good with no wall left to take) while
in its own bore, whose next cells a strike has already cut, it keeps cutting the bends
wide (the tunnel test's "wide" check caught the stricter "next cell is rock"); a strike
cuts the crawl's section along the heading plus the tube round the way's next cells laid
into the head's face (`Tunnel.along`, nearest first, `Tunnel.cuttable` keeping rock by
water), since a bend cut along the heading alone left its outer floor cell standing and
the head stepped up onto it and jammed under its own roof; `LevelCells` reads an
unloaded chunk as hard, so a plan never loads one; and a
refused crawl brings the
next plan forward to within five ticks, never to the next tick. In
main: the entity
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
ticked, ended; the mode synced; the memory in NBT). The Face-Stealer's tree: roam, prowl, stalk, hunt, flee. The playtest taught the stalk three more lines: a
stalker seen and blocked (its tunnel a dead end) turns to the hunt, one watched for two
hundred ticks does too, and one that has grabbed its prey from behind (the ambush the
stalk was made for) hands over to the hunt, where the bite lives.

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

Phase 6 (done): habitat, loot, sounds. `domain/Habitat` (`Rules(yMin, yMax, maxLight,
exclusion, cap)`; `deepAndDark`; `siteInWall` crosses the cave's air then needs seven of
rock ending in a pocket of rock five across, `POCKET_RADIUS`; six and three by three
before 1.2.0). `SpawnRules` on `RegisterSpawnPlacementsEvent`
(NO_RESTRICTIONS, REPLACE): natural spawns need a profile with a habitat fitting the
site's depth and light, not deep dark, a wall thick enough, none within the exclusion,
fewer than the cap; the biome modifier adds the type to `#is_overworld` (weight 2).
`Aberrant.finalizeSpawn` picks the fitting profile by weight, bores the pocket
(`DigWorld`, quiet) and moves there; persists on stalk/hunt; drops the profile's loot
table (`data/aberrantmobs/loot_table/creature/face_stealer.json`: 12-18 chitin +
looting, one cracked carapace) plus, 15 % of the time, the Stolen Face carrying the
victim's profile (`StolenFaceItem`); 50 XP; the death clip runs its forty ticks before
the body goes. Items chitin, cracked_carapace, stolen_face with icons drawn by
`devtools/art/build.py`. Sounds cut from freesound CC0 recordings by the same script
(`devtools/art/sounds/SOURCES.md`): skitter (two takes) every six ticks under way at 0.35
quiet / 1.0 loud, breath when still, and on the cues click, hiss, screech, grab, bite,
crack, dig loud/quiet; death as the death sound; the clang stays vanilla's anvil.

Phase 7 (built; the plan's phases A and B, with the edge wrap of C): the chitin armour.
`domain/frame`: `Gravity` (six axis directions), `Frame` (right, up, forward per gravity;
local/world; the box as an axis swap with the feet on the gravity face; the eye; the
rotation; a byte code), `CameraAngles` (yaw/pitch/roll to a quaternion and back, gimbal
rows included), `Transition` (into a wall when pushing at 0.02+ and the box fits; over
an edge onto the ledge's face; release to the world's down by the least way out), `Blend`
(an eased slerp over six ticks). Main: `ModContent` chitin material (netherite's plating,
mended with chitin) and four pieces (recipes from chitin, the chestplate with the cracked
carapace), `wallwalk/FrameCarrier` (on Player by mixin: one synced byte for the frame,
the last wall and move tried, the blend), `WallWalk` (the rule on `PlayerTickEvent.Post`
on the server and the local client alike: wearing the full set and not in water, flying,
riding, gliding, asleep or a spectator; into-wall, over-edge, let-go), `WallWalkMove`
(the game's move with the frame's up as its vertical: axis-ordered collision, a step up
along the frame's up, flags, ground, fall and velocity in the frame, the wall recorded
for the rule). Mixins: Entity (box, eye, eye position, view vector, move, supporting
block, on-pos), LivingEntity (travel's move calls turned local to world), Player (the
carrier), ServerPlayer (the fall check along the frame), LocalPlayer (no nudge out of
edges), Camera (the eye), LivingEntityRenderer (the model stands along the frame); the
camera angles event composes the frame's blend with the look and hands back yaw, pitch
and roll. Of the plan's phase C: a knockback (given in the world's horizontal) and an
entity push land in the wearer's frame (`LivingEntityMixin.knockback`,
`EntityMixin.push`); not built: block placement facing on a wall, the shadow and
nameplate, the third-person back camera (see Next). Eye contact is a stare, not a
glance: `domain/Gaze` counts eight of the last ten ticks.

The playtest (`./gradlew runPlaytest`, `gametest/Playtest`): a real world (seed
20260915, normal generation), the survival player set down in a dark cave below y -8 and
blessed at the miracle's door, a Face-Stealer come into the world on its own in the rock
by another cave thirty to fifty blocks off; the player breaks a block at tick 80; the
server logs mode, verb, distance, sight and blocks dug every ten ticks for a thousand,
a frame every eighty with the player looking the creature's way; verdicts: it came, it
prowled at the noise, it dug beyond its pocket, it came within sixteen, it stalked or
hunted.

## How it is verified

`./gradlew test` (116 JUnit tests, the reader proved against the saved file),
`runGameTestServer` (21 gametests: the profile and the creature, the parts along the body
after a walk, only the crack takes a blow, most feet on the floor mid-walk, a clip's
cues on their ticks, a scripted walk, floor-wall-ceiling, a coherent tunnel round
bedrock to a target, over a thick wall without cutting it, a pounce landing where aimed, a distant step → prowl toward a vague
bearing and a near noise exact, a block break heard through the world, a player seen
underground → stalk out of view → eye contact → hunt, the grab holds at the maw and the
bite devours and takes the face, a blessed player survives and is let go and it flees,
hunting in sight it coils then pounces, the spawn rules refuse the lit surface and
accept a dark chimney by thick rock where it bores its pocket, the creative tab shows the
mod and its egg or a summon puts the creature whole on the lit surface, killed it drops
chitin and its plate and experience, a full set walked into a wall takes it and climbs and lets
go without the helmet and lands, water lets go and the undressed stop at the wall),
`runPhotoBooth` (37 checks: every sound event resolves, side,
quarter, face, a walking strip, from above, each clip playing on the client at its key
frames, drawn after the death, the stolen face known to the client, the client held at
the maw, on the wall, dug in, past an oak and past a lone pillar upright, the client in
the chitin set standing on a wall from its own eyes and from behind). The booth world is
normal difficulty with spawning off: a
monster is discarded in peaceful.

## Decisions

See `decisions/`.

## Releases

1.0.0 (2026-09-15): the first, on Rusty's go ("the server is private anyway"), all seven
phases, into pack 1.33.0 on the Mod Hub; the repo public at
`github.com/the-rusty-shackleford/minecraft-aberrant-mobs` from that day. Rusty's own
vetting of the creature in play is still to come; what it finds is the next version.

1.1.0 (2026-09-15, pack 1.34.0): Rusty asked for a page in the creative inventory. The
`aberrant_mobs` tab (icon: the Stolen Face) shows the drops, the armour, then one
`aberrant_spawn_egg` per creature profile loaded, named after its creature
(`AberrantEggItem`: the profile rides the egg's entity data); the same items join the
vanilla Ingredients, Combat and Spawn Eggs pages. Two things the egg exposed and fixed:
an egg's entity data lands *after* `finalizeSpawn`, and on a lit surface no habitat fits,
so the creature was discarded before its profile arrived -- a non-wild spawn (egg,
command, spawner) now takes what fits, else the first profile known, and never discards;
and a creature given only a profile (the documented `/summon ... {Profile:...}`, or an
egg) kept the default 20 health, since only `Aberrant.create` applied the stats --
`takeProfile` now applies them whenever a profile arrives on a creature that lacks them,
while a saved creature (its attributes and crack read first) keeps its health. Gametest:
the tab's contents and names, the egg on the surface at 84 health, the summon likewise.
Also in 1.1.0, from Rusty's first evening with 1.0.0 on the server: (a) the game's "Press
Shift to dismount" hint no longer shows or is narrated when the vehicle is a creature
(`ClientPacketListenerMixin`, two redirects in the passengers packet handler) -- the grip
refuses the dismount, so the hint was a lie; (b) it went in circles, walking back over
itself: a target inside its turning circle (speed over turn rate, about a block) was
orbited forever -- the crawl now scales the step by the cosine of what is left of the
turn, so a wish beside or behind it is pivoted to (`aWishBesideOrBehindItIsPivotedToNotOrbited`);
(c) it was too easy to lose: sight 40 -> 64, memory 600 -> 2400 ticks, and the ears now
follow the prey it knew when sight is lost (`Hearing.estimateFrom` by the player's UUID,
`Aberrant.noteTarget`; the last sound within the memory becomes `target.last_pos`, exact
within 24 blocks, erred beyond) -- before, within sight it knew you exactly whether or not
it could see you, and beyond sight it forgot you in thirty seconds; (d) more and lower
sounds: a screech when the hunt begins (1.6, heard ~25 blocks), a hiss when it starts to
stalk, a click or hiss about every three and a half seconds while stalking or hunting,
the skitter louder hunting (1.3) and less quiet stalking (0.5), breath every 90 ticks,
lower pitches for the dread cues. New recordings (a chitter, a low rumble) are the next
step if Rusty wants scarier still; every sound stays a CC0 recording. The prowl gametest
asserted the way's first-leg heading and flaked under the new planner's tie-breaking; it
now asserts where the crawl is bound. Rusty also wants the creature 1.5x bigger: that is
1.2.0, on its own, since it touches every body-tied number and needs the booth looked at.

1.2.0 (built 2026-09-15 evening, unreleased -- Rusty's go is still to come): the creature
one and a half times bigger, as Rusty asked. The profile's scale is 0.09375 (a sixteenth and
a half a unit), the head's box 3.75, the segments' 3.3, the eye at 2.4, the writhe and the
gait's measures in blocks half again, and every Java number tied to the body now follows from
the profile or the crawl's rules rather than sitting beside them (D-0011): the feet's stride,
reach, lift and lead moved into `rig.gait`; the strike's budget is a straight section's worth
(`Crawl.strikeBudget`, 82); the way keeps the bore's reach from bedrock and water
(`Crawl.Rules.margin`, two cells, passed to `Burrow`); a waypoint or target under the head's
feet counts as reached (`Crawl.reaches`) -- at the new clearance of 2.18 the old reach of 1.5
never arrived and the head stood blocked, replanning; the maw is in model units scaled at use;
the pincers reach 4.5; the spawn pocket is five across, seven into the wall; the shadow is the
profile's width. The mind's grab thresholds scale (3.75 hunting, 6 stalking from behind); the
speed does not (0.45 a tick: the tuning session's number). Gametests: a long arena (31 wide)
for the body's length, the geometry and timings re-derived (the climb is on the wall by tick
32 and under the ceiling by 65; the spawn chimney at x = 5 so a seven-deep site and its
pocket fit the template); the booth's cameras half again further, its wall fifteen high and
five thick, its hill eighteen high and twenty-five wide, the dig judged by more than a
strike's worth cut and the head inside the face. Three things the bigger body exposed and
the gametests caught, all fixed in the domain with tests: a head riding 2.18 off a wall,
half a block under its top, read its wish toward the way's next cell over the edge as
"into the wall" and stood blocked (a wish into the face with no other face in reach is now
followed along whatever of it lies in the face); a leap landed when a face was within one
tick's travel plus a hair, which at the old clearance was the riding height and at the new
one overshot the aim by 3.5 (it lands at its clearance plus this tick's approach); and the
planner rated an air cell cheap only when a solid cell touched it, so in a five-by-four
bore the way ran along the ceiling row and the head stood wishing upward (an air cell is
cheap within the body's hold of a face). And one the booth's frame showed, at either size:
a held player's eyes sat inside the head's front cube, since nfx's maw point is the maw
cube's centre; the held one now hangs against the mask's front at the maw's height, half
their own width forward, in the pincers. Rusty's first surface test at this size (stuck on
trees, flipped over, panting like a dog) was one mechanism, reproduced in the booth on an oak
and a one-block pillar and fixed as D-0012: the crawl climbed a post the way went round,
went over it and hung refused on the far side wanting the ground 2.8 blocks below; now it
climbs only where the way rises and lets go of a wall or a ceiling it cannot follow the way
from, falling to the floor; the breath is a low one at odds of one in three hundred a tick
while still. The oak and the pillar are booth scenes now (37 checks). The 1.2.0 queue also
holds (b) the item and armour art redrawn in vanilla's family and (c) scarier recordings,
the breath's among them.

## Next

Rusty's vetting of the whole in play, now at the new size: the booth's frames first, then the
server. Then (b) and (c) of the 1.2.0 queue. Open: the
armour's remaining phase C (block placement facing on a wall, the shadow and nameplate,
explosions into the frame, and the third-person back camera: on a wall it backs off
down into the ground and the game's clip parks it on the surface, so the booth uses the
front camera); the wall-walk under Iris in the booth from a second client; whatever the
playtest's frames say about the dread.

The planner's budget question (2026-09-15 morning: a hunt to a point ten blocks into
rock with open air about found no way within 4000 expansions, and a budget-exhausted
plan cost 7-44 ms in the game) is settled by D-0010: the search got cheaper, the budget
did not grow. The booth's hill is shaped so that boring in through its face is the
cheapest way (twelve high, seventeen wide); before that day the dig had passed on the
straight-line fallback the playtest fixes removed, never on the planner. Each plan's
size and time is still logged at DEBUG (`planned a way of N cells in M us`, in
`debug.log`) for the next time a number is wanted.
