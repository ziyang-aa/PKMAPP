package com.example.pkmapp.record;

import android.content.Context;

import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.CategoryDao;
import com.example.pkmapp.core.database.CustomCategoryEntity;
import com.example.pkmapp.data.TransactionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Room-backed category store for the record and profile management pages. */
public final class CategoryStore {
    private final CategoryDao categoryDao;

    public CategoryStore(Context context) {
        categoryDao = AppDatabase.getInstance(context).categoryDao();
    }

    public synchronized List<String> getCategories(TransactionType type) {
        List<CustomCategoryEntity> rows = categoryDao.getByType(type.name());
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
        List<String> defaults = defaultCategories(type);
        CustomCategoryEntity existing = categoryDao.find(type.name(), normalized);
        if (defaults.contains(normalized)) {
            if (existing != null && existing.isDeletedDefault()) {
                categoryDao.deleteByTypeAndName(type.name(), normalized);
                return true;
            }
            return false;
        }
        if (existing != null) {
            return false;
        }
        int nextSort = categoryDao.maxSortOrder(type.name()) + 1;
        categoryDao.upsert(new CustomCategoryEntity(0L, type.name(), normalized, false, nextSort));
        return true;
    }

    public synchronized boolean deleteCategory(TransactionType type, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || "自定义".equals(normalized)) {
            return false;
        }
        CustomCategoryEntity existing = categoryDao.find(type.name(), normalized);
        if (existing != null && !existing.isDeletedDefault()) {
            return categoryDao.deleteByTypeAndName(type.name(), normalized) > 0;
        }
        if (!defaultCategories(type).contains(normalized)) {
            return false;
        }
        if (existing != null && existing.isDeletedDefault()) {
            return false;
        }
        int nextSort = categoryDao.maxSortOrder(type.name()) + 1;
        categoryDao.upsert(new CustomCategoryEntity(0L, type.name(), normalized, true, nextSort));
        return true;
    }

    public synchronized int getCategoryCount() {
        return getCategories(TransactionType.EXPENSE).size()
                + getCategories(TransactionType.INCOME).size();
    }

    public synchronized void clear() {
        categoryDao.clear();
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
