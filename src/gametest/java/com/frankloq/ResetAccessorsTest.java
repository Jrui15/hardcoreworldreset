package com.frankloq;

import com.frankloq.mixin.MinecraftServerAccessor;
import com.frankloq.mixin.PlayerAccessor;
import com.frankloq.mixin.ServerLevelAccessor;
import com.frankloq.reset.WorldInjectionUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ResetAccessorsTest {
    @GameTest
    public void serverAccessors(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var access = (MinecraftServerAccessor) server;
        helper.assertTrue(access.hwr$storage() != null, "World storage is accessible");
        helper.assertTrue(access.hwr$executor() != null, "Server executor is accessible");
        helper.assertTrue(access.hwr$levels().get(helper.getLevel().dimension()) == helper.getLevel(),
                "Accessor exposes the live dimension map");
        helper.assertTrue(((ServerLevelAccessor) helper.getLevel()).hwr$customSpawners() != null,
                "Custom spawners are accessible");
        access.hwr$setWorldGenSettings(server.getWorldGenSettings());
        helper.succeed();
    }

    @GameTest
    public void playerStateWipe(GameTestHelper helper) {
        var player = createPlayer(helper);
        var previous = player.getFoodData();
        ((PlayerAccessor) player).hwr$setFoodData(new FoodData());
        helper.assertTrue(player.getFoodData() != previous, "Food data accessor replaces state");
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.awardStat(net.minecraft.stats.Stats.JUMP, 7);
        player.setTicksFrozen(80);
        player.setRemainingFireTicks(100);
        player.experienceLevel = 12;
        player.getFoodData().setFoodLevel(1);
        WorldInjectionUtils.wipePlayerState(player);
        helper.assertTrue(player.getInventory().isEmpty(), "Inventory is cleared");
        helper.assertTrue(player.getEnderChestInventory().isEmpty(), "Ender chest is cleared");
        helper.assertTrue(player.experienceLevel == 0 && player.totalExperience == 0, "Experience is cleared");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "Hunger is restored");
        helper.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP)) == 0,
                "Statistics are cleared in memory");
        helper.assertTrue(player.getTicksFrozen() == 0 && !player.isOnFire(), "Environmental damage is cleared");
        helper.succeed();
    }

    @GameTest
    public void cancelDeathCountdownRestoresPlayers(GameTestHelper helper) {
        var first = createPlayer(helper);
        var second = createPlayer(helper);
        first.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        first.die(first.damageSources().generic());
        second.die(second.damageSources().generic());
        helper.assertTrue(first.isSpectator() && second.isSpectator(), "Death mixin protects both players");
        helper.assertTrue(HardcoreWorldReset.cancelCountdown(helper.getLevel().getServer()), "Countdown cancels");
        helper.assertTrue(first.gameMode() == net.minecraft.world.level.GameType.ADVENTURE &&
                second.gameMode() == net.minecraft.world.level.GameType.SURVIVAL, "Original modes are restored");
        helper.assertTrue(!com.frankloq.reset.WorldResetManager.isResetting(), "No reset began");
        helper.succeed();
    }
    private static net.minecraft.server.level.ServerPlayer createPlayer(GameTestHelper helper) {
        return createPlayer(helper.getLevel(), java.util.UUID.randomUUID());
    }

    static net.minecraft.server.level.ServerPlayer createPlayer(net.minecraft.server.level.ServerLevel level, java.util.UUID id) {
        var server = level.getServer();
        var profile = new com.mojang.authlib.GameProfile(id, "hwr-test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var saved = server.getPlayerList().loadPlayerData(new net.minecraft.server.players.NameAndId(profile))
                .map(tag -> net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess(), tag));
        var position = saved.flatMap(input -> input.read(net.minecraft.server.level.ServerPlayer.SavedPosition.MAP_CODEC));
        var spawnLevel = position.flatMap(net.minecraft.server.level.ServerPlayer.SavedPosition::dimension)
                .map(server::getLevel).orElse(level);
        var player = new net.minecraft.server.level.ServerPlayer(server, spawnLevel, profile,
                cookie.clientInformation());
        saved.ifPresent(player::load);
        var spawn = position.flatMap(net.minecraft.server.level.ServerPlayer.SavedPosition::position)
                .orElse(net.minecraft.world.phys.Vec3.atBottomCenterOf(spawnLevel.getRespawnData().pos()));
        player.snapTo(spawn, 0, 0);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
