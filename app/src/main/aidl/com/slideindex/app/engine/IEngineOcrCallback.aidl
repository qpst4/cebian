package com.slideindex.app.engine;

/** OCR 推理结果回传（在发起进程的主线程回调）。 */
interface IEngineOcrCallback {
    /** success=false 时 text 为失败原因。 */
    void onTextResult(boolean success, String textOrReason);

    /** 行框结果为 JSON（OcrLineDto 数组）；空串表示"已处理但没有行"。 */
    void onLinesResult(String linesJson);
}
