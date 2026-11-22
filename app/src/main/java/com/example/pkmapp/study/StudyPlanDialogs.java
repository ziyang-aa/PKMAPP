package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;
import static com.example.pkmapp.study.StudySheet.MOSS;
import static com.example.pkmapp.study.StudySheet.OAT;
import static com.example.pkmapp.study.StudySheet.SAGE;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestMotion;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

final class StudyPlanDialogs {
    private final Context context;
    private final StudyStore store;
    private final StudySheet sheet;
    private final Runnable changed;
    private final Consumer<String> history;
    private final Consumer<StudySheet> backup;

    StudyPlanDialogs(Context context, StudyStore store, StudySheet sheet, Runnable changed,
                     Consumer<String> history, Consumer<StudySheet> backup) {
        this.context = context;
        this.store = store;
        this.sheet = sheet;
        this.changed = changed;
        this.history = history;
        this.backup = backup;
    }

    void manager() {
        StudyNotebook notebook = store.notebook();
        StudyPlan current = notebook.currentPlan();
        StudySheet.Pane pane = pane("学习计划", null, R.drawable.ic_study_book);
        int archived = 0;
        if (current != null && current.archived == 0) {
            String detail = notebook.currentHasSession() ? "有一段专注正在进行"
                : "上次科目 · " + current.book(current.selected).title;
            add(pane.body, StudySheet.item(context, current.title, detail, glyph(current.kind), MOSS,
                "管理 ›", false, () -> currentActions(current)), 0);
        }
        boolean hasOther = false;
        for (StudyPlan plan : notebook.plans()) {
            if (plan.archived != 0) {
                archived++;
                continue;
            }
            if (plan == current) continue;
            if (!hasOther) add(pane.body, StudySheet.section(context, "进行中的其他计划"), 17);
            hasOther = true;
            String detail = plan.id.equals(notebook.activePlanId()) ? "有一段专注正在进行"
                : "上次科目 · " + plan.book(plan.selected).title;
            item(pane, plan.title, detail, glyph(plan.kind), Color.TRANSPARENT, () ->
                save(() -> store.update(n -> { n.switchPlan(plan.id); return null; })));
        }
        if ((current == null || current.archived != 0) && !hasOther)
            add(pane.body, text(context, "当前没有进行中的计划。", 14, MUTED), 4);
        add(pane.body, StudySheet.section(context, "存档与备份"), 20);
        item(pane, "已完成", archived == 0 ? null : archived + " 个已归档计划",
            R.drawable.ic_study_trail, Color.TRANSPARENT, this::archived);
        item(pane, "学习备份", null, R.drawable.ic_leaf, Color.TRANSPARENT, () -> backup.accept(sheet));
        pane.primary("新建计划", this::newPlan);
        sheet.push(pane);
    }

    private void currentActions(StudyPlan plan) {
        StudySheet.Pane pane = pane(plan.title, "当前计划 · " + plan.kind.title, glyph(plan.kind));
        item(pane, "编辑当前计划", "调整书架、结束日和学习伙伴", R.drawable.ic_study_book, OAT, () -> {
            if (store.notebook().currentHasSession()) showError("请先完成这次学习，再编辑这个计划");
            else new Editor(plan.kind, plan).show();
        });
        item(pane, "完成当前计划", "归档记录或开启下一轮", R.drawable.ic_study_sprout, MOSS, () -> {
            if (store.notebook().currentHasSession()) showError("请先保存这段学习，再完成计划");
            else complete(plan);
        });
        deleteLink(pane, plan);
        sheet.push(pane);
    }

    private void archived() {
        StudySheet.Pane pane = pane("已完成的计划", null, R.drawable.ic_study_trail);
        for (StudyPlan plan : store.notebook().plans()) {
            if (plan.archived != 0) item(pane, plan.title, "归档于 " + StudyNotebook.dayKey(plan.archived),
                glyph(plan.kind), Color.TRANSPARENT, () -> archivedActions(plan));
        }
        if (pane.body.getChildCount() == 0)
            add(pane.body, text(context, "这里会保存每个已完成的计划。", 14, MUTED), 4);
        sheet.push(pane);
    }

    private void archivedActions(StudyPlan plan) {
        StudySheet.Pane pane = pane(plan.title, "归档于 " + StudyNotebook.dayKey(plan.archived),
            R.drawable.ic_study_trail);
        item(pane, "学习备份", null, R.drawable.ic_leaf, Color.TRANSPARENT, () -> backup.accept(sheet));
        deleteLink(pane, plan);
        pane.primary("查看学习足迹", () -> { sheet.dismiss(); history.accept(plan.id); });
        pane.secondary("恢复这个计划", () -> restore(plan));
        sheet.push(pane);
    }

    void restore(StudyPlan plan) {
        save(() -> store.update(n -> { n.restore(plan.id); return null; }));
    }

    private void deleteLink(StudySheet.Pane pane, StudyPlan plan) {
        add(pane.body, StudySheet.link(context, "删除计划", 0xff8d4836, () -> {
            if (plan.id.equals(store.notebook().activePlanId())) {
                showError("请先结束这段计时，再删除计划");
                return;
            }
            confirmDelete(plan);
        }), 12);
    }

    private void confirmDelete(StudyPlan plan) {
        int count = store.notebook().forPlan(plan.id).size();
        StudySheet.Pane pane = pane("删除「" + plan.title + "」？", null, R.drawable.ic_study_book);
        TextView message = text(context, "将从本机删除这个计划及其 "
            + count + " 条学习记录（含手记）。此操作无法撤销。", 14, MUTED);
        message.setLineSpacing(dp(context, 5), 1);
        add(pane.body, message, 3);
        pane.primary("保留计划", sheet::pop);
        pane.secondary("确认删除", () -> save(() -> store.update(n -> { n.deletePlan(plan.id); return null; })));
        sheet.push(pane);
    }

    void newPlan() {
        StudySheet.Pane pane = pane("新建学习计划", null, R.drawable.ic_study_sprout);
        item(pane, "考研", "按院校和专业找初试科目", glyph(StudyPlan.Kind.GRADUATE), MOSS,
            () -> new Editor(StudyPlan.Kind.GRADUATE, null).show());
        item(pane, "期末", "按实际选课和考试安排组书架", glyph(StudyPlan.Kind.FINALS), OAT,
            () -> new Editor(StudyPlan.Kind.FINALS, null).show());
        item(pane, "自定义", "从空书架开始，读书、考证或学技能", glyph(StudyPlan.Kind.CUSTOM), SAGE,
            () -> new Editor(StudyPlan.Kind.CUSTOM, null).show());
        sheet.push(pane);
    }

    private void complete(StudyPlan plan) {
        List<StudyNotebook.Record> records = store.notebook().forPlan(plan.id);
        long total = 0;
        for (StudyNotebook.Record record : records) total += record.durationMillis;
        String latest = records.isEmpty() ? "暂无学习记录" : StudyNotebook.dayKey(records.get(0).completedAt);
        StudySheet.Pane pane = pane("完成「" + plan.title + "」", null, R.drawable.ic_study_sprout);
        LinearLayout report = column(context);
        report.setPadding(dp(context, 17), dp(context, 15), dp(context, 17), dp(context, 15));
        report.setBackground(rounded(context, MOSS, 0, 18));
        add(report, text(context, "累计专注", 12, DEEP), 0);
        add(report, heading(context, StudyFormat.duration(total), 30), 5);
        add(report, text(context, records.size() + " 次学习 · 最后学习日 " + latest, 12, MUTED), 9);
        add(pane.body, report, 0);
        pane.primary("完成并归档", () -> save(() -> store.update(n -> {
            n.archive(plan.id, System.currentTimeMillis());
            return null;
        })));
        pane.secondary("完成并开启下一轮", () -> nextRound(plan));
        sheet.push(pane);
    }

    private void nextRound(StudyPlan plan) {
        StudySheet.Pane pane = pane("开启下一轮", "沿用书架、伙伴和目标，新一轮从零开始记录。", R.drawable.ic_study_sprout);
        String suggested = plan.title.replaceFirst("一轮$", "二轮");
        if (suggested.equals(plan.title)) suggested = plan.title.length() <= 34 ? plan.title + " 下一轮" : "下一轮";
        EditText name = field(pane.body, "下一轮计划名称", "", suggested, 40);
        MaterialCheckBox dates = StudyWorkDialogs.check(context, "沿用科目考试或提交日期");
        boolean dated = false;
        for (StudySubject subject : plan.books()) if (!subject.due.isEmpty()) dated = true;
        if (dated) add(pane.body, dates, 8);
        Map<String, MaterialCheckBox> carried = new LinkedHashMap<>();
        List<StudyWork.Task> pending = store.notebook().pendingTasks(plan.id);
        if (!pending.isEmpty()) {
            add(pane.body, StudySheet.section(context, "带到下一轮的待做事项（选填）"), 10);
            for (StudyWork.Task task : pending) {
                MaterialCheckBox check = StudyWorkDialogs.check(context,
                    plan.book(task.subjectId).title + " · " + task.title);
                add(pane.body, check, 6);
                carried.put(task.id, check);
            }
        }
        pane.primary("归档并新建", () -> {
            if (value(name).isEmpty()) name.setError("请填写下一轮计划名称");
            else {
                Set<String> selected = new HashSet<>();
                for (Map.Entry<String, MaterialCheckBox> entry : carried.entrySet()) {
                    if (entry.getValue().isChecked()) selected.add(entry.getKey());
                }
                save(() -> store.update(n -> n.nextRound(plan.id, value(name), System.currentTimeMillis(),
                    dates.isChecked(), selected)));
            }
        });
        sheet.push(pane);
    }

    private void save(Runnable action) {
        try {
            action.run();
            if (sheet.isShowing()) sheet.dismiss();
            changed.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private StudySheet.Pane pane(String title, String detail, int glyph) {
        return new StudySheet.Pane(context, title, detail, glyph);
    }

    private void item(StudySheet.Pane pane, String title, String detail, int icon, int tint, Runnable click) {
        add(pane.body, StudySheet.item(context, title, detail, icon, tint, "›", false, click), 6);
    }

    private static int glyph(StudyPlan.Kind kind) {
        switch (kind) {
            case FINALS: return R.drawable.ic_study_trail;
            case CUSTOM: return R.drawable.ic_study_sprout;
            default: return R.drawable.ic_study_book;
        }
    }

    private EditText input(String label, String hint, String value, int max) {
        EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(max) });
        field.setHint(hint);
        field.setContentDescription(label);
        field.setText(value);
        StudySheet.styleField(field);
        return field;
    }

    private EditText field(LinearLayout parent, String label, String hint, String value, int max) {
        add(parent, StudySheet.section(context, label), parent.getChildCount() == 0 ? 0 : 14);
        EditText field = input(label, hint, value, max);
        add(parent, field, 6);
        return field;
    }

    private static String value(EditText field) {
        return field.getText().toString().trim();
    }

    private void source(String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            showError("这台设备无法打开来源链接");
        }
    }

    private final class Editor {
        private final StudyPlan.Kind kind;
        private final StudyPlan original;
        private final List<StudySubject> shelf = new ArrayList<>();
        private StudyPlan.Partner partner;
        private LinearLayout shelfView, partnerView;
        private TextView shelfCount;

        Editor(StudyPlan.Kind kind, StudyPlan original) {
            this.kind = kind;
            this.original = original;
            if (original != null) shelf.addAll(original.books());
            partner = original == null ? StudyPlan.Partner.SQUIRREL : original.partner;
        }

        void show() {
            StudySheet.Pane pane = pane(original == null ? "新建" + kind.title + "计划" : "编辑计划",
                null, glyph(kind));
            EditText name = field(pane.body, "计划名称", "例如 408 一轮", original == null ? "" : original.title, 40);
            EditText due = field(pane.body, "预计结束日（选填）", "YYYY-MM-DD", original == null ? "" : original.due, 10);
            partnerView = column(context);
            add(pane.body, partnerView, 13);
            showPartner();

            LinearLayout shelfHeader = row(context);
            shelfHeader.addView(StudySheet.section(context, "科目书架"), new LinearLayout.LayoutParams(0, -2, 1));
            shelfCount = text(context, "", 12, MUTED);
            shelfHeader.addView(shelfCount);
            add(pane.body, shelfHeader, 17);
            LinearLayout actions = row(context);
            TextView templates = StudySheet.link(context, "从科目库添加", DEEP, this::catalog);
            templates.setBackground(ripple(context, rounded(context, MOSS, 0, 13), false));
            actions.addView(templates, new LinearLayout.LayoutParams(0, -2, 1));
            if (kind != StudyPlan.Kind.FINALS)
                actions.addView(StudySheet.link(context, "自定义科目", DEEP, () -> subject(-1)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            add(pane.body, actions, 8);
            shelfView = column(context);
            add(pane.body, shelfView, 6);
            showShelf();

            pane.primary("保存计划", () -> {
                if (value(name).isEmpty()) {
                    name.requestFocus();
                    name.setError("请填写计划名称");
                    return;
                }
                try {
                    StudyPlan.date(value(due));
                } catch (IllegalArgumentException e) {
                    due.requestFocus();
                    due.setError(e.getMessage());
                    return;
                }
                save(() -> store.update(n -> {
                    if (original == null) n.createPlan(value(name), kind, value(due), partner, shelf,
                        System.currentTimeMillis());
                    else n.editPlan(original.id, value(name), value(due), partner, shelf);
                    return null;
                }));
            });
            sheet.push(pane);
        }

        private void showPartner() {
            partnerView.removeAllViews();
            add(partnerView, StudySheet.item(context, "学习伙伴", partner.title, R.drawable.ic_leaf,
                OAT, "›", false, this::partners), 0);
        }

        private void partners() {
            StudySheet.Pane pane = pane("学习伙伴", null, R.drawable.ic_leaf);
            for (StudyPlan.Partner choice : StudyPlan.Partner.values()) {
                boolean current = choice == partner;
                add(pane.body, StudySheet.item(context, choice.title, null, R.drawable.ic_study_sprout,
                    current ? MOSS : Color.TRANSPARENT, current ? "✓" : null, current, () -> {
                        partner = choice;
                        showPartner();
                        sheet.pop();
                    }), 6);
            }
            sheet.push(pane);
        }

        private void showShelf() {
            shelfView.removeAllViews();
            shelfCount.setText(shelf.size() + " 门");
            if (shelf.isEmpty()) add(shelfView, text(context, "书架还没有科目。", 13, MUTED), 6);
            for (int i = 0; i < shelf.size(); i++) {
                int index = i;
                StudySubject book = shelf.get(i);
                String detail = book.hint;
                if (!book.due.isEmpty()) detail += (detail.isEmpty() ? "" : " · ") + book.due;
                LinearLayout entry = StudySheet.item(context, book.title, detail.isEmpty() ? null : detail,
                    0, Color.TRANSPARENT, "›", false, () -> bookActions(index));
                View dot = new View(context);
                dot.setBackground(rounded(context, book.color, 0, 4));
                dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams dotSize = new LinearLayout.LayoutParams(dp(context, 8), dp(context, 8));
                dotSize.setMarginEnd(dp(context, 10));
                entry.addView(dot, 0, dotSize);
                add(shelfView, entry, 3);
            }
        }

        private void bookActions(int index) {
            StudySheet.Pane pane = pane(shelf.get(index).title, null, R.drawable.ic_study_book);
            item(pane, "编辑科目信息", null, 0, MOSS, () -> subject(index));
            if (index > 0) item(pane, "上移", null, 0, Color.TRANSPARENT, () -> move(index, index - 1));
            if (index < shelf.size() - 1)
                item(pane, "下移", null, 0, Color.TRANSPARENT, () -> move(index, index + 1));
            pane.secondary("从书架移除", () -> {
                shelf.remove(index);
                showShelf();
                sheet.pop();
            });
            sheet.push(pane);
        }

        private void move(int from, int to) {
            Collections.swap(shelf, from, to);
            showShelf();
            sheet.pop();
        }

        private void subject(int index) {
            subject(index, "");
        }

        private void subject(int index, String suggestion) {
            StudySubject old = index < 0 ? null : shelf.get(index);
            StudySheet.Pane pane = pane(old == null ? "自定义科目" : "编辑科目", null, R.drawable.ic_study_book);
            EditText title = field(pane.body, "科目名称", "例如 数据库原理", old == null ? suggestion : old.title, 40);
            EditText code = field(pane.body, "考试科目代码（选填）", "例如 408", old == null ? "" : old.hint, 40);
            EditText due = field(pane.body, "考试或提交日（选填）", "YYYY-MM-DD", old == null ? "" : old.due, 10);
            EditText assessment = field(pane.body, "考核方式（选填）", "例如 闭卷或课程报告", old == null ? "" : old.assessment, 40);
            pane.primary("保存科目", () -> {
                if (value(title).isEmpty()) {
                    title.requestFocus();
                    title.setError("请填写科目名称");
                    return;
                }
                try {
                    StudySubject book = old == null ? StudyCatalog.book(value(title), value(code), shelf.size()) : old;
                    book = book.edit(value(title), value(code), value(due), value(assessment));
                    if (old == null) shelf.add(book);
                    else shelf.set(index, book);
                    showShelf();
                    sheet.pop();
                    if (old != null) sheet.pop();
                } catch (IllegalArgumentException e) {
                    due.requestFocus();
                    due.setError(e.getMessage());
                }
            });
            sheet.push(pane);
        }

        private void catalog() {
            StudySheet.Pane pane = pane("科目库", null, R.drawable.ic_study_book);
            String queryLabel = "搜索科目、代码或专业";
            EditText search = input(queryLabel, queryLabel, "", 60);
            search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_study_sheet_search, 0, 0, 0);
            search.setCompoundDrawablePadding(dp(context, 8));
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            search.setOnEditorActionListener((field, action, event) -> {
                if (action != EditorInfo.IME_ACTION_SEARCH) return false;
                sheet.hideKeyboard();
                return true;
            });
            search.setPaddingRelative(search.getPaddingStart(), search.getPaddingTop(), dp(context, 44),
                search.getPaddingBottom());
            FrameLayout searchBox = new FrameLayout(context);
            searchBox.addView(search, new FrameLayout.LayoutParams(-1, -2));
            ImageButton clear = StudySheet.iconButton(context, R.drawable.ic_study_sheet_close, "清除搜索");
            clear.setOnClickListener(v -> search.setText(""));
            FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(dp(context, 34), dp(context, 34),
                Gravity.END | Gravity.CENTER_VERTICAL);
            clearLp.setMarginEnd(dp(context, 6));
            searchBox.addView(clear, clearLp);
            add(pane.above, searchBox, 0);

            LinearLayout tabs = row(context);
            tabs.setBackground(rounded(context, 0xffebe7da, 0xffdcd8c8, 13));
            tabs.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
            TextView graduate = StudySheet.link(context, "考研模板", DEEP, () -> {});
            TextView finals = StudySheet.link(context, "期末课程", DEEP, () -> {});
            for (int i = 0; i < 2; i++) {
                TextView tab = i == 0 ? graduate : finals;
                tab.setMinimumHeight(dp(context, 38));
                LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, -2, 1);
                if (i == 1) tabParams.setMarginStart(dp(context, 4));
                tabs.addView(tab, tabParams);
            }
            add(pane.above, tabs, 10);
            TextView notice = text(context, "", 12, MUTED);
            add(pane.body, notice, 8);
            LinearLayout matches = column(context);
            add(pane.body, matches, 8);
            StudyPlan.Kind[] selected = {kind == StudyPlan.Kind.FINALS ? kind : StudyPlan.Kind.GRADUATE};
            Runnable refresh = () -> {
                matches.removeAllViews();
                String query = value(search).toLowerCase(Locale.ROOT);
                clear.setVisibility(search.length() == 0 ? View.GONE : View.VISIBLE);
                boolean graduateSelected = selected[0] == StudyPlan.Kind.GRADUATE;
                tabState(graduate, query.isEmpty() && graduateSelected);
                tabState(finals, query.isEmpty() && !graduateSelected);
                notice.setText(query.isEmpty() ? (graduateSelected
                    ? "2027 年院校示例 · 按本人报考目录调整"
                    : "按学科找专业") : "考研与期末搜索结果");
                if (query.isEmpty() && !graduateSelected) {
                    addFinalTracks(matches);
                    return;
                }
                for (StudyCatalog.Group group : StudyCatalog.GROUPS) {
                    if (query.isEmpty() && group.kind != selected[0]) continue;
                    List<String> entries = new ArrayList<>();
                    boolean byGroup = (group.title + group.detail).toLowerCase(Locale.ROOT).contains(query);
                    for (String entry : group.entries)
                        if (byGroup || entry.toLowerCase(Locale.ROOT).contains(query)) entries.add(entry);
                    if (!entries.isEmpty()) add(matches, StudySheet.item(context, group.title, group.detail,
                        glyph(group.kind), Color.TRANSPARENT, "›", false, () -> chooseBooks(group, entries)), 3);
                }
                if (matches.getChildCount() == 0) {
                    add(matches, text(context, "没有找到匹配的科目或专业。", 13, MUTED), 9);
                    add(matches, StudySheet.link(context, "自定义科目", DEEP,
                        () -> subject(-1, value(search))), 7);
                }
            };
            graduate.setOnClickListener(v -> {
                selected[0] = StudyPlan.Kind.GRADUATE;
                if (search.length() == 0) refresh.run();
                else search.setText("");
            });
            finals.setOnClickListener(v -> {
                selected[0] = StudyPlan.Kind.FINALS;
                if (search.length() == 0) refresh.run();
                else search.setText("");
            });
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
                public void afterTextChanged(Editable s) {}
            });
            refresh.run();
            sheet.push(pane);
        }

        private void addFinalTracks(LinearLayout host) {
            android.content.res.Configuration layout = context.getResources().getConfiguration();
            boolean sideBySide = layout.screenWidthDp >= 360 && layout.fontScale <= 1.15f;
            LinearLayout tracks = sideBySide ? row(context) : column(context);
            for (StudyCatalog.Track track : StudyCatalog.Track.values()) {
                LinearLayout card = trackCard(track);
                if (sideBySide) {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                    if (tracks.getChildCount() > 0) params.setMarginStart(dp(context, 10));
                    tracks.addView(card, params);
                } else add(tracks, card, tracks.getChildCount() == 0 ? 0 : 8);
            }
            add(host, tracks, 4);
        }

        private LinearLayout trackCard(StudyCatalog.Track track) {
            boolean humanities = track == StudyCatalog.Track.HUMANITIES;
            int count = 0;
            for (StudyCatalog.Group group : StudyCatalog.GROUPS) if (group.track == track) count++;
            LinearLayout card = column(context);
            card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 13));
            card.setBackground(ripple(context, rounded(context, humanities ? OAT : MOSS, 0, 16), false));
            card.setContentDescription(track.title + "，" + count + " 个专业，" + track.description);
            card.setFocusable(true);
            card.setOnClickListener(v -> chooseMajor(track));
            ForestMotion.bind(card);

            LinearLayout iconRow = row(context);
            ImageView icon = new ImageView(context);
            icon.setImageResource(humanities ? R.drawable.ic_study_book : R.drawable.ic_study_trail);
            icon.setImageTintList(ColorStateList.valueOf(humanities ? WOOD : DEEP));
            icon.setBackground(rounded(context, LIGHT, 0, 10));
            icon.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            iconRow.addView(icon, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)));
            View space = new View(context);
            iconRow.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
            TextView next = text(context, "›", 22, DEEP);
            next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            iconRow.addView(next);
            card.addView(iconRow);
            add(card, heading(context, track.title, 22), 11);
            add(card, text(context, count + " 个专业", 12, DEEP), 6);
            add(card, text(context, track.description, 12, MUTED), 7);
            return card;
        }

        private void chooseMajor(StudyCatalog.Track track) {
            StudySheet.Pane pane = pane(track.title + "专业", "期末课程",
                R.drawable.ic_study_book);
            LinearLayout list = column(context);
            list.setBackground(rounded(context, LIGHT, 0, 16));
            for (StudyCatalog.Group group : StudyCatalog.GROUPS) {
                if (group.track != track) continue;
                if (list.getChildCount() > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(LINE);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(context, 1));
                    params.setMarginStart(dp(context, 12));
                    params.setMarginEnd(dp(context, 12));
                    list.addView(divider, params);
                }
                String count = group.entries.size() + " 门";
                LinearLayout major = StudySheet.item(context, group.title, null, 0, Color.TRANSPARENT,
                    count + " ›", false, () -> chooseBooks(group, group.entries));
                major.setContentDescription(group.title + "，" + count + "课程可选");
                list.addView(major, new LinearLayout.LayoutParams(-1, -2));
            }
            add(pane.body, list, 0);
            pane.secondary("找不到专业或科目？自定义添加", () -> subject(-1));
            sheet.push(pane);
        }

        private void tabState(TextView tab, boolean selected) {
            tab.setSelected(selected);
            tab.setTextColor(selected ? 0xff243624 : 0xff6b7568);
            tab.setTypeface(Typeface.create(
                selected ? "sans-serif-medium" : "sans-serif",
                selected ? Typeface.BOLD : Typeface.NORMAL
            ));
            tab.setBackground(ripple(context, rounded(context,
                selected ? 0xfffffcf3 : Color.TRANSPARENT,
                selected ? 0xffc6d4c0 : 0, 10), false));
        }

        private void chooseBooks(StudyCatalog.Group group, List<String> entries) {
            String detail = group.kind == StudyPlan.Kind.FINALS
                ? group.track.title + " · 按本学期实际选课和排考勾选" : group.detail;
            StudySheet.Pane pane = pane(group.title, detail, glyph(group.kind));
            LinearLayout toolbar = row(context);
            toolbar.setPadding(0, 0, 0, dp(context, 4));
            TextView available = text(context, entries.size() + " 门课程", 12, MUTED);
            toolbar.addView(available, new LinearLayout.LayoutParams(0, -2, 1));
            TextView sourceLink = StudySheet.link(context, "查看官方来源", DEEP, () -> source(group.source));
            sourceLink.setTextSize(12);
            sourceLink.setMinimumHeight(dp(context, 32));
            sourceLink.setPadding(dp(context, 8), dp(context, 4), dp(context, 4), dp(context, 4));
            toolbar.addView(sourceLink);
            add(pane.above, toolbar, 0);
            List<MaterialCheckBox> checks = new ArrayList<>();
            for (String raw : entries) {
                String[] fields = raw.split("\\|", 2);
                String title = fields[fields.length - 1];
                String code = fields.length == 2 ? fields[0] : "";
                boolean present = contains(title, code);
                MaterialCheckBox checkbox = com.example.pkmapp.forest.ForestUi.check(
                    context,
                    com.example.pkmapp.forest.ForestUi.badgeText(
                        context, code, title, present ? "  · 已加入书架" : ""
                    ),
                    false
                );
                checkbox.setTextColor(present ? 0xff4f5b4e : 0xff243624);
                checkbox.setChecked(present);
                checkbox.setEnabled(!present);
                add(pane.body, checkbox, 7);
                checks.add(checkbox);
            }
            TextView count = text(context, "", 12, DEEP);
            count.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            count.setPadding(0, 0, 0, dp(context, 4));
            add(pane.footer, count, 0);
            MaterialButton apply = pane.primary("加入书架", () -> {
                for (int i = 0; i < entries.size(); i++) {
                    if (!checks.get(i).isEnabled() || !checks.get(i).isChecked()) continue;
                    String[] fields = entries.get(i).split("\\|", 2);
                    shelf.add(StudyCatalog.book(fields[fields.length - 1], fields.length == 2 ? fields[0] : "", shelf.size()));
                }
                showShelf();
                sheet.pop();
            });
            Runnable update = () -> {
                int added = 0;
                for (MaterialCheckBox checkbox : checks) if (checkbox.isEnabled() && checkbox.isChecked()) added++;
                count.setText("已选 " + added + " 门");
                apply.setEnabled(added > 0);
            };
            for (MaterialCheckBox checkbox : checks)
                checkbox.setOnCheckedChangeListener((button, checked) -> update.run());
            if (group.kind == StudyPlan.Kind.GRADUATE) pane.secondary("全选本组科目", () -> {
                for (MaterialCheckBox checkbox : checks) if (checkbox.isEnabled()) checkbox.setChecked(true);
            });
            else pane.secondary("找不到科目？自定义添加", () -> subject(-1));
            update.run();
            sheet.push(pane);
        }

        private boolean contains(String title, String code) {
            for (StudySubject subject : shelf) {
                if (subject.title.equals(title) && subject.hint.equals(code)) return true;
            }
            return false;
        }
    }
}
