package com.errorsys.quarry_reforged.client.debug;

import com.errorsys.quarry_reforged.client.net.QuarryMotionClientState;
import com.errorsys.quarry_reforged.client.render.QuarryRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class QuarryRenderTraceCapture {
    private static ActiveCapture activeCapture;

    private QuarryRenderTraceCapture() {}

    public static void start(BlockPos quarryPos, int durationTicks, double jumpThreshold) {
        int safeDurationTicks = Math.max(20, durationTicks);
        double safeJumpThreshold = Math.max(0.05D, jumpThreshold);
        long nowMs = System.currentTimeMillis();
        StringBuilder rows = new StringBuilder(
                "wallMs,worldTick,tickDelta,renderTick,renderPhase,renderStateTick,toolHeadStateTick,noPower,forceHome,interpEnabled,source,fromStream,bufferedSamples,lastServerTick,lastArrivalLocalTick,tickOffset,estimatedServerTick,targetTick,stale,extrapolated,rawX,rawY,rawZ,renderX,renderY,renderZ,deltaFromPrev,jumpEvent,jumpThreshold\n"
        );
        activeCapture = new ActiveCapture(
                quarryPos.toImmutable(),
                nowMs + (safeDurationTicks * 50L),
                safeJumpThreshold,
                rows,
                null,
                0L,
                0L
        );
        sendClientMessage("Quarry render trace started at " + quarryPos.toShortString() + " for " + (safeDurationTicks / 20) + "s.");
    }

    public static void stop(String reason) {
        ActiveCapture capture = activeCapture;
        if (capture == null) return;
        activeCapture = null;
        writeCapture(capture, reason);
    }

    public static void onRenderSample(QuarryRenderContext context,
                                      float tickDelta,
                                      Vec3d rawPos,
                                      Vec3d renderedPos,
                                      boolean interpolationEnabled,
                                      QuarryMotionClientState.InterpolatedMotionSample motionSample) {
        ActiveCapture capture = activeCapture;
        if (capture == null) return;
        if (!capture.quarryPos.equals(context.quarryPos())) return;

        long nowMs = System.currentTimeMillis();
        if (nowMs >= capture.endWallMs) {
            activeCapture = null;
            writeCapture(capture, "duration complete");
            return;
        }

        double renderTick = ((double) context.worldTime()) + (double) Math.max(0.0F, Math.min(1.0F, tickDelta));
        double delta = capture.lastRenderedPos == null ? 0.0D : renderedPos.distanceTo(capture.lastRenderedPos);
        boolean jumpEvent = capture.lastRenderedPos != null && delta > capture.jumpThreshold;
        if (jumpEvent) {
            capture.jumpEvents++;
        }
        capture.frames++;
        capture.lastRenderedPos = renderedPos;

        capture.rows.append(nowMs).append(',')
                .append(context.worldTime()).append(',')
                .append(format(tickDelta)).append(',')
                .append(format(renderTick)).append(',')
                .append(csv(context.renderPhase().name())).append(',')
                .append(context.renderStateTick()).append(',')
                .append(context.toolHeadStateTick()).append(',')
                .append(context.noPower()).append(',')
                .append(context.forceHomeGantry()).append(',')
                .append(interpolationEnabled).append(',')
                .append(csv(motionSample.source().name())).append(',')
                .append(motionSample.fromStream()).append(',')
                .append(motionSample.bufferedSamples()).append(',')
                .append(motionSample.lastServerTick()).append(',')
                .append(motionSample.lastArrivalLocalTick()).append(',')
                .append(format(motionSample.tickOffsetEstimate())).append(',')
                .append(format(motionSample.estimatedServerTick())).append(',')
                .append(format(motionSample.targetTick())).append(',')
                .append(motionSample.stale()).append(',')
                .append(motionSample.extrapolated()).append(',')
                .append(format(rawPos.x)).append(',')
                .append(format(rawPos.y)).append(',')
                .append(format(rawPos.z)).append(',')
                .append(format(renderedPos.x)).append(',')
                .append(format(renderedPos.y)).append(',')
                .append(format(renderedPos.z)).append(',')
                .append(format(delta)).append(',')
                .append(jumpEvent).append(',')
                .append(format(capture.jumpThreshold))
                .append('\n');
    }

    private static void writeCapture(ActiveCapture capture, String reason) {
        try {
            Path logsDir = Paths.get("logs");
            Files.createDirectories(logsDir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "quarry_client_render_trace_" + ts + "_" + capture.quarryPos.toShortString().replace(", ", "_").replace(",", "_") + ".csv";
            Path out = logsDir.resolve(fileName);
            Files.writeString(out, capture.rows.toString());
            sendClientMessage("Quarry render trace saved (" + reason + "): " + out.toAbsolutePath()
                    + " frames=" + capture.frames + " jumpEvents=" + capture.jumpEvents);
        } catch (Exception e) {
            sendClientMessage("Failed to save quarry render trace: " + e.getMessage());
        }
    }

    private static void sendClientMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static final class ActiveCapture {
        private final BlockPos quarryPos;
        private final long endWallMs;
        private final double jumpThreshold;
        private final StringBuilder rows;
        private Vec3d lastRenderedPos;
        private long frames;
        private long jumpEvents;

        private ActiveCapture(BlockPos quarryPos,
                              long endWallMs,
                              double jumpThreshold,
                              StringBuilder rows,
                              Vec3d lastRenderedPos,
                              long frames,
                              long jumpEvents) {
            this.quarryPos = quarryPos;
            this.endWallMs = endWallMs;
            this.jumpThreshold = jumpThreshold;
            this.rows = rows;
            this.lastRenderedPos = lastRenderedPos;
            this.frames = frames;
            this.jumpEvents = jumpEvents;
        }
    }
}
