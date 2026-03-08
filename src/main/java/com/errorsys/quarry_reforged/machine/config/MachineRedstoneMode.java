package com.errorsys.quarry_reforged.machine.config;

public enum MachineRedstoneMode {
    IGNORED,
    HIGH,
    LOW;

    public MachineRedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }
}
