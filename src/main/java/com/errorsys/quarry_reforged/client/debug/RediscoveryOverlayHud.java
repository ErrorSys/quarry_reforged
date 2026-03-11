package com.errorsys.quarry_reforged.client.debug;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RediscoveryOverlayHud {
    private static boolean enabled = false;
    @Nullable
    private static Snapshot snapshot = null;

    private RediscoveryOverlayHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(RediscoveryOverlayHud::render);
    }

    public static void clear() {
        enabled = false;
        snapshot = null;
    }

    public static void update(Snapshot next) {
        enabled = true;
        snapshot = next;
    }

    private static void render(DrawContext context, float tickDelta) {
        if (!enabled || snapshot == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int screenW = context.getScaledWindowWidth();
        int xRight = screenW - 4;
        int y = 4;

        List<String> lines = new ArrayList<>();
        lines.add("Quarry Rediscovery");
        lines.add("pos: " + snapshot.pos().toShortString());
        lines.add("active=" + snapshot.active() + " areaLocked=" + snapshot.areaLocked());
        lines.add("drain=" + snapshot.drainRediscoveryQueue()
                + " fullRescan=" + snapshot.rediscoveryFullRescanPending()
                + " finalSweep=" + snapshot.finalRediscoverySweepDone());
        lines.add("scanLayerY=" + snapshot.rediscoveryLayerY()
                + " nextScanIn=" + snapshot.nextScanInTicks() + "t");
        lines.add("target=" + snapshot.activeTargetType() + " @ " + formatPos(snapshot.activeTargetPos()));
        lines.add("state=" + snapshot.machinePhase()
                + " laser=" + snapshot.laserSubstate()
                + " gantry=" + snapshot.gantrySubstate()
                + " return=" + snapshot.returnPhase());
        lines.add("render=" + snapshot.renderChannelPhase()
                + " verticalTravel=" + snapshot.rediscoveryLaserVerticalTravelActive());
        lines.add("caller=" + (snapshot.rediscoveryCallerActive()
                ? (snapshot.rediscoveryCallerPhase()
                + "/" + snapshot.rediscoveryCallerLaserSubstate()
                + "/" + snapshot.rediscoveryCallerGantrySubstate())
                : "none"));
        lines.add("rediscoveryQ raw=" + snapshot.rediscoveryQueueRawSize()
                + " valid=" + snapshot.rediscoveryQueueValidSize()
                + " head=" + formatPos(snapshot.rediscoveryQueueHead()));

        for (String line : lines) {
            int w = tr.getWidth(line);
            int x = xRight - w;
            context.drawText(tr, line, x, y, 0xFFFFFFFF, true);
            y += tr.fontHeight + 2;
        }
    }

    private static String formatPos(@Nullable BlockPos pos) {
        return pos == null ? "(none)" : pos.toShortString();
    }

    public record Snapshot(
            BlockPos pos,
            boolean active,
            boolean areaLocked,
            boolean drainRediscoveryQueue,
            boolean rediscoveryFullRescanPending,
            boolean finalRediscoverySweepDone,
            int rediscoveryLayerY,
            long nextScanInTicks,
            String activeTargetType,
            @Nullable BlockPos activeTargetPos,
            String machinePhase,
            String laserSubstate,
            String gantrySubstate,
            String returnPhase,
            String renderChannelPhase,
            boolean rediscoveryLaserVerticalTravelActive,
            boolean rediscoveryCallerActive,
            String rediscoveryCallerPhase,
            String rediscoveryCallerLaserSubstate,
            String rediscoveryCallerGantrySubstate,
            int rediscoveryQueueRawSize,
            int rediscoveryQueueValidSize,
            @Nullable BlockPos rediscoveryQueueHead
    ) {}
}
