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
            case "美团": slug = "meituan"; break;
            case "淘宝闪购": slug = "taobao_flash"; break;
            case "购物":
            case "实体购物": slug = "shopping"; break;
            case "网购": slug = "online_shopping"; break;
            case "零食": slug = "snacks"; break;
            case "饮料": slug = "drinks"; break;
            case "交通": slug = "transport"; break;
            case "日用": slug = "daily"; break;
            case "房租水电": slug = "housing"; break;
            case "美妆理发": slug = "beauty"; break;
            case "学习": slug = "study"; break;
            case "水果": slug = "fruit"; break;
            case "娱乐": slug = "entertainment"; break;
            case "服饰": slug = "clothing"; break;
            case "运动健身": slug = "fitness"; break;
            case "礼物": slug = "gift"; break;
            case "红包": slug = "red_packet"; break;
            case "人情": slug = "relationship"; break;
            case "医疗": slug = "medical"; break;
            case "订阅": slug = "subscription"; break;
            case "借出": slug = "loan"; break;
            case "还款": slug = "repayment"; break;
            case "工资": slug = "salary"; break;
            case "奖金": slug = "bonus"; break;
            case "零花":
            case "生活费": slug = "allowance"; break;
            case "兼职": slug = "part_time"; break;
            case "收红包": slug = "red_packet"; break;
            case "退款报销": slug = "refund"; break;
            case "理财利息": slug = "invest"; break;
            case "转卖": slug = "resale"; break;
            case "收回欠款": slug = "repaid"; break;
            default: slug = "custom";
        }
        String prefix = type == TransactionType.EXPENSE ? "expense" : "income";
        return "norm_type_" + prefix + "_" + slug;
    }
}
