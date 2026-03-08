package com.errorsys.quarry_reforged.machine.config;

public enum ItemIoGroup {
    UPGRADES,
    OUTPUT;

    public String getTranslationKey() {
        return switch (this) {
            case UPGRADES -> "gui.quarry_reforged.io.group.upgrades";
            case OUTPUT -> "gui.quarry_reforged.io.group.output";
        };
    }

    public MachineIoMode defaultMode() {
        return switch (this) {
            case UPGRADES -> MachineIoMode.NONE;
            case OUTPUT -> MachineIoMode.OUTPUT;
        };
    }

    public boolean supports(MachineIoMode mode) {
        if (this == OUTPUT) {
            return mode == MachineIoMode.NONE || mode == MachineIoMode.OUTPUT;
        }
        return true;
    }

    public MachineIoMode nextMode(MachineIoMode current) {
        MachineIoMode[] values = MachineIoMode.values();
        MachineIoMode next = current;
        for (int i = 0; i < values.length; i++) {
            next = values[(next.ordinal() + 1) % values.length];
            if (supports(next)) return next;
        }
        return current;
    }
}
