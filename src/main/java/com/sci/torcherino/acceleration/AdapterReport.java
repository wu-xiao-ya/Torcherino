package com.sci.torcherino.acceleration;

import com.sci.torcherino.api.AdapterClassification;

public final class AdapterReport {
    private final String id;
    private final AdapterClassification classification;
    private final boolean enabled;
    private final String signature;
    private final String detail;

    AdapterReport(String id, AdapterClassification classification, boolean enabled, String signature, String detail) {
        this.id = id;
        this.classification = classification;
        this.enabled = enabled;
        this.signature = signature;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }

    public AdapterClassification getClassification() {
        return classification;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSignature() {
        return signature;
    }

    public String getDetail() {
        return detail;
    }
}
