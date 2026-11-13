package com.huimeng.bookshop.ui;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.huimeng.bookshop.core.ServiceClient;
import com.huimeng.bookshop.core.SessionStore;
import org.json.JSONObject;
import java.util.function.IntConsumer;

/** Stock is confirmed online; it is never queued as a complete product edit. */
final class ProductStockEditor {
    static void open(AppCompatActivity activity,String productId,IntConsumer updated){
        ServiceClient client=new ServiceClient(activity);
        client.get("merchant/products/"+productId,result->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            JSONObject product=result.optJSONObject("data");
            if(result.optBoolean("cached")||product==null){message(activity,"请联网读取实时库存后重试");return;}
            int expected=product.optInt("stock");
            LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
            int padding=Math.round(24*activity.getResources().getDisplayMetrics().density);content.setPadding(padding,0,padding,0);
            TextView hint=new TextView(activity);hint.setText("当前可售库存 "+expected+" 件\n仅调整可售数量，已占用库存和商品审核状态保持不变。");content.addView(hint);
            EditText input=new EditText(activity);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("调整后的可售库存");input.setContentDescription("调整后的可售库存");input.setText(String.valueOf(expected));input.selectAll();content.addView(input);
            var dialog=new MaterialAlertDialogBuilder(activity).setTitle("调整库存").setView(content).setNegativeButton("取消",null).setPositiveButton("确认调整",null).create();
            dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
                final int stock;try{stock=Integer.parseInt(input.getText().toString().trim());if(stock<0)throw new NumberFormatException();}catch(Exception error){input.setError("请填写有效的非负整数");return;}
                dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);input.setEnabled(false);dialog.setCancelable(false);
                try{client.post("merchant/products/"+productId+"/stock",new JSONObject().put("stock",stock).put("expectedStock",expected),r->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    dialog.dismiss();updated.accept(r.optInt("stock",stock));

                    if(new SessionStore(activity).hasLocalSpace())com.huimeng.bookshop.core.sync.SyncCoordinator.get(activity).requestSync();
                },error->{if(activity.isFinishing()||activity.isDestroyed())return;dialog.dismiss();message(activity,error);});}
                catch(Exception error){dialog.dismiss();message(activity,"库存未调整，请重新打开后重试");}
            }));dialog.show();
        },error->message(activity,error));
    }
    private static void message(AppCompatActivity activity,String text){if(!activity.isFinishing()&&!activity.isDestroyed())new MaterialAlertDialogBuilder(activity).setMessage(text).setPositiveButton("知道了",null).show();}
}
