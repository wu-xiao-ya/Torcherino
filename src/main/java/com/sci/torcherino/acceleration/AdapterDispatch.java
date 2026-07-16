package com.sci.torcherino.acceleration;

import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdvanceResult;

final class AdapterDispatch {
    private final String adapterId;
    private final AdapterClassification classification;
    private final AdvanceResult result;
    private final boolean blacklisted;

    AdapterDispatch(String adapterId, AdapterClassification classification, AdvanceResult result, boolean blacklisted) {
        this.adapterId = adapterId;
        this.classification = classification;
        this.result = result;
        this.blacklisted = blacklisted;
    }

    String getAdapterId() {
        return adapterId;
    }

    AdapterClassification getClassification() {
        return classification;
    }

    AdvanceResult getResult() {
        return result;
    }

    boolean isBlacklisted() {
        return blacklisted;
    }
}
