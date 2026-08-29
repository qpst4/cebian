/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

package com.slideindex.app.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class HoneycombGeometry {
    private static final float SQRT_THREE_OVER_TWO = 0.8660254f;

    static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private HoneycombGeometry() { }

    static List<Point> compactPoints(int count, float pitch) {
        if (count <= 0 || pitch <= 0f) return Collections.emptyList();
        int[] capacities = circularRowCapacities(count);
        ArrayList<Point> result = new ArrayList<>(count);
        float verticalPitch = pitch * SQRT_THREE_OVER_TWO;
        float centerRow = (capacities.length - 1) * 0.5f;
        for (int row = 0; row < capacities.length; row++) {
            int capacity = capacities[row];
            float y = (row - centerRow) * verticalPitch;
            int latticeRow = row - capacities.length / 2;
            float stagger = Math.floorMod(latticeRow, 2) == 0 ? 0f : 0.5f;
            ArrayList<Point> rowPoints = new ArrayList<>(capacity + 4);
            for (int column = -capacity - 2; column <= capacity + 2; column++) {
                rowPoints.add(new Point((column + stagger) * pitch, y));
            }
            rowPoints.sort(Comparator.comparingDouble(point -> Math.abs(point.x)));
            result.addAll(rowPoints.subList(0, capacity));
        }
        recenter(result);
        result.sort(Comparator
                .comparingDouble(HoneycombGeometry::squaredRadius)
                .thenComparingDouble(point -> Math.atan2(point.y, point.x)));
        return result;
    }

    private static void recenter(ArrayList<Point> points) {
        if (points.isEmpty()) return;
        float sumX = 0f;
        float sumY = 0f;
        for (Point point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        float centerX = sumX / points.size();
        float centerY = sumY / points.size();
        for (int index = 0; index < points.size(); index++) {
            Point point = points.get(index);
            points.set(index, new Point(point.x - centerX, point.y - centerY));
        }
    }

    private static int[] circularRowCapacities(int count) {
        int maximumRows = Math.max(1, Math.min(count,
                (int) Math.ceil(Math.sqrt(count) * 1.65f) + 2));
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int rows = 1; rows <= maximumRows; rows++) {
            if (rows > count || (rows % 2 == 0 && count % 2 != 0)) continue;
            int[] candidate = allocateCircularRows(count, rows);
            int maximumCapacity = 0;
            for (int capacity : candidate) maximumCapacity = Math.max(maximumCapacity, capacity);
            float width = maximumCapacity;
            float height = (rows - 1) * SQRT_THREE_OVER_TWO + 1f;
            float aspectPenalty = Math.abs((float) Math.log(width / height));
            float targetRows = 1.20f * (float) Math.sqrt(count);
            float densityPenalty = Math.abs(rows - targetRows) * 0.018f;
            float score = aspectPenalty + densityPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? new int[] {count} : best;
    }

    private static int[] allocateCircularRows(int count, int rows) {
        int[] capacities = new int[rows];
        float[] targets = new float[rows];
        for (int row = 0; row < rows; row++) capacities[row] = 1;
        float center = (rows - 1) * 0.5f;
        float radius = Math.max(0.5f, rows * 0.5f);
        float weightSum = 0f;
        for (int row = 0; row < rows; row++) {
            float normalized = (row - center) / radius;
            targets[row] = (float) Math.sqrt(Math.max(0f, 1f - normalized * normalized));
            weightSum += targets[row];
        }
        int remaining = count - rows;
        for (int row = 0; row < rows; row++) {
            targets[row] = 1f + remaining * targets[row] / Math.max(0.001f, weightSum);
        }
        while (remaining > 0) {
            int bestRow = 0;
            float bestDeficit = -Float.MAX_VALUE;
            int bestMirrorImbalance = Integer.MAX_VALUE;
            for (int row = 0; row < rows; row++) {
                float deficit = targets[row] - capacities[row];
                int mirror = rows - 1 - row;
                int mirrorImbalance = Math.abs(capacities[row] + 1 - capacities[mirror]);
                if (deficit > bestDeficit + 0.0001f
                        || (Math.abs(deficit - bestDeficit) <= 0.0001f
                        && mirrorImbalance < bestMirrorImbalance)) {
                    bestDeficit = deficit;
                    bestRow = row;
                    bestMirrorImbalance = mirrorImbalance;
                }
            }
            capacities[bestRow]++;
            remaining--;
        }
        return capacities;
    }

    private static float squaredRadius(Point point) {
        return point.x * point.x + point.y * point.y;
    }

    static float smoothScale(float distance, float radius,
                             float centerScale, float edgeScale) {
        if (radius <= 0f) return edgeScale;
        float t = clamp(distance / radius, 0f, 1f);
        t = t * t * (3f - 2f * t);
        return centerScale + (edgeScale - centerScale) * t;
    }

    static float edgeAlpha(float distance, float start, float end) {
        return 1f;
    }

    static int hit(List<Point> centers, float x, float y, float radius) {
        int best = -1;
        float bestDistance = radius * radius;
        for (int index = 0; index < centers.size(); index++) {
            Point point = centers.get(index);
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) continue;
            float dx = x - point.x;
            float dy = y - point.y;
            float distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    static int hitScaled(List<Point> centers, float x, float y, float iconSize,
                         float effectCenterX, float effectCenterY, float effectRadius,
                         float centerScale, float edgeScale) {
        int best = -1;
        float bestNormalized = Float.MAX_VALUE;
        for (int index = 0; index < centers.size(); index++) {
            Point point = centers.get(index);
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) continue;
            float effectDistance = (float) Math.hypot(point.x - effectCenterX,
                    point.y - effectCenterY);
            float scale = smoothScale(effectDistance, effectRadius, centerScale, edgeScale);
            float hitRadius = Math.max(8f, iconSize * scale * 0.58f);
            float dx = x - point.x;
            float dy = y - point.y;
            float normalized = (dx * dx + dy * dy) / (hitRadius * hitRadius);
            if (normalized <= 1f && normalized < bestNormalized) {
                bestNormalized = normalized;
                best = index;
            }
        }
        return best;
    }

    static float edgeVisibility(float distance, float viewportRadius, float iconRadius) {
        float start = Math.max(0f, viewportRadius - iconRadius);
        float end = viewportRadius + iconRadius;
        if (distance <= start) return 1f;
        if (distance >= end) return 0f;
        float progress = clamp((distance - start) / Math.max(1f, end - start), 0f, 1f);
        progress = progress * progress * (3f - 2f * progress);
        return 1f - progress;
    }

    static float edgeInset(float distance, float viewportRadius, float iconRadius) {
        float visibility = edgeVisibility(distance, viewportRadius, iconRadius);
        return iconRadius * (1f - visibility) * 0.62f;
    }

    static float edgeScale(float distance, float viewportRadius, float iconRadius) {
        float visibility = edgeVisibility(distance, viewportRadius, iconRadius);
        return visibility <= 0f ? 0f : 0.22f + 0.78f * visibility;
    }

    static int hitVisible(float[] centersX, float[] centersY, float[] radii,
                          int count, float x, float y) {
        int best = -1;
        float bestNormalized = Float.MAX_VALUE;
        int limit = Math.min(count, Math.min(centersX.length,
                Math.min(centersY.length, radii.length)));
        for (int index = 0; index < limit; index++) {
            float radius = radii[index];
            if (!Float.isFinite(centersX[index]) || !Float.isFinite(centersY[index])
                    || radius <= 1f) continue;
            float dx = x - centersX[index];
            float dy = y - centersY[index];
            float normalized = (dx * dx + dy * dy) / (radius * radius);
            if (normalized <= 1f && normalized < bestNormalized) {
                bestNormalized = normalized;
                best = index;
            }
        }
        return best;
    }

    static float pressureInfluence(float distance, float radius) {
        if (radius <= 0f || distance >= radius) return 0f;
        float influence = 1f - clamp(distance / radius, 0f, 1f);
        return influence * influence * (3f - 2f * influence);
    }

    static float fisheyeScale(float distance, float radius,
                              float farScale, float focusScale) {
        float influence = pressureInfluence(distance, radius);
        return farScale + (focusScale - farScale) * influence;
    }

    static float surfaceBulgeOffset(float distance, float radius,
                                    float maximumOffset) {
        if (distance <= 0f || radius <= 0f || maximumOffset <= 0f
                || distance >= radius) return 0f;
        float progress = clamp(distance / radius, 0f, 1f);
        float remaining = 1f - progress;
        return maximumOffset * 6.75f * progress * remaining * remaining;
    }

    static float edgePanStrength(float distance, float radius, float startFraction) {
        if (radius <= 0f) return 0f;
        float start = radius * clamp(startFraction, 0f, 0.98f);
        if (distance <= start) return 0f;
        float progress = clamp((distance - start) / Math.max(1f, radius - start),
                0f, 1f);
        return progress * progress * (3f - 2f * progress);
    }

    static float counterPanDelta(float fingerDelta, float acceleration) {
        return -fingerDelta * clamp(acceleration, 0f, 1f);
    }

    static float resisted(float value, float min, float max, float factor) {
        if (value < min) return min + (value - min) * factor;
        if (value > max) return max + (value - max) * factor;
        return value;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Layout-space slot coordinate (pixels, centered at origin). */
    public static final class LayoutSlot {
        public final float x;
        public final float y;

        public LayoutSlot(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Pitch in pixels matching runtime overlay layout. */
    public static float pitchPx(float iconSizeDp, float spacingDp, float density) {
        return Math.max(iconSizeDp + 4f, spacingDp) * density;
    }

    public static List<LayoutSlot> layoutSlots(int count, float pitchPx) {
        List<Point> points = compactPoints(count, pitchPx);
        ArrayList<LayoutSlot> result = new ArrayList<>(points.size());
        for (Point point : points) {
            result.add(new LayoutSlot(point.x, point.y));
        }
        return result;
    }

    public static int nearestLayoutSlot(List<LayoutSlot> screenSlots,
                                        float x, float y, float radius) {
        int best = -1;
        float bestDistance = radius * radius;
        for (int index = 0; index < screenSlots.size(); index++) {
            LayoutSlot point = screenSlots.get(index);
            float dx = x - point.x;
            float dy = y - point.y;
            float distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                best = index;
                bestDistance = distance;
            }
        }
        return best;
    }
}
