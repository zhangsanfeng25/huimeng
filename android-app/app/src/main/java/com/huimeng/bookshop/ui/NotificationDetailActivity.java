package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityNotificationDetailBinding;
import org.json.JSONObject;

public final class NotificationDetailActivity extends AppCompatActivity{
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        ActivityNotificationDetailBinding binding=ActivityNotificationDetailBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        binding.back.setOnClickListener(v->finish());binding.title.setText(getIntent().getStringExtra("title"));binding.content.setText(getIntent().getStringExtra("content"));binding.time.setText(getIntent().getStringExtra("created_at"));
        String id=getIntent().getStringExtra("notice_id");
        if(id==null||id.isEmpty()){binding.delete.setEnabled(false);return;}
        if(!getIntent().getBooleanExtra("read",false))new ApiClient(this).post("me/notifications/"+id+"/read",new JSONObject(),result->{},error->{});
        binding.delete.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("删除这条消息？此操作仅影响当前账户。")
            .setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{
                binding.delete.setEnabled(false);
                new ApiClient(this).delete("me/notifications/"+id,result->finish(),error->{binding.delete.setEnabled(true);Toast.makeText(this,error,Toast.LENGTH_SHORT).show();});
            }).show());
    }
}
