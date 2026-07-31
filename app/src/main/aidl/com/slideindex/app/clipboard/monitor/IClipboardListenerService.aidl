package com.slideindex.app.clipboard.monitor;

import com.slideindex.app.clipboard.monitor.IOnClipboardChanged;

interface IClipboardListenerService {
    void destroy() = 16777114;
    void exit() = 1;
    void startListening(IOnClipboardChanged callback, boolean useRoot, String filePath, boolean useHiddenApi) = 2;
    void stopListening() = 3;
}
