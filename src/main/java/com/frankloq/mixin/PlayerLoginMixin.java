package com.frankloq.mixin;

import com.frankloq.reset.WorldResetManager;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;

@Mixin(PlayerList.class)
public class PlayerLoginMixin {

     // Intercepts the login sequence.
     // If the world is actively resetting, this kicks the player at the connection screen.
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void preventJoinDuringReset(SocketAddress address, NameAndId profile, CallbackInfoReturnable<Component> cir) {
        if (WorldResetManager.isResetting()) {
            cir.setReturnValue(Component.literal("§cThe world is currently resetting.\n§fPlease wait a few seconds and try again."));
        }
    }
}
