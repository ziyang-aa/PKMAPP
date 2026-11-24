package com.example.pkmapp.savings;

import android.content.Context;

import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.SavingsGoalDao;
import com.example.pkmapp.core.database.SavingsGoalEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class SavingsGoalStore {
    private final SavingsGoalDao savingsGoalDao;

    public SavingsGoalStore(Context context) {
        savingsGoalDao = AppDatabase.getInstance(context).savingsGoalDao();
    }

    public synchronized List<SavingsGoal> getGoals() {
        List<SavingsGoal> goals = new ArrayList<>();
        for (SavingsGoalEntity row : savingsGoalDao.getAll()) {
            try {
                goals.add(new SavingsGoal(
                        row.getId(),
                        row.getTitle(),
                        row.getTargetCents(),
                        row.getSavedCents(),
                        row.getCreatedAtMillis()
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Collections.unmodifiableList(goals);
    }

    public synchronized void replaceGoals(List<SavingsGoal> goals) {
        if (goals == null) {
            throw new IllegalArgumentException("攒钱目标不能为空");
        }
        save(goals);
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
        savingsGoalDao.clear();
    }

    private void save(List<SavingsGoal> goals) {
        List<SavingsGoalEntity> entities = new ArrayList<>(goals.size());
        for (int index = 0; index < goals.size(); index++) {
            SavingsGoal goal = goals.get(index);
            entities.add(new SavingsGoalEntity(
                    goal.getId(),
                    goal.getName(),
                    goal.getTargetCents(),
                    goal.getSavedCents(),
                    "",
                    goal.getCreatedAtMillis(),
                    index
            ));
        }
        savingsGoalDao.replaceAll(entities);
    }
}
