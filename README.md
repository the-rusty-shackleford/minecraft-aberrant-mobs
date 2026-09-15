# Aberrant Mobs

A protocol for monsters, for NeoForge 1.21.1. A creature is a datapack entry and a
Blockbench project: this mod owns the rig that stands it up, the body that follows its
head, the mind that reads its senses through a decision tree, the crawling, digging,
stalking, grabbing and biting its verbs do, and what it drops. The first creature is the
**Face-Stealer**, nfx's centipede: eleven blocks of it, a mask for a face.

This is phase 2 of 7 (`~/.claude/plans/wiggly-cuddling-adleman.md` is the plan): the
creature exists, is sized and named by its profile, its body follows its head along a
trail and writhes, its feet stand on the world and step in a wave, its plating rings and
its crack glows, and its attack animations play on the server's say. It does not yet
crawl on its own, think, dig, or bite.

## A creature

`data/<ns>/aberrantmobs/creature/<name>.json`:

```jsonc
{
  "model": "aberrantmobs:face_stealer",      // assets/<ns>/aberrantmobs/model/<name>.bbmodel, saved as it is
  "scale": 0.0625,                            // model units to blocks (optional, a sixteenth)
  "body": {"width": 2.5, "height": 2.5, "eye_height": 1.6},   // the box the world collides with, blocks
  "stats": {"health": 84, "speed": 0.45}
}
```

Every creature is the one entity type `aberrantmobs:aberrant`; the profile id rides its
synced data. `/summon aberrantmobs:aberrant ~ ~ ~ {Profile:"aberrantmobs:face_stealer"}`.
The name is `creature.<ns>.<name>` in the lang file.

## The rig

The project is read whole (`domain/BbRig`): every group is a bone that keeps its origin
and rotation, every cube belongs to the bone of its group and is expressed relative to
the bone's pivot, and a bone stands in the model at `W(parent) * T(pivot - parent's
pivot) * R(rest) * R(pose)`, so the rest pose reproduces the file exactly, group
rotations included -- the reader's test proves it against the saved file: the same
bounds and the same corner of a twice-turned leg as an independent reading. A `Pose`
turns any bone about its pivot or places it outright; the renderer bakes one mesh per
bone once per reload and pushes each through its placement every frame. Nothing of the
friend's file is edited; animation, when it comes, is ours in code.

## The body

The head's path is a trail (`domain/Trail`: the last positions of the head's axis with
the surface's up at each and the arc length along them); the chain of segments is laid
along it at the distances the file's own pivots give (`domain/ChainPose`), each segment
facing the one before it and standing on its own surface's up, so a body over an edge
bends round it. A wave travels down the body from the head (`domain/Undulation`): a
third of a block of weave at speed, easing to a tenth and a ten-second writhe standing
still, a small vertical ripple at twice the frequency -- the creature snakes even going
straight. `domain/Body` resolves the profile's bone names on the rig, reads each leg's
segment, hip and rest foot off the file's pivots and cubes, and turns a chain pose and
the legs' poses into the rig's `Pose` each frame. Nothing of the pose is synced: each
side keeps its own trail from the head positions it already has.

## The feet

Every leg has a foot (`domain/Legs`): planted on a solid surface of the level, it stays
where it is while the body walks over it, until it has drifted a stride from the leg's
rest point or its block is gone; then it swings, a few ticks on a lifted arc, to the
surface under the rest point a little ahead -- floor, wall or ceiling, found by a cast
along the segment's own down -- or hangs at rest when nothing is in reach. Most feet are
down at all times: a foot may start a swing only while its pair's other foot stands and
fewer than 40 % of all feet are swinging, candidates taken in the gait's metachronal
order (`domain/LegGait`: each pair a little behind the pair before, left and right half a
cycle apart), so the steps run in a wave down the body; a foot with nothing under it
steps first. Swings shorten and strides lengthen with speed, so a scurrying body's feet
keep up. Standing still nothing steps. The blocks are read through `domain/Cells`, one
kind per cell (air, rock, hard, fluid; `LevelCells` in main reads the level), so the
rule runs under JUnit on synthetic rock. Both sides step their own feet each tick;
nothing is synced. The renderer aims each leg bone at its foot as a lever: the lift in
the leg's own plane, then the swing about the segment's up.

## The clips

The attacks and reactions are authored in code (`domain/FaceStealerClips`): **coil**
(the front four segments rise into an S, the head rears, the pincers spread; the click
at tick 5, the hiss at 12), **pounce** (the body straightens, pincers wide; the screech
at launch), **strike** (the pincers snap shut; the blow on the closing frame -- the dig's
beat), **grab**, **bite**, **flinch**, **death** (the body loosens, the legs curl in
over forty ticks). A `domain/Clip` is keys per bone slerped between ticks and cues at
ticks; `domain/Animator` plays one over the body's own pose, composing each named bone's
turn after its own, bones it does not name untouched. The server starts a clip
(`Aberrant.play`) and its name rides synced data with a serial, so every client starts
the same clip within a tick; both sides advance their own animator, the server acting on
the cues (for now remembering them; the sounds and the dig come with their phases).

On the server every chain segment is a part (`AberrantPart`, the game's multipart
entities) standing where the chain puts it, so a sword or an arrow meets the segment it
aims at. The carapace (`domain/Carapace`) says what the blow comes to: a random segment
among the profile's candidates is cracked at birth (synced, saved); a blow on it lands
whole and the body flinches; a blow anywhere else, the head's own box included, rings
off the plating and does nothing; an explosion lands half wherever it goes. The cracked
segment's glowing cubes (the profile's `weak_spot.glow`, a glob on cube names) are drawn
full bright in a pulsing ember. The server reads the model from the mod jar
(`RigStore`) for the segment spacing; a resource pack cannot move what the world hits.

A scripted walk (`Aberrant.setScriptedWalk`) moves the creature for the booth and the
tests until the crawl arrives.

## Verifying it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test                   # JUnit on the pure layer: the rig, the body, the feet, the clips
./gradlew check                  # plus the gametests and the photo booth (needs a display; -PskipBooth, -PskipGameTests)
```

The domain layer is compiled against the JDK alone; `net.minecraft` there is a compile
error. Its tests are partitioned by each class's spec and named in the class docs. The
gametests, on a headless server, register the profile and make a creature from it, walk
it and find its parts along its body and most of its feet on the floor, ring its plating
and crack it, and play a clip through its cues. The booth (`Xephyr :7 -screen 1280x720
-ac -br -noreset`, then `DISPLAY=:7 __GLX_VENDOR_LIBRARY_NAME=mesa
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460 ./gradlew runPhotoBooth`) photographs the creature from
the side, the front quarter and up close, walking from the side and from above, and each
clip at its key frames, silent from its first tick; its `booth: PASS/FAIL` lines are the
assertion, and the frames are looked at.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
