package com.example.pkmapp.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pkmapp.R;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.record.CategoryIconResolver;
import com.example.pkmapp.record.MoneyParser;

import java.util.ArrayList;
import java.util.List;

public final class TransactionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 1;
    private static final int VIEW_TYPE_TRANSACTION = 2;
    private final List<DetailsListItem> items = new ArrayList<>();
    private final OnDeleteRequested onDeleteRequested;
    private final OnEditRequested onEditRequested;

    public TransactionListAdapter(OnDeleteRequested onDeleteRequested,
            OnEditRequested onEditRequested) {
        this.onDeleteRequested = onDeleteRequested;
        this.onEditRequested = onEditRequested;
    }

    public interface OnDeleteRequested {
        void onDeleteRequested(Transaction transaction);
    }

    public interface OnEditRequested {
        void onEditRequested(Transaction transaction);
    }

    public void submit(List<DetailsListItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getKind() == DetailsListItem.Kind.DATE_HEADER
                ? VIEW_TYPE_HEADER : VIEW_TYPE_TRANSACTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_transaction_date_header,
                    parent, false));
        }
        return new TransactionViewHolder(inflater.inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DetailsListItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
            return;
        }
        ((TransactionViewHolder) holder).bind(item.getTransaction(), onDeleteRequested,
                onEditRequested);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView header;
        private final TextView netAmount;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.transaction_date_header);
            netAmount = itemView.findViewById(R.id.transaction_date_net);
        }

        void bind(DetailsListItem item) {
            header.setText(item.getHeaderLabel());
            long net = item.getNetAmountInCents();
            if (net == 0L) {
                netAmount.setText("收支平衡");
                netAmount.setTextColor(ContextCompat.getColor(itemView.getContext(),
                        R.color.wood_brown));
            } else {
                String prefix = net > 0L ? "+" : "−";
                netAmount.setText(prefix + MoneyParser.formatCents(Math.abs(net)));
                netAmount.setTextColor(ContextCompat.getColor(itemView.getContext(), net > 0L
                        ? R.color.forest_green : R.color.expense_red));
            }
        }
    }

    private static final class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView category;
        private final TextView note;
        private final TextView type;
        private final TextView amount;
        private final TextView edit;
        private final TextView delete;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.transaction_icon);
            category = itemView.findViewById(R.id.transaction_category);
            note = itemView.findViewById(R.id.transaction_note);
            type = itemView.findViewById(R.id.transaction_type);
            amount = itemView.findViewById(R.id.transaction_amount);
            edit = itemView.findViewById(R.id.transaction_edit);
            delete = itemView.findViewById(R.id.transaction_delete);
        }

        void bind(Transaction transaction, OnDeleteRequested onDeleteRequested,
                OnEditRequested onEditRequested) {
            boolean income = transaction.getType() == TransactionType.INCOME;
            boolean savingsDeposit = income && transaction.getCategory().startsWith("攒钱 · ");
            int color = ContextCompat.getColor(itemView.getContext(), income
                    ? R.color.forest_green : R.color.expense_red);
            int iconResource = itemView.getResources().getIdentifier(
                    CategoryIconResolver.resourceName(transaction.getType(), transaction.getCategory()),
                    "drawable", itemView.getContext().getPackageName());
            if (iconResource == 0) {
                iconResource = income ? R.drawable.norm_type_income_allowance
                        : R.drawable.norm_type_expense_custom;
            }
            icon.setImageResource(iconResource);
            icon.setContentDescription(transaction.getCategory());
            category.setText(transaction.getCategory());
            note.setText(transaction.getNote().isEmpty() ? transaction.getCategory()
                    : transaction.getNote());
            type.setText(savingsDeposit ? "攒钱" : income ? "收入" : "支出");
            type.setTextColor(color);
            amount.setText((income ? "＋" : "−")
                    + MoneyParser.formatCents(transaction.getAmountInCents()));
            amount.setTextColor(color);
            edit.setContentDescription("编辑这笔" + transaction.getCategory() + "交易");
            edit.setOnClickListener(view -> onEditRequested.onEditRequested(transaction));
            delete.setContentDescription("删除这笔" + transaction.getCategory() + "交易");
            delete.setOnClickListener(view -> onDeleteRequested.onDeleteRequested(transaction));
        }
    }
}
