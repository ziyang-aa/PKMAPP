package com.example.pkmapp.server.sync;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {
    private final JdbcTemplate jdbcTemplate;

    public SyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> push(String userId, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return Map.of("serverSeq", currentMaxSeq(userId), "acceptedCount", 0);
        }
        long now = System.currentTimeMillis();
        long seq = allocateServerSeq();
        int accepted = 0;

        accepted += upsertLedgers(userId, payload.path("ledgers"), seq, now);
        accepted += upsertTransactions(userId, payload.path("transactions"), seq, now);
        accepted += upsertBorrowings(userId, payload.path("borrowings"), seq, now);
        accepted += upsertSavingsGoals(userId, payload.path("savingsGoals"), seq, now);
        accepted += upsertCustomCategories(userId, payload.path("customCategories"), seq, now);
        accepted += upsertStudyPlans(userId, payload.path("studyPlans"), seq, now);
        accepted += upsertStudySubjects(userId, payload.path("studySubjects"), seq, now);
        accepted += upsertStudyRecords(userId, payload.path("studyRecords"), seq, now);
        accepted += upsertStudyTasks(userId, payload.path("studyTasks"), seq, now);
        accepted += upsertStudyReviews(userId, payload.path("studyReviews"), seq, now);
        accepted += upsertStudyGardenState(userId, payload.path("studyGardenState"), seq, now);
        accepted += upsertTodos(userId, payload.path("todos"), seq, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverSeq", seq);
        result.put("acceptedCount", accepted);
        return result;
    }

    public Map<String, Object> pull(String userId, long since) {
        long safeSince = Math.max(0L, since);
        long[] maxSeq = new long[]{safeSince};

        List<Map<String, Object>> ledgers = queryLedgers(userId, safeSince, maxSeq);
        List<Map<String, Object>> transactions = queryTransactions(userId, safeSince, maxSeq);
        List<Map<String, Object>> borrowings = queryBorrowings(userId, safeSince, maxSeq);
        List<Map<String, Object>> savingsGoals = querySavingsGoals(userId, safeSince, maxSeq);
        List<Map<String, Object>> customCategories = queryCustomCategories(userId, safeSince, maxSeq);
        List<Map<String, Object>> studyPlans = queryStudyPlans(userId, safeSince, maxSeq);
        List<Map<String, Object>> studySubjects = queryStudySubjects(userId, safeSince, maxSeq);
        List<Map<String, Object>> studyRecords = queryStudyRecords(userId, safeSince, maxSeq);
        List<Map<String, Object>> studyTasks = queryStudyTasks(userId, safeSince, maxSeq);
        List<Map<String, Object>> studyReviews = queryStudyReviews(userId, safeSince, maxSeq);
        Map<String, Object> gardenState = queryStudyGardenState(userId, safeSince, maxSeq);
        List<Map<String, Object>> todos = queryTodos(userId, safeSince, maxSeq);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nextCursor", maxSeq[0]);
        data.put("ledgers", ledgers);
        data.put("transactions", transactions);
        data.put("borrowings", borrowings);
        data.put("savingsGoals", savingsGoals);
        data.put("customCategories", customCategories);
        data.put("studyPlans", studyPlans);
        data.put("studySubjects", studySubjects);
        data.put("studyRecords", studyRecords);
        data.put("studyTasks", studyTasks);
        data.put("studyReviews", studyReviews);
        if (gardenState != null) {
            data.put("studyGardenState", gardenState);
        }
        data.put("todos", todos);
        return data;
    }

    private long allocateServerSeq() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO sys_sync_seq VALUES ()", Statement.RETURN_GENERATED_KEYS);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : System.currentTimeMillis();
    }

    private long currentMaxSeq(String userId) {
        Long val = jdbcTemplate.queryForObject(
                """
                SELECT GREATEST(
                    COALESCE((SELECT MAX(server_seq) FROM biz_ledgers WHERE user_id = ?), 0),
                    COALESCE((SELECT MAX(server_seq) FROM biz_transactions WHERE user_id = ?), 0),
                    COALESCE((SELECT MAX(server_seq) FROM biz_borrowing_records WHERE user_id = ?), 0),
                    COALESCE((SELECT MAX(server_seq) FROM biz_savings_goals WHERE user_id = ?), 0),
                    COALESCE((SELECT MAX(server_seq) FROM biz_study_records WHERE user_id = ?), 0),
                    COALESCE((SELECT MAX(server_seq) FROM biz_todo_items WHERE user_id = ?), 0)
                )
                """,
                Long.class,
                userId, userId, userId, userId, userId, userId
        );
        return val != null ? val : 0L;
    }

    private int upsertLedgers(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "l", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_ledgers (
                        user_id, id, name, initial_assets_cents, liabilities_cents, monthly_budget_cents,
                        is_default, is_current, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = IF(VALUES(updated_at) >= updated_at, VALUES(name), name),
                        initial_assets_cents = IF(VALUES(updated_at) >= updated_at, VALUES(initial_assets_cents), initial_assets_cents),
                        liabilities_cents = IF(VALUES(updated_at) >= updated_at, VALUES(liabilities_cents), liabilities_cents),
                        monthly_budget_cents = IF(VALUES(updated_at) >= updated_at, VALUES(monthly_budget_cents), monthly_budget_cents),
                        is_default = IF(VALUES(updated_at) >= updated_at, VALUES(is_default), is_default),
                        is_current = IF(VALUES(updated_at) >= updated_at, VALUES(is_current), is_current),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "name", "默认账本"),
                    longVal(n, "initialAssetsCents", 0L),
                    longVal(n, "liabilitiesCents", 0L),
                    Math.max(0L, longVal(n, "monthlyBudgetCents", 0L)),
                    boolInt(n, "isDefault", false),
                    boolInt(n, "isCurrent", false),
                    intVal(n, "sortOrder", 0),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertTransactions(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "tx", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_transactions (
                        user_id, id, ledger_id, type, category, amount_cents, occurred_at,
                        note, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        ledger_id = IF(VALUES(updated_at) >= updated_at, VALUES(ledger_id), ledger_id),
                        type = IF(VALUES(updated_at) >= updated_at, VALUES(type), type),
                        category = IF(VALUES(updated_at) >= updated_at, VALUES(category), category),
                        amount_cents = IF(VALUES(updated_at) >= updated_at, VALUES(amount_cents), amount_cents),
                        occurred_at = IF(VALUES(updated_at) >= updated_at, VALUES(occurred_at), occurred_at),
                        note = IF(VALUES(updated_at) >= updated_at, VALUES(note), note),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "ledgerId", "default"),
                    text(n, "type", "EXPENSE"),
                    text(n, "category", "餐饮"),
                    longVal(n, "amountCents", 0L),
                    longVal(n, "occurredAt", updatedAt),
                    text(n, "note", ""),
                    intVal(n, "sortOrder", 0),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertBorrowings(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "br", id, updatedAt);
            String sourceTxId = n.path("sourceTransactionId").isNull() || n.path("sourceTransactionId").isMissingNode()
                    ? null
                    : n.path("sourceTransactionId").asText("");
            if (sourceTxId != null && sourceTxId.isBlank()) sourceTxId = null;

            jdbcTemplate.update(
                    """
                    INSERT INTO biz_borrowing_records (
                        user_id, id, ledger_id, direction, counterparty, amount_cents, occurred_at,
                        note, source_transaction_id, settled, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        ledger_id = IF(VALUES(updated_at) >= updated_at, VALUES(ledger_id), ledger_id),
                        direction = IF(VALUES(updated_at) >= updated_at, VALUES(direction), direction),
                        counterparty = IF(VALUES(updated_at) >= updated_at, VALUES(counterparty), counterparty),
                        amount_cents = IF(VALUES(updated_at) >= updated_at, VALUES(amount_cents), amount_cents),
                        occurred_at = IF(VALUES(updated_at) >= updated_at, VALUES(occurred_at), occurred_at),
                        note = IF(VALUES(updated_at) >= updated_at, VALUES(note), note),
                        source_transaction_id = IF(VALUES(updated_at) >= updated_at, VALUES(source_transaction_id), source_transaction_id),
                        settled = IF(VALUES(updated_at) >= updated_at, VALUES(settled), settled),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "ledgerId", "default"),
                    text(n, "direction", "LEND"),
                    text(n, "counterparty", ""),
                    longVal(n, "amountCents", 0L),
                    longVal(n, "occurredAt", updatedAt),
                    text(n, "note", ""),
                    sourceTxId,
                    boolInt(n, "settled", false),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertSavingsGoals(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "sg", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_savings_goals (
                        user_id, id, title, target_cents, saved_cents, deadline,
                        created_at_millis, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title),
                        target_cents = IF(VALUES(updated_at) >= updated_at, VALUES(target_cents), target_cents),
                        saved_cents = IF(VALUES(updated_at) >= updated_at, VALUES(saved_cents), saved_cents),
                        deadline = IF(VALUES(updated_at) >= updated_at, VALUES(deadline), deadline),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "title", ""),
                    longVal(n, "targetCents", 0L),
                    longVal(n, "savedCents", 0L),
                    text(n, "deadline", ""),
                    longVal(n, "createdAtMillis", updatedAt),
                    intVal(n, "sortOrder", 0),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertCustomCategories(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String type = text(n, "type", "");
            String name = text(n, "name", "");
            if (type.isEmpty() || name.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "cat", type + "_" + name, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_custom_categories (
                        user_id, type, name, is_deleted_default, sort_order,
                        client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        is_deleted_default = IF(VALUES(updated_at) >= updated_at, VALUES(is_deleted_default), is_deleted_default),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, type, name,
                    boolInt(n, "isDeletedDefault", false),
                    intVal(n, "sortOrder", 0),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudyPlans(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "sp", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_study_plans (
                        user_id, id, title, kind, due, partner, previous_plan_id, created_at, archived_at,
                        selected_subject_id, session_goal, daily_goal, remind_deadlines, remind_time,
                        is_current, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title),
                        kind = IF(VALUES(updated_at) >= updated_at, VALUES(kind), kind),
                        due = IF(VALUES(updated_at) >= updated_at, VALUES(due), due),
                        partner = IF(VALUES(updated_at) >= updated_at, VALUES(partner), partner),
                        archived_at = IF(VALUES(updated_at) >= updated_at, VALUES(archived_at), archived_at),
                        selected_subject_id = IF(VALUES(updated_at) >= updated_at, VALUES(selected_subject_id), selected_subject_id),
                        session_goal = IF(VALUES(updated_at) >= updated_at, VALUES(session_goal), session_goal),
                        daily_goal = IF(VALUES(updated_at) >= updated_at, VALUES(daily_goal), daily_goal),
                        remind_deadlines = IF(VALUES(updated_at) >= updated_at, VALUES(remind_deadlines), remind_deadlines),
                        remind_time = IF(VALUES(updated_at) >= updated_at, VALUES(remind_time), remind_time),
                        is_current = IF(VALUES(updated_at) >= updated_at, VALUES(is_current), is_current),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "title", ""), text(n, "kind", "CUSTOM"),
                    text(n, "due", ""), text(n, "partner", "SQUIRREL"), text(n, "previousPlanId", ""),
                    longVal(n, "createdAt", updatedAt), longVal(n, "archivedAt", 0L),
                    text(n, "selectedSubjectId", ""), intVal(n, "sessionGoal", 45),
                    intVal(n, "dailyGoal", 180), boolInt(n, "remindDeadlines", false),
                    text(n, "remindTime", "18:00"), boolInt(n, "isCurrent", false),
                    intVal(n, "sortOrder", 0), reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudySubjects(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String planId = text(n, "planId", "");
            String id = text(n, "id", "");
            if (planId.isEmpty() || id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "ss", planId + "_" + id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_study_subjects (
                        user_id, plan_id, id, title, hint, due, assessment, color, sort_order,
                        client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title),
                        hint = IF(VALUES(updated_at) >= updated_at, VALUES(hint), hint),
                        due = IF(VALUES(updated_at) >= updated_at, VALUES(due), due),
                        assessment = IF(VALUES(updated_at) >= updated_at, VALUES(assessment), assessment),
                        color = IF(VALUES(updated_at) >= updated_at, VALUES(color), color),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, planId, id, text(n, "title", ""), text(n, "hint", ""),
                    text(n, "due", ""), text(n, "assessment", ""), intVal(n, "color", 0),
                    intVal(n, "sortOrder", 0), reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudyRecords(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            long duration = longVal(n, "durationMillis", 0L);
            String reqId = resolveReqId(n, "sr", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_study_records (
                        user_id, id, plan_id, subject_id, subject_title_snapshot, subject_hint_snapshot,
                        subject_color_snapshot, subject_due_snapshot, subject_assessment_snapshot,
                        duration_millis, completed_at, note, `manual`, reward_millis, edited_at, adjusted,
                        task_id, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        plan_id = IF(VALUES(updated_at) >= updated_at, VALUES(plan_id), plan_id),
                        subject_id = IF(VALUES(updated_at) >= updated_at, VALUES(subject_id), subject_id),
                        subject_title_snapshot = IF(VALUES(updated_at) >= updated_at, VALUES(subject_title_snapshot), subject_title_snapshot),
                        duration_millis = IF(VALUES(updated_at) >= updated_at, VALUES(duration_millis), duration_millis),
                        completed_at = IF(VALUES(updated_at) >= updated_at, VALUES(completed_at), completed_at),
                        note = IF(VALUES(updated_at) >= updated_at, VALUES(note), note),
                        `manual` = IF(VALUES(updated_at) >= updated_at, VALUES(`manual`), `manual`),
                        reward_millis = IF(VALUES(updated_at) >= updated_at, VALUES(reward_millis), reward_millis),
                        edited_at = IF(VALUES(updated_at) >= updated_at, VALUES(edited_at), edited_at),
                        adjusted = IF(VALUES(updated_at) >= updated_at, VALUES(adjusted), adjusted),
                        task_id = IF(VALUES(updated_at) >= updated_at, VALUES(task_id), task_id),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "planId", ""), text(n, "subjectId", ""),
                    text(n, "subjectTitleSnapshot", ""), text(n, "subjectHintSnapshot", ""),
                    intVal(n, "subjectColorSnapshot", 0), text(n, "subjectDueSnapshot", ""),
                    text(n, "subjectAssessmentSnapshot", ""), duration,
                    longVal(n, "completedAt", updatedAt), text(n, "note", ""),
                    boolInt(n, "manual", false), longVal(n, "rewardMillis", duration),
                    longVal(n, "editedAt", 0L), boolInt(n, "adjusted", false),
                    text(n, "taskId", ""), intVal(n, "sortOrder", 0),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudyTasks(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "st", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_study_tasks (
                        user_id, id, plan_id, subject_id, title, due, due_time, remind,
                        remind_lead_minutes, created_at, done_at, is_next, priority,
                        client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title),
                        due = IF(VALUES(updated_at) >= updated_at, VALUES(due), due),
                        due_time = IF(VALUES(updated_at) >= updated_at, VALUES(due_time), due_time),
                        remind = IF(VALUES(updated_at) >= updated_at, VALUES(remind), remind),
                        remind_lead_minutes = IF(VALUES(updated_at) >= updated_at, VALUES(remind_lead_minutes), remind_lead_minutes),
                        done_at = IF(VALUES(updated_at) >= updated_at, VALUES(done_at), done_at),
                        is_next = IF(VALUES(updated_at) >= updated_at, VALUES(is_next), is_next),
                        priority = IF(VALUES(updated_at) >= updated_at, VALUES(priority), priority),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "planId", ""), text(n, "subjectId", ""),
                    text(n, "title", ""), text(n, "due", ""), text(n, "dueTime", ""),
                    boolInt(n, "remind", false), intVal(n, "remindLeadMinutes", 30),
                    longVal(n, "createdAt", updatedAt), longVal(n, "doneAt", 0L),
                    boolInt(n, "isNext", false), longVal(n, "priority", 0L),
                    reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudyReviews(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String recordId = text(n, "recordId", "");
            if (recordId.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "srv", recordId, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_study_reviews (
                        user_id, record_id, prompt, day, completed_at,
                        client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        prompt = IF(VALUES(updated_at) >= updated_at, VALUES(prompt), prompt),
                        day = IF(VALUES(updated_at) >= updated_at, VALUES(day), day),
                        completed_at = IF(VALUES(updated_at) >= updated_at, VALUES(completed_at), completed_at),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, recordId, text(n, "prompt", ""), text(n, "day", ""),
                    longVal(n, "completedAt", updatedAt), reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private int upsertStudyGardenState(String userId, JsonNode n, long seq, long now) {
        if (!n.isObject()) return 0;
        long updatedAt = longVal(n, "updatedAt", now);
        String rewardedJson = n.has("rewardedDaysJson") && !n.path("rewardedDaysJson").asText("").isBlank()
                ? n.path("rewardedDaysJson").asText("[]")
                : "[]";
        jdbcTemplate.update(
                """
                INSERT INTO biz_study_garden_state (
                    user_id, current_plan_id, water, carry_millis, seeds, planted, growth, trees,
                    rewarded_days_json, updated_at, server_seq
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_plan_id = IF(VALUES(updated_at) >= updated_at, VALUES(current_plan_id), current_plan_id),
                    water = IF(VALUES(updated_at) >= updated_at, VALUES(water), water),
                    carry_millis = IF(VALUES(updated_at) >= updated_at, VALUES(carry_millis), carry_millis),
                    seeds = IF(VALUES(updated_at) >= updated_at, VALUES(seeds), seeds),
                    planted = IF(VALUES(updated_at) >= updated_at, VALUES(planted), planted),
                    growth = IF(VALUES(updated_at) >= updated_at, VALUES(growth), growth),
                    trees = IF(VALUES(updated_at) >= updated_at, VALUES(trees), trees),
                    rewarded_days_json = IF(VALUES(updated_at) >= updated_at, VALUES(rewarded_days_json), rewarded_days_json),
                    server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                    updated_at = GREATEST(updated_at, VALUES(updated_at))
                """,
                userId, text(n, "currentPlanId", ""), intVal(n, "water", 0),
                longVal(n, "carryMillis", 0L), intVal(n, "seeds", 1),
                boolInt(n, "planted", false), intVal(n, "growth", 0),
                intVal(n, "trees", 0), rewardedJson, updatedAt, seq
        );
        return 1;
    }

    private int upsertTodos(String userId, JsonNode arr, long seq, long now) {
        if (!arr.isArray()) return 0;
        int count = 0;
        for (JsonNode n : arr) {
            String id = text(n, "id", "");
            if (id.isEmpty()) continue;
            long updatedAt = longVal(n, "updatedAt", now);
            String reqId = resolveReqId(n, "td", id, updatedAt);
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_todo_items (
                        user_id, id, title, note, due_date, due_time, remind, remind_before_minutes,
                        created_at, completed_at, sort_order, client_request_id, updated_at, deleted_at, server_seq
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title),
                        note = IF(VALUES(updated_at) >= updated_at, VALUES(note), note),
                        due_date = IF(VALUES(updated_at) >= updated_at, VALUES(due_date), due_date),
                        due_time = IF(VALUES(updated_at) >= updated_at, VALUES(due_time), due_time),
                        remind = IF(VALUES(updated_at) >= updated_at, VALUES(remind), remind),
                        remind_before_minutes = IF(VALUES(updated_at) >= updated_at, VALUES(remind_before_minutes), remind_before_minutes),
                        completed_at = IF(VALUES(updated_at) >= updated_at, VALUES(completed_at), completed_at),
                        sort_order = IF(VALUES(updated_at) >= updated_at, VALUES(sort_order), sort_order),
                        client_request_id = IF(VALUES(updated_at) >= updated_at, VALUES(client_request_id), client_request_id),
                        deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                        server_seq = IF(VALUES(updated_at) >= updated_at, VALUES(server_seq), server_seq),
                        updated_at = GREATEST(updated_at, VALUES(updated_at))
                    """,
                    userId, id, text(n, "title", ""), text(n, "note", ""),
                    text(n, "dueDate", ""), text(n, "dueTime", ""),
                    boolInt(n, "remind", false), intVal(n, "remindBeforeMinutes", 30),
                    longVal(n, "createdAt", updatedAt), longVal(n, "completedAt", 0L),
                    intVal(n, "sortOrder", 0), reqId, updatedAt, longVal(n, "deletedAt", 0L), seq
            );
            count++;
        }
        return count;
    }

    private List<Map<String, Object>> queryLedgers(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_ledgers WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("name", rs.getString("name"));
                    m.put("initialAssetsCents", rs.getLong("initial_assets_cents"));
                    m.put("liabilitiesCents", rs.getLong("liabilities_cents"));
                    m.put("monthlyBudgetCents", rs.getLong("monthly_budget_cents"));
                    m.put("isDefault", rs.getInt("is_default") == 1);
                    m.put("isCurrent", rs.getInt("is_current") == 1);
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryTransactions(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_transactions WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("ledgerId", rs.getString("ledger_id"));
                    m.put("type", rs.getString("type"));
                    m.put("category", rs.getString("category"));
                    m.put("amountCents", rs.getLong("amount_cents"));
                    m.put("occurredAt", rs.getLong("occurred_at"));
                    m.put("note", rs.getString("note"));
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryBorrowings(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_borrowing_records WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("ledgerId", rs.getString("ledger_id"));
                    m.put("direction", rs.getString("direction"));
                    m.put("counterparty", rs.getString("counterparty"));
                    m.put("amountCents", rs.getLong("amount_cents"));
                    m.put("occurredAt", rs.getLong("occurred_at"));
                    m.put("note", rs.getString("note"));
                    m.put("sourceTransactionId", rs.getString("source_transaction_id"));
                    m.put("settled", rs.getInt("settled") == 1);
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> querySavingsGoals(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_savings_goals WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("targetCents", rs.getLong("target_cents"));
                    m.put("savedCents", rs.getLong("saved_cents"));
                    m.put("deadline", rs.getString("deadline"));
                    m.put("createdAtMillis", rs.getLong("created_at_millis"));
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryCustomCategories(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_custom_categories WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", rs.getString("type"));
                    m.put("name", rs.getString("name"));
                    m.put("isDeletedDefault", rs.getInt("is_deleted_default") == 1);
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryStudyPlans(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_plans WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("kind", rs.getString("kind"));
                    m.put("due", rs.getString("due"));
                    m.put("partner", rs.getString("partner"));
                    m.put("createdAt", rs.getLong("created_at"));
                    m.put("archivedAt", rs.getLong("archived_at"));
                    m.put("selectedSubjectId", rs.getString("selected_subject_id"));
                    m.put("sessionGoal", rs.getInt("session_goal"));
                    m.put("dailyGoal", rs.getInt("daily_goal"));
                    m.put("remindDeadlines", rs.getInt("remind_deadlines") == 1);
                    m.put("remindTime", rs.getString("remind_time"));
                    m.put("isCurrent", rs.getInt("is_current") == 1);
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryStudySubjects(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_subjects WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("planId", rs.getString("plan_id"));
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("hint", rs.getString("hint"));
                    m.put("due", rs.getString("due"));
                    m.put("assessment", rs.getString("assessment"));
                    m.put("color", rs.getInt("color"));
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryStudyRecords(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_records WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("planId", rs.getString("plan_id"));
                    m.put("subjectId", rs.getString("subject_id"));
                    m.put("subjectTitleSnapshot", rs.getString("subject_title_snapshot"));
                    m.put("durationMillis", rs.getLong("duration_millis"));
                    m.put("completedAt", rs.getLong("completed_at"));
                    m.put("note", rs.getString("note"));
                    m.put("manual", rs.getInt("manual") == 1);
                    m.put("rewardMillis", rs.getLong("reward_millis"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryStudyTasks(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_tasks WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("planId", rs.getString("plan_id"));
                    m.put("subjectId", rs.getString("subject_id"));
                    m.put("title", rs.getString("title"));
                    m.put("due", rs.getString("due"));
                    m.put("dueTime", rs.getString("due_time"));
                    m.put("remind", rs.getInt("remind") == 1);
                    m.put("remindLeadMinutes", rs.getInt("remind_lead_minutes"));
                    m.put("createdAt", rs.getLong("created_at"));
                    m.put("doneAt", rs.getLong("done_at"));
                    m.put("isNext", rs.getInt("is_next") == 1);
                    m.put("priority", rs.getLong("priority"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryStudyReviews(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_reviews WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("recordId", rs.getString("record_id"));
                    m.put("prompt", rs.getString("prompt"));
                    m.put("day", rs.getString("day"));
                    m.put("completedAt", rs.getLong("completed_at"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private Map<String, Object> queryStudyGardenState(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_study_garden_state WHERE user_id = ? AND server_seq > ?",
                rs -> {
                    if (!rs.next()) return null;
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("currentPlanId", rs.getString("current_plan_id"));
                    m.put("water", rs.getInt("water"));
                    m.put("carryMillis", rs.getLong("carry_millis"));
                    m.put("seeds", rs.getInt("seeds"));
                    m.put("planted", rs.getInt("planted") == 1);
                    m.put("growth", rs.getInt("growth"));
                    m.put("trees", rs.getInt("trees"));
                    m.put("rewardedDaysJson", rs.getString("rewarded_days_json"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private List<Map<String, Object>> queryTodos(String userId, long since, long[] maxSeq) {
        return jdbcTemplate.query(
                "SELECT * FROM biz_todo_items WHERE user_id = ? AND server_seq > ? ORDER BY server_seq ASC",
                (rs, i) -> {
                    long s = rs.getLong("server_seq");
                    if (s > maxSeq[0]) maxSeq[0] = s;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("note", rs.getString("note"));
                    m.put("dueDate", rs.getString("due_date"));
                    m.put("dueTime", rs.getString("due_time"));
                    m.put("remind", rs.getInt("remind") == 1);
                    m.put("remindBeforeMinutes", rs.getInt("remind_before_minutes"));
                    m.put("createdAt", rs.getLong("created_at"));
                    m.put("completedAt", rs.getLong("completed_at"));
                    m.put("sortOrder", rs.getInt("sort_order"));
                    m.put("clientRequestId", rs.getString("client_request_id"));
                    m.put("updatedAt", rs.getLong("updated_at"));
                    m.put("deletedAt", rs.getLong("deleted_at"));
                    m.put("serverSeq", s);
                    return m;
                },
                userId, since
        );
    }

    private String resolveReqId(JsonNode node, String prefix, String id, long updatedAt) {
        String raw = text(node, "clientRequestId", "");
        if (!raw.isBlank()) {
            return raw.length() > 64 ? raw.substring(0, 64) : raw;
        }
        String fallback = prefix + "_" + id + "_" + updatedAt;
        return fallback.length() > 64 ? fallback.substring(0, 64) : fallback;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return (child.isMissingNode() || child.isNull()) ? fallback : child.asText(fallback);
    }

    private long longVal(JsonNode node, String field, long fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? child.asLong(fallback) : fallback;
    }

    private int intVal(JsonNode node, String field, int fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? child.asInt(fallback) : fallback;
    }

    private int boolInt(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        boolean v = child.isBoolean() ? child.asBoolean(fallback) : (child.isNumber() ? child.asInt(0) != 0 : fallback);
        return v ? 1 : 0;
    }
}
