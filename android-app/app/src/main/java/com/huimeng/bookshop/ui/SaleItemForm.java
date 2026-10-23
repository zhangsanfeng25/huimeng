package com.huimeng.bookshop.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.*;
import com.huimeng.bookshop.R;
import org.json.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Item-level after-sale inputs; server always rechecks quantities and amounts. */
final class SaleItemForm {
    private record Row(JSONObject item,CheckBox checked,EditText quantity,EditText amount){}
    private final List<Row> rows=new ArrayList<>();
    SaleItemForm(Context context,LinearLayout parent,JSONArray items,SharedPreferences drafts,String scope){
        if(items==null)return;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null||item.optLong("refundable_cents")<=0||item.optInt("returnable_quantity")<=0)continue;
            String prefix=scope+"|item:"+item.optString("id");
            LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);int pad=(int)(12*context.getResources().getDisplayMetrics().density);box.setPadding(0,pad,0,pad);parent.addView(box);
            CheckBox checked=new CheckBox(context);checked.setText(item.optString("product_name"));checked.setTextColor(context.getColor(R.color.ink));checked.setChecked(drafts.getBoolean(prefix+"|selected",false));box.addView(checked);
            TextView note=new TextView(context);note.setText("剩余可售后数量 "+item.optInt("returnable_quantity")+" · 可退金额 ¥"+money(item.optLong("refundable_cents")));note.setTextColor(context.getColor(R.color.muted));box.addView(note);
            TextView quantityLabel=new TextView(context);quantityLabel.setText("售后数量");quantityLabel.setTextColor(context.getColor(R.color.ink));box.addView(quantityLabel);
            EditText quantity=new EditText(context);quantity.setSingleLine(true);quantity.setInputType(InputType.TYPE_CLASS_NUMBER);quantity.setHint("售后数量");quantity.setContentDescription(item.optString("product_name")+" 售后数量");quantity.setText(drafts.getString(prefix+"|quantity","1"));box.addView(quantity);
            TextView amountLabel=new TextView(context);amountLabel.setText("申请退款金额（元）");amountLabel.setTextColor(context.getColor(R.color.ink));box.addView(amountLabel);
            EditText amount=new EditText(context);amount.setSingleLine(true);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setHint("退款金额（元，可申请较低金额）");amount.setContentDescription(item.optString("product_name")+" 退款金额");amount.setText(drafts.getString(prefix+"|amount",money(Math.min(item.optLong("unit_price_cents"),item.optLong("refundable_cents")))));box.addView(amount);
            quantity.setEnabled(checked.isChecked());amount.setEnabled(checked.isChecked());
            checked.setOnCheckedChangeListener((button,value)->{quantity.setEnabled(value);amount.setEnabled(value);drafts.edit().putBoolean(prefix+"|selected",value).remove(scope+"|request").apply();});
            quantity.addTextChangedListener(watch(()->{try{int n=Integer.parseInt(quantity.getText().toString());amount.setText(money(Math.min((long)n*item.optLong("unit_price_cents"),item.optLong("refundable_cents"))));}catch(Exception ignored){}drafts.edit().putString(prefix+"|quantity",quantity.getText().toString()).remove(scope+"|request").apply();}));
            amount.addTextChangedListener(watch(()->drafts.edit().putString(prefix+"|amount",amount.getText().toString()).remove(scope+"|request").apply()));
            rows.add(new Row(item,checked,quantity,amount));
        }
    }
    static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
    private static TextWatcher watch(Runnable change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){change.run();}public void afterTextChanged(Editable e){}};}
    JSONArray selected()throws Exception{
        JSONArray result=new JSONArray();
        for(Row row:rows)if(row.checked().isChecked()){
            int quantity;long cents;
            try{quantity=Integer.parseInt(row.quantity().getText().toString());cents=new BigDecimal(row.amount().getText().toString()).movePointRight(2).longValueExact();}catch(Exception e){throw new IllegalArgumentException("请填写整数数量，金额最多两位小数");}
            if(quantity<1||quantity>row.item().optInt("returnable_quantity"))throw new IllegalArgumentException("售后数量超出剩余数量");
            if(cents<1||cents>Math.min(row.item().optLong("refundable_cents"),quantity*row.item().optLong("unit_price_cents")))throw new IllegalArgumentException("退款金额超出所选商品可退额度");
            result.put(new JSONObject().put("orderItemId",row.item().getString("id")).put("quantity",quantity).put("amountCents",cents));
        }
        if(result.length()==0)throw new IllegalArgumentException("请至少选择一件售后商品");return result;
    }
}
