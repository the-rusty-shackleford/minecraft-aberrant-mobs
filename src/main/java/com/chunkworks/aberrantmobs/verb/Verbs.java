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
package com.chunkworks.aberrantmobs.verb;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.domain.Crawl;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.mind.Intent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The verbs by name, and the making of them. The protocol's own are
 * registered here; another mod may add its own before the profiles load
 * (a profile naming a verb nobody registered is refused at load).
 *
 * <ul>
 * <li><b>hold</b>: stop where it is.
 * <li><b>wander</b>: a point eight to sixteen blocks off in its face, a
 *     new one when reached or after ten seconds; {@code dig} to bore
 *     through rock on the way.
 * <li><b>approach</b>: toward the point sense named by {@code target}
 *     ({@code target.pos} by default), digging unless {@code dig} is
 *     false, quietly and at six tenths speed when {@code quiet}.
 * <li><b>chase</b>: after {@code target.pos}, digging, loud, full speed.
 * <li><b>flee</b>: away from {@code target.pos}, twenty-four blocks, digging.
 * <li><b>dig</b>: {@code toward} down (default) or up, twelve blocks, digging.
 * <li><b>climb</b>: away from the point named by {@code away_from}
 *     ({@code target.look}), eight blocks along its face, so it leaves
 *     the target's view for a wall.
 * <li><b>pounce</b>: one leap at {@code target.pos}.
 * <li><b>grab</b>, <b>release</b>, <b>bite</b>: named now for the tree;
 *     they do their work in phase 5.
 * </ul>
 */
public final class Verbs {
    private Verbs() {}

    private static final Map<String, Supplier<Verb>> REGISTRY = new LinkedHashMap<>();
    private static boolean frozen;

    static {
        register("hold", Hold::new);
        register("wander", Wander::new);
        register("approach", () -> new Approach(false));
        register("chase", () -> new Approach(true));
        register("flee", Flee::new);
        register("dig", Dig::new);
        register("climb", Climb::new);
        register("pounce", Pounce::new);
        register("grab", Nothing::new);
        register("release", Nothing::new);
        register("bite", Nothing::new);
    }

    /**
     * effects: makes {@code name} a verb, made by {@code maker} for each creature<br>
     * throws: {@link IllegalStateException} once a profile has been read
     */
    public static synchronized void register(String name, Supplier<Verb> maker) {
        if (frozen) {
            throw new IllegalStateException("the verbs are set once the profiles load: " + name);
        }
        REGISTRY.put(name, maker);
    }

    /** effects: returns the verbs' names, and freezes the registry */
    public static synchronized Set<String> names() {
        frozen = true;
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** effects: returns a fresh verb named {@code name} for one creature, or a hold for a name unknown */
    public static synchronized Verb make(String name) {
        Supplier<Verb> maker = REGISTRY.get(name);
        return maker == null ? new Hold() : maker.get();
    }

    static double speed(Aberrant a) {
        return a.profile() == null ? 0.3 : a.profile().stats().speed();
    }

    static boolean bool(Intent intent, String arg, boolean fallback) {
        String v = intent.arg(arg, null);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    /** effects: returns a unit direction in the face of {@code pose}, turned by {@code angle} from its heading */
    static Vec inFace(Crawl.Pose pose, double angle) {
        Vec n = pose.airborne() ? Vec.Y : pose.normal().dir;
        Vec h = pose.heading();
        Vec side = n.cross(h);
        return h.times(Math.cos(angle)).plus(side.times(Math.sin(angle))).normalized();
    }

    static final class Hold implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {
            a.stopCrawl();
        }

        @Override
        public void tick(Aberrant a, Intent intent) {}

        @Override
        public void end(Aberrant a) {}
    }

    static final class Nothing implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {}

        @Override
        public void tick(Aberrant a, Intent intent) {}

        @Override
        public void end(Aberrant a) {}
    }

    static final class Wander implements Verb {
        private static final int LEG = 200;
        private int until = -1;

        @Override
        public void begin(Aberrant a, Intent intent) {
            pick(a, intent);
        }

        @Override
        public void tick(Aberrant a, Intent intent) {
            if (!a.crawling() || a.tickCount >= until) {
                pick(a, intent);
            }
        }

        private void pick(Aberrant a, Intent intent) {
            Crawl.Pose pose = a.crawlPose();
            if (pose == null) {
                return;
            }
            double angle = (a.getRandom().nextDouble() - 0.5) * Math.PI;
            double reach = 8 + 8 * a.getRandom().nextDouble();
            a.setCrawlTarget(pose.centre().plus(inFace(pose, angle).times(reach)), bool(intent, "dig", false), speed(a) * 0.7);
            a.setQuiet(true);
            until = a.tickCount + LEG;
        }

        @Override
        public void end(Aberrant a) {
            a.stopCrawl();
        }
    }

    static final class Approach implements Verb {
        private final boolean chase;

        Approach(boolean chase) {
            this.chase = chase;
        }

        @Override
        public void begin(Aberrant a, Intent intent) {
            tick(a, intent);
        }

        @Override
        public void tick(Aberrant a, Intent intent) {
            Vec point = a.sense(chase ? "target.pos" : intent.arg("target", "target.pos"));
            if (point == null) {
                a.stopCrawl();
                return;
            }
            boolean quiet = !chase && bool(intent, "quiet", false);
            a.setCrawlTarget(point, chase || bool(intent, "dig", true), speed(a) * (quiet ? 0.6 : 1.0));
            a.setQuiet(quiet);
        }

        @Override
        public void end(Aberrant a) {
            a.stopCrawl();
        }
    }

    static final class Flee implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {
            tick(a, intent);
        }

        @Override
        public void tick(Aberrant a, Intent intent) {
            Vec from = a.sense("target.pos");
            Crawl.Pose pose = a.crawlPose();
            if (from == null || pose == null) {
                return;
            }
            Vec away = pose.centre().minus(from);
            if (away.length() < 1e-6) {
                away = pose.heading();
            }
            a.setCrawlTarget(pose.centre().plus(away.normalized().times(24)), true, speed(a));
            a.setQuiet(false);
        }

        @Override
        public void end(Aberrant a) {
            a.stopCrawl();
        }
    }

    static final class Dig implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {
            tick(a, intent);
        }

        @Override
        public void tick(Aberrant a, Intent intent) {
            Crawl.Pose pose = a.crawlPose();
            if (pose == null || a.crawling()) {
                return;
            }
            double dy = "up".equals(intent.arg("toward", "down")) ? 12 : -12;
            a.setCrawlTarget(pose.centre().plus(new Vec(0, dy, 0)), true, speed(a));
            a.setQuiet(bool(intent, "quiet", true));
        }

        @Override
        public void end(Aberrant a) {
            a.stopCrawl();
        }
    }

    static final class Climb implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {
            tick(a, intent);
        }

        @Override
        public void tick(Aberrant a, Intent intent) {
            Crawl.Pose pose = a.crawlPose();
            Vec from = a.sense(intent.arg("away_from", "target.look"));
            if (pose == null) {
                return;
            }
            Vec away = from == null ? pose.heading() : pose.centre().minus(from);
            if (away.length() < 1e-6) {
                away = pose.heading();
            }
            a.setCrawlTarget(pose.centre().plus(away.normalized().times(8)), false, speed(a) * 0.6);
            a.setQuiet(true);
        }

        @Override
        public void end(Aberrant a) {
            a.stopCrawl();
        }
    }

    static final class Pounce implements Verb {
        @Override
        public void begin(Aberrant a, Intent intent) {
            Vec at = a.sense("target.pos");
            if (at != null) {
                a.pounce(at.minus(new Vec(0, 0, 0)));
            }
        }

        @Override
        public void tick(Aberrant a, Intent intent) {}

        @Override
        public void end(Aberrant a) {}
    }
}
