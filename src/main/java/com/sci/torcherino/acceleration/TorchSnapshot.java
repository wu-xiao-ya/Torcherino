package com.sci.torcherino.acceleration;

public final class TorchSnapshot {
    private final long pos;
    private final int range;
    private final int multiplier;

    public TorchSnapshot(long pos, int range, int multiplier) {
        this.pos = pos;
        this.range = range;
        this.multiplier = multiplier;
    }

    public long getPos() {
        return pos;
    }

    public int getRange() {
        return range;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public boolean isActive() {
        return range > 0 && multiplier > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TorchSnapshot)) {
            return false;
        }
        TorchSnapshot other = (TorchSnapshot) obj;
        return pos == other.pos && range == other.range && multiplier == other.multiplier;
    }

    @Override
    public int hashCode() {
        int result = (int) (pos ^ (pos >>> 32));
        result = 31 * result + range;
        result = 31 * result + multiplier;
        return result;
    }
}
