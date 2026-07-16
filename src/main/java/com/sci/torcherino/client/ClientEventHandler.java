package com.sci.torcherino.client;

import com.sci.torcherino.network.KeyHandler;
import com.sci.torcherino.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ClientEventHandler {
    private boolean state;

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isGamePaused() || minecraft.player == null) {
            return;
        }
        boolean keyDown = minecraft.gameSettings.isKeyDown(KeyHandler.usageKey);
        if (keyDown != state) {
            PacketHandler.sendUpdateToSever(keyDown);
            state = keyDown;
        }
    }
}
