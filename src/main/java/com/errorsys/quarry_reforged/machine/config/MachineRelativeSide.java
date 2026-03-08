package com.errorsys.quarry_reforged.machine.config;

import net.minecraft.util.math.Direction;

public enum MachineRelativeSide {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    UP,
    DOWN;

    public String getTranslationKey() {
        return switch (this) {
            case FRONT -> "gui.quarry_reforged.io.side.front";
            case BACK -> "gui.quarry_reforged.io.side.back";
            case LEFT -> "gui.quarry_reforged.io.side.left";
            case RIGHT -> "gui.quarry_reforged.io.side.right";
            case UP -> "gui.quarry_reforged.io.side.up";
            case DOWN -> "gui.quarry_reforged.io.side.down";
        };
    }

    public static MachineRelativeSide fromWorld(Direction machineFacing, Direction worldSide) {
        if (worldSide == Direction.UP) return UP;
        if (worldSide == Direction.DOWN) return DOWN;
        if (worldSide == machineFacing) return FRONT;
        if (worldSide == machineFacing.getOpposite()) return BACK;
        if (worldSide == machineFacing.rotateYClockwise()) return LEFT;
        return RIGHT;
    }

    public Direction toWorld(Direction machineFacing) {
        return switch (this) {
            case FRONT -> machineFacing;
            case BACK -> machineFacing.getOpposite();
            case LEFT -> machineFacing.rotateYClockwise();
            case RIGHT -> machineFacing.rotateYCounterclockwise();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }
}
