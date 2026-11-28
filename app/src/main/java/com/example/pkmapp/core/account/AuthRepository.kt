package com.example.pkmapp.core.account

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.LocalAccountEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.datastore.AppPreferences
import com.example.pkmapp.core.datastore.AppPreferencesSnapshot
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.security.PasswordHasher
import com.example.pkmapp.core.sync.SyncApiClient
import com.example.pkmapp.core.sync.SyncOutcome
import com.example.pkmapp.core.sync.SyncRepository
import com.example.pkmapp.core.work.SyncWorker
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.profile.AvatarStore
import com.example.pkmapp.study.StudyStore
import com.example.pkmapp.todo.TodoStore
import java.util.UUID
import org.json.JSONArray

data class GuestMergeSummary(
    val ledgerCount: Int,
    val transactionCount: Int,
    val borrowingCount: Int,
    val savingsCount: Int,
    val studyRecordCount: Int,
    val todoCount: Int,
    val checkedDateCount: Int
) {
    val hasMergeableData: Boolean
        get() = transactionCount > 0 ||
            ledgerCount > 1 ||
            borrowingCount > 0 ||
            savingsCount > 0 ||
            studyRecordCount > 0 ||
            todoCount > 0 ||
            checkedDateCount > 0
}

sealed class AuthResult {
    data class Success(
        val account: LocalAccountEntity,
        val guestMergeSummary: GuestMergeSummary?
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}

class AuthRepository @JvmOverloads constructor(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val preferences: AppPreferences = AppPreferences.getInstance(context.applicationContext),
    private val sessionStore: CryptoSessionStore = CryptoSessionStore(preferences)
) {
    private val appContext = context.applicationContext
    private val accountDao = database.localAccountDao()

    fun currentAccount(): LocalAccountEntity {
        val ownerId = sessionStore.currentSession().ownerId
        return accountDao.findByOwnerId(ownerId) ?: ensureGuestAccount()
    }

    fun listAccounts(): List<LocalAccountEntity> {
        ensureGuestAccount()
        return accountDao.getAll()
    }

    fun ensureGuestAccount(): LocalAccountEntity {
        val existing = accountDao.findByOwnerId(AccountScope.GUEST_OWNER_ID)
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val guest = LocalAccountEntity(
            ownerId = AccountScope.GUEST_OWNER_ID,
            identifier = AccountScope.GUEST_OWNER_ID,
            nickname = AppPreferencesSnapshot.DEFAULT_NICKNAME,
            checkedDatesJson = "[]",
            isGuest = true,
            createdAt = now,
            updatedAt = now
        )
        accountDao.upsert(guest)
        return guest
    }

    fun register(
        rawIdentifier: String,
        password: String,
        rawNickname: String,
        reloadRuntimeStores: Boolean = true
    ): AuthResult {
        val normalized = normalizeIdentifier(rawIdentifier)
            ?: return AuthResult.Error("请输入有效的手机号或邮箱")
        if (password.length < 8 || password.length > 64) {
            return AuthResult.Error("密码长度需在 8 到 64 位之间")
        }
        val nickname = rawNickname.trim().ifEmpty { defaultNicknameFromIdentifier(normalized) }
        if (nickname.length > 20) {
            return AuthResult.Error("昵称最多 20 个字")
        }
        val existingLocal = accountDao.findByIdentifier(normalized)
        val baseUrl = sessionStore.currentServerBaseUrl()
        if (existingLocal != null) {
            if (baseUrl.isNotEmpty() && PasswordHasher.verify(password, existingLocal.passwordHash, existingLocal.passwordSalt)) {
                return login(normalized, password, reloadRuntimeStores)
            }
            return AuthResult.Error("该手机号或邮箱已在本地注册，请直接登录")
        }

        val now = System.currentTimeMillis()
        var ownerId = "u_" + UUID.randomUUID().toString().replace("-", "").take(16)
        var accessToken = "offline_session_${UUID.randomUUID()}"
        var refreshToken = ""

        if (baseUrl.isNotEmpty()) {
            val remote = SyncApiClient.register(baseUrl, normalized, password, nickname)
            if (remote != null) {
                if (!remote.ok) {
                    return AuthResult.Error(remote.errorMessage.ifEmpty { "云端注册失败，请检查账号或网络" })
                }
                ownerId = remote.userId.ifEmpty { ownerId }
                accessToken = remote.accessToken.ifEmpty { accessToken }
                refreshToken = remote.refreshToken
            }
        }

        val hashed = PasswordHasher.hash(password)
        val entity = LocalAccountEntity(
            ownerId = ownerId,
            identifier = normalized,
            passwordHash = hashed.hashHex,
            passwordSalt = hashed.saltHex,
            nickname = nickname,
            checkedDatesJson = "[]",
            isGuest = false,
            createdAt = now,
            updatedAt = now
        )
        accountDao.upsert(entity)
        val fromGuest = sessionStore.currentSession().isGuest
        val guestSummary = if (fromGuest) inspectGuestMergeCandidate() else null

        activateAccount(
            account = entity,
            accessToken = accessToken,
            refreshToken = refreshToken,
            reloadRuntimeStores = reloadRuntimeStores,
            pullBeforeSeed = false
        )
        return AuthResult.Success(entity, guestSummary?.takeIf { it.hasMergeableData })
    }

    fun login(
        rawIdentifier: String,
        password: String,
        reloadRuntimeStores: Boolean = true
    ): AuthResult {
        val normalized = normalizeIdentifier(rawIdentifier)
            ?: return AuthResult.Error("请输入有效的手机号或邮箱")
        if (password.length < 8) {
            return AuthResult.Error("请输入至少 8 位密码")
        }

        val baseUrl = sessionStore.currentServerBaseUrl()
        val local = accountDao.findByIdentifier(normalized)
        var targetAccount: LocalAccountEntity
        var accessToken = "offline_session_${UUID.randomUUID()}"
        var refreshToken = ""

        if (baseUrl.isNotEmpty()) {
            var remote = SyncApiClient.login(baseUrl, normalized, password)
            if (remote != null && !remote.ok && local != null &&
                PasswordHasher.verify(password, local.passwordHash, local.passwordSalt)
            ) {
                // Local account was originally registered offline before a server URL was configured:
                // automatically promote it to the cloud server on login instead of deadlocking.
                val promoted = SyncApiClient.register(baseUrl, normalized, password, local.nickname)
                if (promoted != null && promoted.ok) {
                    remote = promoted
                }
            }
            if (remote != null) {
                if (!remote.ok) {
                    return AuthResult.Error(remote.errorMessage.ifEmpty { "账号或密码错误" })
                }
                val hashed = PasswordHasher.hash(password)
                val now = System.currentTimeMillis()
                val resolvedOwner = local?.ownerId ?: remote.userId.ifEmpty {
                    "u_" + UUID.randomUUID().toString().replace("-", "").take(16)
                }
                targetAccount = (local ?: LocalAccountEntity(
                    ownerId = resolvedOwner,
                    identifier = normalized,
                    createdAt = now
                )).copy(
                    passwordHash = hashed.hashHex,
                    passwordSalt = hashed.saltHex,
                    nickname = remote.nickname.ifEmpty { local?.nickname ?: defaultNicknameFromIdentifier(normalized) },
                    isGuest = false,
                    updatedAt = now
                )
                accountDao.upsert(targetAccount)
                accessToken = remote.accessToken.ifEmpty { accessToken }
                refreshToken = remote.refreshToken
            } else {
                if (local == null || !PasswordHasher.verify(password, local.passwordHash, local.passwordSalt)) {
                    return AuthResult.Error("服务器暂不可达，且本地无匹配账号或密码错误")
                }
                targetAccount = local
            }
        } else {
            if (local == null) {
                return AuthResult.Error("未找到该账号，请先注册")
            }
            if (!PasswordHasher.verify(password, local.passwordHash, local.passwordSalt)) {
                return AuthResult.Error("账号或密码不正确")
            }
            targetAccount = local
        }

        val fromGuest = sessionStore.currentSession().isGuest
        val guestSummary = if (fromGuest) inspectGuestMergeCandidate() else null
        activateAccount(
            account = targetAccount,
            accessToken = accessToken,
            refreshToken = refreshToken,
            reloadRuntimeStores = reloadRuntimeStores,
            pullBeforeSeed = true
        )
        return AuthResult.Success(targetAccount, guestSummary?.takeIf { it.hasMergeableData })
    }

    fun switchLocalAccount(ownerId: String, reloadRuntimeStores: Boolean = true): LocalAccountEntity {
        val target = accountDao.findByOwnerId(ownerId) ?: ensureGuestAccount()
        val token = if (target.isGuest) "" else "offline_session_${UUID.randomUUID()}"
        activateAccount(target, token, "", reloadRuntimeStores)
        return target
    }

    fun logoutToGuest(reloadRuntimeStores: Boolean = true): LocalAccountEntity {
        val guest = ensureGuestAccount()
        activateAccount(guest, "", "", reloadRuntimeStores)
        return guest
    }

    fun deleteCurrentAccount(reloadRuntimeStores: Boolean = true): Boolean {
        val active = currentAccount()
        if (active.isGuest) return false
        return deleteLocalAccountByOwnerId(active.ownerId, reloadRuntimeStores)
    }

    fun deleteLocalAccountByOwnerId(
        ownerId: String,
        reloadRuntimeStores: Boolean = true
    ): Boolean {
        if (ownerId == AccountScope.GUEST_OWNER_ID) return false
        val target = accountDao.findByOwnerId(ownerId) ?: return false
        if (target.isGuest) return false

        val isCurrent = currentAccount().ownerId == ownerId
        if (isCurrent) {
            val baseUrl = sessionStore.currentServerBaseUrl()
            val token = sessionStore.currentSession().accessToken
            if (baseUrl.isNotEmpty() && token.isNotEmpty() && !token.startsWith("offline_session_")) {
                SyncApiClient.deleteAccount(baseUrl, token)
            }
        }

        database.runInTransaction {
            purgeOwnerBusinessData(ownerId)
            database.feedbackDao().deleteByOwner(ownerId)
            accountDao.deleteNonGuestByOwnerId(ownerId)
        }
        AvatarStore.clear(appContext, ownerId)
        if (isCurrent) {
            logoutToGuest(reloadRuntimeStores)
        }
        return true
    }

    fun inspectGuestMergeCandidate(): GuestMergeSummary {
        val guest = AccountScope.GUEST_OWNER_ID
        val guestAccount = accountDao.findByOwnerId(guest)
        val checkedCount = parseDates(guestAccount?.checkedDatesJson).size
        return GuestMergeSummary(
            ledgerCount = database.ledgerDao().countActiveLedgers(guest),
            transactionCount = database.ledgerDao().countActiveTransactions(guest),
            borrowingCount = database.borrowingDao().getAll(guest).size,
            savingsCount = database.savingsGoalDao().count(guest),
            studyRecordCount = database.studyDao().countActiveRecords(guest),
            todoCount = database.todoDao().countActive(guest),
            checkedDateCount = checkedCount
        )
    }

    fun mergeGuestDataTo(targetOwnerId: String, reloadRuntimeStores: Boolean = true) {
        if (targetOwnerId == AccountScope.GUEST_OWNER_ID) return
        val guest = AccountScope.GUEST_OWNER_ID
        val now = System.currentTimeMillis()
        val targetAcc = accountDao.findByOwnerId(targetOwnerId) ?: return
        val guestAcc = ensureGuestAccount()

        database.runInTransaction {
            // 1. Ledgers & Transactions & Assets
            val ledgerDao = database.ledgerDao()
            val guestLedgers = ledgerDao.getOrderedLedgers(guest)
            val guestTxs = ledgerDao.getAllTransactions(guest)
            val targetLedgers = ledgerDao.getOrderedLedgers(targetOwnerId)
            val targetTxs = ledgerDao.getAllTransactions(targetOwnerId)

            if (guestTxs.isNotEmpty() || guestLedgers.size > 1 || guestLedgers.any { it.initialAssetsCents > 0L || it.liabilitiesCents > 0L }) {
                val targetHasRealData = targetTxs.isNotEmpty() || targetLedgers.size > 1 ||
                    targetLedgers.any { it.initialAssetsCents > 0L || it.liabilitiesCents > 0L }
                val baseLedgers = if (targetHasRealData) targetLedgers else emptyList()
                val existingIds = baseLedgers.map { it.id }.toHashSet()
                val mergedLedgers = ArrayList(baseLedgers)
                var sortIdx = mergedLedgers.size
                for (gl in guestLedgers) {
                    val newId = if (existingIds.contains(gl.id)) "g_${gl.id}" else gl.id
                    existingIds.add(newId)
                    mergedLedgers.add(
                        gl.copy(
                            id = newId,
                            ownerId = targetOwnerId,
                            isDefault = !targetHasRealData && gl.isDefault,
                            isCurrent = !targetHasRealData && gl.isCurrent,
                            sortOrder = sortIdx++,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    )
                }
                if (!targetHasRealData) {
                    ledgerDao.deleteAllLedgers(targetOwnerId)
                }
                if (mergedLedgers.isNotEmpty()) {
                    ledgerDao.upsertAll(mergedLedgers)
                }
                val ledgerMap = guestLedgers.zip(mergedLedgers.takeLast(guestLedgers.size)).associate { (g, m) -> g.id to m.id }
                val existingTxIds = targetTxs.map { it.id }.toHashSet()
                val migratedTxs = guestTxs.map { tx ->
                    val resolvedId = if (existingTxIds.contains(tx.id)) "g_${tx.id}" else tx.id
                    existingTxIds.add(resolvedId)
                    tx.copy(
                        id = resolvedId,
                        ledgerId = ledgerMap[tx.ledgerId] ?: tx.ledgerId,
                        ownerId = targetOwnerId,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = AccountScope.newRequestId()
                    )
                }
                if (migratedTxs.isNotEmpty()) {
                    ledgerDao.insertTransactions(migratedTxs)
                }
            }

            // 2. Borrowings & Recurring rules
            val recurringDao = database.recurringRuleDao()
            val guestRecurring = recurringDao.getAllActive(guest)
            if (guestRecurring.isNotEmpty()) {
                val targetRecurringIds = recurringDao.getAllActive(targetOwnerId).map { it.id }.toHashSet()
                val movedRecurring = guestRecurring.map { r ->
                    val newId = if (targetRecurringIds.contains(r.id)) "g_${r.id}" else r.id
                    r.copy(
                        id = newId,
                        ownerId = targetOwnerId,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = AccountScope.newRequestId()
                    )
                }
                recurringDao.upsertAll(movedRecurring)
            }

            // 2b. Borrowings
            val borrowingDao = database.borrowingDao()
            val guestBorrowings = borrowingDao.getAll(guest)
            if (guestBorrowings.isNotEmpty()) {
                val targetBorrowingIds = borrowingDao.getAll(targetOwnerId).map { it.id }.toHashSet()
                val moved = guestBorrowings.map { b ->
                    val newId = if (targetBorrowingIds.contains(b.id)) "g_${b.id}" else b.id
                    b.copy(
                        id = newId,
                        ownerId = targetOwnerId,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = AccountScope.newRequestId()
                    )
                }
                borrowingDao.insertAll(moved)
            }

            // 3. Savings goals
            val savingsDao = database.savingsGoalDao()
            val guestGoals = savingsDao.getAll(guest)
            if (guestGoals.isNotEmpty()) {
                val targetGoals = savingsDao.getAll(targetOwnerId)
                val targetGoalIds = targetGoals.map { it.id }.toHashSet()
                var nextOrder = targetGoals.size
                val moved = guestGoals.map { g ->
                    val newId = if (targetGoalIds.contains(g.id)) "g_${g.id}" else g.id
                    g.copy(
                        id = newId,
                        sortOrder = nextOrder++,
                        ownerId = targetOwnerId,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = AccountScope.newRequestId()
                    )
                }
                savingsDao.insertAll(moved)
            }

            // 4. Custom categories
            val categoryDao = database.categoryDao()
            for (cat in categoryDao.getAll(guest)) {
                if (categoryDao.find(cat.type, cat.name, targetOwnerId) == null) {
                    categoryDao.upsert(
                        cat.copy(
                            id = 0L,
                            ownerId = targetOwnerId,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    )
                }
            }

            // 5. Study data
            val studyDao = database.studyDao()
            val guestStudy = studyDao.loadSnapshot(guest)
            val targetStudy = studyDao.loadSnapshot(targetOwnerId)
            val guestHasStudy = guestStudy.records.isNotEmpty() || guestStudy.tasks.isNotEmpty() || guestStudy.plans.size > 1
            if (guestHasStudy) {
                val targetHasStudy = targetStudy.records.isNotEmpty() || targetStudy.tasks.isNotEmpty() || targetStudy.plans.size > 1
                if (!targetHasStudy) {
                    studyDao.replaceSnapshot(guestStudy, targetOwnerId)
                } else {
                    val targetPlanIds = targetStudy.plans.map { it.id }.toHashSet()
                    val planRemap = HashMap<String, String>()
                    val movedPlans = guestStudy.plans.mapIndexed { idx, p ->
                        val nextId = if (targetPlanIds.contains(p.id)) "guest_${p.id}_${now}" else p.id
                        planRemap[p.id] = nextId
                        p.copy(
                            id = nextId,
                            isCurrent = false,
                            sortOrder = targetStudy.plans.size + idx,
                            ownerId = targetOwnerId,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    }
                    studyDao.insertPlans(movedPlans)
                    val movedSubjects = guestStudy.subjects.map { s ->
                        s.copy(
                            planId = planRemap[s.planId] ?: s.planId,
                            ownerId = targetOwnerId,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    }
                    studyDao.insertSubjects(movedSubjects)
                    val movedRecords = guestStudy.records.map { r ->
                        r.copy(
                            planId = planRemap[r.planId] ?: r.planId,
                            ownerId = targetOwnerId,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    }
                    studyDao.insertRecords(movedRecords)
                    val movedTasks = guestStudy.tasks.map { t ->
                        t.copy(
                            planId = planRemap[t.planId] ?: t.planId,
                            ownerId = targetOwnerId,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    }
                    studyDao.insertTasks(movedTasks)
                    val movedReviews = guestStudy.reviews.map { rv ->
                        rv.copy(
                            ownerId = targetOwnerId,
                            syncStatus = SyncStatus.PENDING_PUSH,
                            clientRequestId = AccountScope.newRequestId()
                        )
                    }
                    studyDao.insertReviews(movedReviews)
                    val tg = targetStudy.gardenState
                    val gg = guestStudy.gardenState
                    if (tg != null && gg != null) {
                        studyDao.upsertGardenState(
                            tg.copy(
                                water = tg.water + gg.water,
                                seeds = tg.seeds + gg.seeds,
                                trees = tg.trees + gg.trees,
                                updatedAt = now,
                                syncStatus = SyncStatus.PENDING_PUSH
                            )
                        )
                    }
                }
            }

            // 6. Todos
            val todoDao = database.todoDao()
            val guestTodos = todoDao.getAll(guest)
            if (guestTodos.isNotEmpty()) {
                val targetTodos = todoDao.getAll(targetOwnerId)
                val targetTodoIds = targetTodos.map { it.id }.toHashSet()
                var order = targetTodos.size
                val movedTodos = guestTodos.map { t ->
                    val newId = if (targetTodoIds.contains(t.id)) "g_${t.id}" else t.id
                    t.copy(
                        id = newId,
                        sortOrder = order++,
                        ownerId = targetOwnerId,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = AccountScope.newRequestId()
                    )
                }
                todoDao.insertAll(movedTodos)
            }

            // 7. Feedback drafts
            database.feedbackDao().reassignOwner(guest, targetOwnerId, now)

            // 8. Checked dates union
            val mergedDates = (parseDates(targetAcc.checkedDatesJson) + parseDates(guestAcc.checkedDatesJson))
                .sorted()
            val datesJson = JSONArray().apply { mergedDates.forEach { put(it) } }.toString()
            accountDao.updateCheckedDates(targetOwnerId, datesJson, now)

            // 9. Reset guest cleanly
            purgeOwnerBusinessData(guest)
            accountDao.updateCheckedDates(guest, "[]", now)
        }

        // Copy guest avatar if target doesn't have one yet
        if (AvatarStore.load(appContext, targetOwnerId) == null) {
            AvatarStore.load(appContext, guest)?.let { guestBitmap ->
                AvatarStore.save(appContext, targetOwnerId, guestBitmap)
            }
        }
        AvatarStore.clear(appContext, guest)

        val updatedTarget = accountDao.findByOwnerId(targetOwnerId) ?: targetAcc
        syncPreferencesFromAccount(updatedTarget)
        if (reloadRuntimeStores) {
            reloadAllStores()
        }
    }

    private fun purgeOwnerBusinessData(ownerId: String) {
        database.ledgerDao().deleteAllTransactions(ownerId)
        database.ledgerDao().deleteAllLedgers(ownerId)
        database.borrowingDao().clear(ownerId)
        database.recurringRuleDao().clear(ownerId)
        database.savingsGoalDao().clear(ownerId)
        database.categoryDao().clear(ownerId)
        database.studyDao().deleteAllForOwner(ownerId)
        database.todoDao().clear(ownerId)
    }

    private fun activateAccount(
        account: LocalAccountEntity,
        accessToken: String,
        refreshToken: String,
        reloadRuntimeStores: Boolean,
        pullBeforeSeed: Boolean = false
    ) {
        if (account.isGuest) {
            sessionStore.switchToGuest()
        } else {
            sessionStore.saveSession(account.ownerId, accessToken, refreshToken)
        }
        syncPreferencesFromAccount(account)

        val hasCloudSession = !account.isGuest &&
            sessionStore.currentServerBaseUrl().isNotBlank() &&
            accessToken.isNotBlank() &&
            !accessToken.startsWith("offline_session_")

        if (hasCloudSession && pullBeforeSeed) {
            // Pull cloud data BEFORE seeding local default ledger/study notebook so a fresh
            // device reuses the user's cloud default ledger instead of creating a duplicate one.
            val outcome = runInitialCloudSync()
            if (reloadRuntimeStores) {
                reloadAllStores()
            }
            if (outcome is SyncOutcome.Failed) {
                SyncWorker.scheduleDebouncedSync(appContext)
            }
            return
        }

        if (reloadRuntimeStores) {
            reloadAllStores()
        }

        if (hasCloudSession) {
            // Newly registered cloud account: push initial seeded defaults immediately so
            // the user lands on Home in a clean synced state.
            val outcome = runInitialCloudSync()
            if (outcome is SyncOutcome.Failed) {
                SyncWorker.scheduleDebouncedSync(appContext)
            }
        }
    }

    private fun runInitialCloudSync(): SyncOutcome {
        return try {
            SyncRepository(
                context = appContext,
                database = database,
                sessionStore = sessionStore,
                reloadRuntimeStores = false
            ).syncNow()
        } catch (_: Exception) {
            SyncOutcome.Failed(0, "初次同步暂不可用，稍后将自动重试")
        }
    }

    private fun syncPreferencesFromAccount(account: LocalAccountEntity) {
        preferences.setNickname(account.nickname.ifBlank { AppPreferencesSnapshot.DEFAULT_NICKNAME })
        preferences.setCheckedDates(parseDates(account.checkedDatesJson))
    }

    private fun reloadAllStores() {
        InMemoryLedgerRepository.getInstance().reloadFromPersistence()
        StudyStore.get(appContext).reloadForCurrentAccount()
        TodoStore.get(appContext).reloadForCurrentAccount()
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val CN_PHONE_REGEX = Regex("^(\\+?86)?1[3-9]\\d{9}$")
        private val INTL_PHONE_REGEX = Regex("^\\+[1-9]\\d{6,14}$")

        fun normalizeIdentifier(raw: String): String? {
            val cleaned = raw.trim().replace(" ", "").replace("-", "")
            if (cleaned.isEmpty()) return null
            if (EMAIL_REGEX.matches(cleaned)) {
                return cleaned.lowercase()
            }
            if (CN_PHONE_REGEX.matches(cleaned)) {
                return cleaned.removePrefix("+86").removePrefix("86")
            }
            if (INTL_PHONE_REGEX.matches(cleaned)) {
                return cleaned
            }
            return null
        }

        private fun defaultNicknameFromIdentifier(identifier: String): String {
            return if (identifier.contains("@")) {
                identifier.substringBefore("@").take(12).ifEmpty { "森林旅人" }
            } else if (identifier.length >= 4) {
                "松果友" + identifier.takeLast(4)
            } else {
                "森林守护员"
            }
        }

        private fun parseDates(json: String?): Set<String> {
            if (json.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(json)
                val out = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) out.add(v)
                }
                out
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
}
