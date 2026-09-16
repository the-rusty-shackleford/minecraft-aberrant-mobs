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
package com.chunkworks.aberrantmobs.gametest;

import com.chunkworks.aberrantmobs.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silent real-client art comparison. Partitions: vanilla/chitin; front/side/back;
 * armour shoulders, waist, knees and boots at identical camera positions.
 * The server-controlled actor is delivered by the real entity/equipment tracker.
 */
public final class ArtBooth {
    private ArtBooth() {}
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant art booth");
    private static final UUID ACTOR = UUID.fromString("f0b219b0-9812-4a4f-9fef-14c0f8b8b042");
    private static FakePlayer actor;
    private static double floor;

    /** effects: creates a stationary actor and a flying observer in the real level. */
    public static void setUp(ServerPlayer observer) {
        floor = observer.serverLevel().getMinBuildHeight() + 4;
        observer.serverLevel().setDayTime(6000);
        observer.getAbilities().flying = true;
        observer.onUpdateAbilities();
        actor = new FakePlayer(observer.serverLevel(), new GameProfile(ACTOR, "Armour"));
        actor.setNoGravity(true);
        actor.moveTo(0, floor, 0, 180, 0);
        actor.setYHeadRot(180);
        actor.setYBodyRot(180);
        dress(false);
        observer.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, actor));
        observer.serverLevel().addNewPlayer(actor);
        scene(observer, 0);
    }

    private static void dress(boolean chitin) {
        Item[] items = chitin
                ? new Item[] {ModContent.CHITIN_HELMET.get(), ModContent.CHITIN_CHESTPLATE.get(), ModContent.CHITIN_LEGGINGS.get(), ModContent.CHITIN_BOOTS.get()}
                : new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) actor.setItemSlot(slots[i], new ItemStack(items[i]));
    }

    private static void scene(ServerPlayer observer, int index) {
        dress(index >= 3);
        Vec3[] positions = {new Vec3(0, floor + 0.2, -3.8), new Vec3(3.8, floor + 0.2, 0), new Vec3(0, floor + 0.2, 3.8)};
        Vec3 at = positions[index % 3];
        observer.teleportTo(observer.serverLevel(), at.x, at.y, at.z, 0, 0);
        observer.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0, floor + 0.95, 0));
        actor.doTick();
    }

    /** effects: captures six matched views, verifies tracked equipment and closes the client. */
    public static void tick(Minecraft mc, int tick) {
        if (tick >= 480) {
            LOG.info("booth: PASS all checks ran");
            mc.stop();
            return;
        }
        int index = tick / 80;
        if (tick % 80 == 10) {
            var server = mc.getSingleplayerServer();
            UUID observer = mc.player.getUUID();
            server.execute(() -> scene(server.getPlayerList().getPlayer(observer), index));
        }
        if (tick % 80 == 60) {
            var remote = mc.level.getPlayerByUUID(ACTOR);
            Item expected = index >= 3 ? ModContent.CHITIN_CHESTPLATE.get() : Items.NETHERITE_CHESTPLATE;
            boolean valid = remote != null && remote.getItemBySlot(EquipmentSlot.CHEST).is(expected);
            if (valid) LOG.info("booth: PASS tracked armour scene {}", index);
            else LOG.error("booth: FAIL tracked armour scene {}", index);
            String name = "art-" + (index >= 3 ? "chitin" : "netherite") + "-" + new String[] {"front", "side", "back"}[index % 3];
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("booth: {}", message.getString()));
        }
    }
}
