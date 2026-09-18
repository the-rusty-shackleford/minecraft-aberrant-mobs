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
package com.chunkworks.aberrantmobs.domain.frame;

import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Vec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: every old/new gravity; oblique/rebased look; start/mid/end; live look/movement; interrupted transition; invalid time. */
final class PoseBlendTest {
    private static void same(Quat a, Quat b) {
        for (Vec axis : new Vec[]{Vec.X,Vec.Y,Vec.Z}) assertTrue(a.rotate(axis).minus(b.rotate(axis)).length()<1e-8);
    }
    @Test void allFramesPreserveTheDisplayedPoseAndReachTheExactEndpoint() {
        for (Gravity a:Gravity.values()) for (Gravity b:Gravity.values()) {
            Quat old=Frame.of(a).rotation().times(CameraAngles.compose(new CameraAngles.Angles(37,23,0)));
            Quat next=Frame.of(b).rotation().times(CameraAngles.compose(new CameraAngles.Angles(-90,-12,0)));
            Vec from=new Vec(2,3,4), to=new Vec(3,5,2);
            PoseBlend blend=PoseBlend.between(old,from,next,to,8.5);
            same(old,blend.rotation(next,8.5));
            assertEquals(from,blend.position(to,8.5));
            same(next,blend.rotation(next,18.5));
            assertEquals(to,blend.position(to,18.5));
            assertEquals(0.5,blend.remaining(13.5),1e-12);
            assertTrue(blend.done(18.5));
        }
    }
    @Test void mouseAndMovementRemainLiveDuringTheTurn() {
        Quat target=Frame.of(Gravity.WEST).rotation();
        PoseBlend blend=PoseBlend.between(Quat.IDENTITY,Vec.ZERO,target,Vec.X,0);
        Quat mouse=Quat.fromAxisAngle(Vec.Y,0.2);
        same(blend.rotation(target,3).times(mouse),blend.rotation(target.times(mouse),3));
        assertEquals(Vec.Z,blend.position(Vec.X.plus(Vec.Z),3).minus(blend.position(Vec.X,3)));
    }
    @Test void interruptingATurnStartsAtTheCurrentlyShownPose() {
        Quat target=Frame.of(Gravity.EAST).rotation();
        PoseBlend first=PoseBlend.between(Quat.IDENTITY,Vec.ZERO,target,Vec.X,0);
        Quat shown=first.rotation(target,4);
        Vec position=first.position(Vec.X,4);
        Quat next=Frame.of(Gravity.UP).rotation();
        PoseBlend second=PoseBlend.between(shown,position,next,Vec.Y,4);
        same(shown,second.rotation(next,4));
        assertEquals(position,second.position(Vec.Y,4));
        same(next,second.rotation(next,14));
    }
    @Test void invalidTimeIsRejected() {
        for(double d:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new PoseBlend(Quat.IDENTITY,Vec.ZERO,0,d));
    }
}
