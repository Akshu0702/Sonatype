package com.platform.tenants.provisioning;

/** Bit positions for tenants.provisioning_steps_completed. Never renumber a shipped bit. */
public enum ProvisioningStep {
    REALM_CREATED(1),
    CLIENT_CREATED(2),
    ADMIN_USER_CREATED(4);

    public static final int ALL = REALM_CREATED.bit | CLIENT_CREATED.bit | ADMIN_USER_CREATED.bit;

    private final int bit;

    ProvisioningStep(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public boolean isDoneIn(int mask) {
        return (mask & bit) != 0;
    }
}
