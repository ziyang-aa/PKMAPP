package com.example.pkmapp.record;

import android.content.Context;

import com.example.pkmapp.core.database.AccountScope;
import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.CategoryDao;
import com.example.pkmapp.core.database.CustomCategoryEntity;
import com.example.pkmapp.core.database.SyncStatus;
import com.example.pkmapp.core.work.SyncWorker;
import com.example.pkmapp.data.TransactionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Room-backed category store scoped by active ownerId. */
public final class CategoryStore {
    private final Context appContext;
    private final CategoryDao categoryDao;

    public CategoryStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.categoryDao = AppDatabase.getInstance(appContext).categoryDao();
    }

    private String ownerId() {
        return AccountScope.currentOwnerId(appContext);
    }

    public synchronized List<String> getCategories(TransactionType type) {
        List<CustomCategoryEntity> rows = categoryDao.getByType(type.name(), ownerId());
        Set<String> deletedDefaults = new HashSet<>();
        List<String> customAdded = new ArrayList<>();
        for (CustomCategoryEntity row : rows) {
            if (row.isDeletedDefault()) {
                deletedDefaults.add(row.getName());
            } else {
                customAdded.add(row.getName());
            }
        }
        List<String> categories = new ArrayList<>();
        for (String category : defaultCategories(type)) {
            if (!deletedDefaults.contains(category)) {
                categories.add(category);
            }
        }
        for (String category : customAdded) {
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }
        return Collections.unmodifiableList(categories);
    }

    public synchronized boolean addCustomCategory(TransactionType type, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || "自定义".equals(normalized)) {
            return false;
        }
        String owner = ownerId();
        List<String> defaults = defaultCategories(type);
        CustomCategoryEntity existing = categoryDao.find(type.name(), normalized, owner);
        if (defaults.contains(normalized)) {
            if (existing != null && existing.isDeletedDefault()) {
                categoryDao.deleteByTypeAndName(type.name(), normalized, owner);
                SyncWorker.scheduleDebouncedSync(appContext);
                return true;
            }
            return false;
        }
        if (existing != null) {
            return false;
        }
        int nextSort = categoryDao.maxSortOrder(type.name(), owner) + 1;
        categoryDao.upsert(new CustomCategoryEntity(
                0L,
                type.name(),
                normalized,
                false,
                nextSort,
                owner,
                System.currentTimeMillis(),
                0L,
                SyncStatus.PENDING_PUSH,
                AccountScope.newRequestId()
        ));
        SyncWorker.scheduleDebouncedSync(appContext);
        return true;
    }

    public synchronized boolean deleteCategory(TransactionType type, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || "自定义".equals(normalized)) {
            return false;
        }
        String owner = ownerId();
        CustomCategoryEntity existing = categoryDao.find(type.name(), normalized, owner);
        if (existing != null && !existing.isDeletedDefault()) {
            boolean deleted = categoryDao.deleteByTypeAndName(type.name(), normalized, owner) > 0;
            if (deleted) {
                SyncWorker.scheduleDebouncedSync(appContext);
            }
            return deleted;
        }
        if (!defaultCategories(type).contains(normalized)) {
            return false;
        }
        if (existing != null && existing.isDeletedDefault()) {
            return false;
        }
        int nextSort = categoryDao.maxSortOrder(type.name(), owner) + 1;
        categoryDao.upsert(new CustomCategoryEntity(
                0L,
                type.name(),
                normalized,
                true,
                nextSort,
                owner,
                System.currentTimeMillis(),
                0L,
                SyncStatus.PENDING_PUSH,
                AccountScope.newRequestId()
        ));
        SyncWorker.scheduleDebouncedSync(appContext);
        return true;
    }

    public synchronized int getCategoryCount() {
        return getCategories(TransactionType.EXPENSE).size()
                + getCategories(TransactionType.INCOME).size();
    }

    public synchronized void clear() {
        categoryDao.clear(ownerId());
    }

    public static List<String> defaultCategories(TransactionType type) {
        if (type == TransactionType.EXPENSE) {
            return Arrays.asList("餐饮", "美团", "淘宝闪购", "饮料", "实体购物", "网购", "零食",
                    "水果", "交通", "日用", "房租水电", "美妆理发", "学习", "娱乐", "服饰",
                    "运动健身", "礼物", "红包", "人情", "医疗", "订阅", "借出", "还款");
        }
        return Arrays.asList("工资", "奖金", "兼职", "生活费", "收红包", "退款报销", "理财利息",
                "转卖", "收回欠款", "借入");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
