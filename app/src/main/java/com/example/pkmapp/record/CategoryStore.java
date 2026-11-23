package com.example.pkmapp.record;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pkmapp.data.TransactionType;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Persists the categories shown by the record page. */
public final class CategoryStore {
    private static final String PREFERENCES = "record_categories";
    private static final String CUSTOM_EXPENSE = "custom_expense";
    private static final String CUSTOM_INCOME = "custom_income";
    private static final String DELETED_EXPENSE = "deleted_expense";
    private static final String DELETED_INCOME = "deleted_income";

    private final SharedPreferences preferences;

    public CategoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized List<String> getCategories(TransactionType type) {
        List<String> categories = new ArrayList<>();
        List<String> deleted = readList(deletedKey(type));
        for (String category : defaultCategories(type)) {
            if (!deleted.contains(category)) {
                categories.add(category);
            }
        }
        for (String category : readList(customKey(type))) {
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }
        return Collections.unmodifiableList(categories);
    }

    public synchronized boolean addCustomCategory(TransactionType type, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || defaultCategories(type).contains(normalized)) {
            return false;
        }
        List<String> categories = new ArrayList<>(readList(customKey(type)));
        if (categories.contains(normalized)) {
            return false;
        }
        categories.add(normalized);
        writeList(customKey(type), categories);
        return true;
    }

    public synchronized boolean deleteCategory(TransactionType type, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || "自定义".equals(normalized)) {
            return false;
        }
        List<String> custom = new ArrayList<>(readList(customKey(type)));
        if (custom.remove(normalized)) {
            writeList(customKey(type), custom);
            return true;
        }
        if (!defaultCategories(type).contains(normalized)) {
            return false;
        }
        List<String> deleted = new ArrayList<>(readList(deletedKey(type)));
        if (deleted.contains(normalized)) {
            return false;
        }
        deleted.add(normalized);
        writeList(deletedKey(type), deleted);
        return true;
    }

    public synchronized int getCategoryCount() {
        return getCategories(TransactionType.EXPENSE).size()
                + getCategories(TransactionType.INCOME).size();
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

    private String customKey(TransactionType type) {
        return type == TransactionType.EXPENSE ? CUSTOM_EXPENSE : CUSTOM_INCOME;
    }

    private String deletedKey(TransactionType type) {
        return type == TransactionType.EXPENSE ? DELETED_EXPENSE : DELETED_INCOME;
    }

    private List<String> readList(String key) {
        String encoded = preferences.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(encoded);
            List<String> values = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                String value = normalize(array.getString(index));
                if (!value.isEmpty() && !values.contains(value)) {
                    values.add(value);
                }
            }
            return values;
        } catch (JSONException exception) {
            return Collections.emptyList();
        }
    }

    private void writeList(String key, List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        preferences.edit().putString(key, array.toString()).apply();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
