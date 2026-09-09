package com.example.pkmapp.savings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class SavingsGoalStore {
    private static final String PREFERENCES = "savings_goals";
    private static final String GOALS_KEY = "goals";

    private final SharedPreferences preferences;

    public SavingsGoalStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        if (!preferences.contains(GOALS_KEY)) {
            save(Collections.emptyList());
        } else {
            removeLegacyDemoGoals();
        }
    }

    public synchronized List<SavingsGoal> getGoals() {
        String encoded = preferences.getString(GOALS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(encoded);
            List<SavingsGoal> goals = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                goals.add(new SavingsGoal(
                        item.getString("id"),
                        item.getString("name"),
                        item.getLong("targetCents"),
                        item.getLong("savedCents"),
                        item.getLong("createdAtMillis")));
            }
            return Collections.unmodifiableList(goals);
        } catch (JSONException | IllegalArgumentException exception) {
            return Collections.emptyList();
        }
    }

    public synchronized SavingsGoal createGoal(String name, long targetCents) {
        SavingsGoal goal = new SavingsGoal(UUID.randomUUID().toString(), name, targetCents, 0L,
                System.currentTimeMillis());
        List<SavingsGoal> goals = new ArrayList<>(getGoals());
        goals.add(goal);
        save(goals);
        return goal;
    }

    public synchronized SavingsGoal deposit(String goalId, long amountCents) {
        List<SavingsGoal> goals = new ArrayList<>(getGoals());
        for (int index = 0; index < goals.size(); index++) {
            SavingsGoal goal = goals.get(index);
            if (goal.getId().equals(goalId)) {
                SavingsGoal updated = goal.deposit(amountCents);
                goals.set(index, updated);
                save(goals);
                return updated;
            }
        }
        throw new IllegalArgumentException("攒钱目标不存在");
    }

    public synchronized SavingsGoal updateGoal(String goalId, String name, long targetCents) {
        List<SavingsGoal> goals = new ArrayList<>(getGoals());
        for (int index = 0; index < goals.size(); index++) {
            SavingsGoal goal = goals.get(index);
            if (goal.getId().equals(goalId)) {
                SavingsGoal updated = goal.updateDetails(name, targetCents);
                goals.set(index, updated);
                save(goals);
                return updated;
            }
        }
        throw new IllegalArgumentException("攒钱目标不存在");
    }

    public synchronized boolean deleteGoal(String goalId) {
        List<SavingsGoal> goals = new ArrayList<>(getGoals());
        boolean removed = goals.removeIf(goal -> goal.getId().equals(goalId));
        if (removed) {
            save(goals);
        }
        return removed;
    }

    public synchronized void clear() {
        save(Collections.emptyList());
    }

    private void save(List<SavingsGoal> goals) {
        JSONArray array = new JSONArray();
        for (SavingsGoal goal : goals) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", goal.getId());
                item.put("name", goal.getName());
                item.put("targetCents", goal.getTargetCents());
                item.put("savedCents", goal.getSavedCents());
                item.put("createdAtMillis", goal.getCreatedAtMillis());
            } catch (JSONException exception) {
                throw new IllegalStateException("保存攒钱目标失败", exception);
            }
            array.put(item);
        }
        preferences.edit().putString(GOALS_KEY, array.toString()).apply();
    }

    private void removeLegacyDemoGoals() {
        List<SavingsGoal> goals = new ArrayList<>(getGoals());
        boolean removed = goals.removeIf(goal -> goal.getId().equals("default-autumn-trip")
                || goal.getId().equals("default-forest-home"));
        if (removed) {
            save(goals);
        }
    }
}
