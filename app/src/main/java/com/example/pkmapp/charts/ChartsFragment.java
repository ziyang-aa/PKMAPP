package com.example.pkmapp.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentChartsBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChartsFragment extends Fragment {
 private FragmentChartsBinding binding; private TransactionType type=TransactionType.EXPENSE; private int period=Calendar.MONTH;
 @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup p,@Nullable Bundle s){binding=FragmentChartsBinding.inflate(i,p,false);return binding.getRoot();}
 @Override public void onViewCreated(@NonNull View v,@Nullable Bundle s){binding.pageBackButton.setOnClickListener(x->((MainActivity)requireActivity()).showDestination(AppDestination.HOME));binding.chartExpenseButton.setChecked(true);binding.chartMonthButton.setChecked(true);binding.chartExpenseButton.setOnClickListener(x->{type=TransactionType.EXPENSE;render();});binding.chartIncomeButton.setOnClickListener(x->{type=TransactionType.INCOME;render();});binding.chartWeekButton.setOnClickListener(x->{period=Calendar.WEEK_OF_YEAR;render();});binding.chartMonthButton.setOnClickListener(x->{period=Calendar.MONTH;render();});binding.chartYearButton.setOnClickListener(x->{period=Calendar.YEAR;render();});render();}
 private void render(){long[] points=period==Calendar.WEEK_OF_YEAR?new long[]{14,26,18,35,22,41,30}:period==Calendar.YEAR?new long[]{15,28,20,42,32,50,38,44,36,54,48,62}:new long[]{12,30,18,45,32,56,40};Map<String,Long> ranks=new LinkedHashMap<>();long total=0;for(Transaction t:InMemoryLedgerRepository.getInstance().getTransactionsForCurrentLedger())if(t.getType()==type){total+=t.getAmountInCents();ranks.put(t.getCategory(),ranks.getOrDefault(t.getCategory(),0L)+t.getAmountInCents());}if(total==0){total=type==TransactionType.EXPENSE?16360:658000;ranks.put(type==TransactionType.EXPENSE?"餐饮":"工资",total);}binding.chartTrendView.setValues(points,type==TransactionType.EXPENSE);binding.chartSummary.setText((type==TransactionType.EXPENSE?"支出":"收入")+"趋势 · "+periodName()+"  合计 "+MoneyParser.formatCents(total));StringBuilder text=new StringBuilder();int n=1;for(Map.Entry<String,Long> e:ranks.entrySet()){text.append(n++).append(". ").append(e.getKey()).append("  ").append(MoneyParser.formatCents(e.getValue())).append('\n');if(n>3)break;}binding.chartRanking.setText(text.toString().trim());}
 private String periodName(){return period==Calendar.WEEK_OF_YEAR?"本周":period==Calendar.YEAR?"本年":"本月";}
 @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
