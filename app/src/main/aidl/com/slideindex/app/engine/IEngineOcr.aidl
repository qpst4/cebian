package com.slideindex.app.engine;

import android.graphics.Bitmap;
import com.slideindex.app.engine.IEngineOcrCallback;

/**
 * `:engine` 进程的 OCR 推理入口。
 *
 * 目的是让 onnxruntime / opencv / tesseract 只常驻引擎进程；调用方（取词、搜图、屏幕搜索）
 * 通过它拿结果，不再自己加载引擎。位图经 Bitmap 的 Parcelable（ashmem）跨进程传输。
 */
interface IEngineOcr {
    void recognizeBitmap(in Bitmap bitmap, String modelId, IEngineOcrCallback callback);
    void recognizeLines(in Bitmap bitmap, String modelId, IEngineOcrCallback callback);
}
