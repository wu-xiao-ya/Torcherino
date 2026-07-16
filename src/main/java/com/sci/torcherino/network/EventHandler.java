package com.sci.torcherino.network;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.acceleration.AccelerationService;
import com.sci.torcherino.blocks.tiles.TileTorcherino;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EventHandler {
    private boolean state;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
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

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.side == Side.SERVER
            && event.phase == TickEvent.Phase.END
            && event.world instanceof net.minecraft.world.WorldServer) {
            AccelerationService.tick((net.minecraft.world.WorldServer) event.world);
        }
    }

    @SubscribeEvent
    public void worldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote
            && event.getWorld() instanceof net.minecraft.world.WorldServer) {
            AccelerationService.unload((net.minecraft.world.WorldServer) event.getWorld());
        }
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Torcherino.keyStates.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerRightClick(RightClickBlock event) {
        if (event.getHand() == EnumHand.OFF_HAND) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (!(tile instanceof TileTorcherino)) {
            return;
        }
        if (!event.getWorld().isRemote) {
            TileTorcherino torch = (TileTorcherino) tile;
            Boolean modifier = Torcherino.keyStates.get(player.getUniqueID());
            torch.changeMode(modifier != null && modifier.booleanValue());
            player.sendStatusMessage(torch.getDescription(), true);
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }
}
