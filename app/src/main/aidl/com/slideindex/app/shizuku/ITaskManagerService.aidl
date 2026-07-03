package com.slideindex.app.shizuku;

interface ITaskManagerService {
    void destroy() = 16777114;

    boolean removeTaskById(String taskId) = 1;
    String getFrontTaskId() = 2;
    String[] getTaskIdsForPackage(String packageName) = 3;
    String[] getRecentTaskPackages() = 4;
    boolean moveTaskToFreeWindow(String taskId, int windowingMode, int left, int top, int right, int bottom) = 5;
    int getApiVersion() = 6;
    String getFrontTaskPackage() = 7;
    boolean forceStopPackage(String packageName) = 8;
    String[] getPublishedShortcuts(String packageName) = 9;
    boolean startPublishedShortcut(String packageName, String shortcutId) = 10;
    /** Each row: package<TAB>shortcutId<TAB>label */
    String[] getAllPublishedShortcuts() = 18;
    /** Each row: taskId<TAB>identifier<TAB>title<TAB>topComponent */
    String[] getRecentTasks() = 11;
    boolean switchToTask(String taskId, String identifier, String topComponent) = 12;
    boolean showVoiceAssistant() = 13;
    boolean runShellCommand(in String[] cmd) = 14;
    /** Returns "exitCode\\n---\\noutput" */
    String runShellCommandOutput(in String[] cmd) = 15;
    /** Returns "exitCode\\n---\\noutput" */
    String runShellCommandLine(String command, boolean useRoot, boolean forceAdb) = 16;
    /** Whether root execution is available from the current Shizuku service. */
    boolean probeRootAvailable() = 17;
}
