package com.slideindex.app.xposed.bridge;

/**
 * system_server 模块 → app 的单向事件桥。
 *
 * 模块在输入层吞掉接管区域内的触摸流后，逐条把事件回传给 app，
 * 由 app 现有手势引擎（面板跟手、Pie、连续调节、子手势）继续处理。
 */
interface IModuleGestureBridge {
    /**
     * 同步询问 app 当前是否真的能处理该边的接管触摸（宿主未就绪、边被隐藏时返回 false）。
     * 模块据此在吞掉 DOWN 之前决定放行，避免事件被吞掉却无人处理。
     */
    boolean canAcceptTouch(int sideId);

    oneway void onTouchEvent(
        long sessionId,
        int sideId,
        int action,
        float x,
        float y,
        long eventTime,
        long downTime,
        int metaState
    );

    oneway void onSessionEnd(long sessionId, int reason);
}