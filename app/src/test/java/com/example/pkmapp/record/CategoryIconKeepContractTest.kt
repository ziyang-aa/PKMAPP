package com.example.pkmapp.record

import com.example.pkmapp.data.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoryIconKeepContractTest {

    @Test
    fun allBuiltInDynamicIconsExistAndAreProtectedByKeepXml() {
        val projectRoot = locateProjectRoot()
        val drawableDir = File(projectRoot, "app/src/main/res/drawable-nodpi")
        val keepXml = File(projectRoot, "app/src/main/res/raw/keep.xml")
        assertTrue("Expected keep.xml at ${keepXml.absolutePath}", keepXml.isFile)
        val keepContent = keepXml.readText()
        assertTrue(keepContent.contains("@drawable/norm_type_*"))
        assertTrue(keepContent.contains("@drawable/savings_seedling_*"))

        val expenseLabels = listOf(
            "餐饮", "美团", "淘宝闪购", "购物", "实体购物", "网购", "零食", "饮料",
            "交通", "日用", "房租水电", "美妆理发", "学习", "水果", "娱乐", "服饰",
            "运动健身", "礼物", "红包", "人情", "医疗", "订阅", "借出", "还款", "自定义分类"
        )
        val incomeLabels = listOf(
            "工资", "奖金", "零花", "生活费", "兼职", "收红包",
            "退款报销", "理财利息", "转卖", "收回欠款", "借入", "攒钱 · 旅行"
        )

        val allResolved = expenseLabels.map { CategoryIconResolver.resourceName(TransactionType.EXPENSE, it) } +
            incomeLabels.map { CategoryIconResolver.resourceName(TransactionType.INCOME, it) }

        for (resName in allResolved) {
            val found = listOf("png", "webp", "xml").any { ext ->
                File(drawableDir, "$resName.$ext").isFile
            }
            assertTrue("Missing dynamic icon resource '$resName' in ${drawableDir.absolutePath}", found)
        }
    }

    private fun locateProjectRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/raw/keep.xml").isFile) return dir
            dir = dir.parentFile
        }
        return File(".").canonicalFile
    }
}
