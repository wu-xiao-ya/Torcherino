package com.sci.torcherino.blocks.tiles;

import com.sci.torcherino.acceleration.AccelerationService;
import com.sci.torcherino.acceleration.TorchSnapshot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class TileTorcherino extends TileEntity implements ITickable {
    private static final String[] MODES = new String[]{
        "Stopped",
        "Area: 3x3x3",
        "Area: 5x3x5",
        "Area: 7x3x7",
        "Area: 9x3x9"
    };
    private static final int SPEEDS = 4;

    private boolean poweredByRedstone;
    private byte speed;
    private byte mode;
    private boolean published;

    protected int speed(int base) {
        return base;
    }

    @Override
    public void update() {
        if (!world.isRemote && !published) {
            publish();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            publish();
        }
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            AccelerationService.remove(this);
        }
        published = false;
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) {
            AccelerationService.remove(this);
        }
        published = false;
        super.onChunkUnload();
    }

    public TorchSnapshot createAccelerationSnapshot() {
        int multiplier = poweredByRedstone ? 0 : speed(speed);
        return new TorchSnapshot(pos.toLong(), mode, multiplier);
    }

    public void setPoweredByRedstone(boolean poweredByRedstone) {
        if (this.poweredByRedstone == poweredByRedstone) {
            return;
        }
        this.poweredByRedstone = poweredByRedstone;
        changed();
    }

    public void changeMode(boolean modifier) {
        if (modifier) {
            speed = (byte) (speed < SPEEDS ? speed + 1 : 0);
        } else {
            mode = (byte) (mode < MODES.length - 1 ? mode + 1 : 0);
        }
        changed();
    }

    public TextComponentString getDescription() {
        return new TextComponentString(
            MODES[mode] + " | Speed: " + speed(speed) * 100 + "%"
        );
    }

    public String getMode() {
        return MODES[mode];
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("Speed", speed);
        tag.setByte("Mode", mode);
        tag.setBoolean("PoweredByRedstone", poweredByRedstone);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        speed = (byte) clamp(tag.getByte("Speed"), 0, SPEEDS);
        mode = (byte) clamp(tag.getByte("Mode"), 0, MODES.length - 1);
        poweredByRedstone = tag.getBoolean("PoweredByRedstone");
        published = false;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), -999, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(
        World world,
        BlockPos pos,
        IBlockState oldState,
        IBlockState newState
    ) {
        return oldState.getBlock() != newState.getBlock();
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            publish();
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private void publish() {
        AccelerationService.upsert(this);
        published = true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
