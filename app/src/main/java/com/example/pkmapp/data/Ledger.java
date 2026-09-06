package com.example.pkmapp.data;

import java.util.Objects;

public final class Ledger {
    private final String id;
    private final String name;

    public Ledger(String id, String name) {
        this.id = requireText(id, "账本编号不能为空");
        this.name = requireText(name, "账本名称不能为空");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    private static String requireText(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
