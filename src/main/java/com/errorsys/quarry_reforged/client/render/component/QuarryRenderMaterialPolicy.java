package com.errorsys.quarry_reforged.client.render.component;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class QuarryRenderMaterialPolicy {
    public static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    public enum ComponentKind {
        GANTRY,
        TOOL_HEAD,
        PIPE,
        CUBE,
        BEAM
    }

    public RenderLayer layerFor(ComponentKind component, Identifier texture) {
        return switch (component) {
            case BEAM -> RenderLayer.getEntityCutoutNoCull(texture);
            case GANTRY, TOOL_HEAD, PIPE, CUBE -> RenderLayer.getEntityCutoutNoCull(texture);
        };
    }

    public int lightFor(ComponentKind component, int shadedLight) {
        return switch (component) {
            case BEAM -> FULL_BRIGHT_LIGHT;
            case GANTRY, TOOL_HEAD, PIPE, CUBE -> shadedLight;
        };
    }
}
