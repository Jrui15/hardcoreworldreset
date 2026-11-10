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
        var player = helper.makeMockServerPlayerInLevel();
        var previous = player.getFoodData();
        ((PlayerAccessor) player).hwr$setFoodData(new FoodData());
        helper.assertTrue(player.getFoodData() != previous, "Food data accessor replaces state");
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.experienceLevel = 12;
        player.getFoodData().setFoodLevel(1);
        WorldInjectionUtils.wipePlayerState(player);
        helper.assertTrue(player.getInventory().isEmpty(), "Inventory is cleared");
        helper.assertTrue(player.getEnderChestInventory().isEmpty(), "Ender chest is cleared");
        helper.assertTrue(player.experienceLevel == 0 && player.totalExperience == 0, "Experience is cleared");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "Hunger is restored");
        helper.succeed();
    }
}
