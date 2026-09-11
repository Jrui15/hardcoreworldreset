package com.frankloq.mixin;

import com.frankloq.HardcoreWorldReset;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerDeathMixin {

	@Inject(
			method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void onPlayerDeath(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;

		if (!HardcoreWorldReset.isModEnabled()) {
			return; // The mod is off, let the game handle the death normally.
		}

		HardcoreWorldReset.LOGGER.info(
				"MIXIN FIRED for player: {}",
				player.getName().getString()
		);

		ci.cancel();
		HardcoreWorldReset.handlePlayerDeath(player, damageSource);
	}
}