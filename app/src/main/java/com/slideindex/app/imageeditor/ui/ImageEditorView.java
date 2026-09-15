package com.slideindex.app.imageeditor.ui;

import com.slideindex.app.imageeditor.EditorViewMetrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.FrameMetricsAggregator;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;
import com.slideindex.app.inspire.ManagedBitmap;
import com.slideindex.app.imageeditor.model.EditAction;
import com.slideindex.app.imageeditor.model.EditorMode;
import com.slideindex.app.imageeditor.model.EditorPoint;
import com.slideindex.app.imageeditor.state.EditorSession;
import com.slideindex.app.imageeditor.state.EditorUiState;
import com.slideindex.app.imageeditor.utils.EditorExportEngine;
import com.slideindex.app.imageeditor.utils.EditorRenderUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kotlin.NoWhenBranchMatchedException;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;

/* loaded from: classes2.dex */
public final class ImageEditorView extends View {
    private static final long DOUBLE_TAP_TIMEOUT_MS = 300;
    private static final float INITIAL_IMAGE_FIT_ZOOM = 0.92f;
    private static final float MAX_IMAGE_ZOOM = 6.0f;
    private static final float MIN_IMAGE_ZOOM = 0.35f;
    private static final long MODE_SWITCH_MULTI_TOUCH_GUARD_MS = 180;
    private EditorPoint activeCropEnd;
    private EditorPoint activeCropStart;
    private List<EditorPoint> activePoints;
    private EditorPoint activeShapeEnd;
    private EditorPoint activeShapeStart;
    private ManagedBitmap baseBitmapHandle;
    private Callback callback;
    private final Paint centerBrushPreviewFillPaint;
    private final Paint centerBrushPreviewOutlinePaint;
    private boolean centerBrushPreviewVisible;
    private final Paint checkerPaint;
    private final Paint controlOutlinePaint;
    private CropDragMode cropDragMode;
    private EditorPoint cropDragStartPoint;
    private RectF cropDragStartRect;
    private final Paint cropFramePaint;
    private final Paint cropHandlePaint;
    private final Paint cropMaskPaint;
    private RectF cropSelection;
    private final Paint deleteHandlePaint;
    private final Paint deleteIconPaint;
    private EditorPoint dragStartImagePoint;
    private EditAction.Shape dragStartShapeAction;
    private RectF dragStartShapeBounds;
    private EditorPoint dragStartTextAnchor;
    private float dragStartTextRadius;
    private Float dragStartTextSize;
    private String draggingShapeId;
    private String draggingTextId;
    private GestureKind gestureKind;
    private float imagePanX;
    private float imagePanY;
    private float imageZoom;
    private float lastFocusX;
    private float lastFocusY;
    private EditorMode lastObservedMode;
    private String lastTextTapId;
    private long lastTextTapTimestamp;
    private float lastTouchX;
    private float lastTouchY;
    private Bitmap mosaicBitmap;
    private EditorPoint pendingTextTapPoint;
    private final ScaleGestureDetector scaleDetector;
    private String selectedShapeId;
    private String selectedTextId;
    private final Paint selectionPaint;
    private EditorSession session;
    private final EditorSession.Listener sessionListener;
    private boolean shapeGestureMoved;
    private long suppressMultiTouchUntilUptimeMs;
    private boolean textGestureMoved;
    private final Paint textHandlePaint;
    private EditorUiState uiState;

    private enum GestureKind {
        NONE, PAN, CROP, PATH, SHAPE_DRAW, SHAPE_DRAG, SHAPE_RESIZE, SHAPE_TAP, TEXT_DRAG, TEXT_RESIZE, TEXT_TAP
    }

    private enum CropDragMode {
        NEW, MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /* compiled from: ImageEditorView.kt */
    public interface Callback {
        void onRequestAddText(EditorPoint anchor, float suggestedSize);

        void onRequestEditText(EditAction.Text action);
    }

    /* compiled from: ImageEditorView.kt */
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;
        public static final /* synthetic */ int[] $EnumSwitchMapping$1;
        public static final /* synthetic */ int[] $EnumSwitchMapping$2;

        static {
            int[] iArr = new int[EditorMode.values().length];
            try {
                iArr[EditorMode.NAVIGATE.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[EditorMode.CROP.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                iArr[EditorMode.DOODLE.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                iArr[EditorMode.ERASER.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            try {
                iArr[EditorMode.MOSAIC.ordinal()] = 5;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                iArr[EditorMode.SHAPE.ordinal()] = 6;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                iArr[EditorMode.TEXT.ordinal()] = 7;
            } catch (NoSuchFieldError unused7) {
            }
            $EnumSwitchMapping$0 = iArr;
            int[] iArr2 = new int[CropDragMode.values().length];
            try {
                iArr2[CropDragMode.NEW.ordinal()] = 1;
            } catch (NoSuchFieldError unused8) {
            }
            try {
                iArr2[CropDragMode.MOVE.ordinal()] = 2;
            } catch (NoSuchFieldError unused9) {
            }
            try {
                iArr2[CropDragMode.LEFT.ordinal()] = 3;
            } catch (NoSuchFieldError unused10) {
            }
            try {
                iArr2[CropDragMode.TOP.ordinal()] = 4;
            } catch (NoSuchFieldError unused11) {
            }
            try {
                iArr2[CropDragMode.RIGHT.ordinal()] = 5;
            } catch (NoSuchFieldError unused12) {
            }
            try {
                iArr2[CropDragMode.BOTTOM.ordinal()] = 6;
            } catch (NoSuchFieldError unused13) {
            }
            try {
                iArr2[CropDragMode.TOP_LEFT.ordinal()] = 7;
            } catch (NoSuchFieldError unused14) {
            }
            try {
                iArr2[CropDragMode.TOP_RIGHT.ordinal()] = 8;
            } catch (NoSuchFieldError unused15) {
            }
            try {
                iArr2[CropDragMode.BOTTOM_LEFT.ordinal()] = 9;
            } catch (NoSuchFieldError unused16) {
            }
            try {
                iArr2[CropDragMode.BOTTOM_RIGHT.ordinal()] = 10;
            } catch (NoSuchFieldError unused17) {
            }
            $EnumSwitchMapping$1 = iArr2;
            int[] iArr3 = new int[GestureKind.values().length];
            try {
                iArr3[GestureKind.SHAPE_RESIZE.ordinal()] = 1;
            } catch (NoSuchFieldError unused18) {
            }
            try {
                iArr3[GestureKind.TEXT_RESIZE.ordinal()] = 2;
            } catch (NoSuchFieldError unused19) {
            }
            try {
                iArr3[GestureKind.PAN.ordinal()] = 3;
            } catch (NoSuchFieldError unused20) {
            }
            try {
                iArr3[GestureKind.CROP.ordinal()] = 4;
            } catch (NoSuchFieldError unused21) {
            }
            try {
                iArr3[GestureKind.PATH.ordinal()] = 5;
            } catch (NoSuchFieldError unused22) {
            }
            try {
                iArr3[GestureKind.SHAPE_DRAW.ordinal()] = 6;
            } catch (NoSuchFieldError unused23) {
            }
            try {
                iArr3[GestureKind.SHAPE_TAP.ordinal()] = 7;
            } catch (NoSuchFieldError unused24) {
            }
            try {
                iArr3[GestureKind.SHAPE_DRAG.ordinal()] = 8;
            } catch (NoSuchFieldError unused25) {
            }
            try {
                iArr3[GestureKind.TEXT_TAP.ordinal()] = 9;
            } catch (NoSuchFieldError unused26) {
            }
            try {
                iArr3[GestureKind.TEXT_DRAG.ordinal()] = 10;
            } catch (NoSuchFieldError unused27) {
            }
            try {
                iArr3[GestureKind.NONE.ordinal()] = 11;
            } catch (NoSuchFieldError unused28) {
            }
            $EnumSwitchMapping$2 = iArr3;
        }
    }

    /* JADX WARN: 'this' call moved to the top of the method (can break code semantics) */
    public ImageEditorView(Context context) {
        this(context, null, 0, 6, null);
        Intrinsics.checkNotNullParameter(context, "context");
    }

    /* JADX WARN: 'this' call moved to the top of the method (can break code semantics) */
    public ImageEditorView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0, 4, null);
        Intrinsics.checkNotNullParameter(context, "context");
    }

    public /* synthetic */ ImageEditorView(Context context, AttributeSet attributeSet, int i, int i2, DefaultConstructorMarker defaultConstructorMarker) {
        this(context, (i2 & 2) != 0 ? null : attributeSet, (i2 & 4) != 0 ? 0 : i);
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public ImageEditorView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        Intrinsics.checkNotNullParameter(context, "context");
        Paint paint = new Paint(1);
        paint.setStyle(Paint.Style.FILL);
        this.checkerPaint = paint;
        Paint paint2 = new Paint(1);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setColor(-1);
        this.selectionPaint = paint2;
        Paint paint3 = new Paint(1);
        paint3.setStyle(Paint.Style.FILL);
        paint3.setColor(Color.argb(EditorViewMetrics.CROP_MASK_ALPHA, 0, 0, 0));
        this.cropMaskPaint = paint3;
        Paint paint4 = new Paint(1);
        paint4.setStyle(Paint.Style.STROKE);
        paint4.setColor(-1);
        this.cropFramePaint = paint4;
        Paint paint5 = new Paint(1);
        paint5.setStyle(Paint.Style.FILL);
        paint5.setColor(-1);
        this.cropHandlePaint = paint5;
        Paint paint6 = new Paint(1);
        paint6.setStyle(Paint.Style.FILL);
        paint6.setColor(-1);
        this.textHandlePaint = paint6;
        Paint paint7 = new Paint(1);
        paint7.setStyle(Paint.Style.STROKE);
        paint7.setStrokeCap(Paint.Cap.ROUND);
        paint7.setStrokeJoin(Paint.Join.ROUND);
        paint7.setColor(-1);
        this.controlOutlinePaint = paint7;
        Paint paint8 = new Paint(1);
        paint8.setStyle(Paint.Style.FILL);
        paint8.setColor(android.graphics.Color.RED);
        this.deleteHandlePaint = paint8;
        Paint paint9 = new Paint(1);
        paint9.setStyle(Paint.Style.STROKE);
        paint9.setStrokeCap(Paint.Cap.ROUND);
        paint9.setStrokeJoin(Paint.Join.ROUND);
        paint9.setColor(-1);
        this.deleteIconPaint = paint9;
        Paint paint10 = new Paint(1);
        paint10.setStyle(Paint.Style.FILL);
        this.centerBrushPreviewFillPaint = paint10;
        Paint paint11 = new Paint(1);
        paint11.setStyle(Paint.Style.STROKE);
        this.centerBrushPreviewOutlinePaint = paint11;
        this.uiState = EditorUiState.defaults();
        this.sessionListener = new EditorSession.Listener() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$$ExternalSyntheticLambda0
            @Override // com.slideindex.app.imageeditor.state.EditorSession.Listener
            public final void onStateChanged(EditorUiState editorUiState) {
                ImageEditorView.sessionListener$lambda$11(ImageEditorView.this, editorUiState);
            }
        };
        this.imageZoom = 1.0f;
        this.gestureKind = GestureKind.NONE;
        this.activePoints = new ArrayList();
        this.cropDragMode = CropDragMode.NEW;
        this.lastObservedMode = this.uiState.getCurrentMode();
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$scaleDetector$1
            @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
            public boolean onScale(ScaleGestureDetector detector) {
                Intrinsics.checkNotNullParameter(detector, "detector");
                ImageEditorView.this.applyScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    public final Callback getCallback() {
        return this.callback;
    }

    public final void setCallback(Callback callback) {
        this.callback = callback;
    }

    private float getMinCropTouchTargetPx() {
        return EditorViewMetrics.dp(getContext(), 24.0f);
    }

    private float getCropHandleRadiusPx() {
        return EditorViewMetrics.dp(getContext(), 8.0f);
    }

    private float getCropCornerHitRadiusPx() {
        return getCropHandleRadiusPx() + EditorViewMetrics.dp(getContext(), 12.0f);
    }

    private float getCropEdgeHitSlopPx() {
        return EditorViewMetrics.dp(getContext(), 16.0f);
    }

    private float getShapeResizeHandleRadiusPx() {
        return EditorViewMetrics.dp(getContext(), 10.0f);
    }

    private float getShapeResizeHitSlopPx() {
        return EditorViewMetrics.dp(getContext(), 14.0f);
    }

    private float getMinShapeTouchTargetPx() {
        return EditorViewMetrics.dp(getContext(), 18.0f);
    }

    private float getTextResizeHandleRadiusPx() {
        return EditorViewMetrics.dp(getContext(), 10.0f);
    }

    private float getTextResizeHitSlopPx() {
        return EditorViewMetrics.dp(getContext(), 14.0f);
    }

    private float getMinTextTouchTargetPx() {
        return EditorViewMetrics.dp(getContext(), 18.0f);
    }

    private float getDeleteHandleRadiusPx() {
        return EditorViewMetrics.dp(getContext(), 11.0f);
    }

    private float getDeleteHandleHitSlopPx() {
        return EditorViewMetrics.dp(getContext(), 14.0f);
    }

    private float getSuggestedTextTouchTargetPx() {
        return Math.max(EditorViewMetrics.dp(getContext(), 28.0f), this.uiState.getCurrentStrokeWidth() * 1.8f);
    }

    private float getBlankTextDoubleTapSlopPx() {
        return EditorViewMetrics.dp(getContext(), 48.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void sessionListener$lambda$11(ImageEditorView ImageEditorView, EditorUiState state) {
        Intrinsics.checkNotNullParameter(state, "state");
        boolean z = ImageEditorView.lastObservedMode != EditorMode.CROP && state.getCurrentMode() == EditorMode.CROP;
        ImageEditorView.uiState = state;
        if (state.getCurrentMode() != EditorMode.SHAPE) {
            ImageEditorView.selectedShapeId = null;
        }
        if (state.getCurrentMode() != EditorMode.TEXT) {
            ImageEditorView.selectedTextId = null;
        }
        if (z) {
            ImageEditorView.ensureCropSelectionInitialized();
        }
        ImageEditorView.lastObservedMode = state.getCurrentMode();
        ImageEditorView.invalidate();
    }

    private Bitmap getBaseBitmap() {
        ManagedBitmap managedBitmap = this.baseBitmapHandle;
        if (managedBitmap != null) {
            return managedBitmap.getBitmapOrNull();
        }
        return null;
    }

    public final void bindSession(EditorSession editorSession) {
        Intrinsics.checkNotNullParameter(editorSession, "editorSession");
        EditorSession editorSession2 = this.session;
        if (editorSession2 == editorSession) {
            return;
        }
        if (editorSession2 != null) {
            editorSession2.removeListener(this.sessionListener);
        }
        this.session = editorSession;
        editorSession.addListener(this.sessionListener);
    }

    public final void setImageBitmap(ManagedBitmap handle) {
        Intrinsics.checkNotNullParameter(handle, "handle");
        releaseBaseBitmap();
        this.baseBitmapHandle = handle.acquire();
        rebuildMosaicBitmap();
        resetViewport();
        ensureCropSelectionInitialized();
        invalidate();
    }

    public final void clearImage() {
        releaseBaseBitmap();
        resetGestureState();
        this.cropSelection = null;
        invalidate();
    }

    public final Bitmap exportBitmap(float scaleFactor) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return null;
        }
        return EditorExportEngine.INSTANCE.exportBitmap(baseBitmap, this.uiState.getVisibleActions(), scaleFactor);
    }

    public final EditorPoint visibleImageCenterPoint() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return null;
        }
        float currentDisplayScale = currentDisplayScale(baseBitmap);
        float currentOriginX = currentOriginX(baseBitmap, currentDisplayScale);
        float currentOriginY = currentOriginY(baseBitmap, currentDisplayScale);
        float coerceIn = RangesKt.coerceIn((0.0f - currentOriginX) / currentDisplayScale, 0.0f, baseBitmap.getWidth());
        float coerceIn2 = RangesKt.coerceIn((0.0f - currentOriginY) / currentDisplayScale, 0.0f, baseBitmap.getHeight());
        float coerceIn3 = RangesKt.coerceIn((getWidth() - currentOriginX) / currentDisplayScale, 0.0f, baseBitmap.getWidth());
        float coerceIn4 = RangesKt.coerceIn((getHeight() - currentOriginY) / currentDisplayScale, 0.0f, baseBitmap.getHeight());
        if (coerceIn3 <= coerceIn || coerceIn4 <= coerceIn2) {
            return new EditorPoint(baseBitmap.getWidth() / 2.0f, baseBitmap.getHeight() / 2.0f);
        }
        return new EditorPoint((coerceIn + coerceIn3) / 2.0f, (coerceIn2 + coerceIn4) / 2.0f);
    }

    public final float suggestedTextSizeForInsert() {
        return suggestedTextSize();
    }

    public final Pair<Integer, Integer> currentBitmapSize() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return null;
        }
        return TuplesKt.to(Integer.valueOf(baseBitmap.getWidth()), Integer.valueOf(baseBitmap.getHeight()));
    }

    public final void resetViewport() {
        this.imageZoom = 0.92f;
        this.imagePanX = 0.0f;
        this.imagePanY = 0.0f;
        invalidate();
    }

    public final void clearCropSelection() {
        this.activeCropStart = null;
        this.activeCropEnd = null;
        this.cropSelection = null;
        this.cropDragStartPoint = null;
        this.cropDragStartRect = null;
        this.cropDragMode = CropDragMode.NEW;
        invalidate();
    }

    public final void cancelOngoingInteraction() {
        resetGestureState();
        invalidate();
    }

    public static /* synthetic */ void suppressMultiTouchTemporarily$default(ImageEditorView ImageEditorView, long j, int i, Object obj) {
        if ((i & 1) != 0) {
            j = 180;
        }
        ImageEditorView.suppressMultiTouchTemporarily(j);
    }

    public final void suppressMultiTouchTemporarily(long durationMs) {
        this.suppressMultiTouchUntilUptimeMs = SystemClock.uptimeMillis() + durationMs;
    }

    public final void setCenterBrushPreviewVisible(boolean visible) {
        if (this.centerBrushPreviewVisible == visible) {
            return;
        }
        this.centerBrushPreviewVisible = visible;
        invalidate();
    }

    public final boolean applyCropSelection() {
        Rect buildCropBitmapRect;
        Bitmap cropByRect;
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null || (buildCropBitmapRect = buildCropBitmapRect(baseBitmap)) == null) {
            return false;
        }
        cropByRect = Bitmap.createBitmap(
                baseBitmap,
                buildCropBitmapRect.left,
                buildCropBitmapRect.top,
                buildCropBitmapRect.width(),
                buildCropBitmapRect.height()
        );
        if (cropByRect == null) {
            return false;
        }
        List<EditAction> visibleActions = this.uiState.getVisibleActions();
        ArrayList arrayList = new ArrayList();
        Iterator it = visibleActions.iterator();
        while (it.hasNext()) {
            EditAction transformActionForCrop = transformActionForCrop((EditAction) it.next(), buildCropBitmapRect);
            if (transformActionForCrop != null) {
                arrayList.add(transformActionForCrop);
            }
        }
        ArrayList arrayList2 = arrayList;
        ManagedBitmap from = ManagedBitmap.from(cropByRect);
        try {
            setImageBitmap(from);
            from.close();
            EditorSession editorSession = this.session;
            if (editorSession != null) {
                editorSession.replaceActions(arrayList2);
            }
            clearCropSelection();
            return true;
        } catch (Throwable th) {
            from.close();
            throw th;
        }
    }

    @Override // android.view.View
    protected void onDetachedFromWindow() {
        EditorSession editorSession = this.session;
        if (editorSession != null) {
            editorSession.removeListener(this.sessionListener);
        }
        super.onDetachedFromWindow();
        releaseBaseBitmap();
    }

    @Override // android.view.View
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        Intrinsics.checkNotNullParameter(canvas, "canvas");
        super.onDraw(canvas);
        drawCheckerboard(canvas);
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        float currentDisplayScale = currentDisplayScale(baseBitmap);
        float currentOriginX = currentOriginX(baseBitmap, currentDisplayScale);
        float currentOriginY = currentOriginY(baseBitmap, currentDisplayScale);
        updateOverlayPaints(currentDisplayScale);
        canvas.save();
        canvas.translate(currentOriginX, currentOriginY);
        canvas.scale(currentDisplayScale, currentDisplayScale);
        canvas.drawBitmap(baseBitmap, 0.0f, 0.0f, (Paint) null);
        int saveLayer = canvas.saveLayer(0.0f, 0.0f, baseBitmap.getWidth(), baseBitmap.getHeight(), null);
        Iterator it = this.uiState.getVisibleActions().iterator();
        while (it.hasNext()) {
            EditorRenderUtils.INSTANCE.drawAction(canvas, (EditAction) it.next(), this.mosaicBitmap, null);
        }
        drawTemporaryAction(canvas);
        drawCropMask(canvas, baseBitmap);
        canvas.restoreToCount(saveLayer);
        drawCropOverlay(canvas, baseBitmap);
        EditAction.Shape selectedShapeAction = selectedShapeAction();
        if (selectedShapeAction != null) {
            canvas.drawRect(EditorRenderUtils.INSTANCE.buildShapeBounds(selectedShapeAction, 12f), this.selectionPaint);
            drawSelectedShapeDeleteHandle(canvas, selectedShapeAction);
            drawSelectedShapeHandle(canvas, selectedShapeAction);
        }
        EditAction.Text selectedTextAction = selectedTextAction();
        if (selectedTextAction != null) {
            canvas.drawRect(EditorRenderUtils.INSTANCE.buildTextBounds(selectedTextAction, 12f), this.selectionPaint);
            drawSelectedTextDeleteHandle(canvas, selectedTextAction);
            drawSelectedTextHandle(canvas, selectedTextAction);
        }
        canvas.restore();
        drawCenterBrushPreview(canvas);
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent event) {
        Intrinsics.checkNotNullParameter(event, "event");
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return false;
        }
        this.scaleDetector.onTouchEvent(event);
        boolean z = event.getPointerCount() > 1 && SystemClock.uptimeMillis() < this.suppressMultiTouchUntilUptimeMs;
        int actionMasked = event.getActionMasked();
        if (actionMasked == 0) {
            this.lastTouchX = event.getX();
            this.lastTouchY = event.getY();
            handleActionDown(baseBitmap, event.getX(), event.getY());
            return true;
        }
        if (actionMasked == 1) {
            handleActionUp(baseBitmap);
            performClick();
            return true;
        }
        if (actionMasked == 2) {
            if (event.getPointerCount() <= 1) {
                handleActionMove(baseBitmap, event.getX(), event.getY());
                this.lastTouchX = event.getX();
                this.lastTouchY = event.getY();
                return true;
            }
            if (z) {
                return true;
            }
            Pair<Float, Float> pointerFocus = pointerFocus(event);
            this.imagePanX += pointerFocus.getFirst().floatValue() - this.lastFocusX;
            this.imagePanY += pointerFocus.getSecond().floatValue() - this.lastFocusY;
            this.lastFocusX = pointerFocus.getFirst().floatValue();
            this.lastFocusY = pointerFocus.getSecond().floatValue();
            invalidate();
            return true;
        }
        if (actionMasked == 3) {
            resetGestureState();
            invalidate();
            return true;
        }
        if (actionMasked == 5) {
            if (z) {
                cancelActiveDrawing();
                this.gestureKind = GestureKind.NONE;
                return true;
            }
            cancelActiveDrawing();
            this.gestureKind = GestureKind.PAN;
            Pair<Float, Float> pointerFocus2 = pointerFocus(event);
            this.lastFocusX = pointerFocus2.getFirst().floatValue();
            this.lastFocusY = pointerFocus2.getSecond().floatValue();
            return true;
        }
        if (actionMasked == 6) {
            if (event.getPointerCount() > 2) {
                Pair<Float, Float> pointerFocusExcludingIndex = pointerFocusExcludingIndex(event, event.getActionIndex());
                this.lastFocusX = pointerFocusExcludingIndex.getFirst().floatValue();
                this.lastFocusY = pointerFocusExcludingIndex.getSecond().floatValue();
            } else {
                int i = event.getActionIndex() == 0 ? 1 : 0;
                this.lastTouchX = event.getX(i);
                float y = event.getY(i);
                this.lastTouchY = y;
                this.lastFocusX = this.lastTouchX;
                this.lastFocusY = y;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleActionDown(Bitmap bitmap, float x, float y) {
        GestureKind gestureKind;
        EditorPoint screenToImagePoint = screenToImagePoint(bitmap, x, y);
        EditorPoint screenToImagePointUnbounded = screenToImagePointUnbounded(bitmap, x, y);
        switch (WhenMappings.$EnumSwitchMapping$0[this.uiState.getCurrentMode().ordinal()]) {
            case 1:
                this.gestureKind = GestureKind.PAN;
                return;
            case 2:
                RectF rectF = this.cropSelection;
                CropDragMode detectCropDragMode = (rectF == null || screenToImagePointUnbounded == null) ? null : detectCropDragMode(rectF, screenToImagePointUnbounded);
                if (detectCropDragMode == null && screenToImagePoint == null) {
                    this.gestureKind = GestureKind.PAN;
                    return;
                }
                this.gestureKind = GestureKind.CROP;
                if (screenToImagePointUnbounded == null) {
                    screenToImagePointUnbounded = screenToImagePoint;
                }
                this.cropDragStartPoint = screenToImagePointUnbounded;
                if (detectCropDragMode != null) {
                    this.cropDragMode = detectCropDragMode;
                    this.cropDragStartRect = new RectF(rectF);
                    this.activeCropStart = null;
                    this.activeCropEnd = null;
                } else {
                    if (screenToImagePoint == null) {
                        return;
                    }
                    this.cropDragMode = CropDragMode.NEW;
                    this.cropDragStartRect = null;
                    this.activeCropStart = screenToImagePoint;
                    this.activeCropEnd = screenToImagePoint;
                    this.cropSelection = new RectF(screenToImagePoint.getX(), screenToImagePoint.getY(), screenToImagePoint.getX(), screenToImagePoint.getY());
                }
                invalidate();
                return;
            case 3:
            case 4:
            case 5:
                if (screenToImagePoint == null) {
                    this.gestureKind = GestureKind.PAN;
                    return;
                } else {
                    this.gestureKind = GestureKind.PATH;
                    this.activePoints = CollectionsKt.mutableListOf(screenToImagePoint);
                    return;
                }
            case 6:
                if (screenToImagePointUnbounded == null) {
                    screenToImagePointUnbounded = screenToImagePoint;
                }
                if (screenToImagePointUnbounded == null) {
                    this.gestureKind = GestureKind.PAN;
                    return;
                }
                if (deleteSelectedShapeIfHit(screenToImagePointUnbounded)) {
                    this.gestureKind = GestureKind.NONE;
                    invalidate();
                    return;
                }
                EditAction.Shape findShapeHandleAtPoint = findShapeHandleAtPoint(screenToImagePointUnbounded);
                if (findShapeHandleAtPoint == null) {
                    findShapeHandleAtPoint = findShapeAtPoint(screenToImagePointUnbounded);
                }
                this.selectedShapeId = findShapeHandleAtPoint != null ? findShapeHandleAtPoint.getId() : null;
                if (findShapeHandleAtPoint != null) {
                    if (isShapeResizeHandleHit(findShapeHandleAtPoint, screenToImagePointUnbounded)) {
                        gestureKind = GestureKind.SHAPE_RESIZE;
                    } else {
                        gestureKind = GestureKind.SHAPE_TAP;
                    }
                    this.gestureKind = gestureKind;
                    this.draggingShapeId = findShapeHandleAtPoint.getId();
                    this.dragStartImagePoint = screenToImagePointUnbounded;
                    this.dragStartShapeAction = findShapeHandleAtPoint;
                    this.dragStartShapeBounds = EditorRenderUtils.INSTANCE.buildShapeContentBounds(findShapeHandleAtPoint);
                    this.shapeGestureMoved = false;
                    this.activeShapeStart = null;
                    this.activeShapeEnd = null;
                } else {
                    if (screenToImagePoint == null) {
                        this.gestureKind = GestureKind.PAN;
                        return;
                    }
                    this.gestureKind = GestureKind.SHAPE_DRAW;
                    this.activeShapeStart = screenToImagePoint;
                    this.activeShapeEnd = screenToImagePoint;
                    this.draggingShapeId = null;
                    this.dragStartShapeAction = null;
                    this.dragStartShapeBounds = null;
                    this.shapeGestureMoved = false;
                }
                invalidate();
                return;
            case 7:
                if (screenToImagePointUnbounded != null) {
                    screenToImagePoint = screenToImagePointUnbounded;
                }
                if (screenToImagePoint == null) {
                    this.gestureKind = GestureKind.PAN;
                    return;
                }
                if (deleteSelectedTextIfHit(screenToImagePoint)) {
                    this.gestureKind = GestureKind.NONE;
                    invalidate();
                    return;
                }
                EditAction.Text findTextHandleAtPoint = findTextHandleAtPoint(screenToImagePoint);
                if (findTextHandleAtPoint == null) {
                    findTextHandleAtPoint = findTextAtPoint(screenToImagePoint);
                }
                this.selectedTextId = findTextHandleAtPoint != null ? findTextHandleAtPoint.getId() : null;
                if (findTextHandleAtPoint != null) {
                    this.gestureKind = isTextResizeHandleHit(findTextHandleAtPoint, screenToImagePoint) ? GestureKind.TEXT_RESIZE : GestureKind.TEXT_TAP;
                    this.draggingTextId = findTextHandleAtPoint.getId();
                    this.dragStartImagePoint = screenToImagePoint;
                    this.dragStartTextAnchor = findTextHandleAtPoint.getAnchor();
                    this.dragStartTextSize = Float.valueOf(findTextHandleAtPoint.getTextSize());
                    this.dragStartTextRadius = RangesKt.coerceAtLeast(distance(findTextHandleAtPoint.getAnchor(), EditorRenderUtils.INSTANCE.buildTextResizeHandleCenter(findTextHandleAtPoint, 12f)), 1.0f);
                    this.textGestureMoved = false;
                } else {
                    this.gestureKind = GestureKind.NONE;
                    this.selectedTextId = null;
                    this.textGestureMoved = false;
                }
                invalidate();
                return;
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    private void handleActionMove(Bitmap bitmap, float x, float y) {
        EditorPoint editorPoint;
        final EditAction.Shape shape;
        RectF rectF;
        EditorPoint screenToImagePoint = screenToImagePoint(bitmap, x, y);
        final EditorPoint screenToImagePointUnbounded = screenToImagePointUnbounded(bitmap, x, y);
        switch (WhenMappings.$EnumSwitchMapping$2[this.gestureKind.ordinal()]) {
            case 1:
            case 7:
            case 8:
                String str = this.draggingShapeId;
                if (str == null || (editorPoint = this.dragStartImagePoint) == null || (shape = this.dragStartShapeAction) == null || screenToImagePointUnbounded == null) {
                    return;
                }
                if (WhenMappings.$EnumSwitchMapping$2[this.gestureKind.ordinal()] == 1) {
                    final RectF rectF2 = this.dragStartShapeBounds;
                    if (rectF2 == null) {
                        return;
                    }
                    if (!this.shapeGestureMoved && (Math.abs(screenToImagePointUnbounded.getX() - editorPoint.getX()) > 4.0f || Math.abs(screenToImagePointUnbounded.getY() - editorPoint.getY()) > 4.0f)) {
                        this.shapeGestureMoved = true;
                    }
                    EditorSession editorSession = this.session;
                    if (editorSession != null) {
                        editorSession.updateShapeAction(str, new Function1() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$$ExternalSyntheticLambda1
                            @Override // kotlin.jvm.functions.Function1
                            public final Object invoke(Object obj) {
                                EditAction.Shape handleActionMove$lambda$19;
                                handleActionMove$lambda$19 = ImageEditorView.handleActionMove$lambda$19(ImageEditorView.this, shape, rectF2, screenToImagePointUnbounded, (EditAction.Shape) obj);
                                return handleActionMove$lambda$19;
                            }
                        });
                    }
                } else {
                    final float x2 = screenToImagePointUnbounded.getX() - editorPoint.getX();
                    final float y2 = screenToImagePointUnbounded.getY() - editorPoint.getY();
                    if (!this.shapeGestureMoved && (Math.abs(x2) > 4.0f || Math.abs(y2) > 4.0f)) {
                        this.gestureKind = GestureKind.SHAPE_DRAG;
                        this.shapeGestureMoved = true;
                    }
                    EditorSession editorSession2 = this.session;
                    if (editorSession2 != null) {
                        editorSession2.updateShapeAction(str, new Function1() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$$ExternalSyntheticLambda2
                            @Override // kotlin.jvm.functions.Function1
                            public final Object invoke(Object obj) {
                                EditAction.Shape handleActionMove$lambda$20;
                                handleActionMove$lambda$20 = ImageEditorView.handleActionMove$lambda$20(shape, x2, y2, (EditAction.Shape) obj);
                                return handleActionMove$lambda$20;
                            }
                        });
                    }
                }
                invalidate();
                return;
            case 2:
            case 9:
            case 10:
                String str2 = this.draggingTextId;
                if (str2 == null || screenToImagePointUnbounded == null) {
                    if (this.pendingTextTapPoint == null) {
                        this.imagePanX += x - this.lastTouchX;
                        this.imagePanY += y - this.lastTouchY;
                        invalidate();
                        return;
                    }
                    return;
                }
                EditorPoint editorPoint2 = this.dragStartImagePoint;
                if (editorPoint2 == null) {
                    return;
                }
                if (WhenMappings.$EnumSwitchMapping$2[this.gestureKind.ordinal()] == 2) {
                    Float f = this.dragStartTextSize;
                    if (f != null) {
                        final float floatValue = f.floatValue();
                        EditorPoint editorPoint3 = this.dragStartTextAnchor;
                        if (editorPoint3 == null) {
                            return;
                        }
                        float coerceAtLeast = RangesKt.coerceAtLeast(distance(editorPoint3, screenToImagePointUnbounded), 1.0f);
                        if (!this.textGestureMoved && Math.abs(coerceAtLeast - this.dragStartTextRadius) > 4.0f) {
                            this.textGestureMoved = true;
                        }
                        final float f2 = coerceAtLeast / this.dragStartTextRadius;
                        EditorSession editorSession3 = this.session;
                        if (editorSession3 != null) {
                            editorSession3.updateTextAction(str2, new Function1() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$$ExternalSyntheticLambda3
                                @Override // kotlin.jvm.functions.Function1
                                public final Object invoke(Object obj) {
                                    EditAction.Text handleActionMove$lambda$21;
                                    handleActionMove$lambda$21 = ImageEditorView.handleActionMove$lambda$21(floatValue, f2, ImageEditorView.this, (EditAction.Text) obj);
                                    return handleActionMove$lambda$21;
                                }
                            });
                            return;
                        }
                        return;
                    }
                    return;
                }
                final EditorPoint editorPoint4 = this.dragStartTextAnchor;
                if (editorPoint4 == null) {
                    return;
                }
                final float x3 = screenToImagePointUnbounded.getX() - editorPoint2.getX();
                final float y3 = screenToImagePointUnbounded.getY() - editorPoint2.getY();
                if (!this.textGestureMoved && (Math.abs(x3) > 4.0f || Math.abs(y3) > 4.0f)) {
                    this.gestureKind = GestureKind.TEXT_DRAG;
                    this.textGestureMoved = true;
                }
                EditorSession editorSession4 = this.session;
                if (editorSession4 != null) {
                    editorSession4.updateTextAction(str2, new Function1() { // from class: com.slideindex.app.imageeditor.ui.ImageEditorView$$ExternalSyntheticLambda4
                        @Override // kotlin.jvm.functions.Function1
                        public final Object invoke(Object obj) {
                            EditAction.Text handleActionMove$lambda$22;
                            handleActionMove$lambda$22 = ImageEditorView.handleActionMove$lambda$22(editorPoint4, x3, y3, (EditAction.Text) obj);
                            return handleActionMove$lambda$22;
                        }
                    });
                    return;
                }
                return;
            case 3:
                this.imagePanX += x - this.lastTouchX;
                this.imagePanY += y - this.lastTouchY;
                invalidate();
                return;
            case 4:
                if (screenToImagePointUnbounded != null) {
                    if (WhenMappings.$EnumSwitchMapping$1[this.cropDragMode.ordinal()] == 1) {
                        EditorPoint coerceWithinBitmap = coerceWithinBitmap(screenToImagePointUnbounded, bitmap);
                        this.activeCropEnd = coerceWithinBitmap;
                        this.cropSelection = normalizedRect(this.activeCropStart, coerceWithinBitmap);
                    } else {
                        EditorPoint editorPoint5 = this.cropDragStartPoint;
                        if (editorPoint5 != null && (rectF = this.cropDragStartRect) != null) {
                            this.cropSelection = updateCropRect(bitmap, rectF, editorPoint5, screenToImagePointUnbounded, this.cropDragMode);
                        }
                    }
                    invalidate();
                    return;
                }
                break;
            case 5:
                if (screenToImagePoint != null) {
                    this.activePoints.add(screenToImagePoint);
                    invalidate();
                    return;
                }
                break;
            case 6:
                if (screenToImagePoint != null) {
                    this.activeShapeEnd = screenToImagePoint;
                    invalidate();
                    return;
                }
                break;
            case 11:
                break;
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final EditAction.Shape handleActionMove$lambda$19(ImageEditorView host, EditAction.Shape shape, RectF rectF, EditorPoint editorPoint, EditAction.Shape it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return host.resizeShapeAction(shape, rectF, editorPoint);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final EditAction.Shape handleActionMove$lambda$20(EditAction.Shape shape, float f, float f2, EditAction.Shape it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return new EditAction.Shape(it.getId(), it.getType(), new EditorPoint(shape.getStart().getX() + f, shape.getStart().getY() + f2), new EditorPoint(shape.getEnd().getX() + f, shape.getEnd().getY() + f2), it.getColor(), it.getStrokeWidth());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final EditAction.Text handleActionMove$lambda$21(float f, float f2, ImageEditorView host, EditAction.Text it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return new EditAction.Text(it.getId(), it.getText(), it.getAnchor(), it.getColor(), RangesKt.coerceAtLeast(f * f2, host.minTextSizeInImageSpace()));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final EditAction.Text handleActionMove$lambda$22(EditorPoint editorPoint, float f, float f2, EditAction.Text it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return new EditAction.Text(it.getId(), it.getText(), new EditorPoint(editorPoint.getX() + f, editorPoint.getY() + f2), it.getColor(), it.getTextSize());
    }

    private void handleActionUp(Bitmap bitmap) {
        int i = WhenMappings.$EnumSwitchMapping$2[this.gestureKind.ordinal()];
        if (i == 2 || i == 9) {
            EditAction.Text selectedTextAction = selectedTextAction();
            if (selectedTextAction != null && !this.textGestureMoved) {
                handleTextTap(selectedTextAction);
            } else if (this.draggingTextId != null) {
                screenToImagePoint(bitmap, this.lastTouchX, this.lastTouchY);
            }
        } else if (i == 4) {
            commitCropSelection();
        } else if (i == 5) {
            commitPathAction();
        } else if (i == 6) {
            commitShapeAction();
        }
        resetGestureState();
        invalidate();
    }

    private void commitPathAction() {
        EditAction pathAction;
        EditorSession editorSession;
        if (this.activePoints.size() < 2) {
            return;
        }
        float currentBrushWidthInImageSpace = currentBrushWidthInImageSpace();
        int i = WhenMappings.$EnumSwitchMapping$0[this.uiState.getCurrentMode().ordinal()];
        if (i == 3) {
            pathAction = new EditAction.Doodle(buildActionId(), CollectionsKt.toList(this.activePoints), this.uiState.getCurrentColor(), currentBrushWidthInImageSpace, false);
        } else if (i == 4) {
            pathAction = new EditAction.Doodle(buildActionId(), CollectionsKt.toList(this.activePoints), 0, currentBrushWidthInImageSpace, true);
        } else {
            pathAction = i != 5 ? null : new EditAction.Mosaic(buildActionId(), CollectionsKt.toList(this.activePoints), currentBrushWidthInImageSpace);
        }
        if (pathAction == null || (editorSession = this.session) == null) {
            return;
        }
        editorSession.addAction(pathAction);
    }

    private void commitShapeAction() {
        EditorPoint editorPoint;
        EditorPoint editorPoint2 = this.activeShapeStart;
        if (editorPoint2 == null || (editorPoint = this.activeShapeEnd) == null || ((float) Math.sqrt(((editorPoint.getX() - editorPoint2.getX()) * (editorPoint.getX() - editorPoint2.getX())) + ((editorPoint.getY() - editorPoint2.getY()) * (editorPoint.getY() - editorPoint2.getY())))) < 4.0f) {
            return;
        }
        float currentBrushWidthInImageSpace = currentBrushWidthInImageSpace();
        EditorSession editorSession = this.session;
        if (editorSession != null) {
            editorSession.addAction(new EditAction.Shape(buildActionId(), this.uiState.getCurrentShapeType(), editorPoint2, editorPoint, this.uiState.getCurrentColor(), currentBrushWidthInImageSpace));
        }
    }

    private void commitCropSelection() {
        RectF rectF;
        Bitmap baseBitmap = getBaseBitmap();
        RectF rectF2 = null;
        if (baseBitmap == null) {
            this.cropSelection = null;
            return;
        }
        float minCropSizeInImageSpace = minCropSizeInImageSpace(baseBitmap);
        if (WhenMappings.$EnumSwitchMapping$1[this.cropDragMode.ordinal()] != 1 ? !((rectF = this.cropSelection) == null || rectF.width() < minCropSizeInImageSpace || rectF.height() < minCropSizeInImageSpace) : !((rectF = normalizedRect(this.activeCropStart, this.activeCropEnd)) == null || rectF.width() < minCropSizeInImageSpace || rectF.height() < minCropSizeInImageSpace)) {
            rectF2 = rectF;
        }
        this.cropSelection = rectF2;
    }

    private void drawTemporaryAction(Canvas canvas) {
        EditorPoint editorPoint;
        switch (WhenMappings.$EnumSwitchMapping$0[this.uiState.getCurrentMode().ordinal()]) {
            case 1:
            case 2:
            case 7:
                return;
            case 3:
            case 4:
                if (this.activePoints.size() < 2) {
                    return;
                }
                EditorRenderUtils.INSTANCE.drawAction( canvas, new EditAction.Doodle("temp", this.activePoints, this.uiState.getCurrentMode() == EditorMode.ERASER ? 0 : this.uiState.getCurrentColor(), currentBrushWidthInImageSpace(), this.uiState.getCurrentMode() == EditorMode.ERASER), this.mosaicBitmap, null);
                return;
            case 5:
                if (this.activePoints.size() < 2) {
                    return;
                }
                EditorRenderUtils.INSTANCE.drawAction( canvas, new EditAction.Mosaic("temp", this.activePoints, currentBrushWidthInImageSpace()), this.mosaicBitmap, null);
                return;
            case 6:
                EditorPoint editorPoint2 = this.activeShapeStart;
                if (editorPoint2 == null || (editorPoint = this.activeShapeEnd) == null) {
                    return;
                }
                EditorRenderUtils.INSTANCE.drawAction( canvas, new EditAction.Shape("temp", this.uiState.getCurrentShapeType(), editorPoint2, editorPoint, this.uiState.getCurrentColor(), currentBrushWidthInImageSpace()), this.mosaicBitmap, null);
                return;
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    private void drawCropMask(Canvas canvas, Bitmap bitmap) {
        RectF currentCropRect = currentCropRect();
        if (currentCropRect == null) {
            return;
        }
        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0.0f, 0.0f, bitmap.getWidth(), bitmap.getHeight(), Path.Direction.CW);
        path.addRect(currentCropRect, Path.Direction.CCW);
        canvas.drawPath(path, this.cropMaskPaint);
    }

    private void drawCropOverlay(Canvas canvas, Bitmap bitmap) {
        RectF currentCropRect = currentCropRect();
        if (currentCropRect == null) {
            return;
        }
        canvas.drawRect(currentCropRect, this.cropFramePaint);
        float cropHandleRadiusPx = getCropHandleRadiusPx() / currentDisplayScale(bitmap);
        drawControlCircle(canvas, new EditorPoint(currentCropRect.left, currentCropRect.top), cropHandleRadiusPx, this.cropHandlePaint);
        drawControlCircle(canvas, new EditorPoint(currentCropRect.right, currentCropRect.top), cropHandleRadiusPx, this.cropHandlePaint);
        drawControlCircle(canvas, new EditorPoint(currentCropRect.left, currentCropRect.bottom), cropHandleRadiusPx, this.cropHandlePaint);
        drawControlCircle(canvas, new EditorPoint(currentCropRect.right, currentCropRect.bottom), cropHandleRadiusPx, this.cropHandlePaint);
    }

    private void drawSelectedTextDeleteHandle(Canvas canvas, EditAction.Text action) {
        EditorPoint buildTextDeleteHandleCenter$default = EditorRenderUtils.INSTANCE.buildTextDeleteHandleCenter(action, 12f);
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        drawDeleteHandle(canvas, buildTextDeleteHandleCenter$default, getDeleteHandleRadiusPx() / currentDisplayScale(baseBitmap));
    }

    private void drawSelectedTextHandle(Canvas canvas, EditAction.Text action) {
        EditorPoint buildTextResizeHandleCenter$default = EditorRenderUtils.INSTANCE.buildTextResizeHandleCenter(action, 12f);
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        drawControlCircle(canvas, buildTextResizeHandleCenter$default, getTextResizeHandleRadiusPx() / currentDisplayScale(baseBitmap), this.textHandlePaint);
    }

    private void drawSelectedShapeDeleteHandle(Canvas canvas, EditAction.Shape action) {
        EditorPoint buildShapeDeleteHandleCenter$default = EditorRenderUtils.INSTANCE.buildShapeDeleteHandleCenter(action, 12f);
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        drawDeleteHandle(canvas, buildShapeDeleteHandleCenter$default, getDeleteHandleRadiusPx() / currentDisplayScale(baseBitmap));
    }

    private void drawSelectedShapeHandle(Canvas canvas, EditAction.Shape action) {
        EditorPoint buildShapeResizeHandleCenter$default = EditorRenderUtils.INSTANCE.buildShapeResizeHandleCenter(action, 12f);
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        drawControlCircle(canvas, buildShapeResizeHandleCenter$default, getShapeResizeHandleRadiusPx() / currentDisplayScale(baseBitmap), this.textHandlePaint);
    }

    private void drawControlCircle(Canvas canvas, EditorPoint center, float radius, Paint fillPaint) {
        canvas.drawCircle(center.getX(), center.getY(), radius, fillPaint);
        canvas.drawCircle(center.getX(), center.getY(), radius, this.controlOutlinePaint);
    }

    private void drawDeleteHandle(Canvas canvas, EditorPoint center, float radius) {
        canvas.drawCircle(center.getX(), center.getY(), radius, this.deleteHandlePaint);
        canvas.drawCircle(center.getX(), center.getY(), radius, this.deleteIconPaint);
        float f = radius * 0.42f;
        canvas.drawLine(center.getX() - f, center.getY() - f, center.getX() + f, center.getY() + f, this.deleteIconPaint);
        canvas.drawLine(center.getX() - f, center.getY() + f, center.getX() + f, center.getY() - f, this.deleteIconPaint);
    }

    private void drawCenterBrushPreview(Canvas canvas) {
        int alphaComponent;
        int i;
        if (this.centerBrushPreviewVisible && supportsCenterBrushPreview(this.uiState.getCurrentMode())) {
            float coerceAtLeast = RangesKt.coerceAtLeast(this.uiState.getCurrentStrokeWidth(), 1.0f) / 2.0f;
            int i2 = WhenMappings.$EnumSwitchMapping$0[this.uiState.getCurrentMode().ordinal()];
            if (i2 == 4) {
                alphaComponent = ColorUtils.setAlphaComponent(MaterialColors.getColor(this, R.attr.colorSurfaceContainerHighest), EditorViewMetrics.SURFACE_CONTAINER_ALPHA);
            } else if (i2 == 5) {
                alphaComponent = MaterialColors.getColor(this, android.R.attr.colorPrimary);
            } else {
                alphaComponent = this.uiState.getCurrentColor();
            }
            if (WhenMappings.$EnumSwitchMapping$0[this.uiState.getCurrentMode().ordinal()] == 4) {
                i = MaterialColors.getColor(this, R.attr.colorOnSurface);
            } else {
                i = ColorUtils.calculateLuminance(alphaComponent) > 0.5d ? ViewCompat.MEASURED_STATE_MASK : -1;
            }
            this.centerBrushPreviewFillPaint.setColor(alphaComponent);
            this.centerBrushPreviewOutlinePaint.setColor(ColorUtils.setAlphaComponent(i, 230));
            this.centerBrushPreviewOutlinePaint.setStrokeWidth(EditorViewMetrics.dp(getContext(), 2.0f));
            canvas.drawCircle(getWidth() / 2.0f, getHeight() / 2.0f, coerceAtLeast, this.centerBrushPreviewFillPaint);
            canvas.drawCircle(getWidth() / 2.0f, getHeight() / 2.0f, coerceAtLeast, this.centerBrushPreviewOutlinePaint);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void applyScale(float scaleFactor, float focusX, float focusY) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return;
        }
        float currentDisplayScale = currentDisplayScale(baseBitmap);
        float currentOriginX = currentOriginX(baseBitmap, currentDisplayScale);
        float currentOriginY = currentOriginY(baseBitmap, currentDisplayScale);
        this.imageZoom = RangesKt.coerceIn(this.imageZoom * scaleFactor, 0.35f, MAX_IMAGE_ZOOM);
        float currentDisplayScale2 = currentDisplayScale(baseBitmap);
        float baseOriginX = baseOriginX(baseBitmap, currentDisplayScale2);
        float baseOriginY = baseOriginY(baseBitmap, currentDisplayScale2);
        this.imagePanX = (focusX - (((focusX - currentOriginX) / currentDisplayScale) * currentDisplayScale2)) - baseOriginX;
        this.imagePanY = (focusY - (((focusY - currentOriginY) / currentDisplayScale) * currentDisplayScale2)) - baseOriginY;
        invalidate();
    }

    private final EditAction.Text findTextAtPoint(EditorPoint point) {
        Object obj;
        List asReversed = CollectionsKt.asReversed(this.uiState.getVisibleActions());
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : asReversed) {
            if (obj2 instanceof EditAction.Text) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            obj = null;
            if (!it.hasNext()) {
                break;
            }
            Object next = it.next();
            if (EditorRenderUtils.INSTANCE.buildTextBounds((EditAction.Text) next, 12f).contains(point.getX(), point.getY())) {
                obj = next;
                break;
            }
        }
        return (EditAction.Text) obj;
    }

    private final EditAction.Shape findShapeAtPoint(EditorPoint point) {
        Object obj;
        List asReversed = CollectionsKt.asReversed(this.uiState.getVisibleActions());
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : asReversed) {
            if (obj2 instanceof EditAction.Shape) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            obj = null;
            if (!it.hasNext()) {
                break;
            }
            Object next = it.next();
            if (EditorRenderUtils.INSTANCE.buildShapeBounds((EditAction.Shape) next, 12f).contains(point.getX(), point.getY())) {
                obj = next;
                break;
            }
        }
        return (EditAction.Shape) obj;
    }

    private final EditAction.Shape findShapeHandleAtPoint(EditorPoint point) {
        Object obj;
        List asReversed = CollectionsKt.asReversed(this.uiState.getVisibleActions());
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : asReversed) {
            if (obj2 instanceof EditAction.Shape) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            if (isShapeResizeHandleHit((EditAction.Shape) obj, point)) {
                break;
            }
        }
        return (EditAction.Shape) obj;
    }

    private final EditAction.Text findTextHandleAtPoint(EditorPoint point) {
        Object obj;
        List asReversed = CollectionsKt.asReversed(this.uiState.getVisibleActions());
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : asReversed) {
            if (obj2 instanceof EditAction.Text) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            if (isTextResizeHandleHit((EditAction.Text) obj, point)) {
                break;
            }
        }
        return (EditAction.Text) obj;
    }

    private final boolean deleteSelectedShapeIfHit(EditorPoint point) {
        EditAction.Shape selectedShapeAction = selectedShapeAction();
        if (selectedShapeAction == null || !isShapeDeleteHandleHit(selectedShapeAction, point)) {
            return false;
        }
        EditorSession editorSession = this.session;
        if (editorSession != null) {
            editorSession.removeAction(selectedShapeAction.getId());
        }
        this.selectedShapeId = null;
        this.draggingShapeId = null;
        this.dragStartShapeAction = null;
        this.dragStartShapeBounds = null;
        this.dragStartImagePoint = null;
        this.shapeGestureMoved = false;
        return true;
    }

    private final boolean deleteSelectedTextIfHit(EditorPoint point) {
        EditAction.Text selectedTextAction = selectedTextAction();
        if (selectedTextAction == null || !isTextDeleteHandleHit(selectedTextAction, point)) {
            return false;
        }
        EditorSession editorSession = this.session;
        if (editorSession != null) {
            editorSession.removeAction(selectedTextAction.getId());
        }
        this.selectedTextId = null;
        this.draggingTextId = null;
        this.dragStartImagePoint = null;
        this.dragStartTextAnchor = null;
        this.dragStartTextSize = null;
        this.dragStartTextRadius = 0.0f;
        this.pendingTextTapPoint = null;
        this.textGestureMoved = false;
        this.lastTextTapId = null;
        this.lastTextTapTimestamp = 0L;
        return true;
    }

    private final EditAction.Shape selectedShapeAction() {
        String str = this.selectedShapeId;
        Object obj = null;
        if (str == null) {
            return null;
        }
        List<EditAction> visibleActions = this.uiState.getVisibleActions();
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : visibleActions) {
            if (obj2 instanceof EditAction.Shape) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            Object next = it.next();
            if (Intrinsics.areEqual(((EditAction.Shape) next).getId(), str)) {
                obj = next;
                break;
            }
        }
        return (EditAction.Shape) obj;
    }

    private final EditAction.Text selectedTextAction() {
        String str = this.selectedTextId;
        Object obj = null;
        if (str == null) {
            return null;
        }
        List<EditAction> visibleActions = this.uiState.getVisibleActions();
        ArrayList arrayList = new ArrayList();
        for (Object obj2 : visibleActions) {
            if (obj2 instanceof EditAction.Text) {
                arrayList.add(obj2);
            }
        }
        Iterator it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            Object next = it.next();
            if (Intrinsics.areEqual(((EditAction.Text) next).getId(), str)) {
                obj = next;
                break;
            }
        }
        return (EditAction.Text) obj;
    }

    private void handleTextTap(EditAction.Text action) {
        long currentTimeMillis = System.currentTimeMillis();
        boolean z = Intrinsics.areEqual(this.lastTextTapId, action.getId()) && currentTimeMillis - this.lastTextTapTimestamp <= DOUBLE_TAP_TIMEOUT_MS;
        this.lastTextTapId = action.getId();
        this.lastTextTapTimestamp = currentTimeMillis;
        if (z) {
            Callback callback = this.callback;
            if (callback != null) {
                callback.onRequestEditText(action);
            }
            this.lastTextTapId = null;
            this.lastTextTapTimestamp = 0L;
        }
    }

    private final float distance(EditorPoint start, EditorPoint end) {
        float x = end.getX() - start.getX();
        float y = end.getY() - start.getY();
        return (float) Math.sqrt((x * x) + (y * y));
    }

    private final boolean isTextResizeHandleHit(EditAction.Text action, EditorPoint point) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return false;
        }
        return distance(point, EditorRenderUtils.INSTANCE.buildTextResizeHandleCenter(action, 12f)) <= (getTextResizeHandleRadiusPx() + getTextResizeHitSlopPx()) / currentDisplayScale(baseBitmap);
    }

    private final boolean isTextDeleteHandleHit(EditAction.Text action, EditorPoint point) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return false;
        }
        return distance(point, EditorRenderUtils.INSTANCE.buildTextDeleteHandleCenter(action, 12f)) <= (getDeleteHandleRadiusPx() + getDeleteHandleHitSlopPx()) / currentDisplayScale(baseBitmap);
    }

    private final boolean isShapeResizeHandleHit(EditAction.Shape action, EditorPoint point) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return false;
        }
        return distance(point, EditorRenderUtils.INSTANCE.buildShapeResizeHandleCenter(action, 12f)) <= (getShapeResizeHandleRadiusPx() + getShapeResizeHitSlopPx()) / currentDisplayScale(baseBitmap);
    }

    private final boolean isShapeDeleteHandleHit(EditAction.Shape action, EditorPoint point) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return false;
        }
        return distance(point, EditorRenderUtils.INSTANCE.buildShapeDeleteHandleCenter(action, 12f)) <= (getDeleteHandleRadiusPx() + getDeleteHandleHitSlopPx()) / currentDisplayScale(baseBitmap);
    }

    private void updateOverlayPaints(float scale) {
        float coerceAtLeast = 1.0f / RangesKt.coerceAtLeast(scale, 1.0E-4f);
        ImageEditorView ImageEditorView = this;
        int color = MaterialColors.getColor(ImageEditorView, android.R.attr.colorPrimary);
        int color2 = MaterialColors.getColor(ImageEditorView, R.attr.colorOnPrimary);
        int color3 = MaterialColors.getColor(ImageEditorView, androidx.appcompat.R.attr.colorError);
        int i = ColorUtils.calculateLuminance(color3) > 0.5d ? ViewCompat.MEASURED_STATE_MASK : -1;
        this.selectionPaint.setColor(color);
        this.selectionPaint.setStrokeWidth(EditorViewMetrics.dp(getContext(), 2.0f) * coerceAtLeast);
        this.selectionPaint.setPathEffect(new DashPathEffect(new float[]{EditorViewMetrics.dp(getContext(), 18.0f) * coerceAtLeast, EditorViewMetrics.dp(getContext(), 10.0f) * coerceAtLeast}, 0.0f));
        this.cropFramePaint.setColor(color);
        this.cropFramePaint.setStrokeWidth(EditorViewMetrics.dp(getContext(), 3.0f) * coerceAtLeast);
        this.cropHandlePaint.setColor(color);
        this.textHandlePaint.setColor(color);
        this.controlOutlinePaint.setColor(color2);
        this.controlOutlinePaint.setStrokeWidth(Math.max(EditorViewMetrics.dp(getContext(), 1.5f) * coerceAtLeast, 1.0f));
        this.deleteHandlePaint.setColor(color3);
        this.deleteIconPaint.setColor(i);
        this.deleteIconPaint.setStrokeWidth(Math.max(EditorViewMetrics.dp(getContext(), 2.0f) * coerceAtLeast, 1.0f));
    }

    private final boolean supportsCenterBrushPreview(EditorMode mode) {
        return mode == EditorMode.DOODLE || mode == EditorMode.ERASER || mode == EditorMode.SHAPE || mode == EditorMode.MOSAIC;
    }

    private void drawCheckerboard(Canvas canvas) {
        int cell = Math.max(18, (int) (getResources().getDisplayMetrics().density * 12));
        int colorA = Color.parseColor("#2A2A2A");
        int colorB = Color.parseColor("#1F1F1F");
        int row = 0;
        for (int y = 0; y < getHeight(); y += cell, row++) {
            int col = 0;
            for (int x = 0; x < getWidth(); x += cell, col++) {
                this.checkerPaint.setColor(((row + col) & 1) == 0 ? colorA : colorB);
                canvas.drawRect(
                        x,
                        y,
                        Math.min(getWidth(), x + cell),
                        Math.min(getHeight(), y + cell),
                        this.checkerPaint);
            }
        }
    }

    private final float currentDisplayScale(Bitmap bitmap) {
        return Math.min(getWidth() / bitmap.getWidth(), getHeight() / bitmap.getHeight()) * this.imageZoom;
    }

    private final float currentOriginX(Bitmap bitmap, float scale) {
        return baseOriginX(bitmap, scale) + this.imagePanX;
    }

    private final float currentOriginY(Bitmap bitmap, float scale) {
        return baseOriginY(bitmap, scale) + this.imagePanY;
    }

    private final float baseOriginX(Bitmap bitmap, float scale) {
        return (getWidth() - (bitmap.getWidth() * scale)) / 2.0f;
    }

    private final float baseOriginY(Bitmap bitmap, float scale) {
        return (getHeight() - (bitmap.getHeight() * scale)) / 2.0f;
    }

    private final EditorPoint screenToImagePoint(Bitmap bitmap, float x, float y) {
        EditorPoint screenToImagePointUnbounded = screenToImagePointUnbounded(bitmap, x, y);
        if (screenToImagePointUnbounded == null) {
            return null;
        }
        float width = bitmap.getWidth();
        float x2 = screenToImagePointUnbounded.getX();
        if (0.0f <= x2 && x2 <= width) {
            float height = bitmap.getHeight();
            float y2 = screenToImagePointUnbounded.getY();
            if (0.0f <= y2 && y2 <= height) {
                return screenToImagePointUnbounded;
            }
        }
        return null;
    }

    private final EditorPoint screenToImagePointUnbounded(Bitmap bitmap, float x, float y) {
        float currentDisplayScale = currentDisplayScale(bitmap);
        return new EditorPoint((x - currentOriginX(bitmap, currentDisplayScale)) / currentDisplayScale, (y - currentOriginY(bitmap, currentDisplayScale)) / currentDisplayScale);
    }

    private final EditorPoint coerceWithinBitmap(EditorPoint editorPoint, Bitmap bitmap) {
        return new EditorPoint(RangesKt.coerceIn(editorPoint.getX(), 0.0f, bitmap.getWidth()), RangesKt.coerceIn(editorPoint.getY(), 0.0f, bitmap.getHeight()));
    }

    private final Pair<Float, Float> pointerFocus(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        float f = 0.0f;
        float f2 = 0.0f;
        for (int i = 0; i < pointerCount; i++) {
            f += event.getX(i);
            f2 += event.getY(i);
        }
        return TuplesKt.to(Float.valueOf(f / event.getPointerCount()), Float.valueOf(f2 / event.getPointerCount()));
    }

    private final Pair<Float, Float> pointerFocusExcludingIndex(MotionEvent event, int excludedIndex) {
        int pointerCount = event.getPointerCount();
        float f = 0.0f;
        int i = 0;
        float f2 = 0.0f;
        for (int i2 = 0; i2 < pointerCount; i2++) {
            if (i2 != excludedIndex) {
                f += event.getX(i2);
                f2 += event.getY(i2);
                i++;
            }
        }
        if (i <= 0) {
            return TuplesKt.to(Float.valueOf(this.lastFocusX), Float.valueOf(this.lastFocusY));
        }
        float f3 = i;
        return TuplesKt.to(Float.valueOf(f / f3), Float.valueOf(f2 / f3));
    }

    private final float suggestedTextSize() {
        return suggestedTextSizeInImageSpace();
    }

    private final float currentBrushWidthInImageSpace() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return this.uiState.getCurrentStrokeWidth();
        }
        return RangesKt.coerceAtLeast(this.uiState.getCurrentStrokeWidth() / RangesKt.coerceAtLeast(currentDisplayScale(baseBitmap), 1.0E-4f), 1.0f);
    }

    private final float suggestedTextSizeInImageSpace() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return getSuggestedTextTouchTargetPx();
        }
        return RangesKt.coerceAtLeast(getSuggestedTextTouchTargetPx() / RangesKt.coerceAtLeast(currentDisplayScale(baseBitmap), 1.0E-4f), 1.0f);
    }

    private float minTextSizeInImageSpace() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return getMinTextTouchTargetPx();
        }
        return RangesKt.coerceAtLeast(getMinTextTouchTargetPx() / RangesKt.coerceAtLeast(currentDisplayScale(baseBitmap), 1.0E-4f), 1.0f);
    }

    private final float minShapeSizeInImageSpace() {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return getMinShapeTouchTargetPx();
        }
        return RangesKt.coerceAtLeast(getMinShapeTouchTargetPx() / RangesKt.coerceAtLeast(currentDisplayScale(baseBitmap), 1.0E-4f), 1.0f);
    }

    private final EditAction.Shape resizeShapeAction(EditAction.Shape action, RectF originalBounds, EditorPoint targetPoint) {
        float minShapeSizeInImageSpace = minShapeSizeInImageSpace();
        RectF buildShapeBounds$default = EditorRenderUtils.INSTANCE.buildShapeBounds(action, 12f);
        RectF rectF = new RectF(originalBounds.left, originalBounds.top, Math.max(targetPoint.getX() - (buildShapeBounds$default.right - originalBounds.right), originalBounds.left + minShapeSizeInImageSpace), Math.max(targetPoint.getY() - (buildShapeBounds$default.bottom - originalBounds.bottom), originalBounds.top + minShapeSizeInImageSpace));
        return new EditAction.Shape(action.getId(), action.getType(), mapPointBetweenRects(action.getStart(), originalBounds, rectF, 0.0f, 0.0f), mapPointBetweenRects(action.getEnd(), originalBounds, rectF, 1.0f, 1.0f), action.getColor(), action.getStrokeWidth());
    }

    private final EditorPoint mapPointBetweenRects(EditorPoint point, RectF sourceRect, RectF targetRect, float zeroWidthFallbackFraction, float zeroHeightFallbackFraction) {
        return new EditorPoint(mapCoordinateBetweenRanges(point.getX(), sourceRect.left, sourceRect.right, targetRect.left, targetRect.right, zeroWidthFallbackFraction), mapCoordinateBetweenRanges(point.getY(), sourceRect.top, sourceRect.bottom, targetRect.top, targetRect.bottom, zeroHeightFallbackFraction));
    }

    private final float mapCoordinateBetweenRanges(float value, float sourceStart, float sourceEnd, float targetStart, float targetEnd, float zeroFallbackFraction) {
        float f = sourceEnd - sourceStart;
        if (Math.abs(f) >= 1.0E-4f) {
            zeroFallbackFraction = (value - sourceStart) / f;
        }
        return targetStart + ((targetEnd - targetStart) * zeroFallbackFraction);
    }

    private void rebuildMosaicBitmap() {
        Bitmap bitmap = this.mosaicBitmap;
        if (bitmap != null) {
            if (bitmap.isRecycled()) {
                bitmap = null;
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        Bitmap baseBitmap = getBaseBitmap();
        this.mosaicBitmap = baseBitmap != null ? EditorRenderUtils.INSTANCE.generateMosaicBitmap(baseBitmap) : null;
    }

    private void cancelActiveDrawing() {
        this.activePoints.clear();
        this.activeShapeStart = null;
        this.activeShapeEnd = null;
        this.activeCropStart = null;
        this.activeCropEnd = null;
        this.cropDragStartPoint = null;
        this.cropDragStartRect = null;
        this.cropDragMode = CropDragMode.NEW;
        this.draggingShapeId = null;
        this.dragStartShapeAction = null;
        this.dragStartShapeBounds = null;
        this.shapeGestureMoved = false;
    }

    private void resetGestureState() {
        this.gestureKind = GestureKind.NONE;
        this.activePoints.clear();
        this.activeShapeStart = null;
        this.activeShapeEnd = null;
        this.activeCropStart = null;
        this.activeCropEnd = null;
        this.cropDragStartPoint = null;
        this.cropDragStartRect = null;
        this.cropDragMode = CropDragMode.NEW;
        this.draggingShapeId = null;
        this.dragStartShapeAction = null;
        this.dragStartShapeBounds = null;
        this.shapeGestureMoved = false;
        this.draggingTextId = null;
        this.dragStartImagePoint = null;
        this.dragStartTextAnchor = null;
        this.dragStartTextSize = null;
        this.dragStartTextRadius = 0.0f;
        this.pendingTextTapPoint = null;
        this.textGestureMoved = false;
    }

    private void releaseBaseBitmap() {
        Bitmap bitmap = this.mosaicBitmap;
        if (bitmap != null) {
            if (bitmap.isRecycled()) {
                bitmap = null;
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        this.mosaicBitmap = null;
        ManagedBitmap managedBitmap = this.baseBitmapHandle;
        if (managedBitmap != null) {
            managedBitmap.close();
        }
        this.baseBitmapHandle = null;
    }

    private final String buildActionId() {
        return String.valueOf(System.nanoTime());
    }

    private void ensureCropSelectionInitialized() {
        Bitmap baseBitmap;
        if (this.uiState.getCurrentMode() == EditorMode.CROP && this.cropSelection == null && (baseBitmap = getBaseBitmap()) != null) {
            this.cropSelection = new RectF(0.0f, 0.0f, baseBitmap.getWidth(), baseBitmap.getHeight());
            this.activeCropStart = null;
            this.activeCropEnd = null;
            this.cropDragStartPoint = null;
            this.cropDragStartRect = null;
            this.cropDragMode = CropDragMode.NEW;
        }
    }

    private final Rect buildCropBitmapRect(Bitmap bitmap) {
        RectF rectF = this.cropSelection;
        if (rectF == null) {
            return null;
        }
        Rect rect = new Rect(RangesKt.coerceIn((int) rectF.left, 0, bitmap.getWidth()), RangesKt.coerceIn((int) rectF.top, 0, bitmap.getHeight()), RangesKt.coerceIn((int) rectF.right, 0, bitmap.getWidth()), RangesKt.coerceIn((int) rectF.bottom, 0, bitmap.getHeight()));
        if (rect.width() <= 0 || rect.height() <= 0) {
            return null;
        }
        return rect;
    }

    private final RectF normalizedRect(EditorPoint start, EditorPoint end) {
        if (start == null || end == null) {
            return null;
        }
        return new RectF(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()), Math.max(start.getX(), end.getX()), Math.max(start.getY(), end.getY()));
    }

    private final RectF currentCropRect() {
        if (this.uiState.getCurrentMode() != EditorMode.CROP) {
            return null;
        }
        RectF normalizedRect = normalizedRect(this.activeCropStart, this.activeCropEnd);
        return normalizedRect == null ? this.cropSelection : normalizedRect;
    }

    private final CropDragMode detectCropDragMode(RectF rect, EditorPoint point) {
        Bitmap baseBitmap = getBaseBitmap();
        if (baseBitmap == null) {
            return null;
        }
        float currentDisplayScale = currentDisplayScale(baseBitmap);
        float cropCornerHitRadiusPx = getCropCornerHitRadiusPx() / currentDisplayScale;
        if (distance(point, new EditorPoint(rect.left, rect.top)) <= cropCornerHitRadiusPx) {
            return CropDragMode.TOP_LEFT;
        }
        if (distance(point, new EditorPoint(rect.right, rect.top)) <= cropCornerHitRadiusPx) {
            return CropDragMode.TOP_RIGHT;
        }
        if (distance(point, new EditorPoint(rect.left, rect.bottom)) <= cropCornerHitRadiusPx) {
            return CropDragMode.BOTTOM_LEFT;
        }
        if (distance(point, new EditorPoint(rect.right, rect.bottom)) <= cropCornerHitRadiusPx) {
            return CropDragMode.BOTTOM_RIGHT;
        }
        float cropEdgeHitSlopPx = getCropEdgeHitSlopPx() / currentDisplayScale;
        boolean z = Math.abs(point.getX() - rect.left) <= cropEdgeHitSlopPx;
        boolean z2 = Math.abs(point.getX() - rect.right) <= cropEdgeHitSlopPx;
        boolean z3 = Math.abs(point.getY() - rect.top) <= cropEdgeHitSlopPx;
        boolean z4 = Math.abs(point.getY() - rect.bottom) <= cropEdgeHitSlopPx;
        if (z) {
            float f = rect.top;
            float f2 = rect.bottom;
            float y = point.getY();
            if (f <= y && y <= f2) {
                return CropDragMode.LEFT;
            }
        }
        if (z2) {
            float f3 = rect.top;
            float f4 = rect.bottom;
            float y2 = point.getY();
            if (f3 <= y2 && y2 <= f4) {
                return CropDragMode.RIGHT;
            }
        }
        if (z3) {
            float f5 = rect.left;
            float f6 = rect.right;
            float x = point.getX();
            if (f5 <= x && x <= f6) {
                return CropDragMode.TOP;
            }
        }
        if (z4) {
            float f7 = rect.left;
            float f8 = rect.right;
            float x2 = point.getX();
            if (f7 <= x2 && x2 <= f8) {
                return CropDragMode.BOTTOM;
            }
        }
        if (rect.contains(point.getX(), point.getY())) {
            return CropDragMode.MOVE;
        }
        return null;
    }

    private final RectF updateCropRect(Bitmap bitmap, RectF startRect, EditorPoint startPoint, EditorPoint currentPoint, CropDragMode mode) {
        float x = currentPoint.getX() - startPoint.getX();
        float y = currentPoint.getY() - startPoint.getY();
        switch (WhenMappings.$EnumSwitchMapping$1[mode.ordinal()]) {
            case 1:
                return startRect;
            case 2:
                RectF rectF = new RectF(startRect);
                rectF.offsetTo(RangesKt.coerceIn(startRect.left + x, 0.0f, bitmap.getWidth() - startRect.width()), RangesKt.coerceIn(startRect.top + y, 0.0f, bitmap.getHeight() - startRect.height()));
                return rectF;
            case 3:
                return resizeCropRect$default(this, bitmap, startRect, Float.valueOf(startRect.left + x), null, null, null, 56, null);
            case 4:
                return resizeCropRect$default(this, bitmap, startRect, null, Float.valueOf(startRect.top + y), null, null, 52, null);
            case 5:
                return resizeCropRect$default(this, bitmap, startRect, null, null, Float.valueOf(startRect.right + x), null, 44, null);
            case 6:
                return resizeCropRect$default(this, bitmap, startRect, null, null, null, Float.valueOf(startRect.bottom + y), 28, null);
            case 7:
                return resizeCropRect$default(this, bitmap, startRect, Float.valueOf(startRect.left + x), Float.valueOf(startRect.top + y), null, null, 48, null);
            case 8:
                return resizeCropRect$default(this, bitmap, startRect, null, Float.valueOf(startRect.top + y), Float.valueOf(startRect.right + x), null, 36, null);
            case 9:
                return resizeCropRect$default(this, bitmap, startRect, Float.valueOf(startRect.left + x), null, null, Float.valueOf(startRect.bottom + y), 24, null);
            case 10:
                return resizeCropRect$default(this, bitmap, startRect, null, null, Float.valueOf(startRect.right + x), Float.valueOf(startRect.bottom + y), 12, null);
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    static /* synthetic */ RectF resizeCropRect$default(ImageEditorView ImageEditorView, Bitmap bitmap, RectF rectF, Float f, Float f2, Float f3, Float f4, int i, Object obj) {
        if ((i & 4) != 0) {
            f = null;
        }
        if ((i & 8) != 0) {
            f2 = null;
        }
        if ((i & 16) != 0) {
            f3 = null;
        }
        if ((i & 32) != 0) {
            f4 = null;
        }
        return ImageEditorView.resizeCropRect(bitmap, rectF, f, f2, f3, f4);
    }

    private final RectF resizeCropRect(Bitmap bitmap, RectF startRect, Float left, Float top, Float right, Float bottom) {
        float minCropSizeInImageSpace = minCropSizeInImageSpace(bitmap);
        float f = startRect.left;
        float f2 = startRect.top;
        float f3 = startRect.right;
        float f4 = startRect.bottom;
        if (left != null) {
            f = RangesKt.coerceIn(left.floatValue(), 0.0f, f3 - minCropSizeInImageSpace);
        }
        if (top != null) {
            f2 = RangesKt.coerceIn(top.floatValue(), 0.0f, f4 - minCropSizeInImageSpace);
        }
        if (right != null) {
            f3 = RangesKt.coerceIn(right.floatValue(), f + minCropSizeInImageSpace, bitmap.getWidth());
        }
        if (bottom != null) {
            f4 = RangesKt.coerceIn(bottom.floatValue(), minCropSizeInImageSpace + f2, bitmap.getHeight());
        }
        return new RectF(f, f2, f3, f4);
    }

    private final float minCropSizeInImageSpace(Bitmap bitmap) {
        return RangesKt.coerceAtMost(getMinCropTouchTargetPx() / RangesKt.coerceAtLeast(currentDisplayScale(bitmap), 1.0E-4f), Math.min(bitmap.getWidth(), bitmap.getHeight()));
    }

    private final EditAction transformActionForCrop(EditAction action, Rect cropRect) {
        RectF rectF = new RectF(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom);
        if (action instanceof EditAction.Doodle) {
            EditAction.Doodle doodle = (EditAction.Doodle) action;
            if (!RectF.intersects(pathBounds(doodle.getPoints()), rectF)) {
                return null;
            }
            List<EditorPoint> points = doodle.getPoints();
            ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(points, 10));
            Iterator it = points.iterator();
            while (it.hasNext()) {
                arrayList.add(offsetByCrop((EditorPoint) it.next(), cropRect));
            }
            return new EditAction.Doodle(doodle.getId(), arrayList, doodle.getColor(), doodle.getStrokeWidth(), doodle.isEraser());
        }
        if (action instanceof EditAction.Mosaic) {
            EditAction.Mosaic mosaic = (EditAction.Mosaic) action;
            if (!RectF.intersects(pathBounds(mosaic.getPoints()), rectF)) {
                return null;
            }
            List<EditorPoint> points2 = mosaic.getPoints();
            ArrayList arrayList2 = new ArrayList(CollectionsKt.collectionSizeOrDefault(points2, 10));
            Iterator it2 = points2.iterator();
            while (it2.hasNext()) {
                arrayList2.add(offsetByCrop((EditorPoint) it2.next(), cropRect));
            }
            return new EditAction.Mosaic(mosaic.getId(), arrayList2, mosaic.getStrokeWidth());
        }
        if (action instanceof EditAction.Shape) {
            EditAction.Shape shape = (EditAction.Shape) action;
            if (RectF.intersects(new RectF(Math.min(shape.getStart().getX(), shape.getEnd().getX()), Math.min(shape.getStart().getY(), shape.getEnd().getY()), Math.max(shape.getStart().getX(), shape.getEnd().getX()), Math.max(shape.getStart().getY(), shape.getEnd().getY())), rectF)) {
                return new EditAction.Shape(shape.getId(), shape.getType(), offsetByCrop(shape.getStart(), cropRect), offsetByCrop(shape.getEnd(), cropRect), shape.getColor(), shape.getStrokeWidth());
            }
            return null;
        }
        if (!(action instanceof EditAction.Text)) {
            throw new NoWhenBranchMatchedException();
        }
        EditAction.Text text = (EditAction.Text) action;
        if (RectF.intersects(EditorRenderUtils.INSTANCE.buildTextBounds(text, 12f), rectF)) {
            return new EditAction.Text(text.getId(), text.getText(), offsetByCrop(text.getAnchor(), cropRect), text.getColor(), text.getTextSize());
        }
        return null;
    }

    private final RectF pathBounds(List<EditorPoint> points) {
        if (points.isEmpty()) {
            return new RectF();
        }
        float x = ((EditorPoint) CollectionsKt.first((List) points)).getX();
        float y = ((EditorPoint) CollectionsKt.first((List) points)).getY();
        float x2 = ((EditorPoint) CollectionsKt.first((List) points)).getX();
        float y2 = ((EditorPoint) CollectionsKt.first((List) points)).getY();
        for (EditorPoint editorPoint : CollectionsKt.drop(points, 1)) {
            x = Math.min(x, editorPoint.getX());
            y = Math.min(y, editorPoint.getY());
            x2 = Math.max(x2, editorPoint.getX());
            y2 = Math.max(y2, editorPoint.getY());
        }
        return new RectF(x, y, x2, y2);
    }

    private final EditorPoint offsetByCrop(EditorPoint editorPoint, Rect rect) {
        return new EditorPoint(editorPoint.getX() - rect.left, editorPoint.getY() - rect.top);
    }
}

