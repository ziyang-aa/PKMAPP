package com.example.pkmapp.profile

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.example.pkmapp.backup.BackupCodec
import com.example.pkmapp.backup.BackupImportResult
import com.example.pkmapp.backup.BackupManager
import com.example.pkmapp.backup.BackupSnapshot
import com.example.pkmapp.forest.ForestDialogBuilder
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupCoordinator(
    caller: ActivityResultCaller,
    private val contextProvider: () -> Context,
    private val scopeProvider: () -> LifecycleCoroutineScope,
    private val isHostAlive: () -> Boolean,
    private val onImportApplied: () -> Unit
) {
    constructor(
        fragment: Fragment,
        onImportApplied: () -> Unit
    ) : this(
        caller = fragment,
        contextProvider = { fragment.requireContext() },
        scopeProvider = { fragment.viewLifecycleOwner.lifecycleScope },
        isHostAlive = { fragment.isAdded && fragment.view != null },
        onImportApplied = onImportApplied
    )

    constructor(
        activity: AppCompatActivity,
        onImportApplied: () -> Unit
    ) : this(
        caller = activity,
        contextProvider = { activity },
        scopeProvider = { activity.lifecycleScope },
        isHostAlive = { !activity.isFinishing && !activity.isDestroyed },
        onImportApplied = onImportApplied
    )

    private val exportPicker: ActivityResultLauncher<String> =
        caller.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            handleExportDestination(uri)
        }

    private val importPicker: ActivityResultLauncher<Array<String>> =
        caller.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            handleImportSelected(uri)
        }

    fun startExport() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        exportPicker.launch("小松果备份_${today}.pkmbackup.json")
    }

    fun startImport() {
        importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun handleExportDestination(uri: Uri?) {
        if (uri == null || !isHostAlive()) return
        val appContext = contextProvider().applicationContext
        scopeProvider().launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = BackupCodec.encode(BackupManager(appContext).capture())
                    val output = appContext.contentResolver.openOutputStream(uri)
                        ?: throw IOException("无法打开备份文件")
                    output.use { stream ->
                        BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8)).use { writer ->
                            writer.write(encoded)
                        }
                    }
                }
            }
            if (!isHostAlive()) return@launch
            result.onSuccess {
                Toast.makeText(contextProvider(), "账单已导出", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    contextProvider(),
                    "导出失败：${safeMessage(error)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleImportSelected(uri: Uri?) {
        if (uri == null || !isHostAlive()) return
        val appContext = contextProvider().applicationContext
        scopeProvider().launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = readText(appContext.contentResolver, uri)
                    BackupCodec.decode(encoded)
                }
            }
            if (!isHostAlive()) return@launch
            decoded.onSuccess { snapshot ->
                showImportModeDialog(snapshot)
            }.onFailure { error ->
                Toast.makeText(
                    contextProvider(),
                    "导入失败：${safeMessage(error)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showImportModeDialog(snapshot: BackupSnapshot) {
        val context = contextProvider()
        val message = "备份包含 ${snapshot.ledgers.size} 个账本、" +
            "${snapshot.transactions.size} 笔交易、" +
            "${snapshot.savingsGoals.size} 个攒钱目标。\n\n请选择导入方式"
        ForestDialogBuilder(context)
            .setTitle("导入账单")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setNeutralButton("合并导入") { _, _ ->
                runImport(snapshot, BackupManager.ImportMode.MERGE)
            }
            .setPositiveButton("完全覆盖") { _, _ ->
                confirmOverwrite(snapshot)
            }
            .show()
    }

    private fun confirmOverwrite(snapshot: BackupSnapshot) {
        ForestDialogBuilder(contextProvider())
            .setTitle("确认完全覆盖？")
            .setMessage("当前账本、交易、攒钱目标、资产和借钱记录将被备份内容替换。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认覆盖") { _, _ ->
                runImport(snapshot, BackupManager.ImportMode.OVERWRITE)
            }
            .show()
    }

    private fun runImport(snapshot: BackupSnapshot, mode: BackupManager.ImportMode) {
        val appContext = contextProvider().applicationContext
        scopeProvider().launch {
            val imported = withContext(Dispatchers.IO + NonCancellable) {
                runCatching {
                    BackupManager(appContext).importSnapshot(snapshot, mode)
                }
            }
            if (!isHostAlive()) return@launch
            imported.onSuccess { result ->
                onImportApplied()
                Toast.makeText(
                    contextProvider(),
                    importResultMessage(result, mode),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    contextProvider(),
                    "导入失败：${safeMessage(error)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        internal fun importResultMessage(
            result: BackupImportResult,
            mode: BackupManager.ImportMode
        ): String {
            if (mode == BackupManager.ImportMode.OVERWRITE) {
                return "已恢复 ${result.importedTransactions} 笔交易和 ${result.importedSavingsGoals} 个攒钱目标"
            }
            return "合并完成：新增 ${result.importedTransactions} 笔交易，跳过重复 " +
                "${result.skippedTransactions} 笔，新增 ${result.importedSavingsGoals} 个攒钱目标"
        }

        private fun readText(resolver: ContentResolver, uri: Uri): String {
            val input = resolver.openInputStream(uri) ?: throw IOException("无法读取备份文件")
            return input.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
            }
        }

        private fun safeMessage(error: Throwable): String = error.message ?: "文件处理失败"
    }
}
