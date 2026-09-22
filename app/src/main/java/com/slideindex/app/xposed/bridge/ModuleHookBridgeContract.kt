package com.slideindex.app.xposed.bridge

/**
 * app 进程与 system_server 内 LSPosed 模块之间的桥接契约。
 *
 * 机制对齐 EdgeX：配置以 JSON 快照文件 + 变更广播下发，模块侧带 TTL 缓存读取，
 * 读不到时反向请求快照；模块侧同时把最近一次配置持久化到 `/data/system` 供重启后恢复。
 */
object ModuleHookBridgeContract {
  const val MODULE_PACKAGE = "com.slideindex.app"

  const val ACTION_CONFIG_CHANGED = "com.slideindex.app.xposed.action.CONFIG_CHANGED"
  const val ACTION_CONFIG_SNAPSHOT_REQUEST = "com.slideindex.app.xposed.action.CONFIG_SNAPSHOT_REQUEST"
  const val ACTION_MODULE_STATUS_REQUEST = "com.slideindex.app.xposed.action.MODULE_STATUS_REQUEST"
  const val ACTION_MODULE_STATUS_RESPONSE = "com.slideindex.app.xposed.action.MODULE_STATUS_RESPONSE"

  const val EXTRA_CONFIG_JSON = "config_json"
  const val EXTRA_STATUS_ACTIVE = "status_active"
  const val EXTRA_STATUS_DETAIL = "status_detail"

  /** 模块侧绑定的 app 服务；服务声明为 exported 并在入口校验调用方 uid。 */
  const val BRIDGE_SERVICE_ACTION = "com.slideindex.app.xposed.bridge.ModuleGestureBridgeService"

  const val SNAPSHOT_FILE_NAME = "hook_config.json"

  /** 模块侧持久化目录（system_server 自身可读写）。 */
  const val SYSTEM_SNAPSHOT_DIR = "/data/system/slideindex"

  /** 系统侧读取 app 快照的兜底路径（`/data/user_de/0/<pkg>/files/`）。 */
  const val APP_SNAPSHOT_PATH = "/data/user_de/0/$MODULE_PACKAGE/files/$SNAPSHOT_FILE_NAME"

  const val SNAPSHOT_VERSION = 1

  /** 接管分组位掩码：顶部。 */
  const val GROUP_TOP = 1 shl 0

  /** 接管分组位掩码：左右两侧。 */
  const val GROUP_SIDES = 1 shl 1

  /** 接管分组位掩码：底部。 */
  const val GROUP_BOTTOM = 1 shl 2

  const val SIDE_LEFT = 0
  const val SIDE_RIGHT = 1
  const val SIDE_BOTTOM = 2
  const val SIDE_TOP = 3

  /** 全面屏手势导航（`Settings.Secure.navigation_mode == 2`）。 */
  const val NAVIGATION_MODE_GESTURAL = 2

  /** 模块侧配置缓存 TTL。 */
  const val SNAPSHOT_TTL_MS = 2_000L

  /** 模块侧反向请求快照的最小间隔。 */
  const val SNAPSHOT_REQUEST_THROTTLE_MS = 30_000L
}