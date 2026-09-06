package com.example.pkmapp.record;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentRecordBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import java.text.DateFormat;
import java.util.Calendar;

public final class RecordFragment extends Fragment {
    private FragmentRecordBinding binding;
    private final Calendar date = Calendar.getInstance();
    private TransactionType type = TransactionType.EXPENSE;
    private String category;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) { binding=FragmentRecordBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        binding.pageBackButton.setOnClickListener(v -> ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.recordTypeExpense.setChecked(true);
        binding.recordTypeExpense.setOnClickListener(v->{type=TransactionType.EXPENSE; renderCategories();});
        binding.recordTypeIncome.setOnClickListener(v->{type=TransactionType.INCOME; renderCategories();});
        binding.recordDateButton.setOnClickListener(v->showWheelDatePicker());
        binding.recordCalculatorButton.setOnClickListener(v->showCalculator());
        binding.recordSaveButton.setOnClickListener(v->save()); dateLabel(); renderCategories();
    }
    private void dateLabel(){binding.recordDateButton.setText("日期："+DateFormat.getDateInstance().format(date.getTime()));}
    private void showWheelDatePicker(){LinearLayout wheels=new LinearLayout(requireContext());wheels.setOrientation(LinearLayout.HORIZONTAL);int width=0;NumberPicker year=picker(2020,2035,date.get(Calendar.YEAR));NumberPicker month=picker(1,12,date.get(Calendar.MONTH)+1);NumberPicker day=picker(1,31,date.get(Calendar.DAY_OF_MONTH));wheels.addView(year,new LinearLayout.LayoutParams(width,180,1));wheels.addView(month,new LinearLayout.LayoutParams(width,180,1));wheels.addView(day,new LinearLayout.LayoutParams(width,180,1));new MaterialAlertDialogBuilder(requireContext()).setTitle("选择日期").setView(wheels).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{date.set(year.getValue(),month.getValue()-1,day.getValue());dateLabel();}).show();}
    private NumberPicker picker(int min,int max,int value){NumberPicker picker=new NumberPicker(requireContext());picker.setMinValue(min);picker.setMaxValue(max);picker.setValue(value);picker.setWrapSelectorWheel(false);return picker;}
    private void renderCategories(){ category=null; binding.recordEntryCard.setVisibility(View.GONE); binding.recordCategoryGroup.removeAllViews(); String[] labels=type==TransactionType.EXPENSE?new String[]{"餐饮","网购","日用","交通","娱乐","住房","自定义"}:new String[]{"工资","奖金","兼职","红包","理财","其他","自定义"}; for(String label:labels){LinearLayout item=new LinearLayout(requireContext());item.setGravity(android.view.Gravity.CENTER);item.setOrientation(LinearLayout.VERTICAL);MaterialButton button=new MaterialButton(requireContext());button.setIconResource(com.example.pkmapp.R.drawable.ic_add_ledger);button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);button.setCornerRadius(999);button.setMinWidth(56);button.setMinHeight(56);button.setText("");button.setContentDescription(label);button.setOnClickListener(v->{if("自定义".equals(label))showCustomCategory();else {category=label;showEntry();}});TextView name=new TextView(requireContext());name.setText(label);name.setGravity(android.view.Gravity.CENTER);name.setOnClickListener(v->button.performClick());item.addView(button,new LinearLayout.LayoutParams(64,64));item.addView(name);android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams();p.width=0;p.columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f);p.setMargins(4,8,4,8);binding.recordCategoryGroup.addView(item,p);} }
    private void showCustomCategory(){EditText input=new EditText(requireContext());input.setHint("输入分类名称");new MaterialAlertDialogBuilder(requireContext()).setTitle("自定义分类").setView(input).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{String value=input.getText().toString().trim();if(value.isEmpty())Toast.makeText(requireContext(),"请输入分类名称",Toast.LENGTH_SHORT).show();else {category=value;showEntry();}}).show();}
    private void showEntry(){binding.recordEntryCard.setVisibility(View.VISIBLE);binding.recordAmountInput.requestFocus();}
    private void showCalculator(){EditText input=new EditText(requireContext());input.setHint("例如 12.5+8×2");new MaterialAlertDialogBuilder(requireContext()).setTitle("金额计算器").setView(input).setNegativeButton("取消",null).setPositiveButton("使用结果",(d,w)->{try{CalculatorEngine engine=new CalculatorEngine();for(char c:input.getText().toString().toCharArray())engine.append(String.valueOf(c));binding.recordAmountInput.setText(MoneyParser.formatCents(engine.evaluateToCents()).replace("¥", ""));}catch(IllegalArgumentException|ArithmeticException e){Toast.makeText(requireContext(),e.getMessage(),Toast.LENGTH_SHORT).show();}}).show();}
    private void save(){ try {long cents=MoneyParser.parseYuanToCents(String.valueOf(binding.recordAmountInput.getText())); if(category==null){Toast.makeText(requireContext(),"请选择分类",Toast.LENGTH_SHORT).show();return;} InMemoryLedgerRepository.getInstance().addTransaction(type,cents,category,String.valueOf(binding.recordNoteInput.getText()),date.getTimeInMillis()); Toast.makeText(requireContext(),"已收进森林账本",Toast.LENGTH_SHORT).show(); ((MainActivity)requireActivity()).showDestination(AppDestination.DETAILS);}catch(IllegalArgumentException e){binding.recordAmountLayout.setError(e.getMessage());}}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
