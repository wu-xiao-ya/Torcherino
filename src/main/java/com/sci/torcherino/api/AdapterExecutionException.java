package com.sci.torcherino.api;

public final class AdapterExecutionException extends Exception {
    private final int consumedTicks;

    public AdapterExecutionException(int consumedTicks, String message, Throwable cause) {
        super(message, cause);
        this.consumedTicks = consumedTicks;
    }

    public int getConsumedTicks() {
        return consumedTicks;
    }
}
