package com.sci.torcherino.compat;

import com.sci.torcherino.api.AdvanceResult;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActuallyAdditionsProcessingAdapterTest {
    static {
        Bootstrap.register();
    }

    private static final Fluid CANOLA_OIL = createFluid("test_canola_oil");
    private static final Fluid REFINED_OIL = createFluid("test_refined_oil");

    @Test
    void canolaPressBatchesAcrossCompletionBoundaries() throws Exception {
        FakeCanolaPress tile = new FakeCanolaPress();
        tile.inv.setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
        tile.storage.energy = 10000;
        ActuallyAdditionsProcessingAuditAdapter.CanolaAccess access =
            ActuallyAdditionsProcessingAuditAdapter.CanolaAccess.bind(
                FakeCanolaPress.class,
                CANOLA_OIL,
                Items.WHEAT,
                0
            );

        AdvanceResult result =
            ActuallyAdditionsProcessingAuditAdapter.advanceWithAccess(
                tile,
                65,
                access
            );

        assertEquals(65, result.getConsumedTicks());
        assertTrue(result.isSyncRequired());
        assertEquals(5, tile.currentProcessTime);
        assertEquals(1, tile.inv.getStackInSlot(0).getCount());
        assertEquals(160, tile.tank.getFluidAmount());
        assertEquals(10000 - 65 * 35, tile.storage.energy);
    }

    @Test
    void canolaPressResetsProgressWithoutSpendingEnergyWhenBlocked()
        throws Exception {
        FakeCanolaPress tile = new FakeCanolaPress();
        tile.inv.setStackInSlot(0, new ItemStack(Items.WHEAT, 1));
        tile.storage.energy = 10000;
        tile.currentProcessTime = 12;
        tile.tank.fillInternal(new FluidStack(CANOLA_OIL, 1960), true);
        ActuallyAdditionsProcessingAuditAdapter.CanolaAccess access =
            ActuallyAdditionsProcessingAuditAdapter.CanolaAccess.bind(
                FakeCanolaPress.class,
                CANOLA_OIL,
                Items.WHEAT,
                0
            );

        ActuallyAdditionsProcessingAuditAdapter.advanceWithAccess(
            tile,
            36,
            access
        );

        assertEquals(0, tile.currentProcessTime);
        assertEquals(10000, tile.storage.energy);
        assertEquals(1, tile.inv.getStackInSlot(0).getCount());
    }

    @Test
    void canolaPureProgressDoesNotRequestChunkSync() throws Exception {
        FakeCanolaPress tile = new FakeCanolaPress();
        tile.inv.setStackInSlot(0, new ItemStack(Items.WHEAT, 1));
        tile.storage.energy = 10000;
        ActuallyAdditionsProcessingAuditAdapter.CanolaAccess access =
            ActuallyAdditionsProcessingAuditAdapter.CanolaAccess.bind(
                FakeCanolaPress.class,
                CANOLA_OIL,
                Items.WHEAT,
                0
            );

        AdvanceResult result =
            ActuallyAdditionsProcessingAuditAdapter.advanceWithAccess(
                tile,
                4,
                access
            );

        assertEquals(4, result.getConsumedTicks());
        assertFalse(result.isSyncRequired());
        assertEquals(4, tile.currentProcessTime);
        assertEquals(10000 - 4 * 35, tile.storage.energy);
    }

    @Test
    void fermentingBarrelBatchesFluidConversions() throws Exception {
        FakeFermentingBarrel tile = new FakeFermentingBarrel();
        tile.canolaTank.fillInternal(
            new FluidStack(CANOLA_OIL, 240),
            true
        );
        ActuallyAdditionsProcessingAuditAdapter.BarrelAccess access =
            ActuallyAdditionsProcessingAuditAdapter.BarrelAccess.bind(
                FakeFermentingBarrel.class,
                REFINED_OIL
            );

        AdvanceResult result =
            ActuallyAdditionsProcessingAuditAdapter.advanceWithAccess(
                tile,
                250,
                access
            );

        assertEquals(250, result.getConsumedTicks());
        assertTrue(result.isSyncRequired());
        assertEquals(50, tile.currentProcessTime);
        assertEquals(80, tile.canolaTank.getFluidAmount());
        assertEquals(160, tile.oilTank.getFluidAmount());
    }

    @Test
    void barrelPureProgressDoesNotRequestChunkSync() throws Exception {
        FakeFermentingBarrel tile = new FakeFermentingBarrel();
        tile.canolaTank.fillInternal(
            new FluidStack(CANOLA_OIL, 80),
            true
        );
        ActuallyAdditionsProcessingAuditAdapter.BarrelAccess access =
            ActuallyAdditionsProcessingAuditAdapter.BarrelAccess.bind(
                FakeFermentingBarrel.class,
                REFINED_OIL
            );

        AdvanceResult result =
            ActuallyAdditionsProcessingAuditAdapter.advanceWithAccess(
                tile,
                4,
                access
            );

        assertEquals(4, result.getConsumedTicks());
        assertFalse(result.isSyncRequired());
        assertEquals(4, tile.currentProcessTime);
    }

    private static Fluid createFluid(String name) {
        Fluid fluid = new Fluid(
            name,
            new ResourceLocation("torcherino", name + "_still"),
            new ResourceLocation("torcherino", name + "_flow")
        );
        FluidRegistry.registerFluid(fluid);
        return FluidRegistry.getFluid(name);
    }

    static final class FakeCanolaPress extends TileEntity {
        public final FakeEnergyStorage storage = new FakeEnergyStorage();
        public final FluidTank tank = new FluidTank(2000);
        public final FakeInventory inv = new FakeInventory();
        public int currentProcessTime;

        public static boolean isCanola(ItemStack stack) {
            return !stack.isEmpty();
        }
    }

    static final class FakeFermentingBarrel extends TileEntity {
        public final FluidTank canolaTank = new FluidTank(2000);
        public final FluidTank oilTank = new FluidTank(2000);
        public int currentProcessTime;
    }

    static final class FakeEnergyStorage implements IEnergyStorage {
        private int energy;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return extractEnergyInternal(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return 10000;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        public int extractEnergyInternal(int amount, boolean simulate) {
            int extracted = Math.min(amount, energy);
            if (!simulate) {
                energy -= extracted;
            }
            return extracted;
        }
    }

    static final class FakeInventory extends ItemStackHandler {
        private FakeInventory() {
            super(1);
        }
    }
}
