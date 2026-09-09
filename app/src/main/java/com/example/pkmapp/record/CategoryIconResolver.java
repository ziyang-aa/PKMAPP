package com.example.pkmapp.record;

import com.example.pkmapp.data.TransactionType;

public final class CategoryIconResolver {
    private CategoryIconResolver() { }

    public static String resourceName(TransactionType type, String label) {
        if (type == TransactionType.INCOME && label != null && label.startsWith("攒钱 · ")) {
            return "savings_seedling_stage_1";
        }
        if (type == TransactionType.INCOME && "借入".equals(label)) {
            return "norm_type_expense_loan";
        }
        String slug;
        switch (label) {
            case "餐饮": slug = "dining"; break;
            case "购物": slug = "shopping"; break;
            case "网购": slug = "shopping"; break;
            case "饮料": slug = "drinks"; break;
            case "交通": slug = "transport"; break;
            case "日用": slug = "daily"; break;
            case "学习": slug = "study"; break;
            case "水果": slug = "fruit"; break;
            case "娱乐": slug = "entertainment"; break;
            case "服饰": slug = "clothing"; break;
            case "礼物": slug = "gift"; break;
            case "红包": slug = "red_packet"; break;
            case "人情": slug = "relationship"; break;
            case "医疗": slug = "medical"; break;
            case "订阅": slug = "subscription"; break;
            case "借出": slug = "loan"; break;
            case "还款": slug = "repayment"; break;
            case "工资": slug = "salary"; break;
            case "零花": slug = "allowance"; break;
            case "兼职": slug = "part_time"; break;
            case "转卖": slug = "resale"; break;
            default: slug = "custom";
        }
        String prefix = type == TransactionType.EXPENSE ? "expense" : "income";
        return "norm_type_" + prefix + "_" + slug;
    }
}
