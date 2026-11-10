package com.frankloq;

import com.frankloq.reset.WorldResetManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opt-in destructive integration suite. Only runs in the dedicated disposable test run. */
public class ResetIntegration implements ModInitializer {
    private ServerPlayer first, second, rescued;
    private long originalSeed;
    private java.util.UUID offlineId;
    private int stage, ticks;

    @Override public void onInitialize() {
        if (!Boolean.getBoolean("hwr.integration")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            originalSeed = server.getWorldGenSettings().options().seed();
            first = ResetAccessorsTest.createPlayer(server.overworld(), java.util.UUID.randomUUID());
            second = ResetAccessorsTest.createPlayer(server.overworld(), java.util.UUID.randomUUID());
            offlineId = java.util.UUID.randomUUID();
            var offline = ResetAccessorsTest.createPlayer(server.overworld(), offlineId);
            offline.getInventory().add(new ItemStack(Items.DIAMOND, 32));
            offline.awardStat(net.minecraft.stats.Stats.JUMP, 9);
            server.getPlayerList().remove(offline);
            first.getInventory().add(new ItemStack(Items.DIAMOND, 32));
            second.getInventory().add(new ItemStack(Items.DIAMOND, 32));
            HardcoreWorldReset.reuseSeed = true;
            first.die(first.damageSources().generic());
            second.die(second.damageSources().generic());
            stage = 1;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stage == 0 || stage == 3) return;
            try {
                if (++ticks > 1200) throw new AssertionError("Reset timed out");
                if (WorldResetManager.isResetting() || WorldResetManager.getCurrentTry(server) != stage + 1) return;
                for (var player : java.util.List.of(first, second)) {
                    check(player.level() == server.overworld(), "Player returned to fresh overworld");
                    check(player.getInventory().isEmpty(), "Player inventory cleared");
                    check(!player.isSpectator(), "Player survival restored");
                }
                long seed = server.getWorldGenSettings().options().seed();
                if (stage == 1) {
                    check(seed == originalSeed, "Reused seed preserved");
                    var returning = ResetAccessorsTest.createPlayer(server.overworld(), offlineId);
                    check(returning.getInventory().isEmpty(), "Offline inventory reset");
                    check(returning.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP)) == 0,
                            "Offline stats reset");
                    check(LimboDimension.teleportToLimbo(returning), "Limbo teleport succeeded");
                    server.getPlayerList().remove(returning);
                    rescued = ResetAccessorsTest.createPlayer(server.overworld(), offlineId);
                    check(rescued.level().dimension().equals(LimboDimension.LIMBO_KEY), "Saved limbo position loaded on reconnect");
                    HardcoreWorldReset.reuseSeed = false;
                    first.die(first.damageSources().generic());
                    stage = 2;
                } else {
                    check(seed != originalSeed, "New seed generated");
                    check(rescued.level() == server.overworld() && !rescued.isSpectator(), "Offline limbo player recovered");
                    HardcoreWorldReset.LOGGER.info("HWR INTEGRATION PASS: multiplayer death, limbo, both seeds, offline progress");
                    java.nio.file.Files.writeString(java.nio.file.Path.of("hwr-integration-passed"), "PASS");
                    stage = 3;
                    server.halt(false);
                }
            } catch (Throwable error) {
                HardcoreWorldReset.LOGGER.error("HWR INTEGRATION FAILED", error);
                stage = 3;
                server.halt(false);
            }
        });
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
