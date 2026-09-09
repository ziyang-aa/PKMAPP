package com.example.pkmapp.borrowing;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.example.pkmapp.record.RecordDateState;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public final class BorrowingFragment extends Fragment {
    public static final String ARG_DIRECTION = "borrowing_direction";
    public static final String ARG_AMOUNT_CENTS = "borrowing_amount_cents";
    public static final String ARG_OCCURRED_AT = "borrowing_occurred_at";
    public static final String ARG_SOURCE_TRANSACTION = "borrowing_source_transaction";

    private final InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
    private BorrowingRecordStore store;
    private RecordDateState date = RecordDateState.today();
    private BorrowingDirection direction = BorrowingDirection.LEND;
    private String sourceTransactionId;
    private EditText amountInput;
    private EditText personInput;
    private EditText noteInput;
    private View dateField;
    private TextView dateButton;
    private TextView lendButton;
    private TextView borrowButton;
    private TextView modeHint;
    private TextView status;
    private LinearLayout recordList;
    private TextView emptyState;

    public static BorrowingFragment newComposer(BorrowingDirection direction, long amountCents,
            long occurredAtMillis, @Nullable String sourceTransactionId) {
        BorrowingFragment fragment = new BorrowingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DIRECTION, direction.name());
        args.putLong(ARG_AMOUNT_CENTS, amountCents);
        args.putLong(ARG_OCCURRED_AT, occurredAtMillis);
        if (sourceTransactionId != null) {
            args.putString(ARG_SOURCE_TRANSACTION, sourceTransactionId);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_borrowing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = new BorrowingRecordStore(requireContext());
        amountInput = view.findViewById(R.id.borrowing_amount_input);
        personInput = view.findViewById(R.id.borrowing_person_input);
        noteInput = view.findViewById(R.id.borrowing_note_input);
        dateField = view.findViewById(R.id.borrowing_date_field);
        dateButton = view.findViewById(R.id.borrowing_date_button);
        lendButton = view.findViewById(R.id.borrowing_direction_lend);
        borrowButton = view.findViewById(R.id.borrowing_direction_borrow);
        modeHint = view.findViewById(R.id.borrowing_mode_hint);
        status = view.findViewById(R.id.borrowing_status);
        recordList = view.findViewById(R.id.borrowing_record_list);
        emptyState = view.findViewById(R.id.borrowing_empty_state);
        readArguments();

        view.findViewById(R.id.borrowing_back_button).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.DETAILS));
        lendButton.setOnClickListener(v -> {
            direction = BorrowingDirection.LEND;
            renderDirection();
        });
        borrowButton.setOnClickListener(v -> {
            direction = BorrowingDirection.BORROW;
            renderDirection();
        });
        dateField.setOnClickListener(v -> showDateCalendar());
        view.findViewById(R.id.borrowing_save_button).setOnClickListener(v -> save());

        renderDirection();
        renderDate();
        renderRecords();
    }

    private void readArguments() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        String directionName = args.getString(ARG_DIRECTION);
        if (directionName != null) {
            try {
                direction = BorrowingDirection.valueOf(directionName);
            } catch (IllegalArgumentException ignored) {
                direction = BorrowingDirection.LEND;
            }
        }
        long occurredAt = args.getLong(ARG_OCCURRED_AT, 0L);
        if (occurredAt > 0L) {
            Calendar initial = Calendar.getInstance();
            initial.setTimeInMillis(occurredAt);
            date = new RecordDateState(initial);
        }
        long amountCents = args.getLong(ARG_AMOUNT_CENTS, 0L);
        if (amountCents > 0L && amountInput != null) {
            amountInput.setText(MoneyParser.formatCents(amountCents).replace("¥", "").trim());
        }
        sourceTransactionId = args.getString(ARG_SOURCE_TRANSACTION);
    }

    private void renderDirection() {
        if (lendButton == null) return;
        boolean lend = direction == BorrowingDirection.LEND;
        lendButton.setBackgroundResource(lend ? R.drawable.bg_details_picker_selected
                : R.drawable.bg_details_picker_option);
        borrowButton.setBackgroundResource(lend ? R.drawable.bg_details_picker_option
                : R.drawable.bg_details_picker_selected);
        lendButton.setTextColor(ContextCompat.getColor(requireContext(),
                lend ? R.color.forest_green : R.color.wood_brown));
        borrowButton.setTextColor(ContextCompat.getColor(requireContext(),
                lend ? R.color.wood_brown : R.color.forest_green));
        modeHint.setText(lend ? "现在：借出" : "现在：借入");
    }

    private void renderDate() {
        if (dateButton == null) return;
        dateButton.setText(String.format(java.util.Locale.CHINA, "%d年%02d月%02d日",
                date.year(), date.month(), date.day()));
    }

    private void save() {
        try {
            long amountCents = MoneyParser.parseYuanToCents(amountInput.getText().toString());
            String person = personInput.getText().toString().trim();
            if (person.isEmpty()) {
                personInput.setError("请填写对方姓名");
                return;
            }
            BorrowingRecord record = new BorrowingRecord(
                    UUID.randomUUID().toString(), repository.getCurrentLedger().getId(), direction,
                    amountCents, date.timeInMillis(), person,
                    noteInput.getText().toString(), sourceTransactionId);
            store.add(record);
            if (BorrowingTransactionSync.shouldCreateMainTransaction(sourceTransactionId)) {
                repository.addTransaction(
                        BorrowingTransactionSync.transactionType(direction), amountCents,
                        BorrowingTransactionSync.category(direction),
                        BorrowingTransactionSync.note(direction, person,
                                noteInput.getText().toString()), date.timeInMillis());
            }
            sourceTransactionId = null;
            amountInput.setText("");
            personInput.setText("");
            noteInput.setText("");
            status.setText("已留下这笔来往记录");
            status.setVisibility(View.VISIBLE);
            renderRecords();
        } catch (IllegalArgumentException exception) {
            amountInput.setError(exception.getMessage());
        }
    }

    private void renderRecords() {
        if (store == null || recordList == null) return;
        List<BorrowingRecord> records = store.getRecordsForLedger(repository.getCurrentLedger().getId());
        BorrowingSummary lend = BorrowingSummary.from(records, BorrowingDirection.LEND);
        BorrowingSummary borrow = BorrowingSummary.from(records, BorrowingDirection.BORROW);
        TextView lendTotal = requireView().findViewById(R.id.borrowing_lend_total);
        TextView lendCount = requireView().findViewById(R.id.borrowing_lend_count);
        TextView borrowTotal = requireView().findViewById(R.id.borrowing_borrow_total);
        TextView borrowCount = requireView().findViewById(R.id.borrowing_borrow_count);
        lendTotal.setText(MoneyParser.formatCents(lend.getTotalInCents()));
        lendCount.setText(lend.getRecordCount() + " 笔");
        borrowTotal.setText(MoneyParser.formatCents(borrow.getTotalInCents()));
        borrowCount.setText(borrow.getRecordCount() + " 笔");

        recordList.removeAllViews();
        for (BorrowingDateGroup group : BorrowingDateGroup.from(records)) {
            TextView dateLabel = label(group.getLabel(), 13, R.color.wood_brown);
            dateLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, dp(32));
            dateParams.topMargin = dp(8);
            recordList.addView(dateLabel, dateParams);
            for (BorrowingRecord record : group.getRecords()) {
                View item = LayoutInflater.from(requireContext()).inflate(
                        R.layout.item_borrowing_record, recordList, false);
                TextView itemAvatar = item.findViewById(R.id.borrowing_record_avatar);
                TextView itemDirection = item.findViewById(R.id.borrowing_record_direction);
                TextView itemPerson = item.findViewById(R.id.borrowing_record_person);
                TextView itemAmount = item.findViewById(R.id.borrowing_record_amount);
                TextView itemNote = item.findViewById(R.id.borrowing_record_note);
                TextView itemDate = item.findViewById(R.id.borrowing_record_date);
                boolean isLend = record.getDirection() == BorrowingDirection.LEND;
                itemAvatar.setText(BorrowingRecordPresentation.initials(record.getPerson()));
                itemDirection.setText(BorrowingRecordPresentation.directionLabel(record.getDirection()));
                itemDirection.setBackgroundResource(isLend ? R.drawable.bg_borrowing_tag_lend
                        : R.drawable.bg_borrowing_tag_borrow);
                itemDirection.setTextColor(ContextCompat.getColor(requireContext(),
                        isLend ? R.color.warm_orange : R.color.forest_green));
                itemPerson.setText(BorrowingRecordPresentation.counterpartyLabel(
                        record.getDirection(), record.getPerson()));
                itemAmount.setText(BorrowingRecordPresentation.amountLabel(
                        record.getDirection(), record.getAmountInCents()));
                itemAmount.setTextColor(ContextCompat.getColor(requireContext(),
                        isLend ? R.color.expense_red : R.color.forest_green));
                itemNote.setText(record.getNote().isEmpty()
                        ? (isLend ? "借给对方" : "向对方借入") : record.getNote());
                itemDate.setText(group.getLabel());
                item.setContentDescription(String.valueOf(itemDirection.getText())
                        + itemPerson.getText() + "，" + itemAmount.getText());
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                itemParams.bottomMargin = dp(8);
                recordList.addView(item, itemParams);
            }
        }
        emptyState.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDateCalendar() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_borrowing_calendar, null);
        Calendar draft = Calendar.getInstance();
        draft.setTimeInMillis(date.timeInMillis());
        Calendar visibleMonth = Calendar.getInstance();
        visibleMonth.setTimeInMillis(draft.getTimeInMillis());
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);

        TextView selectedDate = root.findViewById(R.id.borrowing_calendar_selected_date);
        TextView selectedWeekday = root.findViewById(R.id.borrowing_calendar_selected_weekday);
        TextView month = root.findViewById(R.id.borrowing_calendar_month);
        GridLayout grid = root.findViewById(R.id.borrowing_calendar_grid);
        renderCalendar(selectedDate, selectedWeekday, month, grid, draft, visibleMonth);

        root.findViewById(R.id.borrowing_calendar_previous).setOnClickListener(view -> {
            visibleMonth.add(Calendar.MONTH, -1);
            renderCalendar(selectedDate, selectedWeekday, month, grid, draft, visibleMonth);
        });
        root.findViewById(R.id.borrowing_calendar_next).setOnClickListener(view -> {
            visibleMonth.add(Calendar.MONTH, 1);
            renderCalendar(selectedDate, selectedWeekday, month, grid, draft, visibleMonth);
        });
        root.findViewById(R.id.borrowing_calendar_close).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.borrowing_calendar_cancel).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.borrowing_calendar_confirm).setOnClickListener(view -> {
            date = new RecordDateState(draft);
            renderDate();
            dialog.dismiss();
        });

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void renderCalendar(TextView selectedDate, TextView selectedWeekday,
            TextView month, GridLayout grid, Calendar draft, Calendar visibleMonth) {
        selectedDate.setText(BorrowingCalendarModel.selectedDateLabel(draft));
        selectedWeekday.setText(BorrowingCalendarModel.weekdayLabel(draft));
        month.setText(BorrowingCalendarModel.monthTitle(visibleMonth));
        grid.removeAllViews();
        List<Calendar> cells = BorrowingCalendarModel.cellsForMonth(visibleMonth);
        for (int index = 0; index < cells.size(); index++) {
            final Calendar chosenCell = cells.get(index);
            TextView day = label(String.valueOf(chosenCell.get(Calendar.DAY_OF_MONTH)), 14,
                    R.color.ink);
            boolean selected = BorrowingCalendarModel.isSameDate(chosenCell, draft);
            boolean inCurrentMonth = BorrowingCalendarModel.isSameMonth(chosenCell, visibleMonth);
            day.setTypeface(null, selected ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            day.setTextColor(ContextCompat.getColor(requireContext(), selected
                    ? R.color.paper_light : inCurrentMonth ? R.color.ink : R.color.brown_soft));
            day.setBackgroundResource(selected ? R.drawable.bg_borrowing_calendar_selected_day
                    : R.drawable.bg_borrowing_calendar_day);
            day.setGravity(Gravity.CENTER);
            FrameLayout cell = new FrameLayout(requireContext());
            cell.setContentDescription(BorrowingCalendarModel.selectedDateLabel(chosenCell)
                    + (selected ? "，已选择" : ""));
            cell.setClickable(true);
            cell.setFocusable(true);
            FrameLayout.LayoutParams dayParams = new FrameLayout.LayoutParams(
                    dp(42), dp(42), Gravity.CENTER);
            cell.addView(day, dayParams);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 7), GridLayout.spec(index % 7, 1, 1f));
            params.width = 0;
            params.height = dp(42);
            params.setMargins(dp(2), dp(1), dp(2), dp(1));
            grid.addView(cell, params);
            cell.setOnClickListener(view -> {
                draft.set(Calendar.YEAR, chosenCell.get(Calendar.YEAR));
                draft.set(Calendar.MONTH, chosenCell.get(Calendar.MONTH));
                draft.set(Calendar.DAY_OF_MONTH, chosenCell.get(Calendar.DAY_OF_MONTH));
                visibleMonth.setTimeInMillis(chosenCell.getTimeInMillis());
                visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
                renderCalendar(selectedDate, selectedWeekday, month, grid, draft, visibleMonth);
            });
        }
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(requireContext(), color));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        store = null;
        amountInput = null;
        personInput = null;
        noteInput = null;
        dateField = null;
        dateButton = null;
        lendButton = null;
        borrowButton = null;
        modeHint = null;
        status = null;
        recordList = null;
        emptyState = null;
        super.onDestroyView();
    }
}
