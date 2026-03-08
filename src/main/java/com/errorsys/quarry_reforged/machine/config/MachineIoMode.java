package com.errorsys.quarry_reforged.machine.config;

public enum MachineIoMode {
    NONE,
    INPUT,
    OUTPUT,
    BOTH;

    public boolean allowsInput() {
        return this == INPUT || this == BOTH;
    }

    public boolean allowsOutput() {
        return this == OUTPUT || this == BOTH;
    }
}
