package com.sci.torcherino.api;

public final class AdapterProbe {
    private final boolean available;
    private final String versionSignature;
    private final String detail;

    private AdapterProbe(boolean available, String versionSignature, String detail) {
        this.available = available;
        this.versionSignature = versionSignature;
        this.detail = detail;
    }

    public static AdapterProbe available(String versionSignature, String detail) {
        return new AdapterProbe(true, versionSignature, detail);
    }

    public static AdapterProbe unavailable(String detail) {
        return new AdapterProbe(false, "unavailable", detail);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getVersionSignature() {
        return versionSignature;
    }

    public String getDetail() {
        return detail;
    }
}
