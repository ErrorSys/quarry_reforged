package com.errorsys.quarry_reforged.client.net;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuarryMotionClientState {
    private static final int MAX_QUARRIES = 256;
    private static final int MAX_BUFFERED_SAMPLES_PER_QUARRY = 64;
    private static final long STALE_TICKS = 80L;
    private static final double INTERPOLATION_DELAY_TICKS = 1.0;
    private static final double MAX_EXTRAPOLATION_TICKS = 1.0;
    private static final double TARGET_NEAREST_SAMPLE_MARGIN_TICKS = 0.20;
    private static final double ESTIMATED_SERVER_TICK_SMOOTH_ALPHA = 0.10;
    private static final Map<Long, MotionSampleBuffer> SAMPLES = new LinkedHashMap<>();

    private QuarryMotionClientState() {}

    public static void update(BlockPos quarryPos, long serverTick, Vec3d toolHeadPos, long localWorldTick) {
        long key = quarryPos.asLong();
        MotionSampleBuffer buffer = SAMPLES.computeIfAbsent(key, ignored -> new MotionSampleBuffer());
        buffer.add(serverTick, toolHeadPos, localWorldTick);
        prune(localWorldTick);
    }

    public static ResolvedMotion resolveToolHeadPos(QuarryBlockEntity be, long worldTick, Vec3d fallback, long fallbackTick) {
        MotionSampleBuffer buffer = SAMPLES.get(be.getPos().asLong());
        if (buffer == null || buffer.isEmpty()) return new ResolvedMotion(fallback, fallbackTick, false);
        MotionSample latest = buffer.latest();
        if (latest == null) return new ResolvedMotion(fallback, fallbackTick, false);
        if (worldTick - buffer.lastArrivalLocalTick > STALE_TICKS) {
            SAMPLES.remove(be.getPos().asLong());
            return new ResolvedMotion(fallback, fallbackTick, false);
        }
        return new ResolvedMotion(latest.toolHeadPos, latest.serverTick, true);
    }

    public static Vec3d interpolateToolHeadPos(BlockPos quarryPos, double renderTick, Vec3d fallback) {
        return sampleInterpolatedToolHeadPos(quarryPos, renderTick, fallback).toolHeadPos();
    }

    public static InterpolatedMotionSample sampleInterpolatedToolHeadPos(BlockPos quarryPos, double renderTick, Vec3d fallback) {
        MotionSampleBuffer buffer = SAMPLES.get(quarryPos.asLong());
        if (buffer == null || buffer.isEmpty()) {
            return InterpolatedMotionSample.fallback(fallback);
        }
        MotionSample latest = buffer.latest();
        if (latest == null) {
            return InterpolatedMotionSample.fallback(fallback);
        }
        if (renderTick - buffer.lastArrivalLocalTick > STALE_TICKS) {
            SAMPLES.remove(quarryPos.asLong());
            return InterpolatedMotionSample.staleFallback(fallback, buffer.size(), latest.serverTick, buffer.lastArrivalLocalTick, buffer.tickOffsetEstimate);
        }
        if (buffer.size() == 1) {
            return new InterpolatedMotionSample(
                    latest.toolHeadPos,
                    MotionSource.STREAM_SINGLE,
                    true,
                    buffer.size(),
                    latest.serverTick,
                    buffer.lastArrivalLocalTick,
                    buffer.tickOffsetEstimate,
                    renderTick - buffer.tickOffsetEstimate,
                    renderTick - buffer.tickOffsetEstimate - INTERPOLATION_DELAY_TICKS,
                    false,
                    false
            );
        }

        double estimatedServerTick = buffer.smoothEstimatedServerTick(renderTick);
        double targetTick = estimatedServerTick - INTERPOLATION_DELAY_TICKS;
        MotionSample oldest = buffer.oldest();
        if (oldest == null) {
            return new InterpolatedMotionSample(
                    latest.toolHeadPos,
                    MotionSource.STREAM_SINGLE,
                    true,
                    buffer.size(),
                    latest.serverTick,
                    buffer.lastArrivalLocalTick,
                    buffer.tickOffsetEstimate,
                    estimatedServerTick,
                    targetTick,
                    false,
                    false
            );
        }
        if (targetTick <= oldest.serverTick) {
            return new InterpolatedMotionSample(
                    oldest.toolHeadPos,
                    MotionSource.STREAM_OLDEST_HOLD,
                    true,
                    buffer.size(),
                    latest.serverTick,
                    buffer.lastArrivalLocalTick,
                    buffer.tickOffsetEstimate,
                    estimatedServerTick,
                    targetTick,
                    false,
                    false
            );
        }

        MotionSample prev = null;
        MotionSample next = null;
        for (MotionSample sample : buffer.samples) {
            if (sample.serverTick <= targetTick) {
                prev = sample;
                continue;
            }
            next = sample;
            break;
        }

        // Hold very near the newest sample edge to avoid INTERPOLATED/EXTRAPOLATED ping-pong.
        if (next == null && prev != null) {
            double clampedTargetTick = Math.min(targetTick, (double) prev.serverTick - TARGET_NEAREST_SAMPLE_MARGIN_TICKS);
            if (clampedTargetTick < targetTick) {
                targetTick = clampedTargetTick;
                prev = null;
                next = null;
                for (MotionSample sample : buffer.samples) {
                    if (sample.serverTick <= targetTick) {
                        prev = sample;
                        continue;
                    }
                    next = sample;
                    break;
                }
            }
        }

        if (prev != null && next != null && next.serverTick > prev.serverTick) {
            double alpha = (targetTick - prev.serverTick) / (double) (next.serverTick - prev.serverTick);
            alpha = Math.max(0.0, Math.min(1.0, alpha));
            return new InterpolatedMotionSample(
                    prev.toolHeadPos.lerp(next.toolHeadPos, alpha),
                    MotionSource.STREAM_INTERPOLATED,
                    true,
                    buffer.size(),
                    latest.serverTick,
                    buffer.lastArrivalLocalTick,
                    buffer.tickOffsetEstimate,
                    estimatedServerTick,
                    targetTick,
                    false,
                    false
            );
        }

        // Past newest sample: allow short extrapolation to hide packet jitter spikes.
        if (prev != null && next == null) {
            MotionSample beforePrev = buffer.beforeLast();
            if (beforePrev == null || prev.serverTick <= beforePrev.serverTick) {
                return new InterpolatedMotionSample(
                        prev.toolHeadPos,
                        MotionSource.STREAM_NEWEST_HOLD,
                        true,
                        buffer.size(),
                        latest.serverTick,
                        buffer.lastArrivalLocalTick,
                        buffer.tickOffsetEstimate,
                        estimatedServerTick,
                        targetTick,
                        false,
                        false
                );
            }
            double dt = prev.serverTick - beforePrev.serverTick;
            Vec3d velocity = prev.toolHeadPos.subtract(beforePrev.toolHeadPos).multiply(1.0 / dt);
            double extrapolationTicks = Math.max(0.0, Math.min(MAX_EXTRAPOLATION_TICKS, targetTick - prev.serverTick));
            return new InterpolatedMotionSample(
                    prev.toolHeadPos.add(velocity.multiply(extrapolationTicks)),
                    MotionSource.STREAM_EXTRAPOLATED,
                    true,
                    buffer.size(),
                    latest.serverTick,
                    buffer.lastArrivalLocalTick,
                    buffer.tickOffsetEstimate,
                    estimatedServerTick,
                    targetTick,
                    false,
                    extrapolationTicks > 0.0
            );
        }

        return new InterpolatedMotionSample(
                latest.toolHeadPos,
                MotionSource.STREAM_NEWEST_HOLD,
                true,
                buffer.size(),
                latest.serverTick,
                buffer.lastArrivalLocalTick,
                buffer.tickOffsetEstimate,
                estimatedServerTick,
                targetTick,
                false,
                false
        );
    }

    private static void prune(long localWorldTick) {
        if (SAMPLES.size() > MAX_QUARRIES) {
            Iterator<Long> it = SAMPLES.keySet().iterator();
            while (SAMPLES.size() > MAX_QUARRIES && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        Iterator<Map.Entry<Long, MotionSampleBuffer>> it = SAMPLES.entrySet().iterator();
        while (it.hasNext()) {
            MotionSampleBuffer buffer = it.next().getValue();
            MotionSample latest = buffer.latest();
            if (latest == null || localWorldTick - buffer.lastArrivalLocalTick > STALE_TICKS) it.remove();
        }
    }

    public record ResolvedMotion(Vec3d toolHeadPos, long stateTick, boolean fromStream) {}

    public enum MotionSource {
        FALLBACK,
        STALE_FALLBACK,
        STREAM_SINGLE,
        STREAM_OLDEST_HOLD,
        STREAM_INTERPOLATED,
        STREAM_NEWEST_HOLD,
        STREAM_EXTRAPOLATED
    }

    public record InterpolatedMotionSample(Vec3d toolHeadPos,
                                           MotionSource source,
                                           boolean fromStream,
                                           int bufferedSamples,
                                           long lastServerTick,
                                           long lastArrivalLocalTick,
                                           double tickOffsetEstimate,
                                           double estimatedServerTick,
                                           double targetTick,
                                           boolean stale,
                                           boolean extrapolated) {
        private static InterpolatedMotionSample fallback(Vec3d fallback) {
            return new InterpolatedMotionSample(
                    fallback,
                    MotionSource.FALLBACK,
                    false,
                    0,
                    -1L,
                    -1L,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    false
            );
        }

        private static InterpolatedMotionSample staleFallback(Vec3d fallback,
                                                              int bufferedSamples,
                                                              long lastServerTick,
                                                              long lastArrivalLocalTick,
                                                              double tickOffsetEstimate) {
            return new InterpolatedMotionSample(
                    fallback,
                    MotionSource.STALE_FALLBACK,
                    false,
                    bufferedSamples,
                    lastServerTick,
                    lastArrivalLocalTick,
                    tickOffsetEstimate,
                    0.0,
                    0.0,
                    true,
                    false
            );
        }
    }

    private record MotionSample(long serverTick, Vec3d toolHeadPos) {}

    private static final class MotionSampleBuffer {
        private final ArrayDeque<MotionSample> samples = new ArrayDeque<>();
        private double tickOffsetEstimate = 0.0;
        private boolean hasOffsetEstimate = false;
        private long lastArrivalLocalTick = Long.MIN_VALUE;
        private double smoothedEstimatedServerTick = 0.0;
        private double lastResolvedRenderTick = Double.NaN;
        private boolean hasSmoothedEstimatedServerTick = false;

        private void add(long serverTick, Vec3d toolHeadPos, long localWorldTick) {
            MotionSample latest = latest();
            if (latest != null) {
                if (serverTick < latest.serverTick) return;
                if (serverTick == latest.serverTick) {
                    samples.removeLast();
                    samples.addLast(new MotionSample(serverTick, toolHeadPos));
                    updateOffsetEstimate(serverTick, localWorldTick);
                    lastArrivalLocalTick = localWorldTick;
                    return;
                }
            }
            samples.addLast(new MotionSample(serverTick, toolHeadPos));
            while (samples.size() > MAX_BUFFERED_SAMPLES_PER_QUARRY) {
                samples.removeFirst();
            }
            updateOffsetEstimate(serverTick, localWorldTick);
            lastArrivalLocalTick = localWorldTick;
        }

        private void updateOffsetEstimate(long serverTick, long localWorldTick) {
            double observed = (double) localWorldTick - (double) serverTick;
            if (!hasOffsetEstimate) {
                tickOffsetEstimate = observed;
                hasOffsetEstimate = true;
                return;
            }
            // Favor timeline stability over rapid correction to suppress render jitter.
            double alpha = 0.03;
            tickOffsetEstimate = tickOffsetEstimate + alpha * (observed - tickOffsetEstimate);
        }

        private double smoothEstimatedServerTick(double renderTick) {
            double rawEstimated = renderTick - tickOffsetEstimate;
            if (!hasSmoothedEstimatedServerTick || Double.isNaN(lastResolvedRenderTick)) {
                smoothedEstimatedServerTick = rawEstimated;
                lastResolvedRenderTick = renderTick;
                hasSmoothedEstimatedServerTick = true;
                return smoothedEstimatedServerTick;
            }
            double renderDelta = Math.max(0.0, renderTick - lastResolvedRenderTick);
            double predicted = smoothedEstimatedServerTick + renderDelta;
            smoothedEstimatedServerTick = predicted + ((rawEstimated - predicted) * ESTIMATED_SERVER_TICK_SMOOTH_ALPHA);
            lastResolvedRenderTick = renderTick;
            return smoothedEstimatedServerTick;
        }

        private boolean isEmpty() {
            return samples.isEmpty();
        }

        private int size() {
            return samples.size();
        }

        private MotionSample latest() {
            return samples.peekLast();
        }

        private MotionSample oldest() {
            return samples.peekFirst();
        }

        private MotionSample beforeLast() {
            if (samples.size() < 2) return null;
            Iterator<MotionSample> descending = samples.descendingIterator();
            descending.next(); // last
            return descending.hasNext() ? descending.next() : null;
        }
    }
}
