package com.slideindex.app.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.slideindex.app.common.repositoryRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationFilterRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val listenerPort: NotificationListenerPort,
    private val shadeActions: NotificationShadeActions,
) {
    private val appContext = context.applicationContext
    private val rulesFile = File(appContext.filesDir, RULES_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _rules = MutableStateFlow<List<NotificationFilterRule>>(emptyList())
    val rules: StateFlow<List<NotificationFilterRule>> = _rules.asStateFlow()

    init {
        scope.launch { _rules.value = readFromDisk() }
    }

    fun matches(sbn: StatusBarNotification): Boolean {
        return findMatchingHideRules(sbn).isNotEmpty()
    }

    fun matches(packageName: String, channelId: String?, title: String, text: String = ""): Boolean {
        return _rules.value.any { rule ->
            rule.userCreated &&
                rule.hidesNotification() &&
                NotificationRuleMatcher.matches(rule, packageName, channelId, title, text)
        }
    }

    fun findMatchingRules(sbn: StatusBarNotification): List<NotificationFilterRule> {
        return NotificationRuleMatcher.findMatching(_rules.value, sbn, appContext)
            .filter { it.userCreated }
    }

    fun findMatchingHideRules(sbn: StatusBarNotification): List<NotificationFilterRule> {
        return findMatchingRules(sbn).filter { it.hidesNotification() }
    }

    fun addRule(rule: NotificationFilterRule) {
        upsertRule(rule.copy(userCreated = true))
    }

    fun upsertRule(rule: NotificationFilterRule) {
        scope.launch { upsertRuleSuspend(rule) }
    }

    suspend fun upsertRuleSuspend(rule: NotificationFilterRule): Result<Unit> {
        val toSave = rule.copy(userCreated = true)
        val result = mutex.withLock {
            repositoryRunCatching {
                val current = readFromDisk()
                val next = listOf(toSave) + current.filterNot { it.id == toSave.id }
                writeToDisk(next)
                _rules.value = next
            }
        }
        if (result.isSuccess) {
            applyRuleToActive(toSave)
        }
        return result
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        scope.launch { setRuleEnabledSuspend(id, enabled) }
    }

    suspend fun setRuleEnabledSuspend(id: String, enabled: Boolean): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            val current = readFromDisk()
            val next = current.map { rule ->
                if (rule.id == id) rule.copy(enabled = enabled) else rule
            }
            writeToDisk(next)
            _rules.value = next
        }
    }

    fun hideItemFromShade(item: NotificationHistoryItem): Boolean {
        val key = item.notificationKey ?: return false
        val listener = listenerPort.listenerOrNull() ?: return false
        val sbn = runCatching {
            listener.activeNotifications?.firstOrNull { it.key == key }
        }.getOrNull()
        return if (sbn != null) {
            shadeActions.hideFromShade(listener, sbn)
        } else {
            shadeActions.hideFromShade(listener, key, sbn)
        }
    }

    fun removeRule(id: String) {
        scope.launch { removeRuleSuspend(id) }
    }

    suspend fun removeRuleSuspend(id: String): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            val next = readFromDisk().filterNot { it.id == id }
            writeToDisk(next)
            _rules.value = next
        }
    }

    fun findMatchingRule(item: NotificationHistoryItem): NotificationFilterRule? {
        return _rules.value.firstOrNull { rule ->
            rule.userCreated && rule.hidesNotification() && rule.matchesItem(item)
        }
    }

    suspend fun removeRuleForItem(item: NotificationHistoryItem): Result<Boolean> {
        val rule = findMatchingRule(item) ?: return Result.success(false)
        return removeRuleSuspend(rule.id).map { true }
    }

    /** Re-apply persisted filter rules to currently active shade notifications. */
    fun snoozeMatchingActiveNotifications(listener: NotificationListenerService) {
        shadeActions.snoozeMatchingActive(appContext, listener, ::matches)
    }

    fun exportRulesJson(): String = NotificationFilterCodec.encode(_rules.value)

    suspend fun importRulesJson(raw: String, replace: Boolean = false): Boolean {
        val imported = NotificationFilterCodec.decode(raw).filter { it.userCreated }
        if (imported.isEmpty()) return false
        mutex.withLock {
            runCatching {
                val current = if (replace) emptyList() else readFromDisk()
                val merged = imported + current.filter { existing ->
                    imported.none { it.id == existing.id }
                }
                writeToDisk(merged)
                _rules.value = merged
            }
        }
        return true
    }

    private fun applyRuleToActive(rule: NotificationFilterRule) {
        if (!rule.enabled || !rule.userCreated) return
        val listener = listenerPort.listenerOrNull() ?: return
        listener.activeNotifications?.forEach { sbn ->
            if (NotificationRuleMatcher.findMatching(listOf(rule), sbn, appContext).isNotEmpty()) {
                shadeActions.executeRules(appContext, listener, sbn, listOf(rule))
            }
        }
    }

    private suspend fun readFromDisk(): List<NotificationFilterRule> = withContext(Dispatchers.IO) {
        if (!rulesFile.exists()) return@withContext emptyList()
        runCatching {
            val all = NotificationFilterCodec.decode(rulesFile.readText())
            val userRules = all.filter { it.userCreated }
            if (userRules.size != all.size) {
                rulesFile.writeText(NotificationFilterCodec.encode(userRules))
            }
            userRules
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(rules: List<NotificationFilterRule>) = withContext(Dispatchers.IO) {
        rulesFile.writeText(NotificationFilterCodec.encode(rules))
    }

    companion object {
        private const val RULES_FILE_NAME = "notification_filter_rules.json"

        private fun NotificationFilterRule.matchesItem(item: NotificationHistoryItem): Boolean {
            return NotificationRuleMatcher.matches(
                rule = this.normalized(),
                packageName = item.packageName,
                channelId = null,
                title = item.title,
                text = item.text,
            )
        }
    }
}
