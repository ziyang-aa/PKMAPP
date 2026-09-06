package com.example.pkmapp.details;
import android.os.Bundle; import android.view.*; import android.widget.TextView;
import androidx.annotation.*; import androidx.fragment.app.Fragment;
import com.example.pkmapp.R; import com.example.pkmapp.MainActivity; import com.example.pkmapp.data.*; import com.example.pkmapp.databinding.FragmentDetailsBinding; import com.example.pkmapp.navigation.AppDestination; import com.example.pkmapp.record.MoneyParser;
import java.text.DateFormat; import java.util.Calendar;
public final class DetailsFragment extends Fragment {
 private FragmentDetailsBinding binding; private final InMemoryLedgerRepository repository=InMemoryLedgerRepository.getInstance(); private final LedgerDataListener listener=this::render;
 @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup p,@Nullable Bundle s){binding=FragmentDetailsBinding.inflate(i,p,false);binding.pageBackButton.setOnClickListener(v->((MainActivity)requireActivity()).showDestination(AppDestination.HOME));return binding.getRoot();}
 @Override public void onResume(){super.onResume();repository.addListener(listener);render();}
 @Override public void onPause(){repository.removeListener(listener);super.onPause();}
 private void render(){if(binding==null)return; MonthlyTotals t=repository.getCurrentMonthTotals(System.currentTimeMillis()); binding.detailsLedgerName.setText("当前账本："+repository.getCurrentLedger().getName());binding.detailsIncome.setText("收入  "+MoneyParser.formatCents(t.getIncomeInCents()));binding.detailsExpense.setText("支出  "+MoneyParser.formatCents(t.getExpenseInCents()));binding.detailsBalance.setText(MoneyParser.formatCents(t.getBalanceInCents()));binding.detailsTransactionList.removeAllViews();for(Transaction x:repository.getTransactionsForCurrentLedger()){TextView row=new TextView(requireContext());row.setPadding(0,24,0,24);String sign=x.getType()==TransactionType.INCOME?"+":"-";row.setText(x.getCategory()+"  ·  "+(x.getNote().isEmpty()?"未写备注":x.getNote())+"\n"+DateFormat.getDateInstance().format(x.getOccurredAtMillis())+"    "+sign+MoneyParser.formatCents(x.getAmountInCents()));row.setTextColor(getResources().getColor(x.getType()==TransactionType.INCOME?R.color.forest_green:R.color.expense_red));binding.detailsTransactionList.addView(row);}}
 @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
