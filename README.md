# Aberrant Mobs

A protocol for monsters, for NeoForge 1.21.1. A creature is a datapack entry and a
Blockbench project: this mod owns the rig that stands it up, the body that follows its
head, the mind that reads its senses through a decision tree, the crawling, digging,
stalking, grabbing and biting its verbs do, and what it drops. The first creature is the
**Face-Stealer**, nfx's centipede: eleven blocks of it, a mask for a face.

This is phase 1 of 7 (`~/.claude/plans/wiggly-cuddling-adleman.md` is the plan): the
creature exists, is sized and named by its profile, and is drawn standing as Blockbench
saved it. It does not yet move, think, dig, or bite.

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
straight. The legs skitter by distance travelled (`domain/LegGait`), each pair a little
behind the pair before, left and right half a cycle apart. `domain/Body` resolves the
profile's bone names on the rig and turns a chain pose and the legs' poses into the
rig's `Pose` each frame. Nothing of the pose is synced: each side keeps its own trail
from the head positions it already has.

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
./gradlew test                   # JUnit on the pure layer: the quaternion, the reader, the rig
./gradlew check                  # plus the gametests and the photo booth (needs a display; -PskipBooth, -PskipGameTests)
```

The domain layer is compiled against the JDK alone; `net.minecraft` there is a compile
error. Its tests are partitioned by each class's spec and named in the class docs. The
gametest, on a headless server, registers the profile and makes a creature from it. The
booth (`Xephyr :7 -screen 1280x720 -ac -br -noreset`, then `DISPLAY=:7
__GLX_VENDOR_LIBRARY_NAME=mesa LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460 ./gradlew runPhotoBooth`)
photographs the creature from the side, the front quarter and up close, silent from its
first tick; its `booth: PASS/FAIL` lines are the assertion, and the frames are looked at.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
