package com.hosseinasgari.aiplayer.brain.navigation;

public final class StuckDetector {
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int stagnantChecks;
    private int totalRecoveryAttempts;
    private final int maxRecoveryAttempts;

    public StuckDetector(int maxRecoveryAttempts) {
        this.maxRecoveryAttempts = Math.max(2, maxRecoveryAttempts);
    }

    public boolean recordSampleAndCheckStuck(double x, double y, double z, boolean intendsMovement) {
        if (!intendsMovement) {
            reset(x, y, z);
            return false;
        }

        double dx = x - anchorX;
        double dy = y - anchorY;
        double dz = z - anchorZ;
        double movedSq = dx * dx + dy * dy + dz * dz;

        if (movedSq >= 0.45 * 0.45) {
            anchorX = x;
            anchorY = y;
            anchorZ = z;
            stagnantChecks = 0;
            return false;
        }

        stagnantChecks++;
        if (stagnantChecks >= 2) {
            stagnantChecks = 0;
            totalRecoveryAttempts++;
            return true;
        }
        return false;
    }

    public boolean hasExhaustedRetries() {
        return totalRecoveryAttempts > maxRecoveryAttempts;
    }

    public int recoveryAttempts() {
        return totalRecoveryAttempts;
    }

    public void reset(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.stagnantChecks = 0;
        this.totalRecoveryAttempts = 0;
    }
}
