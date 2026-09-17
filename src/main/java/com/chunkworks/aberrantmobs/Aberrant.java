/*
 * Aberrant Mobs - a protocol for monsters.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.aberrantmobs;

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.Animator;
import com.chunkworks.aberrantmobs.domain.Body;
import com.chunkworks.aberrantmobs.domain.Burrow;
import com.chunkworks.aberrantmobs.domain.Carapace;
import com.chunkworks.aberrantmobs.domain.Cell;
import com.chunkworks.aberrantmobs.domain.Cells;
import com.chunkworks.aberrantmobs.domain.ChainPose;
import com.chunkworks.aberrantmobs.domain.Clip;
import com.chunkworks.aberrantmobs.domain.Crawl;
import com.chunkworks.aberrantmobs.domain.FaceStealerClips;
import com.chunkworks.aberrantmobs.domain.Gaze;
import com.chunkworks.aberrantmobs.domain.Habitat;
import com.chunkworks.aberrantmobs.domain.Hearing;
import com.chunkworks.aberrantmobs.domain.Leap;
import com.chunkworks.aberrantmobs.domain.Legs;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.chunkworks.aberrantmobs.domain.Rig;
import com.chunkworks.aberrantmobs.domain.Trail;
import com.chunkworks.aberrantmobs.domain.Tunnel;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.mind.Intent;
import com.chunkworks.aberrantmobs.domain.mind.Memory;
import com.chunkworks.aberrantmobs.domain.mind.Mind;
import com.chunkworks.aberrantmobs.domain.mind.Senses;
import com.chunkworks.aberrantmobs.domain.mind.Tree;
import com.chunkworks.aberrantmobs.verb.Verb;
import com.chunkworks.aberrantmobs.verb.Verbs;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A creature: the one entity type of the protocol, sized, skinned and, in
 * later phases, minded by its {@link CreatureProfile}. The profile id is
 * synced data, so a client that first sees it knows what it is; the
 * profile itself is looked up in the registry each time, never cached, so
 * a reload is honoured.
 *
 * <p>It keeps itself out of rock by the {@link Crawl}, not by the game's
 * physics: no gravity, no block collision, no pushing; each server tick
 * the head's axis point is stepped along the face it clings to (floor,
 * wall or ceiling) toward what it wants, climbing, wrapping over edges,
 * boring when it may dig -- the section ahead is cut on the strike clip's
 * cue, so digging is visibly the pincers' work -- and a pounce flies by
 * the {@link Leap} until it lands on whatever it hits. The face it clings
 * to rides synced data as its up. Its box is centred on the axis.
 *
 * <p>The body follows the head: each side keeps its own {@link Trail} of
 * where the head's axis has been (nothing is synced for it: both sides
 * have the head's positions already) and the writhe's {@link
 * Undulation.Wave}, advanced once a tick by the ground speed. The trail is
 * seeded straight behind the head when the body is first known, and again
 * after a jump too long to have been walked, so a creature that just
 * appeared or was moved has a whole body. Each side also keeps the feet:
 * every leg's foot planted on the world, stepped by {@link Legs} from the
 * level's own blocks, so the legs stand where there is something to stand
 * on. Both sides lay an {@link AberrantPart} on every chain segment each
 * tick, where the world meets and hits it (a sword picks its target on
 * the client, from these boxes); the carapace says what a hit comes to,
 * and never more than the profile's share of the health from one blow.
 *
 * <p>An authored {@link Clip} plays on the server's say: its name and a
 * serial ride synced data, so every client starts the same clip within a
 * tick; both sides then advance their own {@link Animator}, the server
 * acting on the cues, the client drawing the turns over the body's pose.
 *
 * <p>It thinks, on the server, when its profile has a mind and its AI is
 * not off: each tick the {@link SensesReader} gathers the senses, the pure
 * {@link Mind} decides an intent from the tree and the memory (kept, and
 * saved, so a reload does not forget a hunt), and the named {@link Verb}
 * is begun, ticked or ended; the verbs drive the crawl. Its ears
 * ({@link Hearing}) are fed by {@link Ears} from the world's game events.
 *
 * <p>It grabs: the target rides it, held at the maw (a passenger, never a
 * driver; the {@link Grip} refuses a dismount while it holds), pinched on
 * the grab clip's cue. It bites: on the bite clip's cue the held one is
 * hurt by {@code aberrantmobs:devoured}, a finite million through the
 * ordinary damage pipeline -- so a miracle, a totem or a blessing at that
 * door still saves them, and a survivor is known to have survived; one
 * who dies is devoured, and if they were a player the creature wears
 * their face from then on (synced, saved) until its next victim.
 *
 * <p>It comes into the world on its own where {@link SpawnRules} allow,
 * as the profile whose habitat fits the site, and bores itself a pocket
 * in the wall beside the cave, so the first sign of it is digging; once
 * it stalks it persists. It drops its profile's loot and, one time in
 * seven, the face it wore. Its cues play its sounds; it skitters as it
 * moves, quietly when stalking, and breathes when still.
 */
public class Aberrant extends Monster {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs");
    private static final EntityDataAccessor<String> DATA_PROFILE = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    /** The cracked segment's index in the chain, -1 for none yet. */
    private static final EntityDataAccessor<Integer> DATA_WEAK = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.INT);
    /** The clip last started, by name ("" for none), and a count of starts so a repeat is noticed. */
    private static final EntityDataAccessor<String> DATA_CLIP = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_CLIP_SERIAL = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.INT);
    /** The face the head clings to, a {@link Crawl.Normal} ordinal. */
    private static final EntityDataAccessor<Byte> DATA_NORMAL = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.BYTE);
    /** The mind's mode, for whoever watches. */
    private static final EntityDataAccessor<String> DATA_MODE = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    /** Whether the pincers hold someone. */
    private static final EntityDataAccessor<Boolean> DATA_HELD = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.BOOLEAN);
    /** The last victim's name and id ("" for none): the face it wears. */
    private static final EntityDataAccessor<String> DATA_FACE_NAME = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_FACE_ID = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);

    /** How far beyond its box a creature is still drawn: a body sixteen blocks long trails well past its head. */
    private static final double CULL_REACH = 18.0;
    /** Trail samples kept: a body of sixteen blocks at the slowest crawl (a sample every twentieth of a block along ten and a half of spine is two hundred and ten), and then some. */
    private static final int TRAIL_CAPACITY = 256;
    /** A head that moved further than this in a tick was moved, not walked: the body is laid afresh behind it. */
    private static final double TELEPORT = 4.0;
    /** Parts a creature is born with; a chain longer than this has no boxes past it. */
    public static final int MAX_PARTS = 16;
    /** A pounce leaves at this speed, blocks a tick. */
    public static final double POUNCE_SPEED = 1.2;
    /** A flight this long lands wherever it is. */
    private static final int MAX_FLIGHT = Leap.MAX_TICKS;
    /** A burrow's way is planned again this often, ticks, and when it is lost. */
    private static final int REPLAN = 20;
    /** A refused crawl brings the next plan forward to within this many ticks -- not to now: a search a tick is more than a server can spare, and the same cell plans the same way. */
    private static final int BLOCKED_REPLAN = 5;
    /**
     * A target this near is reached, and a waypoint within the head's own clearance -- inside its box -- is
     * passed: outright, or across the head's face while under it ({@link Crawl#reaches}), since the axis rides
     * its clearance over what lies on the face. The waypoint's reach follows the body because the lookahead does:
     * a way that goes round a post runs through the cell before it, which a head that looks 2.4 ahead never
     * comes within 1.2 of, and it stood there refused.
     */
    private static final double TARGET_REACH = 1.5;
    /** A target within this of the last one keeps the way already planned, blocks. */
    private static final double SAME_TARGET = 4.0;
    /** Old sounds are forgotten this often, ticks. */
    private static final int FORGET_EVERY = 200;
    /** A blow this hard is a hard one, unless the tree's {@code flinch_at} says. */
    private static final double FLINCH_AT = 10.0;
    /** The pincers reach this far from the head's axis, blocks: at the Face-Stealer's size, twice the maw's reach and a little. */
    public static final double GRAB_REACH = 4.5;
    /** The pinch as the pincers close, and the bite: finite, beyond any absorption, through the pipeline. */
    public static final float PINCH = 2.0f;
    public static final float DEVOUR = 1.0e6f;
    /**
     * Where the held one hangs, from the head's pivot in the head's frame, in the model's own units (times the
     * profile's scale for blocks): at the height of the maw cube's centre, against the mask's front plane -- half
     * their own width further forward, so they hang in the pincers before the face, not inside the head's front
     * cube, where a box centred on the maw put a player's eyes at either size.
     */
    private static final Vec HOLD_UNITS = new Vec(0.0, -11.1, 27.0);
    /** The chance the face it wore drops when it dies. */
    public static final float FACE_DROP_CHANCE = 0.15f;
    /** Experience for the kill. */
    private static final int XP = 50;
    /** The death clip's length: the body is taken away when it ends. */
    private static final int DEATH_TICKS = 40;
    /** A skitter every so many ticks under way; a breath, standing still, at these odds a tick -- about one in fifteen seconds and never on a beat (every ninety ticks it panted like a dog at whoever it stood beside); while stalking or hunting, a click or a hiss about this often, so the prey hears it is there. */
    private static final int SKITTER_EVERY = 6;
    private static final int BREATH_ODDS = 300;
    private static final int DREAD_EVERY = 70;

    private final AberrantPart[] parts;
    @Nullable
    private Trail trail;
    private Undulation.Wave wave;
    /** Blocks travelled along the ground, for the legs' phase, and last tick's ground speed. */
    private double distance;
    private double speed;
    @Nullable
    private Carapace carapace;
    /** The feet, one per leg, null until the body is known; and the legs they belong to, kept while the body is the same. */
    @Nullable
    private Legs.Foot[] feet;
    @Nullable
    private Legs.Leg[] legs;
    @Nullable
    private Body legsOf;
    @Nullable
    private LevelCells cells;
    private final Animator animator = new Animator();
    @Nullable
    private String lastCue;
    private int lastCueTick = -1;

    // The crawl, on the server.
    @Nullable
    private Crawl.Pose crawl;
    /** The face last clung to, for the up while airborne. */
    private Vec lastUp = Vec.Y;
    @Nullable
    private Vec flight;
    private int flightTicks;
    private Vec desired = Vec.ZERO;
    private double crawlSpeed;
    private int crawlTicks;
    private boolean mayDig;
    @Nullable
    private Vec target;
    @Nullable
    private List<Cell> path;
    private int pathAt;
    private int replanIn;
    @Nullable
    private List<Cell> pendingDig;
    private boolean quiet;
    private int blocksDug;
    private boolean lastBlocked;

    // The mind, on the server.
    @Nullable
    private Memory memory;
    /** The prey the senses last held, for the ears to follow when sight is lost; not saved. */
    @Nullable
    private UUID targetId;
    private Hearing hearing = Hearing.SILENT;
    private Senses senses = Senses.NONE;
    @Nullable
    private Verb verb;
    private Intent intent = Intent.NONE;
    private boolean hurtFlag;
    private boolean hurtHard;
    private boolean releasing;
    private boolean grabSurvived;
    private boolean biting;
    private Gaze gaze = Gaze.NONE;

    public Aberrant(EntityType<? extends Aberrant> type, Level level) {
        super(type, level);
        parts = new AberrantPart[MAX_PARTS];
        for (int i = 0; i < MAX_PARTS; i++) {
            parts[i] = new AberrantPart(this, i);
        }
        setId(getId());   // number the parts from this id, now that they exist
        setNoGravity(true);
        noPhysics = true;
        xpReward = XP;
    }

    /**
     * effects: gives this creature id {@code id} and its parts the ids after
     * it, {@code id + 1} on: the game numbers a multipart entity's parts
     * from its parent's id and nowhere else (the dragon does it itself),
     * and a client attacking a part sends that part's id, which the server
     * resolves among its own -- numbered by its entity counter at birth,
     * they matched nothing the client sent, and no sword ever landed on a
     * segment
     */
    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    /**
     * effects: returns a new creature of profile {@code id} at {@code (x, y, z)} facing {@code yaw},
     * with the profile's health, or null when no such profile is loaded
     */
    @Nullable
    public static Aberrant create(Level level, ResourceLocation id, double x, double y, double z, float yaw) {
        if (AberrantMobs.profile(level.registryAccess(), id).isEmpty()) {
            return null;
        }
        Aberrant a = ModContent.ABERRANT.get().create(level);
        if (a == null) {
            return null;
        }
        a.setProfileId(id);
        a.moveTo(x, y, z, yaw, 0.0f);
        a.yHeadRot = yaw;
        a.yBodyRot = yaw;
        return a;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFILE, "");
        builder.define(DATA_WEAK, -1);
        builder.define(DATA_CLIP, "");
        builder.define(DATA_CLIP_SERIAL, 0);
        builder.define(DATA_NORMAL, (byte) Crawl.Normal.UP.ordinal());
        builder.define(DATA_MODE, "");
        builder.define(DATA_HELD, false);
        builder.define(DATA_FACE_NAME, "");
        builder.define(DATA_FACE_ID, "");
    }

    /** effects: returns this creature's profile, or null before one is set or when the packs lost it */
    @Nullable
    public CreatureProfile profile() {
        String id = entityData.get(DATA_PROFILE);
        if (id.isEmpty()) {
            return null;
        }
        return AberrantMobs.profile(level().registryAccess(), ResourceLocation.parse(id)).map(Holder.Reference::value).orElse(null);
    }

    /** effects: returns the profile's id, or null before one is set */
    @Nullable
    public ResourceLocation profileId() {
        String id = entityData.get(DATA_PROFILE);
        return id.isEmpty() ? null : ResourceLocation.parse(id);
    }

    /** effects: returns the body the profile's names make of its model, as the mod jars carry it; null when either is wanting */
    @Nullable
    public Body body() {
        CreatureProfile p = profile();
        ResourceLocation id = profileId();
        return p == null || id == null ? null : RigStore.body(id, p).orElse(null);
    }

    /** effects: makes this a creature of profile {@code id}: its size and health from the profile, its name, its crack */
    public void setProfileId(ResourceLocation id) {
        entityData.set(DATA_PROFILE, id.toString());
        takeProfile();
    }

    /**
     * effects: on the server, gives this creature what its profile says --
     * its health and speed, at full health, and a crack -- unless it has
     * them already: a saved creature comes back with its attributes and
     * its crack and keeps its health, while one that was only given a
     * profile (summoned with {@code {Profile:...}}, or from an egg, whose
     * data lands after the spawn is finalized) takes the profile's; then
     * its size, on both sides
     */
    private void takeProfile() {
        CreatureProfile p = profile();
        if (p != null && !level().isClientSide()) {
            boolean has = getAttribute(Attributes.MAX_HEALTH).getBaseValue() == p.stats().health()
                    && getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == p.stats().speed()
                    && entityData.get(DATA_WEAK) >= 0;
            if (!has) {
                getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.stats().health());
                getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(p.stats().speed());
                setHealth((float) p.stats().health());
                if (entityData.get(DATA_WEAK) < 0) {
                    crack(random.nextLong());
                }
            }
        }
        refreshDimensions();
        sizeParts();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PROFILE.equals(key)) {
            takeProfile();
        }
        if (DATA_CLIP_SERIAL.equals(key) && level().isClientSide()) {
            Clip clip = FaceStealerClips.ALL.get(entityData.get(DATA_CLIP));
            if (clip != null) {
                animator.play(clip);
            }
        }
        if (DATA_NORMAL.equals(key)) {
            Crawl.Normal n = syncedNormal();
            if (n != Crawl.Normal.NONE) {
                lastUp = n.dir;
            }
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        CreatureProfile p = profile();
        if (p == null) {
            return super.getDefaultDimensions(pose);
        }
        return EntityDimensions.scalable((float) p.body().width(), (float) p.body().height()).withEyeHeight((float) p.body().eyeHeight());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(CULL_REACH);
    }

    @Override
    public Component getName() {
        if (hasCustomName()) {
            return super.getName();
        }
        ResourceLocation id = profileId();
        return id == null ? super.getName() : Component.translatable("creature." + id.getNamespace() + "." + id.getPath());
    }

    // --- the parts ------------------------------------------------------

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** effects: gives every chain segment's part its box from the profile, and every part past the chain a speck */
    private void sizeParts() {
        CreatureProfile p = profile();
        Body body = body();
        int segments = p == null || body == null ? 0 : Math.min(MAX_PARTS, body.chain().length);
        for (int i = 0; i < MAX_PARTS; i++) {
            if (i < segments && p != null) {
                parts[i].size(i == 0 ? p.body().width() : p.body().segment().width(), i == 0 ? p.body().height() : p.body().segment().height());
            } else {
                parts[i].size(0.01, 0.01);
            }
        }
    }

    /** effects: stands each segment's part on the chain, its box centred on the segment's axis */
    private void placeParts(ChainPose chain) {
        int segments = Math.min(MAX_PARTS, chain.size());
        for (int k = 0; k < segments; k++) {
            AberrantPart part = parts[k];
            Vec at = chain.position(k).minus(new Vec(0, part.getBbHeight() / 2.0, 0));
            part.setPos(at.x(), at.y(), at.z());
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
        }
    }

    // --- the carapace ---------------------------------------------------

    /** effects: cracks a segment chosen by {@code roll} from the profile's candidates, or any but the head without them */
    private void crack(long roll) {
        Body body = body();
        CreatureProfile p = profile();
        if (body == null || p == null) {
            return;
        }
        int[] candidates = candidates(body, p);
        carapace = Carapace.fresh(body.chain().length, candidates, roll, blowCap(p));
        entityData.set(DATA_WEAK, carapace.weak());
    }

    /**
     * effects: returns the most a blow takes from a creature of profile
     * {@code p}: its health over its {@code stats.blows}, and a hundredth
     * over, so that the last of the blows takes the last of the health in
     * the game's float arithmetic; no cap for one blow
     */
    private static double blowCap(CreatureProfile p) {
        return p.stats().blows() <= 1 ? Carapace.UNCAPPED : p.stats().health() / p.stats().blows() + 0.01;
    }

    private static int[] candidates(Body body, CreatureProfile p) {
        List<String> names = p.body().weakSpot().candidates();
        int[] chain = body.chain();
        if (names.isEmpty()) {
            int[] all = new int[Math.max(1, chain.length - 1)];
            for (int i = 0; i < all.length; i++) {
                all[i] = Math.min(i + 1, chain.length - 1);
            }
            return all;
        }
        return names.stream().mapToInt(n -> body.chainIndexOf(n)).filter(i -> i >= 0).toArray();
    }

    /** effects: returns the cracked segment's index in the chain, -1 for none */
    public int weakSegment() {
        return entityData.get(DATA_WEAK);
    }

    /** effects: returns the carapace as the synced crack and the profile say; rebuilt when the crack moved under it */
    private Carapace carapace(int segments) {
        int weak = entityData.get(DATA_WEAK);
        CreatureProfile p = profile();
        double cap = p == null ? Carapace.UNCAPPED : blowCap(p);
        if (carapace == null || carapace.weak() != weak || carapace.segments() != segments || carapace.blowCap() != cap) {
            carapace = new Carapace(segments, Math.max(0, Math.min(segments - 1, weak)), Carapace.EXPLOSION_SHARE, cap);
        }
        return carapace;
    }

    /**
     * effects: takes a blow the world landed on segment {@code hit}'s box
     * (the head's own 0) on the segment it was aimed at, as
     * {@link #hurtSegment} does: judged from the blow's own geometry -- a
     * melee attacker's look, a projectile's flight, a blast's centre --
     * since the segments' boxes overlap along the body and the box the
     * game's pick named may be a neighbour's; {@code hit} itself when the
     * source has no geometry or aims at none; returns whether the body was
     * hurt
     */
    public boolean hurtAimed(int hit, DamageSource source, float amount) {
        int aimed = aimedSegment(hit, source);
        boolean landed = hurtSegment(aimed, source, amount);
        if (!level().isClientSide() && LOG.isDebugEnabled()) {
            LOG.debug("{} blow of {} by {} on part {} aimed at segment {} (the crack {}): {}", getId(), amount, source.getMsgId(), hit, aimed, weakSegment(), landed ? "landed, health " + getHealth() : "clang");
        }
        return landed;
    }

    /** effects: returns the segment {@code source} was aimed at, see {@link #hurtAimed}; {@code hit} without geometry or a body */
    private int aimedSegment(int hit, DamageSource source) {
        Body body = body();
        CreatureProfile p = profile();
        if (body == null || p == null) {
            return hit;
        }
        int n = Math.min(MAX_PARTS, body.chain().length);
        Vec[] centres = new Vec[n];
        for (int k = 0; k < n; k++) {
            centres[k] = new Vec(parts[k].getX(), parts[k].getY() + parts[k].getBbHeight() / 2.0, parts[k].getZ());
        }
        double reach = p.body().segment().width();
        Entity direct = source.getDirectEntity();
        int aimed = -1;
        if (direct instanceof Projectile shot) {
            Vec3 flight = shot.getDeltaMovement();
            aimed = flight.lengthSqr() > 1e-8 ? Carapace.aimed(vec(shot.position()), vec(flight), centres, reach) : Carapace.nearest(vec(shot.position()), centres);
        } else if (direct instanceof LivingEntity attacker) {
            aimed = Carapace.aimed(vec(attacker.getEyePosition()), vec(attacker.getLookAngle()), centres, reach);
        } else if (source.getSourcePosition() != null) {
            aimed = Carapace.nearest(vec(source.getSourcePosition()), centres);
        }
        return aimed >= 0 ? aimed : hit;
    }

    private static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    /**
     * effects: takes a blow on segment {@code index} (the head 0) as the
     * carapace routes it: the whole of it on the crack, half an explosion
     * anywhere, a clang otherwise, and never more than the profile's share
     * of the health; returns whether the body was hurt
     */
    public boolean hurtSegment(int index, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        Body body = body();
        int segments = body == null ? 1 : body.chain().length;
        Carapace.Cause cause = source.is(DamageTypeTags.IS_EXPLOSION) ? Carapace.Cause.EXPLOSION
                : source.getDirectEntity() instanceof Projectile ? Carapace.Cause.PROJECTILE
                : source.getEntity() != null ? Carapace.Cause.MELEE : Carapace.Cause.OTHER;
        Carapace.Verdict v = carapace(segments).route(new Carapace.Hit(index, amount, cause));
        if (v.isClang()) {
            if (!level().isClientSide()) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4f, 1.6f);
            }
            return false;
        }
        boolean landed = super.hurt(source, (float) v.damage());
        if (landed) {
            hurtFlag = true;
            CreatureProfile p = profile();
            double flinchAt = p != null && p.mind().isPresent() ? p.mind().get().tunable("flinch_at", FLINCH_AT) : FLINCH_AT;
            hurtHard |= v.damage() >= flinchAt;
            if (!level().isClientSide() && isAlive() && !animator.busy()) {
                play(FaceStealerClips.FLINCH);
            }
        }
        return landed;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;   // the plating clangs and the crack fires the flinch's own cue
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return ModContent.DEATH.get();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            release();
            play(FaceStealerClips.DEATH);
        }
    }

    @Override
    protected void tickDeath() {
        // The body loosens over the death clip's forty ticks, not the game's twenty, before it is taken away.
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // --- the world: spawning and loot ------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        ServerLevel server = level.getLevel();
        boolean wild = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        if (profile() == null) {
            // The world's own spawns need a creature whose habitat fits the spot; a command, an egg or a spawner
            // means "put one here", and takes whatever fits, else the first creature known. An egg's own data
            // (its profile) lands after this and is taken then.
            ResourceLocation chosen = chooseProfile(server);
            if (chosen == null && !wild) {
                chosen = server.registryAccess().registryOrThrow(AberrantMobs.CREATURES).holders().findFirst().map(h -> h.key().location()).orElse(null);
            }
            if (chosen == null) {
                discard();
                return data;
            }
            setProfileId(chosen);
        }
        if (wild) {
            LevelCells cells = new LevelCells(server);
            Habitat.siteInWall(cells, Cell.containing(new Vec(getX(), getY(), getZ())), Habitat.SITE_DEPTH).ifPresent(site -> {
                List<Cell> pocket = new java.util.ArrayList<>();
                int r = Habitat.POCKET_RADIUS;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            pocket.add(site.plus(dx, dy, dz));
                        }
                    }
                }
                blocksDug += DigWorld.dig(server, this, pocket, false);
                setPos(site.x() + 0.5, site.y() - r + 0.2, site.z() + 0.5);   // its feet on the pocket's floor
                resetCrawl();
            });
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** effects: returns a profile with a habitat fitting this spot, drawn by weight among those that do; null when none does */
    @Nullable
    private ResourceLocation chooseProfile(ServerLevel server) {
        int sky = server.getBrightness(net.minecraft.world.level.LightLayer.SKY, blockPosition());
        int block = server.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPosition());
        List<Holder.Reference<CreatureProfile>> fitting = new java.util.ArrayList<>();
        int total = 0;
        for (Holder.Reference<CreatureProfile> p : server.registryAccess().registryOrThrow(AberrantMobs.CREATURES).holders().toList()) {
            if (p.value().habitat().isPresent() && Habitat.deepAndDark(p.value().habitat().get().rules(), blockPosition().getY(), sky, block)) {
                fitting.add(p);
                total += p.value().habitat().get().weight();
            }
        }
        if (fitting.isEmpty()) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Holder.Reference<CreatureProfile> p : fitting) {
            roll -= p.value().habitat().get().weight();
            if (roll < 0) {
                return p.key().location();
            }
        }
        return fitting.get(fitting.size() - 1).key().location();
    }

    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        CreatureProfile p = profile();
        return p != null && p.loot().isPresent() ? ResourceKey.create(Registries.LOOT_TABLE, p.loot().get()) : super.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        GameProfile face = face();
        if (face != null && random.nextFloat() < FACE_DROP_CHANCE) {
            spawnAtLocation(StolenFaceItem.of(face));
        }
    }

    /** effects: plays {@code sound} from the head for everyone near, at {@code volume} and a pitch a little off one */
    private void sound(SoundEvent sound, float volume) {
        sound(sound, volume, 0.9f, 1.1f);
    }

    /** effects: plays {@code sound} from the head at {@code volume} (over one, it carries farther) and a pitch drawn between {@code low} and {@code high} */
    private void sound(SoundEvent sound, float volume, float low, float high) {
        Vec at = axis();
        level().playSound(null, at.x(), at.y(), at.z(), sound, SoundSource.HOSTILE, volume, low + random.nextFloat() * (high - low));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // A blow on the body's own box is a blow on the head, unless it was aimed along the body at a segment.
        return hurtAimed(0, source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        // The crawl moves it; the game's physics never do.
    }

    // --- the body -------------------------------------------------------

    /** effects: returns the unit direction the head faces along the ground, from the body's yaw */
    public Vec facing() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** effects: returns the face the head clings to as synced, NONE while airborne */
    public Crawl.Normal syncedNormal() {
        int i = entityData.get(DATA_NORMAL);
        Crawl.Normal[] all = Crawl.Normal.values();
        return i >= 0 && i < all.length ? all[i] : Crawl.Normal.UP;
    }

    /** effects: returns the outward normal of the face the head clings to; the last one while airborne */
    public Vec up() {
        Crawl.Normal n = syncedNormal();
        return n == Crawl.Normal.NONE ? lastUp : n.dir;
    }

    /** effects: returns where the head's axis is now: the centre of the box */
    public Vec axis() {
        return new Vec(getX(), getY() + getBbHeight() / 2.0, getZ());
    }

    /**
     * effects: returns this side's trail of the head's axis, seeded straight
     * behind the head over {@code bodyLength} blocks the first time it is
     * asked for
     */
    public Trail trail(double bodyLength) {
        if (trail == null) {
            trail = Trail.seeded(axis(), facing(), up(), Math.max(1.0, bodyLength), TRAIL_CAPACITY);
        }
        return trail;
    }

    /** effects: returns the writhe's state on this side, at rest until the body has ticked */
    public Undulation.Wave wave(Undulation undulation) {
        if (wave == null) {
            wave = undulation.rest();
        }
        return wave;
    }

    /** effects: returns how far the body has travelled along the ground, blocks */
    public double distance() {
        return distance;
    }

    /** effects: returns last tick's ground speed, blocks a tick */
    public double speed() {
        return speed;
    }

    // --- the crawl ------------------------------------------------------

    /** effects: returns the crawl's measures for this body: the axis height as its clearance, the bore and the lookahead from its width */
    private Crawl.Rules rules(Body body, CreatureProfile p) {
        return new Crawl.Rules(body.axisHeight(), p.body().width() * 0.6, p.body().width() * 0.64);
    }

    private LevelCells cells() {
        if (cells == null) {
            cells = new LevelCells(level());
        }
        return cells;
    }

    /**
     * effects: from now on, for {@code ticks} ticks, the server crawls this
     * creature at {@code velocity}'s speed (blocks a tick), first toward
     * its direction and then, once that direction leaves the face it is on
     * (a corner turned), straight on along whatever face it clings to; no
     * digging -- the booth's and the tests' way to move it
     */
    public void setScriptedWalk(Vec velocity, int ticks) {
        desired = velocity.length() > 1e-9 ? velocity.normalized() : Vec.ZERO;
        crawlSpeed = velocity.length();
        crawlTicks = ticks;
        mayDig = false;
        target = null;
        path = null;
    }

    /**
     * effects: from now on the server burrows this creature toward
     * {@code point} at {@code speed} blocks a tick along a way planned
     * through the blocks, digging through rock when {@code dig}, until it
     * is within reach of it
     */
    public void setCrawlTarget(Vec point, boolean dig, double speed) {
        boolean same = target != null && target.minus(point).length() < SAME_TARGET && mayDig == dig;
        target = point;
        mayDig = dig;
        crawlSpeed = speed;
        crawlTicks = 0;
        if (!same) {
            path = null;
            replanIn = 0;
        }
    }

    /** effects: stops any scripted walk or burrow; the head holds where it is */
    public void stopCrawl() {
        target = null;
        path = null;
        crawlTicks = 0;
        desired = Vec.ZERO;
    }

    /** effects: whether the server's digs are quiet (no particles, no game event) */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * effects: launches the head in a leap that lands its axis over
     * {@code point} (a spot on a surface) if a flight within its speed
     * exists, playing the pounce; returns whether it left
     */
    public boolean pounce(Vec point) {
        Body body = body();
        CreatureProfile p = profile();
        if (crawl == null || body == null || p == null || crawl.airborne()) {
            return false;
        }
        // A target on another face has another up. A floor launch aimed above
        // a ceiling used to strike its underside early, well short of the target.
        // Resolve only the target's immediate support; an airborne target retains
        // the existing current-up fallback. This runs once per pounce, not per tick.
        Crawl.Rules rules = rules(body, p);
        Crawl.Pose destination = Crawl.attach(cells(), point, crawl.heading(), rules, Cells.CAST_STEP * 2);
        Vec aim = destination == null ? point.plus(up().times(rules.clearance())) : destination.centre();
        Optional<Vec> v = Leap.velocity(crawl.centre(), aim, Vec.ZERO, POUNCE_SPEED, Leap.GRAVITY);
        if (v.isEmpty()) {
            return false;
        }
        flight = v.get();
        flightTicks = 0;
        crawl = new Crawl.Pose(crawl.centre(), crawl.heading(), Crawl.Normal.NONE);
        play(FaceStealerClips.POUNCE);
        return true;
    }

    /** effects: forgets where the head clung and the way behind it, so the next tick attaches it afresh where it now is with a straight body: after a teleport */
    public void resetCrawl() {
        crawl = null;
        flight = null;
        path = null;
        trail = null;
    }

    /** effects: returns the head's crawl pose on the server, null before its first tick or on a client */
    @Nullable
    public Crawl.Pose crawlPose() {
        return crawl;
    }

    /** effects: returns how many blocks this creature has dug */
    public int blocksDug() {
        return blocksDug;
    }

    /** effects: returns whether it is still under way toward a target, through a scripted walk, or in the air */
    public boolean crawling() {
        return target != null || crawlTicks > 0 || flight != null;
    }

    /** effects: one server tick of the crawl: the head stepped by its wish, flown by its leap, its dig readied; the entity placed on the head */
    private void crawlTick(Body body, CreatureProfile p) {
        Crawl.Rules rules = rules(body, p);
        LevelCells cells = cells();
        if (crawl == null) {
            Crawl.Pose start = Crawl.attach(cells, axis(), facing(), rules, 3.0);
            crawl = start != null ? start : new Crawl.Pose(axis(), facing(), Crawl.Normal.NONE);
        }
        if (flight != null) {
            fly(cells, rules);
        } else {
            Vec wish = wish(cells, rules);
            boolean dig = mayDig && wayThroughRock(cells, Crawl.DIG_AHEAD + rules.bore());
            boolean climb = wayRises(rules.lookahead() + 1.0);
            Crawl.Step step = Crawl.step(cells, crawl, wish, wish.equals(Vec.ZERO) ? 0.0 : crawlSpeed, rules, dig, climb);
            crawl = step.pose();
            if (step.blocked() && !lastBlocked && LOG.isDebugEnabled()) {
                LOG.debug("{} refused: head {} heading {} on {} wishing {} digging {}", getId(), crawl.centre(), crawl.heading(), crawl.normal(), wish, dig);
            }
            lastBlocked = step.blocked();
            if (step.digNeeded()) {
                pendingDig = readied(rules);
                if (!animator.busy()) {
                    play(FaceStealerClips.STRIKE);
                }
            } else {
                pendingDig = null;
            }
        }
        place();
    }

    /**
     * effects: returns the section the next strike cuts: the crawl's, along
     * the heading, and, following a way, the tube round the way's next
     * cells laid into the head's face within the same reach, so that a bend
     * is cut as a bend (a section along the heading alone left the outer
     * corner of a bend standing, and the head stepped up onto it and jammed
     * under the roof of its own bore); nearest cells first, so a strike's
     * budget takes what is at hand
     */
    private List<Cell> readied(Crawl.Rules rules) {
        List<Cell> section = new java.util.ArrayList<>(Crawl.section(crawl, rules));
        if (path != null && !crawl.airborne()) {
            for (Cell c : Tunnel.along(crawl.centre(), crawl.normal().dir, path, pathAt, Crawl.DIG_AHEAD, rules.bore())) {
                if (!section.contains(c)) {
                    section.add(c);
                }
            }
        }
        Vec centre = crawl.centre();
        section.sort(java.util.Comparator.comparingDouble(c -> c.centre().minus(centre).length()));
        return section;
    }

    /**
     * effects: returns whether the way ahead, from its next waypoint to the
     * first beyond {@code reach} of the head, passes through rock: the crawl
     * cuts only then, so a way that climbs a wall does not dig its foot (the
     * booth found it cutting the foot of a hill its way went over, then
     * standing blocked with no wall left to take), while in its own bore,
     * whose next cells a strike has already cut, it keeps cutting the bends
     * wide; false for a scripted walk or with no way
     */
    private boolean wayThroughRock(LevelCells cells, double reach) {
        if (path == null || crawl == null) {
            return false;
        }
        Vec centre = crawl.centre();
        for (int i = pathAt; i < path.size(); i++) {
            Cell c = path.get(i);
            if (c.centre().minus(centre).length() > reach) {
                return false;
            }
            if (cells.at(c) == com.chunkworks.aberrantmobs.domain.Cells.Kind.ROCK) {
                return true;
            }
        }
        return false;
    }

    /**
     * effects: returns whether the way ahead, from its next waypoint to the
     * first beyond {@code reach} of the head, rises off the face the head
     * clings to: the rock ahead is then something the way climbs, and the
     * crawl may take it as a wall; false for a way that keeps to the face --
     * one that goes round what is ahead, a post or a trunk the head would
     * otherwise climb, go over and hang refused on the far side of -- and
     * true with no way at all, since a scripted walk climbs whatever it
     * meets; false in the air
     */
    private boolean wayRises(double reach) {
        if (path == null || crawl == null) {
            return true;
        }
        if (crawl.airborne()) {
            return false;
        }
        Vec centre = crawl.centre();
        Vec n = crawl.normal().dir;
        for (int i = pathAt; i < path.size(); i++) {
            Vec d = path.get(i).centre().minus(centre);
            if (d.length() > reach) {
                return false;
            }
            if (d.dot(n) > 0.5) {
                return true;
            }
        }
        return false;
    }

    /**
     * effects: returns this tick's wish: the scripted walk's direction while
     * it lies in the face and its heading after, the way toward the target
     * (a waypoint, or the target, under the head's feet on the face it rides
     * counts as reached, {@link Crawl#reaches}: the axis rides its clearance
     * over a player's feet or a sound in the floor), or nothing
     */
    private Vec wish(LevelCells cells, Crawl.Rules rules) {
        if (crawlTicks > 0) {
            crawlTicks--;
            if (!desired.equals(Vec.ZERO)) {
                Vec n = crawl.normal().dir;
                Vec inFace = desired.minus(n.times(desired.dot(n)));
                if (crawl.airborne() || inFace.length() >= 0.25) {
                    return desired;
                }
                desired = Vec.ZERO;
            }
            return crawl.heading();
        }
        if (target == null) {
            return Vec.ZERO;
        }
        Vec centre = crawl.centre();
        if (Crawl.reaches(crawl, rules, target, TARGET_REACH)) {
            target = null;
            path = null;
            return Vec.ZERO;
        }
        if (lastBlocked && replanIn > BLOCKED_REPLAN) {
            replanIn = BLOCKED_REPLAN;
        }
        if (path == null || --replanIn <= 0) {
            // The way, or the nearest it can get when the target is cut off; never the straight line, which a wall or a pool would hold it on forever.
            long began = System.nanoTime();
            path = Burrow.planNearest(cells, Cell.containing(centre), Cell.containing(target), Burrow.HUNT, rules, Burrow.BUDGET).orElse(null);
            pathAt = 0;
            replanIn = REPLAN;
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} planned a way of {} cells in {} us", getId(), path == null ? 0 : path.size(), (System.nanoTime() - began) / 1000);
            }
        }
        if (path == null) {
            return Vec.ZERO;
        }
        while (pathAt < path.size() - 1 && Crawl.reaches(crawl, rules, path.get(pathAt).centre(), rules.clearance())) {
            pathAt++;
        }
        return path.get(pathAt).centre().minus(centre);
    }

    /**
     * effects: one tick of a leap: moved by the flight, the flight bent by
     * gravity; landed on the first face it comes down to its riding height
     * over -- a face it moves toward within its clearance and this tick's
     * approach, so a leap aimed at a spot ends its clearance over that
     * spot, neither short of it nor past it -- or when the flight has gone
     * on too long
     */
    private void fly(LevelCells cells, Crawl.Rules rules) {
        Vec centre = crawl.centre().plus(flight);
        Vec heading = crawl.heading();
        Vec level = new Vec(flight.x(), 0, flight.z());
        if (level.length() > 1e-6) {
            heading = level.normalized();
        }
        flightTicks++;
        Crawl.Pose landed = null;
        if (flightTicks >= 2) {
            double best = Double.MAX_VALUE;
            for (Crawl.Normal n : Crawl.Normal.values()) {
                if (n == Crawl.Normal.NONE || flight.dot(n.dir) >= -1e-6) {
                    continue;
                }
                Vec face = cells.face(centre, n.dir.times(-1), rules.clearance() - flight.dot(n.dir));
                if (face != null && face.minus(centre).length() < best) {
                    best = face.minus(centre).length();
                    Vec h = heading.minus(n.dir.times(heading.dot(n.dir)));
                    landed = new Crawl.Pose(face.plus(n.dir.times(rules.clearance())), h.length() > 1e-6 ? h.normalized() : (Math.abs(n.dir.x()) < 0.5 ? Vec.X : Vec.Z), n);
                }
            }
        }
        if (landed != null) {
            crawl = landed;
            flight = null;
        } else if (flightTicks >= MAX_FLIGHT) {
            crawl = new Crawl.Pose(centre, heading, Crawl.Normal.NONE);
            flight = null;
        } else {
            crawl = new Crawl.Pose(centre, heading, Crawl.Normal.NONE);
            flight = Leap.fallen(flight, Leap.GRAVITY);
        }
    }

    /** effects: puts the entity where the head is: its box centred on the axis, its up synced, its yaw along the heading */
    private void place() {
        Vec c = crawl.centre();
        setPos(c.x(), c.y() - getBbHeight() / 2.0, c.z());
        byte n = (byte) crawl.normal().ordinal();
        if (entityData.get(DATA_NORMAL) != n) {
            entityData.set(DATA_NORMAL, n);
        }
        if (crawl.normal() != Crawl.Normal.NONE) {
            lastUp = crawl.normal().dir;
        }
        Vec h = crawl.heading();
        if (h.x() * h.x() + h.z() * h.z() > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(-h.x(), h.z()));
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    @Override
    public void tick() {
        super.tick();
        CreatureProfile p = profile();
        Body body = body();
        if (!level().isClientSide() && body != null && p != null) {
            if (p.mind().isPresent() && !isNoAi()) {
                think(p.mind().get());
            }
            crawlTick(body, p);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        distance += speed;
        if (p != null) {
            wave = p.rig().undulation().advance(wave(p.rig().undulation()), speed);
        }
        if (body != null && p != null) {
            Trail t = trail(body.length());
            if (t.at(0).pos().minus(axis()).length() > TELEPORT) {
                // Moved, not walked: lay the body straight behind the head again and let the feet find the ground.
                trail = null;
                feet = null;
                legs = null;
                t = trail(body.length());
            }
            t.push(axis(), up());
            ChainPose chain = ChainPose.of(t, body.arcBack(), p.rig().undulation(), wave);
            stepFeet(body, p, chain);
            // On both sides: a sword picks its target on the client, from the parts' boxes as the client places them.
            // Placed on the server alone, a client's parts sat at the world's origin and a sword aimed at the crack
            // only ever met the head's box, and clanged; a blast, applied on the server, landed.
            placeParts(chain);
        }
        for (String cue : animator.advance()) {
            onCue(cue);
        }
        if (!level().isClientSide() && isAlive()) {
            String mode = mode();
            boolean after = mode.equals("stalk") || mode.equals("hunt");
            if (speed > 0.05 && tickCount % SKITTER_EVERY == 0) {
                sound(ModContent.SKITTER.get(), quiet ? 0.5f : after ? 1.3f : 1.0f, 0.85f, 1.05f);
            } else if (speed < 0.02 && !animator.busy() && random.nextInt(BREATH_ODDS) == 0) {
                sound(ModContent.BREATH.get(), after ? 0.5f : 0.35f, 0.6f, 0.8f);
            }
            if (after && !animator.busy() && random.nextInt(DREAD_EVERY) == 0) {
                // Something near you clicks its pincers, or hisses, low.
                sound(random.nextBoolean() ? ModContent.CLICK.get() : ModContent.HISS.get(), 0.7f, 0.7f, 0.9f);
            }
        }
    }

    /** effects: returns where the crawl is bound, or null when it is not under way toward a point */
    @Nullable
    public Vec crawlTarget() {
        return target;
    }

    /** effects: notes the player the senses hold as prey, whose sounds the ears may follow when sight is lost; null for none */
    public void noteTarget(@Nullable UUID id) {
        targetId = id;
    }

    /** effects: returns the prey last noted, or null */
    @Nullable
    public UUID targetId() {
        return targetId;
    }

    // --- the mind -------------------------------------------------------

    /** effects: one tick of thought: the senses read, the tree decided, the verb begun, ticked or changed */
    private void think(Tree tree) {
        if (memory == null) {
            memory = Memory.fresh(tree.start(), random.nextLong());
            hearing = Hearing.seeded(random.nextLong());
        }
        if (tickCount % FORGET_EVERY == 0) {
            hearing = hearing.forgotten(tickCount);
        }
        senses = SensesReader.read(this, tree);
        Mind.Decision d = Mind.tick(tree, senses, memory);
        memory = d.memory();
        if (!memory.mode().equals(entityData.get(DATA_MODE))) {
            String was = entityData.get(DATA_MODE);
            entityData.set(DATA_MODE, memory.mode());
            if (memory.mode().equals("stalk") || memory.mode().equals("hunt")) {
                setPersistenceRequired();   // it knows you: it does not despawn
            }
            if (memory.mode().equals("hunt") && !was.isEmpty()) {
                sound(ModContent.SCREECH.get(), 1.6f, 0.8f, 1.0f);   // the hunt is on, and everyone within twenty-five blocks knows
            } else if (memory.mode().equals("stalk") && !was.isEmpty()) {
                sound(ModContent.HISS.get(), 0.8f, 0.7f, 0.9f);      // something has noticed you
            }
        }
        Intent next = d.intent();
        if (!next.verb().equals(intent.verb())) {
            if (verb != null) {
                verb.end(this);
            }
            intent = next;
            verb = next.isNone() ? null : Verbs.make(next.verb());
            if (verb != null) {
                verb.begin(this, intent);
            }
        } else {
            intent = next;
            if (verb != null) {
                verb.tick(this, intent);
            }
        }
    }

    /** effects: returns the mind's memory as it stands, fresh in the tree's start mode before the first thought */
    public Memory memory() {
        if (memory == null) {
            CreatureProfile p = profile();
            memory = Memory.fresh(p != null && p.mind().isPresent() ? p.mind().get().start() : "", random.nextLong());
        }
        return memory;
    }

    /** effects: replaces the memory: what the senses noted */
    public void remember(Memory m) {
        memory = m;
    }

    /** effects: returns the mind's mode as synced, "" before it thinks */
    public String mode() {
        return entityData.get(DATA_MODE);
    }

    /** effects: returns the point sense named as read this tick, or null */
    @Nullable
    public Vec sense(String point) {
        return senses.point(point);
    }

    /** effects: returns the verb under way, by name; "none" when nothing is */
    public String verb() {
        return intent.verb();
    }

    /** effects: returns whether the last crawl step was refused the way it wanted */
    public boolean lastBlocked() {
        return lastBlocked;
    }

    /** effects: notes whether the target met its eyes this tick; returns whether the target is staring (eight of the last ten) */
    public boolean gazeNoting(boolean met) {
        gaze = gaze.noting(met);
        return gaze.locked();
    }

    /** effects: returns and clears whether a blow landed since last asked, and whether a hard one did */
    public boolean[] takeHurt() {
        boolean[] out = {hurtFlag, hurtHard};
        hurtFlag = false;
        hurtHard = false;
        return out;
    }

    /** effects: these ears hear {@code sound} from where the head is */
    public void hear(Hearing.Sound sound) {
        hearing = hearing.heard(sound, axis());
    }

    /** effects: returns the ears as they stand */
    public Hearing hearing() {
        return hearing;
    }

    // --- the grab, the bite, the face ------------------------------------

    /** effects: returns whether the pincers hold someone */
    public boolean holding() {
        return entityData.get(DATA_HELD);
    }

    /** effects: returns whether the creature is letting go this instant, for the grip to allow it */
    public boolean releasing() {
        return releasing;
    }

    /** effects: returns whether the last bite left its victim alive */
    public boolean grabSurvived() {
        return grabSurvived;
    }

    /** effects: returns the nearest player the senses would hunt within the pincers' reach, or null */
    @Nullable
    public LivingEntity nearestTarget() {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        return SensesReader.nearestPlayer(server, this, GRAB_REACH + 1.0);
    }

    /** effects: returns the one held, or null */
    @Nullable
    public LivingEntity held() {
        return holding() && getFirstPassenger() instanceof LivingEntity l ? l : null;
    }

    /**
     * requires: called on the server
     * effects: closes the pincers on {@code victim} if it is within reach and
     * nothing else is held: it rides the creature at the maw, the grab
     * clip plays and pinches on its cue; returns whether it was taken
     */
    public boolean grab(LivingEntity victim) {
        if (holding() || !getPassengers().isEmpty() || victim.getVehicle() != null) {
            return false;
        }
        Vec at = new Vec(victim.getX(), victim.getY() + victim.getBbHeight() / 2.0, victim.getZ());
        if (at.minus(axis()).length() > GRAB_REACH + victim.getBbWidth()) {
            return false;
        }
        if (!victim.startRiding(this, true)) {
            return false;
        }
        entityData.set(DATA_HELD, true);
        grabSurvived = false;
        play(FaceStealerClips.GRAB);
        return true;
    }

    /** effects: opens the pincers: the held one is let go where it hangs, in air; nothing is held afterwards */
    public void release() {
        Entity passenger = getFirstPassenger();
        releasing = true;
        try {
            if (passenger != null) {
                net.minecraft.world.phys.Vec3 at = holdPoint(passenger);
                passenger.stopRiding();
                passenger.setPos(at.x, at.y - passenger.getBbHeight() / 2.0, at.z);
            }
        } finally {
            releasing = false;
        }
        entityData.set(DATA_HELD, false);
        grabSurvived = false;
        biting = false;
    }

    /** effects: bites the one held: the bite clip plays and devours on its cue; nothing when nothing is held or a bite is under way */
    public boolean bite() {
        if (held() == null || biting) {
            return false;
        }
        biting = true;
        play(FaceStealerClips.BITE);
        return true;
    }

    /** effects: the bite lands: the held one takes the devouring blow; dead, they are eaten and a player's face is taken; alive, they are known to have survived */
    private void devour() {
        biting = false;
        LivingEntity victim = held();
        if (victim == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        Holder<net.minecraft.world.damagesource.DamageType> type = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(AberrantMobs.DEVOURED);
        victim.hurt(new DamageSource(type, this), DEVOUR);
        if (victim.isAlive()) {
            grabSurvived = true;
            return;
        }
        if (victim instanceof Player player) {
            setFace(player.getGameProfile());
        }
        release();
    }

    /** effects: the pincers close: the held one is pinched */
    private void pinch() {
        LivingEntity victim = held();
        if (victim != null) {
            victim.hurt(damageSources().mobAttack(this), PINCH);
        }
    }

    /** effects: wears {@code profile}'s face from now on, on every client, across saves */
    public void setFace(GameProfile profile) {
        entityData.set(DATA_FACE_NAME, profile.getName() == null ? "" : profile.getName());
        entityData.set(DATA_FACE_ID, profile.getId() == null ? "" : profile.getId().toString());
    }

    /** effects: returns the profile whose face it wears, or null for nfx's painted mask */
    @Nullable
    public GameProfile face() {
        String name = entityData.get(DATA_FACE_NAME);
        String id = entityData.get(DATA_FACE_ID);
        if (name.isEmpty() && id.isEmpty()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = id.isEmpty() ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            uuid = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new GameProfile(uuid, name.isEmpty() ? "?" : name);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide()) {
            entityData.set(DATA_HELD, false);
            biting = false;
        }
    }

    /**
     * effects: returns where {@code passenger}'s middle hangs in the jaws: {@link #HOLD_UNITS} at the profile's
     * scale and half the passenger's width forward, in the head's frame this tick -- along the trail's newest
     * sample, or the yaw and the up before the body is known
     */
    private net.minecraft.world.phys.Vec3 jaws(Entity passenger) {
        Vec forward = facing();
        Vec up = up();
        if (trail != null) {
            Trail.Sample s = trail.at(0);
            forward = s.forward();
            up = s.up();
        }
        CreatureProfile p = profile();
        Vec offset = HOLD_UNITS.times(p == null ? 1.0 / 16.0 : p.scale()).plus(new Vec(0.0, 0.0, passenger.getBbWidth() / 2.0));
        Vec at;
        try {
            at = axis().plus(com.chunkworks.aberrantmobs.domain.Quat.lookAlong(forward, up).rotate(offset));
        } catch (IllegalArgumentException e) {
            at = axis().plus(forward.times(offset.z()));
        }
        return new net.minecraft.world.phys.Vec3(at.x(), at.y(), at.z());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        net.minecraft.world.phys.Vec3 at = holdPoint(passenger);
        move.accept(passenger, at.x, at.y - passenger.getBbHeight() / 2.0, at.z);
    }

    /**
     * effects: returns where the held one's middle hangs: in the jaws when
     * the jaws are in air, else at the head's own axis, which the crawl keeps
     * out of rock -- a head that has just surfaced through a wall must not
     * hold its prey inside it
     */
    private net.minecraft.world.phys.Vec3 holdPoint(Entity passenger) {
        net.minecraft.world.phys.Vec3 maw = jaws(passenger);
        double half = passenger.getBbHeight() / 2.0;
        AABB body = new AABB(maw.x - 0.3, maw.y - half, maw.z - 0.3, maw.x + 0.3, maw.y + half, maw.z + 0.3);
        if (level().noCollision(passenger, body)) {
            return maw;
        }
        Vec axis = axis();
        return new net.minecraft.world.phys.Vec3(axis.x(), axis.y(), axis.z());
    }

    // --- the feet -------------------------------------------------------

    /** effects: steps every foot one tick on {@code chain} over the level's blocks, the body's travel along its heading */
    private void stepFeet(Body body, CreatureProfile p, ChainPose chain) {
        if (legs == null || legsOf != body) {
            legs = body.legs();
            legsOf = body;
            feet = Legs.hanging(legs.length);
        }
        Vec travel = crawl != null ? crawl.heading() : facing();
        feet = Legs.step(cells(), chain, legs, feet, p.rig().gait(), distance, speed, travel);
    }

    /** effects: returns every leg's foot as it stands now, pair by pair, left then right; hanging before the body is known; a fresh copy */
    public Legs.Foot[] feet() {
        if (feet == null) {
            Body body = body();
            return Legs.hanging(body == null ? 0 : body.legCount());
        }
        return feet.clone();
    }

    // --- the clips ------------------------------------------------------

    /**
     * requires: called on the server
     * effects: starts {@code clip} on this creature, here and on every
     * client that sees it, dropping whatever played
     */
    public void play(Clip clip) {
        animator.play(clip);
        entityData.set(DATA_CLIP, clip.name());
        entityData.set(DATA_CLIP_SERIAL, entityData.get(DATA_CLIP_SERIAL) + 1);
    }

    /** effects: returns the name of the clip playing on this side, or null */
    @Nullable
    public String clipPlaying() {
        Clip c = animator.playing();
        return c == null ? null : c.name();
    }

    /** effects: returns {@code base} with this side's playing clip laid over it, {@code partialTick} into the tick; {@code base} when none plays */
    public Pose overlay(Rig rig, Pose base, double partialTick) {
        return animator.overlay(rig, base, partialTick);
    }

    /** effects: returns the last cue a clip fired on this side, or null; and the tick it fired on */
    @Nullable
    public String lastCue() {
        return lastCue;
    }

    public int lastCueTick() {
        return lastCueTick;
    }

    /** effects: acts on a clip's cue: the strike cuts the section the crawl readied, the grab pinches, the bite devours; every cue is remembered; the sounds hang on them in a later phase */
    private void onCue(String cue) {
        lastCue = cue;
        lastCueTick = tickCount;
        if (level().isClientSide()) {
            return;
        }
        switch (cue) {
            case FaceStealerClips.CUE_CLICK -> sound(ModContent.CLICK.get(), 0.8f);
            case FaceStealerClips.CUE_HISS -> sound(ModContent.HISS.get(), 0.9f);
            case FaceStealerClips.CUE_SCREECH -> sound(ModContent.SCREECH.get(), 1.2f);
            case FaceStealerClips.CUE_GRAB -> sound(ModContent.GRAB.get(), 1.0f);
            case FaceStealerClips.CUE_BITE -> sound(ModContent.BITE.get(), 1.0f);
            case FaceStealerClips.CUE_CRACK -> sound(ModContent.CRACK.get(), 1.0f);
            case FaceStealerClips.CUE_STRIKE -> sound(quiet ? ModContent.DIG_QUIET.get() : ModContent.DIG_LOUD.get(), quiet ? 0.3f : 1.0f);
            default -> { }
        }
        if (FaceStealerClips.CUE_GRAB.equals(cue)) {
            pinch();
        }
        if (FaceStealerClips.CUE_BITE.equals(cue)) {
            devour();
        }
        if (FaceStealerClips.CUE_STRIKE.equals(cue) && pendingDig != null && level() instanceof ServerLevel server) {
            List<Cell> rock = Tunnel.cuttable(cells(), pendingDig);
            Body body = body();
            CreatureProfile p = profile();
            int budget = body == null || p == null ? rock.size() : Crawl.strikeBudget(rules(body, p));   // a straight section's worth
            int cut = DigWorld.dig(server, this, rock.size() > budget ? rock.subList(0, budget) : rock, !quiet);
            blocksDug += cut;
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} struck: {} of {} rock cut, head {} heading {}", getId(), cut, rock.size(), crawl == null ? null : crawl.centre(), crawl == null ? null : crawl.heading());
            }
            pendingDig = null;
        }
    }

    // --- saving ---------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Profile", entityData.get(DATA_PROFILE));
        tag.putDouble("Distance", distance);
        tag.putInt("Weak", entityData.get(DATA_WEAK));
        if (crawl != null) {
            tag.putByte("Normal", (byte) crawl.normal().ordinal());
            tag.putDouble("HeadingX", crawl.heading().x());
            tag.putDouble("HeadingY", crawl.heading().y());
            tag.putDouble("HeadingZ", crawl.heading().z());
        }
        tag.putString("FaceName", entityData.get(DATA_FACE_NAME));
        tag.putString("FaceId", entityData.get(DATA_FACE_ID));
        if (memory != null) {
            CompoundTag mind = new CompoundTag();
            mind.putString("Mode", memory.mode());
            mind.putLong("Seed", memory.seed());
            CompoundTag timers = new CompoundTag();
            memory.timers().forEach(timers::putInt);
            mind.put("Timers", timers);
            CompoundTag points = new CompoundTag();
            memory.points().forEach((name, v) -> {
                CompoundTag p = new CompoundTag();
                p.putDouble("X", v.x());
                p.putDouble("Y", v.y());
                p.putDouble("Z", v.z());
                points.put(name, p);
            });
            mind.put("Points", points);
            tag.put("Mind", mind);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // The crack before the profile: a saved creature's profile finds its attributes (read above) and its crack in
        // place and leaves its health alone, where one given only a profile takes the profile's (takeProfile).
        if (tag.contains("Weak")) {
            entityData.set(DATA_WEAK, tag.getInt("Weak"));
        }
        if (tag.contains("Profile")) {
            entityData.set(DATA_PROFILE, tag.getString("Profile"));
            takeProfile();
        }
        distance = tag.getDouble("Distance");
        if (tag.contains("Normal") && tag.contains("HeadingX")) {
            Crawl.Normal[] all = Crawl.Normal.values();
            int i = tag.getByte("Normal");
            Crawl.Normal n = i >= 0 && i < all.length ? all[i] : Crawl.Normal.UP;
            Vec h = new Vec(tag.getDouble("HeadingX"), tag.getDouble("HeadingY"), tag.getDouble("HeadingZ"));
            if (h.length() > 1e-6 && (n == Crawl.Normal.NONE || Math.abs(h.normalized().dot(n.dir)) < 1e-6)) {
                crawl = new Crawl.Pose(axis(), h.normalized(), n);
                entityData.set(DATA_NORMAL, (byte) n.ordinal());
            }
        }
        if (tag.contains("FaceName")) {
            entityData.set(DATA_FACE_NAME, tag.getString("FaceName"));
            entityData.set(DATA_FACE_ID, tag.getString("FaceId"));
        }
        if (tag.contains("Mind")) {
            CompoundTag mind = tag.getCompound("Mind");
            Map<String, Integer> timers = new HashMap<>();
            CompoundTag t = mind.getCompound("Timers");
            for (String name : t.getAllKeys()) {
                timers.put(name, Math.max(0, t.getInt(name)));
            }
            Map<String, Vec> points = new HashMap<>();
            CompoundTag ps = mind.getCompound("Points");
            for (String name : ps.getAllKeys()) {
                CompoundTag p = ps.getCompound(name);
                points.put(name, new Vec(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")));
            }
            memory = new Memory(mind.getString("Mode"), timers, points, mind.getLong("Seed"));
            entityData.set(DATA_MODE, memory.mode());
        }
    }

    /** effects: returns the yaw of the body between ticks, for the frame */
    public float bodyYaw(float partialTick) {
        return Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
    }
}
