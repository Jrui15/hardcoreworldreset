package com.frankloq.reset;

public class WorldInjectionUtils {
    public static void wipePlayerState(net.minecraft.server.level.ServerPlayer player) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();

        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
        player.totalExperience = 0;
        player.setScore(0);

        player.setHealth(player.getMaxHealth());
        ((com.frankloq.mixin.PlayerAccessor) player).hwr$setFoodData(new net.minecraft.world.food.FoodData());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.removeAllEffects();

        player.clearFire();

        // Compatibility for Trinkets mod
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets")) {
            try {
                // Find the Trinkets API class
                Class<?> trinketsApi = Class.forName("dev.emi.trinkets.api.TrinketsApi");
                java.lang.reflect.Method getComponent = trinketsApi.getMethod("getTrinketComponent", net.minecraft.world.entity.LivingEntity.class);

                // Get the player's Trinket component
                java.util.Optional<?> opt = (java.util.Optional<?>) getComponent.invoke(null, player);

                if (opt.isPresent()) {
                    Object trinketComponent = opt.get();
                    java.lang.reflect.Method getInventory = trinketComponent.getClass().getMethod("getInventory");

                    // Trinkets stores items in a Map<String, Map<String, TrinketInventory>>
                    java.util.Map<?, ?> inventoryMap = (java.util.Map<?, ?>) getInventory.invoke(trinketComponent);

                    // Loop through all custom accessory slots and clear them
                    for (Object groupMapObj : inventoryMap.values()) {
                        java.util.Map<?, ?> groupMap = (java.util.Map<?, ?>) groupMapObj;
                        for (Object trinketInvObj : groupMap.values()) {
                            if (trinketInvObj instanceof net.minecraft.world.Container) {
                                ((net.minecraft.world.Container) trinketInvObj).clearContent();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                com.frankloq.HardcoreWorldReset.LOGGER.error("not wiping allat", e);
            }
        }
    }
}
