package com.slideindex.app.diagnostic;

import com.slideindex.app.diagnostic.IOnDiagnosticLogLine;

interface IDiagnosticLogService {
    void destroy() = 16777114;
    void exit() = 1;
    void startListening(IOnDiagnosticLogLine callback, int uid) = 2;
    void stopListening() = 3;
}
