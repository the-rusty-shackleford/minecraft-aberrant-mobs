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
import com.chunkworks.aberrantmobs.domain.Carapace;
import com.chunkworks.aberrantmobs.domain.ChainPose;
import com.chunkworks.aberrantmobs.domain.Clip;
import com.chunkworks.aberrantmobs.domain.FaceStealerClips;
import com.chunkworks.aberrantmobs.domain.Legs;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.chunkworks.aberrantmobs.domain.Rig;
import com.chunkworks.aberrantmobs.domain.Trail;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A creature: the one entity type of the protocol, sized, skinned and, in
 * later phases, minded by its {@link CreatureProfile}. The profile id is
 * synced data, so a client that first sees it knows what it is; the
 * profile itself is looked up in the registry each time, never cached, so
 * a reload is honoured.
 *
 * <p>The body follows the head: each side keeps its own {@link Trail} of
 * where the head's axis has been (nothing is synced for it: both sides
 * have the head's positions already) and the writhe's {@link
 * Undulation.Wave}, advanced once a tick by the ground speed. The trail is
 * seeded straight behind the head when the body is first known, so a
 * creature that just appeared has a whole body. Each side also keeps the
 * feet: every leg's foot planted on the world, stepped by {@link Legs}
 * from the level's own blocks, so the legs stand where there is something
 * to stand on. The server lays an {@link AberrantPart} on every chain
 * segment each tick, where the world meets and hits it; the carapace says
 * what a hit comes to.
 *
 * <p>An authored {@link Clip} plays on the server's say: its name and a
 * serial ride synced data, so every client starts the same clip within a
 * tick; both sides then advance their own {@link Animator}, the server
 * acting on the cues, the client drawing the turns over the body's pose.
 */
public class Aberrant extends Monster {
    private static final EntityDataAccessor<String> DATA_PROFILE = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    /** The cracked segment's index in the chain, -1 for none yet. */
    private static final EntityDataAccessor<Integer> DATA_WEAK = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.INT);
    /** The clip last started, by name ("" for none), and a count of starts so a repeat is noticed. */
    private static final EntityDataAccessor<String> DATA_CLIP = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_CLIP_SERIAL = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.INT);

    /** How far beyond its box a creature is still drawn: a body eleven blocks long trails well past its head. */
    private static final double CULL_REACH = 12.0;
    /** Trail samples kept: a body of eleven blocks at the slowest crawl, and then some. */
    private static final int TRAIL_CAPACITY = 256;
    /** Parts a creature is born with; a chain longer than this has no boxes past it. */
    public static final int MAX_PARTS = 16;

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

    public Aberrant(EntityType<? extends Aberrant> type, Level level) {
        super(type, level);
        parts = new AberrantPart[MAX_PARTS];
        for (int i = 0; i < MAX_PARTS; i++) {
            parts[i] = new AberrantPart(this, i);
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
            Vec at = chain.position(k).minus(up().times(part.getBbHeight() / 2.0));
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
        return super.hurt(source, (float) v.damage());
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

    // --- the body -------------------------------------------------------

    /** effects: returns the unit direction the head faces along the ground, from the body's yaw */
    public Vec facing() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** effects: returns the up of the surface the head clings to: the floor's, in this phase */
    public Vec up() {
        return Vec.Y;
    }

    /** effects: returns where the head's axis is now: over the feet by {@code axisHeight} along the up */
    private Vec axis(double axisHeight) {
        return new Vec(getX(), getY(), getZ()).plus(up().times(axisHeight));
    }

    /**
     * effects: returns this side's trail of the head's axis, seeded straight
     * behind the head over {@code bodyLength} blocks the first time it is
     * asked for
     */
    public Trail trail(double axisHeight, double bodyLength) {
        if (trail == null) {
            trail = Trail.seeded(axis(axisHeight), facing(), up(), Math.max(1.0, bodyLength), TRAIL_CAPACITY);
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

    /** A walk the server was told to make: a ground velocity, blocks a tick, for so many ticks. */
    @Nullable
    private Vec walk;
    private int walkTicks;

    /**
     * effects: from now on, for {@code ticks} ticks, the server walks this
     * creature at {@code velocity} (blocks a tick, along the ground) facing
     * that way, gravity kept -- the booth's and the tests' way to move it
     * until the crawl arrives
     */
    public void setScriptedWalk(Vec velocity, int ticks) {
        walk = velocity;
        walkTicks = ticks;
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!level().isClientSide() && walk != null && walkTicks > 0) {
            walkTicks--;
            double vy = onGround() && getDeltaMovement().y <= 0 ? -0.04 : getDeltaMovement().y - 0.08;
            setDeltaMovement(walk.x(), vy, walk.z());
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (onGround() && getDeltaMovement().y < 0) {
                setDeltaMovement(getDeltaMovement().x, 0.0, getDeltaMovement().z);
            }
            if (walk.x() != 0 || walk.z() != 0) {
                float yaw = (float) Math.toDegrees(Math.atan2(-walk.x(), walk.z()));
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            return;
        }
        super.travel(input);
    }

    @Override
    public void tick() {
        super.tick();
        double dx = getX() - xo, dz = getZ() - zo;
        speed = Math.sqrt(dx * dx + dz * dz);
        distance += speed;
        CreatureProfile p = profile();
        Body body = body();
        if (p != null) {
            wave = p.rig().undulation().advance(wave(p.rig().undulation()), speed);
        }
        if (body != null && p != null) {
            Trail t = trail(body.axisHeight(), body.length());
            t.push(axis(body.axisHeight()), up());
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

    // --- the feet -------------------------------------------------------

    /** effects: steps every foot one tick on {@code chain} over the level's blocks, the body's travel along its facing */
    private void stepFeet(Body body, CreatureProfile p, ChainPose chain) {
        if (legs == null || legsOf != body) {
            legs = body.legs();
            legsOf = body;
            feet = Legs.hanging(legs.length);
        }
        if (cells == null) {
            cells = new LevelCells(level());
        }
        feet = Legs.step(cells, chain, legs, feet, p.rig().gait(), distance, speed, facing());
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

    /** effects: acts on a clip's cue: remembered now; the sounds and the dig's blocks hang on it in later phases */
    private void onCue(String cue) {
        lastCue = cue;
        lastCueTick = tickCount;
    }

    // --- saving ---------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Profile", entityData.get(DATA_PROFILE));
        tag.putDouble("Distance", distance);
        tag.putInt("Weak", entityData.get(DATA_WEAK));
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
    }

    /** effects: returns the yaw of the body between ticks, for the frame */
    public float bodyYaw(float partialTick) {
        return Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
    }
}
