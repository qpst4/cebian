/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

package com.slideindex.app.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.util.List;

public final class HoneycombOverlayController {
    public interface Listener {
        void onLaunch(HoneycombRuntimeTarget target, long selectionPressDurationMs);
        void onClosed();
    }

    private final Context context;
    private final Handler mainHandler;
    private final WindowManager windowManager;
    private final Object moveLock = new Object();
    private HoneycombOverlayView view;
    private WindowManager.LayoutParams layoutParams;
    private boolean attached;
    private int windowTop;
    private HoneycombOverlayView pendingMoveView;
    private float pendingMoveX;
    private float pendingMoveY;
    private boolean moveFrameScheduled;
    private final Runnable deliverPendingMove = () -> {
        HoneycombOverlayView target;
        float x;
        float y;
        synchronized (moveLock) {
            target = pendingMoveView;
            x = pendingMoveX;
            y = pendingMoveY;
            pendingMoveView = null;
            moveFrameScheduled = false;
        }
        if (attached && target != null && view == target) {
            target.onExternalMove(toLocalX(x), toLocalY(y));
        }
    };

    HoneycombOverlayController(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        windowManager = context.getSystemService(WindowManager.class);
    }

    private static final String TAG = "HoneycombOverlayController";

    public boolean show(List<HoneycombRuntimeTarget> targets, HoneycombCorner corner,
                 float anchorX, float anchorY, HoneycombDisplayConfig config,
                 boolean externalTracking, Listener listener) {
        removeNow();
        if (windowManager == null || targets.isEmpty()) return false;

        boolean usesNativeWindowBlur = false;
        if (config.getHoneycombBackgroundStyle() == HoneycombDisplayConfig.BACKGROUND_BLUR
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                usesNativeWindowBlur = windowManager.isCrossWindowBlurEnabled();
            } catch (Throwable ignored) {
                usesNativeWindowBlur = false;
            }
        }

        HoneycombOverlayView next = new HoneycombOverlayView(context);
        windowTop = 0;
        next.configure(targets, corner, toLocalX(anchorX), toLocalY(anchorY), config,
                usesNativeWindowBlur,
                new HoneycombOverlayView.Listener() {
            @Override public void onLaunch(HoneycombRuntimeTarget target, long selectionPressDurationMs) {
                removeNow();
                listener.onLaunch(target, selectionPressDurationMs);
            }

            @Override public void onClosed() {
                removeNow();
                listener.onClosed();
            }
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                displayHeight(),
        OverlayWindowTypes.INSTANCE.overlayWindowType(context),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        OverlayWindowTypes.INSTANCE.ensureNoBrightnessOverride(params);
        if (usesNativeWindowBlur) {
            int rawBlurPx = Math.round(config.getHoneycombBlurDp() * context.getResources().getDisplayMetrics().density);
            int clampedBlurPx = Math.min(80, Math.max(1, rawBlurPx));
            params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            params.setBlurBehindRadius(clampedBlurPx);
        }
        if (externalTracking) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        params.gravity = Gravity.TOP | Gravity.START;
        params.y = windowTop;
        params.setFitInsetsTypes(0);
        params.setTitle("HyperGestureHoneycomb");
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams
                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        try {
            windowManager.addView(next, params);
            view = next;
            layoutParams = params;
            attached = true;
            next.post(() -> {
                try {
                    WindowInsetsController controller = next.getWindowInsetsController();
                    if (controller != null) {
                        controller.setSystemBarsBehavior(WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        controller.hide(WindowInsets.Type.navigationBars());
                    }
                } catch (Throwable error) {
                    android.util.Log.e(TAG, "Cannot hide navigation handle for honeycomb", error);
                }
            });
            next.playEntry();
            return true;
        } catch (Throwable error) {
            android.util.Log.e(TAG, "Cannot attach honeycomb overlay", error);
            next.releaseResources();
            view = null;
            attached = false;
            return false;
        }
    }

    private int displayHeight() {
        return windowManager == null ? context.getResources().getDisplayMetrics().heightPixels
                : windowManager.getCurrentWindowMetrics().getBounds().height();
    }

    public boolean isVisible() { return attached && view != null; }

    public void refreshIcons() {
        HoneycombOverlayView current = view;
        if (!attached || current == null) return;
        current.postInvalidateOnAnimation();
    }

    public void externalMove(float x, float y) {
        HoneycombOverlayView current = view;
        if (!attached || current == null) return;
        synchronized (moveLock) {
            pendingMoveView = current;
            pendingMoveX = x;
            pendingMoveY = y;
            if (moveFrameScheduled) return;
            moveFrameScheduled = true;
        }
        current.postOnAnimation(deliverPendingMove);
    }

    public void externalUp(float x, float y, boolean cancelled) {
        HoneycombOverlayView current = view;
        cancelPendingMove(current);
        runOnViewThread(current, () -> {
            float localX = toLocalX(x);
            float localY = toLocalY(y);
            if (!cancelled) current.onExternalMove(localX, localY);
            current.onExternalUp(localX, localY, cancelled);
        });
    }

    public void externalCancel() {
        HoneycombOverlayView current = view;
        cancelPendingMove(current);
        runOnViewThread(current, current::onExternalCancel);
    }

    void setPaused(boolean paused) {
        HoneycombOverlayView current = view;
        runOnViewThread(current, () -> current.setInteractionPaused(paused));
    }

    /** Switch from edge-handoff (NOT_TOUCHABLE) to direct tap/pan on the overlay. */
    public void enableDirectTouch() {
        HoneycombOverlayView current = view;
        WindowManager.LayoutParams params = layoutParams;
        if (!attached || current == null || params == null || windowManager == null) return;
        params.flags = params.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.flags = params.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        runOnViewThread(current, () -> {
            try {
                windowManager.updateViewLayout(current, params);
                current.requestFocus();
            } catch (Throwable error) {
                android.util.Log.e(TAG, "Cannot enable honeycomb direct touch", error);
            }
        });
    }

    public void dismiss() {
        HoneycombOverlayView current = view;
        runOnViewThread(current, current::playDismissal);
    }

    public void removeNow() {
        HoneycombOverlayView current = view;
        cancelPendingMove(current);
        view = null;
        if (!attached || current == null || windowManager == null) {
            attached = false;
            return;
        }
        attached = false;
        layoutParams = null;
        windowTop = 0;
        mainHandler.removeCallbacksAndMessages(current);
        Runnable removal = () -> {
            try {
                windowManager.removeViewImmediate(current);
            } catch (Throwable error) {
                android.util.Log.e(TAG, "Cannot remove honeycomb overlay", error);
            } finally {
                current.releaseResources();
            }
        };
        Handler owner = current.getHandler();
        if (owner != null && owner.getLooper() != Looper.myLooper()) owner.post(removal);
        else removal.run();
    }

    private void runOnViewThread(HoneycombOverlayView current, Runnable action) {
        if (!attached || current == null) return;
        Handler owner = current.getHandler();
        if (owner != null && owner.getLooper() != Looper.myLooper()) {
            owner.post(() -> {
                if (attached && view == current) action.run();
            });
        } else if (attached && view == current) {
            action.run();
        }
    }

    private void cancelPendingMove(HoneycombOverlayView current) {
        if (current != null) current.removeCallbacks(deliverPendingMove);
        synchronized (moveLock) {
            if (pendingMoveView == current) pendingMoveView = null;
            moveFrameScheduled = false;
        }
    }

    private float toLocalX(float screenX) { return screenX; }

    private float toLocalY(float screenY) { return screenY - windowTop; }
}
