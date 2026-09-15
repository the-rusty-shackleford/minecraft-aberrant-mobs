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

import com.chunkworks.aberrantmobs.domain.Crawl;
import com.chunkworks.aberrantmobs.domain.Hearing;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.mind.Memory;
import com.chunkworks.aberrantmobs.domain.mind.Senses;
import com.chunkworks.aberrantmobs.domain.mind.Tree;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Reads a creature's senses off the level each tick, into the fixed
 * vocabulary the tree may name. The target is the nearest survival or
 * adventure player within the tree's {@code sight} (forty blocks unless
 * the profile says); it is seen when there, in sight when the head has a
 * line to it, known while seen or for {@code memory} ticks after (its
 * last position kept in the memory), in eye contact when in sight, its
 * look within {@code eye_cone_deg} of the head and the head's facing
 * within {@code face_cone_deg} of it. Underground is no sky overhead.
 * The ears' estimate gives the heard senses. Every raycast is the mob's
 * own sensing, cached per tick.
 */
public final class SensesReader {
    private SensesReader() {}

    public static final double SIGHT = 40.0;
    public static final int KNOWN_TICKS = 600;
    public static final double EYE_CONE_DEG = 10.0;
    public static final double FACE_CONE_DEG = 75.0;
    /** The point a target looks at is this far along its look, blocks. */
    private static final double LOOK_REACH = 8.0;

    /** effects: returns the senses of {@code a} this tick, its memory updated with what it saw */
    public static Senses read(Aberrant a, Tree tree) {
        ServerLevel level = (ServerLevel) a.level();
        Senses.Builder s = Senses.builder();
        Vec head = a.axis();
        Memory memory = a.memory();
        double sight = tree.tunable("sight", SIGHT);
        Player target = a.held() instanceof Player held ? held : nearestPlayer(level, a, sight);
        boolean seen = target != null;
        s.flag("target.seen", seen);
        if (seen) {
            Vec pos = new Vec(target.getX(), target.getY(), target.getZ());
            Vec eye = new Vec(target.getX(), target.getEyeY(), target.getZ());
            Vec3 look3 = target.getLookAngle();
            Vec look = new Vec(look3.x, look3.y, look3.z);
            boolean inSight = a.getSensing().hasLineOfSight(target);
            s.flag("target.in_sight", inSight);
            s.point("target.pos", pos);
            s.point("target.look", eye.plus(look.times(LOOK_REACH)));
            s.number("target.distance", pos.minus(head).length());
            Vec toHead = head.minus(eye);
            Vec toTarget = pos.minus(head);
            boolean eyes = inSight && angle(look, toHead) <= tree.tunable("eye_cone_deg", EYE_CONE_DEG)
                    && angle(a.facing(), new Vec(toTarget.x(), 0, toTarget.z())) <= tree.tunable("face_cone_deg", FACE_CONE_DEG);
            s.flag("target.eye_contact", eyes);
            s.flag("target.underground", !level.canSeeSky(target.blockPosition()));
            memory = memory.withPoint("target.last_pos", pos).withTimer("seen", (int) tree.tunable("memory", KNOWN_TICKS));
        }
        Vec last = memory.point("target.last_pos");
        boolean known = seen || memory.timer("seen") > 0 && last != null;
        s.flag("target.known", known);
        if (known && last != null) {
            s.point("target.last_pos", last);
            if (!seen) {
                s.number("target.distance", last.minus(head).length());
            }
        }
        s.flag("target.blessed", false);
        s.flag("grab.held", a.holding());
        s.flag("grab.survived", a.grabSurvived());
        boolean[] hurt = a.takeHurt();
        s.flag("hurt", hurt[0]);
        s.flag("hurt_hard", hurt[1]);
        s.number("health", a.getHealth() / Math.max(1.0f, a.getMaxHealth()));
        s.number("y", a.getY());
        s.number("light", level.getMaxLocalRawBrightness(a.blockPosition()));
        s.number("speed", a.speed());
        Crawl.Normal n = a.syncedNormal();
        s.flag("on_wall", n != Crawl.Normal.UP && n != Crawl.Normal.NONE);
        s.flag("airborne", n == Crawl.Normal.NONE);
        s.flag("underground", !level.canSeeSky(BlockPos.containing(head.x(), head.y(), head.z())));
        s.flag("blocked", a.lastBlocked());
        s.number("random", a.getRandom().nextDouble());
        Optional<Hearing.Estimate> heard = a.hearing().estimate(head, a.tickCount);
        s.flag("heard.any", heard.isPresent());
        if (heard.isPresent()) {
            Hearing.Estimate e = heard.get();
            s.point("heard.bearing", e.bearing());
            s.number("heard.error", e.error());
            s.number("heard.age", e.age());
            s.number("heard.distance", e.distance());
            s.flag("heard.loud", e.loud());
        }
        if (memory.point("home") == null) {
            memory = memory.withPoint("home", head);
        }
        s.point("home", memory.point("home"));
        a.remember(memory);
        return s.build();
    }

    /** effects: returns the nearest living survival or adventure player within {@code range} of {@code a}, or null; any player entity, so a test's mock player counts */
    static Player nearestPlayer(ServerLevel level, Aberrant a, double range) {
        Player best = null;
        double bestDistance = range;
        for (Player p : level.getEntities(EntityTypeTest.forClass(Player.class), a.getBoundingBox().inflate(range), Player::isAlive)) {
            if (!fairGame(p)) {
                continue;
            }
            double d = p.distanceTo(a);
            if (d <= bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    /** effects: returns whether {@code p} may be hunted: in survival or adventure by their game mode (a server player's mode is the truth; another player's abilities stand in) */
    static boolean fairGame(Player p) {
        if (p instanceof ServerPlayer sp) {
            GameType mode = sp.gameMode.getGameModeForPlayer();
            return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
        }
        return !p.isCreative() && !p.isSpectator();
    }

    /** effects: returns the angle between {@code a} and {@code b}, degrees; ninety when either is zero */
    static double angle(Vec a, Vec b) {
        double la = a.length(), lb = b.length();
        if (la < 1e-9 || lb < 1e-9) {
            return 90.0;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
    }
}
