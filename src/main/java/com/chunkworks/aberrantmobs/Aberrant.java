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
import com.chunkworks.aberrantmobs.domain.ChainPose;
import com.chunkworks.aberrantmobs.domain.Clip;
import com.chunkworks.aberrantmobs.domain.Crawl;
import com.chunkworks.aberrantmobs.domain.FaceStealerClips;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * on. The server lays an {@link AberrantPart} on every chain segment each
 * tick, where the world meets and hits it; the carapace says what a hit
 * comes to.
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
 */
public class Aberrant extends Monster {
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

    /** How far beyond its box a creature is still drawn: a body eleven blocks long trails well past its head. */
    private static final double CULL_REACH = 12.0;
    /** Trail samples kept: a body of eleven blocks at the slowest crawl, and then some. */
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
    /** A waypoint this near is passed; a target this near is reached. */
    private static final double WAYPOINT_REACH = 1.2;
    private static final double TARGET_REACH = 1.5;
    /** A strike cuts at most this many blocks. */
    private static final int STRIKE_BUDGET = 32;
    /** A target within this of the last one keeps the way already planned, blocks. */
    private static final double SAME_TARGET = 4.0;
    /** Old sounds are forgotten this often, ticks. */
    private static final int FORGET_EVERY = 200;
    /** A blow this hard is a hard one, unless the tree's {@code flinch_at} says. */
    private static final double FLINCH_AT = 10.0;
    /** The pincers reach this far from the head's axis, blocks. */
    public static final double GRAB_REACH = 3.0;
    /** The pinch as the pincers close, and the bite: finite, beyond any absorption, through the pipeline. */
    public static final float PINCH = 2.0f;
    public static final float DEVOUR = 1.0e6f;
    /** Where the held one hangs: the maw's centre from the head's pivot, in the head's frame, blocks (the file's units over sixteen). */
    private static final Vec MAW = new Vec(0.0, -11.1 / 16.0, 22.8 / 16.0);

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

    public Aberrant(EntityType<? extends Aberrant> type, Level level) {
        super(type, level);
        parts = new AberrantPart[MAX_PARTS];
        for (int i = 0; i < MAX_PARTS; i++) {
            parts[i] = new AberrantPart(this, i);
        }
        setNoGravity(true);
        noPhysics = true;
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
        CreatureProfile p = profile();
        if (p != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.stats().health());
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(p.stats().speed());
            setHealth((float) p.stats().health());
            if (!level().isClientSide() && entityData.get(DATA_WEAK) < 0) {
                crack(random.nextLong());
            }
        }
        refreshDimensions();
        sizeParts();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PROFILE.equals(key)) {
            refreshDimensions();
            sizeParts();
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
        carapace = Carapace.fresh(body.chain().length, candidates, roll);
        entityData.set(DATA_WEAK, carapace.weak());
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

    /** effects: returns the carapace as the synced crack says; rebuilt when the crack moved under it */
    private Carapace carapace(int segments) {
        int weak = entityData.get(DATA_WEAK);
        if (carapace == null || carapace.weak() != weak || carapace.segments() != segments) {
            carapace = new Carapace(segments, Math.max(0, Math.min(segments - 1, weak)), Carapace.EXPLOSION_SHARE);
        }
        return carapace;
    }

    /**
     * effects: takes a blow on segment {@code index} (the head 0) as the
     * carapace routes it: the whole of it on the crack, half an explosion
     * anywhere, a clang otherwise; returns whether the body was hurt
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
        }
        return landed;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // A blow on the body's own box is a blow on the head: plating, unless the source cares nothing for plating.
        return hurtSegment(0, source, amount);
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
        Vec aim = point.plus(up().times(rules(body, p).clearance()));
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
            Vec wish = wish(cells);
            Crawl.Step step = Crawl.step(cells, crawl, wish, wish.equals(Vec.ZERO) ? 0.0 : crawlSpeed, rules, mayDig);
            crawl = step.pose();
            lastBlocked = step.blocked();
            if (step.digNeeded()) {
                pendingDig = Crawl.section(crawl, rules);
                if (!animator.busy()) {
                    play(FaceStealerClips.STRIKE);
                }
            } else {
                pendingDig = null;
            }
        }
        place();
    }

    /** effects: returns this tick's wish: the scripted walk's direction while it lies in the face and its heading after, the way toward the target, or nothing */
    private Vec wish(LevelCells cells) {
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
        if (target.minus(centre).length() < TARGET_REACH) {
            target = null;
            path = null;
            return Vec.ZERO;
        }
        if (path == null || --replanIn <= 0) {
            path = Burrow.plan(cells, Cell.containing(centre), Cell.containing(target), Burrow.HUNT, Burrow.BUDGET).orElse(null);
            pathAt = 0;
            replanIn = REPLAN;
        }
        if (path == null) {
            return target.minus(centre);
        }
        while (pathAt < path.size() - 1 && path.get(pathAt).centre().minus(centre).length() < WAYPOINT_REACH) {
            pathAt++;
        }
        return path.get(pathAt).centre().minus(centre);
    }

    /** effects: one tick of a leap: moved by the flight, the flight bent by gravity; landed on the first face it flies into, or when the flight has gone on too long */
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
                Vec face = cells.face(centre, n.dir.times(-1), flight.length() + 0.3);
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
            if (!level().isClientSide()) {
                placeParts(chain);
            }
        }
        for (String cue : animator.advance()) {
            onCue(cue);
        }
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
            entityData.set(DATA_MODE, memory.mode());
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

    /** effects: opens the pincers: the held one is let go where it hangs; nothing is held afterwards */
    public void release() {
        Entity passenger = getFirstPassenger();
        releasing = true;
        try {
            if (passenger != null) {
                passenger.stopRiding();
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

    /** effects: returns the head's orientation this tick: along the trail's newest sample, or the yaw and the up before the body is known */
    private net.minecraft.world.phys.Vec3 mawPoint() {
        Vec forward = facing();
        Vec up = up();
        if (trail != null) {
            Trail.Sample s = trail.at(0);
            forward = s.forward();
            up = s.up();
        }
        Vec maw;
        try {
            maw = axis().plus(com.chunkworks.aberrantmobs.domain.Quat.lookAlong(forward, up).rotate(MAW));
        } catch (IllegalArgumentException e) {
            maw = axis().plus(forward.times(MAW.z()));
        }
        return new net.minecraft.world.phys.Vec3(maw.x(), maw.y(), maw.z());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        net.minecraft.world.phys.Vec3 maw = mawPoint();
        move.accept(passenger, maw.x, maw.y - passenger.getBbHeight() / 2.0, maw.z);
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
        if (FaceStealerClips.CUE_GRAB.equals(cue)) {
            pinch();
        }
        if (FaceStealerClips.CUE_BITE.equals(cue)) {
            devour();
        }
        if (FaceStealerClips.CUE_STRIKE.equals(cue) && pendingDig != null && level() instanceof ServerLevel server) {
            List<Cell> rock = Tunnel.rock(cells(), pendingDig);
            blocksDug += DigWorld.dig(server, this, rock.size() > STRIKE_BUDGET ? rock.subList(0, STRIKE_BUDGET) : rock, !quiet);
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
        if (tag.contains("Profile")) {
            entityData.set(DATA_PROFILE, tag.getString("Profile"));
            refreshDimensions();
            sizeParts();
        }
        distance = tag.getDouble("Distance");
        if (tag.contains("Weak")) {
            entityData.set(DATA_WEAK, tag.getInt("Weak"));
        }
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
