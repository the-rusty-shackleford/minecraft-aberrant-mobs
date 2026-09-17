# Aberrant Mobs

A protocol for monsters, for NeoForge 1.21.1. A creature is a datapack entry and a
Blockbench project: this mod owns the rig that stands it up, the body that follows its
head, the mind that reads its senses through a decision tree, the crawling, digging,
stalking, grabbing and biting its verbs do, and what it drops. The first creature is the
**Face-Stealer**, nfx's centipede: sixteen blocks of it (the model at a unit of a sixteenth
and a half), a mask for a face.

All seven phases of the plan are built (1.0.0): the
creature exists, is sized and named by its profile, its body follows its head along a
trail and writhes, its feet stand on the world and step in a wave, its plating rings and
its crack glows, its attack animations play on the server's say, it crawls over floors,
walls and ceilings, digs its way to a point through rock, coils and pounces, thinks (a
decision tree in its profile over what it senses and hears, driving verbs in Java),
grabs, bites, wears the face of its last victim, spawns in the rock beside deep dark
caves, drops chitin and its cracked plate, and sounds like what it is; and its chitin
makes an armour whose full set walks on walls and ceilings as if they were ground.
Placement directions, explosion impulses, the name anchor and contact shadow now
follow the wearer’s frame. First-person obstruction samples surround the actual eyes,
so the supporting wall no longer blacks out the view. The rear camera uses vanilla clipping along the rotated
look, including when the ground leaves little room. Removing the set under a ceiling
searches all the way to a free standing box (D-0015). These changes are included in 1.2.0.

Chitin’s item and worn textures use vanilla netherite/scute templates recoloured to the
creature’s palette, with an orange chest fissure; see `devtools/art/SOURCES.md` for
credits. The Stolen Face has an authored bone-mask sprite. A CC0 chitter supplements
the clicks and a low recorded rumble replaces the idle breath without changing its
rare timing. Sources and cuts are in
`devtools/art/sounds/SOURCES.md` (D-0016).

## In creative

The creative inventory has an **Aberrant Mobs** page (the Stolen Face is its icon): chitin,
the cracked carapace, the stolen face, the four chitin pieces, and then one spawn egg per
creature profile loaded, each named after its creature ("Face-Stealer Spawn Egg"). The
same items sit on the vanilla pages too: the drops under Ingredients, the armour under
Combat, the eggs under Spawn Eggs. There is one egg item, `aberrantmobs:aberrant_spawn_egg`;
which creature it spawns is the profile in its entity data, so a datapack that adds a
creature gets its egg for free. An egg, a command or a spawner puts a creature down
wherever it is used, at its profile's health, taking the creature whose habitat fits the
spot when the egg names none, else the first known; only the world's own spawns insist on
a fitting habitat. A creature in creative is a spectacle, not a hunt: it hears every
player, but only a survival or adventure player is prey.

## A creature

`data/<ns>/aberrantmobs/creature/<name>.json`:

```jsonc
{
  "model": "aberrantmobs:face_stealer",      // assets/<ns>/aberrantmobs/model/<name>.bbmodel, saved as it is
  "scale": 0.09375,                           // model units to blocks (optional, a sixteenth; the Face-Stealer's is one and a half)
  "body": {"width": 3.75, "height": 3.75, "eye_height": 2.4},   // the box the world collides with, blocks
  "stats": {"health": 84, "speed": 0.45, "blows": 5}   // no blow takes more than a fifth of it: five to kill, however hard the weapon
}
```

Every creature is the one entity type `aberrantmobs:aberrant`; the profile id rides its
synced data. `/summon aberrantmobs:aberrant ~ ~ ~ {Profile:"aberrantmobs:face_stealer"}`.
The name is `creature.<ns>.<name>` in the lang file.

## The mind

A creature's mind is a decision tree in its profile (`"mind"`): modes, each a tree of
nodes, entered at `start`, over a fixed vocabulary of senses:

```jsonc
"mind": {"start": "roam", "tunables": {"sight": 40, "eye_cone_deg": 10},
  "modes": {
    "roam":  {"select": [ {"when": "hurt", "then": {"enter": "hunt"}},
                          {"when": "heard.any", "then": {"enter": "prowl"}},
                          {"act": "wander", "dig": true} ]},
    "prowl": {"select": [ {"when": "!heard.any || heard.age > 2400", "then": {"enter": "roam"}},
                          {"act": "approach", "target": "heard.bearing", "quiet": true} ]},
    "hunt":  {"select": [ {"when": "grab.held", "then": {"cooldown": "bite", "ticks": 30, "then": {"act": "bite"}}},
                          {"when": "!target.known", "then": {"wait": "patience", "ticks": 200, "then": {"enter": "stalk"}}},
                          {"when": "target.distance < 9 && timer.pounced == 0",
                           "then": {"sequence": [{"timer": "pounced", "set": 80}, {"act": "pounce"}]}},
                          {"act": "chase"} ]} } }
```

Nodes: `select` (the first child that decides), `sequence` (every child, the last intent
kept), `when` (a condition, then a node), `act` (a verb and its arguments), `enter` (a
mode; the new mode decides once more the same tick), `timer` (set), `wait` (patience:
start it, hold while it runs, fire once and clear), `cooldown` (fire, then hold while it
runs). Conditions are `&&`, `||`, `!`, parentheses, flags (`hurt`), comparisons
(`target.distance < 4`), and timers (`timer.no_bite == 0`; never set reads as zero). The
senses (`domain/mind/Senses`): `target.seen/known/in_sight/eye_contact/underground`,
`target.distance/pos/last_pos/look`, `hurt`, `hurt_hard`, `health`, `y`, `light`,
`on_wall`, `airborne`, `underground`, `blocked`, `heard.any/bearing/error/age/distance/
loud`, `grab.held/survived`, `random`, `home`. A condition naming anything else, a mode
entered that is not there, or a verb nobody registered is refused when the pack loads,
naming the path. The tree is pure and tested (`domain/mind`); the verbs are Java
(`verb/Verbs`: hold, wander, approach, chase, flee, dig, climb, pounce, grab, release,
bite; another mod may register its own before the profiles load).
Each server tick the creature reads its senses (`SensesReader`: the nearest survival or
adventure player within `sight`, in sight by the mob's own line-of-sight sensing, known
for `memory` ticks after with its last position remembered, eye contact when the
target's look is within `eye_cone_deg` of the head and the head faces them, for eight of the last ten
ticks: a stare, not a glance), the mind
decides, and the verb is begun, ticked or ended. The memory (mode, timers, points, seed)
is saved with the entity, so a reload does not forget a hunt.

## The grab, the bite, the face

Within reach (4.5 blocks of the head's axis), the pincers close (`Aberrant.grab`): the target rides the creature, held
in the jaws of nfx's head -- against the mask's front at the maw's height, half their own
width before the face, so from their own eyes they see the mask and the pincers, not the
inside of the head (or at the head's own axis when the jaws have just surfaced inside
rock, so the held one never hangs in a wall), never steering it, and the grip
(`Grip`) refuses their dismount until the creature lets go, in air; the grab clip
pinches on its cue. A stalker that takes its prey from behind hands over to the hunt,
where the bite lives. The bite
(`Aberrant.bite`) plays its clip and on the cue hurts the held one by
`aberrantmobs:devoured`, a finite million points through the ordinary damage pipeline,
bypassing armour, enchantments, shields, effects and the hurt cooldown but never
invulnerability -- so a totem still fires, and so does any mod that answers at
`LivingDamageEvent.Pre`, as Miracle Bringer does; a survivor is known to have survived
and the tree lets them go and flees. One who dies is devoured, and if they were a
player the creature wears their face: the mask cube's front (`rig.mask` in the
profile) is drawn with the victim's skin, their face and hat layer, on every client and
across saves, until the next victim. The pounce verb coils first (the charge, with its
click and hiss) and leaps as the coil ends.

## Where it lives, what it leaves, how it sounds

A profile with a `habitat` spawns on its own (`{"y_min": -58, "y_max": 0, "max_light":
0, "weight": 2, "exclusion": 128, "cap": 6}`): the biome modifier offers the entity type
to every overworld biome, and `SpawnRules` takes a site only when it is deep and dark
enough for some habitat, not in the deep dark, with a wall beside the cave thick enough
to bore into (`domain/Habitat`: across the cave's air, then seven of rock ending in a
pocket of rock five across), no other creature within the exclusion and fewer than the
cap in the level. The creature then takes the profile whose habitat fits (by weight),
bores its pocket, five across, seven blocks into that wall and starts there, so the
first sign of it is digging.
Once it stalks or hunts it persists; a lit base is safe, since it will not spawn in
light. A command or an egg puts it anywhere.

Killed, it drops its profile's loot table (`"loot"`): for the Face-Stealer twelve to
eighteen **Chitin** (more with Looting), its **Cracked Carapace** (the weak plate, the
armour's core in phase 7), and one time in seven the **Stolen Face** it wore, carrying
the victim's profile so the trophy names them; and fifty experience. The icons are drawn
by `devtools/art/build.py`.

Its sounds are cut by the same script from CC0 recordings on freesound.org, credited in
`devtools/art/sounds/SOURCES.md`, nothing added: a skitter every six ticks under way
(quiet while it stalks or prowls, loud in the hunt), a breath when still, the click and
the hiss of the coil, the screech of the pounce, the pincers meeting, the crunch of the
bite, the crack of its plating giving, the scrape of the dig (quiet or loud), and its
death. The plating's clang is the anvil.

## The chitin armour

Chitin makes a set (`chitin_helmet`, `chitin_chestplate` -- with the Cracked Carapace at
its heart -- `chitin_leggings`, `chitin_boots`; netherite's plating, mended with chitin).
Worn whole, it lets the wearer walk on walls and ceilings as if they were ground: not a
climbing trick but a change of down. A wearer has a *frame* (`domain/frame`): one of six
axis gravities, with right, up and forward axes in the world; its motion, its look and
its box are reckoned in those axes and turned to the world's by the mixins on the
entity (`mixin/EntityMixin`: the box is the axis swap with the feet on the gravity face,
the eyes are up along the frame, the view vector is the local look turned to the world,
the move collides the frame's up axis first and steps up along it, the supporting block
is under the frame's feet), while `travel` keeps working in the wearer's own axes --
gravity along its down, a jump along its up, friction on its floor -- and only its move
into the world is turned (`mixin/LivingEntityMixin`). Walking into a sturdy wall hard
enough takes it (`Transition.intoWall`: the wall becomes the floor, the feet on its face,
if the wearer's box fits there); walking off an edge wraps onto the ledge's face
(`Transition.overEdge`); taking the set off, water, lava, flying, riding, gliding,
sleeping or spectating lets go to the world's own down by the least way out that fits
(`Transition.release`). A knockback or a push lands in the wearer's frame. The client
runs the rule on its own player right after each move (`wallwalk/WallWalk`, the pure
rules over `level.noCollision`); the server runs the same rule right after its re-run of
each move the client reports, before it compares the two positions
(`mixin/ServerGamePacketListenerImplMixin`), and again after its tick for what no move
decides (the set coming off, water: `PlayerTickEvent.Post`); so the two agree and no
packet is added -- the frame rides the player's synced data as one byte
(`mixin/PlayerMixin`). Until 1.2.0 the server ran the rule only after its tick: a take
moves the feet onto the wall's face, the client reported that position, the server's
re-run had not taken the wall, and a survival player was "moved wrongly" and teleported
back while its own frame stayed on the wall -- every move after that re-run on the ground.
The booth's player was in creative, which skips that check, so the booth never saw it; its
wall scene is in survival now and counts the ticks the server disagrees. The server's fall check on a
reported move reads the fall along the wearer's down (`mixin/ServerPlayerMixin`). On the
client the camera sits at the wearer's eyes (`mixin/CameraMixin`), its angles are the
frame's rotation composed with the look and handed back as yaw, pitch and roll
(`client/WallWalkClient` on `ComputeCameraAngles`), a change of frame swinging over six
eased ticks (`Blend`), and a wearer's model stands along its frame
(`mixin/LivingEntityRendererMixin`). Anyone not in the set runs the game's code
untouched: every hook returns at once for the world's frame.

## The ears

It hears what the world hears (`Ears`): every game event a player causes -- a step
(one; two sprinting; nothing sneaking, so silence is a defence), a block broken or
placed (six), a splash, a blow, a blast (twenty) -- within 320 blocks reaches every
creature as a sound; other mobs' sounds and its own digging are nothing to it. Its ears
(`domain/Hearing`) keep the last sound from each source and report the one loudest for
its distance, with an error of half a block per block of distance divided by the
loudness, exact within 24 blocks: the bearing it heads for is the true point moved
sideways by that error along a direction drawn from its seed and re-drawn every 600
ticks, so a guess drifts rather than jitters, and at three hundred blocks a step is no
more than a direction. A sound older than 2400 ticks is forgotten.

Keeping you: a player in survival or adventure within 64 blocks is its target and its
position is known exactly, seen or not; out of that range, or out of range and sight, it
is known for 2400 ticks (two minutes) from the last time, at its last position -- and the
ears keep that position fresh: the last sound from that very player (by their id), if
newer, is where they are now taken to be, exact within 24 blocks and erred beyond
(`SensesReader`, `Hearing.estimateFrom`). Running is heard; sneaking away is not.

## What you hear

It skitters every six ticks under way (1.3 when hunting, 1.0 otherwise, 0.5 stalking,
where a sound over one carries past sixteen blocks) and, standing still, breathes about
once in fifteen seconds, low and quiet, never on a beat (every ninety ticks it panted like
a dog at whoever it stood beside). Its cues play the click, hiss and screech of the coil and pounce, the grab,
the bite, the crack of its plate and the scrape of its digging (loud or quiet). When it
begins to stalk you it hisses once; when the hunt begins it screeches (1.6: twenty-five
blocks); while it stalks or hunts, about every three and a half seconds, it clicks its
pincers or hisses, low. Every recording is CC0 from freesound.org, cut by
`devtools/art/build.py` (`devtools/art/sounds/SOURCES.md`). The game's "Press Shift to
dismount" hint is not shown or narrated when what you ride is a creature: the grip
refuses the dismount, so the hint would be a lie (`ClientPacketListenerMixin`).

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
bends round it. A wave travels down the body from the head (`domain/Undulation`): half
a block of weave at speed, easing to a sixth and a ten-second writhe standing still, a
small vertical ripple at twice the frequency -- the creature snakes even going
straight. `domain/Body` resolves the profile's bone names on the rig, reads each leg's
segment, hip and rest foot off the file's pivots and cubes, and turns a chain pose and
the legs' poses into the rig's `Pose` each frame. Nothing of the pose is synced: each
side keeps its own trail from the head positions it already has.

## The feet

Every leg has a foot (`domain/Legs`): planted on a solid surface of the level, it stays
where it is while the body walks over it, until it has drifted a stride from the leg's
rest point or its block is gone; then it swings, a few ticks on a lifted arc, to the
surface under the rest point a little ahead -- floor, wall or ceiling, found by a cast
along the segment's own down -- or hangs at rest when nothing is in reach. The stride,
the reach of that cast, the lift and how far ahead a step may land are the profile's
(`rig.gait`: `stride`, `reach`, `lift`, `lead`, blocks, beside `cycle_blocks`), so a
bigger body takes bigger steps. Most feet are
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

## The crawl

The creature is not moved by the game's physics: no gravity, no block collision, no
pushing. Its head is a point (`domain/Crawl`) held a fixed clearance off the axis face
it clings to -- floor, wall or ceiling, one of six -- and each server tick it turns
within that face toward what it wants (25 degrees a tick at most), moves, and settles:
snapped to its clearance; over an edge, wrapped onto the ledge's face heading down it;
with nothing under it, attached to any face in reach, else falling, and a fall lands only
on a floor it has come down to. Rock ahead is climbed (the wall becomes its face, the old
up its heading) when the way it follows rises there -- a post or a trunk the way goes
round, it turns round where it stands instead, since a head that climbed a pillar in its
way went over the top and hung refused on the far side -- or, when it may dig, bored:
the head holds, the strike clip plays, and on the strike's cue the section ahead
(`domain/Tunnel`: the cells within the bore's radius of the head's run -- six tenths of
the body's width, so five wide and four tall on an axis for the Face-Stealer -- the
floor it rides on kept, and, following a way, of the tube round the way's next cells
laid into the head's face, so a bend is cut as a bend rather than leaving its outer
corner standing for the head to step up onto; the nearest cells first, at most a
straight section's worth a strike, eighty-two for the Face-Stealer, so the tube round a
bend takes two) is cut to air (`DigWorld`; loud with particles
and a game event, or quiet). Rock is only ever cut when nothing hard or wet is beside
it, so a tunnel never breaches water, lava, bedrock or a chest. A wish to go through
its face bores when it may dig; a wish off its face -- through it without digging, or
away from it -- takes the nearest other face the wish lies along, so a creature on a
wall wanting what is out on the floor steps down onto the floor; with none in reach, a
wall or a ceiling is let go of and it falls to what is below (a floor holds it: it cannot
fly), and a fall lands only on a floor, never the wall it just left. Axis faces only: exact, six cases, enumerable; a slope
reads as corners, as it does to a centipede. The face it clings to rides synced data as its up, and its box is
centred on its axis, so a client draws it on the wall it is on.

A way to a point is planned by `domain/Burrow` for the body's measures (`Crawl.Rules`):
A* over cells, six-connected, air within the body's hold of a face cheap (a face within
three cells along an axis for the Face-Stealer, whose axis rides the third cell over a
floor, so a bore wider than three is planned down its middle and not along its walls),
air with none dearer, rock at the cost of digging it (cheap hunting, dear stalking),
hard and fluid never -- nor any rock within the bore's reach of them, two cells for the
Face-Stealer, so the bore that follows never breaches bedrock or water -- bounded by a
budget; cut off by water or bedrock, it
takes the way to the nearest reachable cell instead, never a straight line a pool would
hold it on. The search knows how deep in rock the target lies (the taxicab distance to
the nearest air, looked for up to eight cells out) and charges every way that many cells
of rock in its bound, so the open air about a buried target is not flooded before the
search commits to the rock; it reads each cell from the level once, into a table that
also holds the search's own state, and expands a cell with a few probes and no
allocation, so a plan is a millisecond or two; a cell in a chunk that is not loaded reads as
hard, so no plan ever loads a chunk. The entity follows it waypoint by waypoint (a
waypoint, or the target, under its feet on the face it rides counts as reached, since
its axis rides its clearance over a player's feet or a sound in the floor), cutting rock only while the way
itself runs through rock within a strike's reach -- a way that climbs a wall does not dig
its foot, while in its own bore it keeps cutting the bends wide -- and plans again every
twenty ticks, or within five of a refusal (a search a tick is more than a server can
spare, and the same cell plans the same way; each plan's size and time is logged at
DEBUG). A pounce (`domain/Leap`) is a launch
velocity that lands the head exactly on a spot under the game's own integration, within
a top speed; in flight the head lands on the first face it flies into. The blocks are
read through `domain/Cells` (`LevelCells`: a fluid is fluid, no collision is air,
bedrock, obsidian, block entities, `#aberrantmobs:undiggable` and the wither-immune are
hard, the rest rock).

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

On both sides every chain segment is a part (`AberrantPart`, the game's multipart
entities) standing where the chain puts it, so a sword or an arrow meets the segment it
aims at: a sword's target is picked on the client, from the parts' boxes as the client
places them, and sent as the part's id, which the server resolves among its own parts --
so the parts are numbered from the creature's id on both sides (`Aberrant.setId`, as the
dragon numbers its own; the game does not do it for a mod's parts). Until 1.2.0 the
parts were placed on the server alone, so a client's sat at the world's origin, and were
numbered by each side's own counter: a sword aimed at the crack only ever met the head's
box and clanged, or named a part the server did not know, while a blast, applied on the
server, landed. The boxes are as wide as the body on a spine spaced about a block,
so they overlap, and the box the game's pick names may be a neighbour's: the server judges
the segment a blow was aimed at from the blow's own geometry (`Aberrant.hurtAimed`,
`Carapace.aimed`: the segment whose centre lies nearest the attacker's look or the arrow's
flight; a blast, the segment nearest its centre). The carapace (`domain/Carapace`) says what the blow comes to: a random segment
among the profile's candidates is cracked at birth (synced, saved); a blow on it lands
whole and the body flinches; a blow anywhere else, the head's own box included, rings
off the plating and does nothing; an explosion lands half wherever it goes; and no blow,
however hard, takes more than the profile's share (`stats.blows`: five blows at the least
to kill the Face-Stealer, a fifth of its health each at most, explosions included). The cracked
segment's glowing cubes (the profile's `weak_spot.glow`, a glob on cube names) are drawn
full bright in a pulsing ember. The server reads the model from the mod jar
(`RigStore`) for the segment spacing; a resource pack cannot move what the world hits.

A scripted walk (`Aberrant.setScriptedWalk`) moves the creature for the booth and the
tests until the crawl arrives.

## Verifying it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test                   # JUnit on the pure layer: the rig, the body, the feet, the clips, the crawl, the mind, the ears
./gradlew check                  # plus the gametests and the photo booth (needs a display; -PskipBooth, -PskipGameTests)
./gradlew runPlaytest            # the dread in a real cave: the log's numbers first, then the frames (needs a display)
```

The domain layer is compiled against the JDK alone; `net.minecraft` there is a compile
error. Its tests are partitioned by each class's spec and named in the class docs. The
gametests, on a headless server, register the profile and make a creature from it, walk
it and find its parts along its body and most of its feet on the floor, ring its plating
and crack it, play a clip through its cues, send it over a floor, up a wall and across a
ceiling, dig it a coherent tunnel round bedrock to a target, land its pounce, and, with
its mind on, prowl toward a distant step, hear a block break through the world, stalk a
player seen underground out of their view and hunt them on eye contact, hold a player at
the maw and devour them, spare a blessed one and flee, coil and pounce, refuse a lit
surface and take a dark chimney by thick rock to bore its pocket, and drop its loot; and a
player in the full chitin set walked into a wall takes it, climbs it, lets go without the
helmet and lands, lets go in water, while the undressed stop at the wall. The booth uses
the existing Xephyr display (check `pgrep -a Xephyr`; keep one rendering client). Set
`DISPLAY` to that display, then `__GLX_VENDOR_LIBRARY_NAME=mesa
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460 ./gradlew runPhotoBooth`) checks every sound event
resolves and photographs the creature from
the side, the front quarter and up close, walking from the side and from above, each
clip at its key frames, wearing the booth player's face, holding them at its maw (from
their own eyes), climbing a wall and digging into a hill, and the booth player in the
chitin set standing on a wall from its own eyes and from behind, silent from its first
tick; its `booth: PASS/FAIL` lines are the assertion, and the frames are looked at.
The playtest (same display) makes a real world, sets the survival player down in a dark
cave, blessed so a bite does not end the run, brings a Face-Stealer into the world in the
rock by another cave thirty to fifty blocks off, has the player break a block, and logs
what the creature does every ten ticks with a frame every eighty; its verdicts are that
it heard, dug, came near and stalked or hunted.

### Focused visual checks

`./gradlew runPhotoBooth -PboothGravity` checks all six tracked frames, rear/front
camera geometry, first-person visibility, explosion packet conversion, armour removal and low/high wall-camera
clearance. `-PboothArt` photographs netherite and chitin from identical front, side
and rear cameras. The default booth exercises actual survival movement and server
agreement. Each run requires its completion marker and no failed assertions.

The focused booth’s remote wearer is a server-controlled actor delivered through real
entity/equipment tracking, not a second connected player. Iris compatibility was
checked with Iris 1.8.14 beta.1, Sodium 0.8.13 beta.2 and Complementary Unbound 5.8.1
in an isolated booth directory.

The independent-client gate is `uv run --no-project python devtools/multiplayer.py`.
Set the same Java 21/software-rendering environment as the booth and `DISPLAY` to
an existing, host-checked test display. Start with no other Minecraft client running.
It creates a disposable loopback server at `127.0.0.1:25579`, waits for a real wearer
to finish joining and disable rendering, then launches the sole rendering observer.
Optional observer mods, shaderpacks and config live in `run/multiplayer/observer/`;
copy the isolated booth's shader setup there to reproduce its visual environment.
Both players finish loading before the fixture starts. Logs are under
`run/multiplayer/driver/`, screenshots under `run/multiplayer/observer/screenshots/`.
Every process must complete; movement corrections fail the gate. On failure the
runner terminates only its own processes. The test never starts or restarts a live server.

The gate covers natural survival wall/ceiling movement, all six tracked frames and
equipment, real motion packets, and ceiling helmet removal. Remote player motion is
checked at vanilla's interpolation target. A four-wall server regression also rejects
forged penetrating positions: stance replay never bypasses ordinary collision checks.
Pounces aim their clearance along the destination's supporting face, fixing an early
ceiling landing. All six landing surfaces are tested with actual creatures. See D-0017.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.

The survival photo booth verifies sixty position snapshots sent after the ordinary
movement packets, avoiding comparisons of live entities from different threads.
Missing samples, position disagreements and server movement-rejection warnings fail
the gate. This instrumentation belongs only to the test mod.
