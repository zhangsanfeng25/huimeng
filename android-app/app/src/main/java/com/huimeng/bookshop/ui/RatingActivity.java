package com.huimeng.bookshop.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.databinding.ActivityRatingBinding;
import org.json.JSONObject;

public class RatingActivity extends AppCompatActivity{
    private ActivityRatingBinding binding;private String itemId;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivityRatingBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());itemId=getIntent().getStringExtra("order_item_id");binding.back.setOnClickListener(v->finish());binding.submit.setOnClickListener(v->submit());}
    private void submit(){String content=String.valueOf(binding.content.getText()).trim();if(content.isEmpty()){Toast.makeText(this,"请填写评价内容",Toast.LENGTH_SHORT).show();return;}binding.submit.setEnabled(false);try{JSONObject body=new JSONObject().put("orderItemId",itemId).put("stars",Math.max(1,(int)binding.stars.getRating())).put("content",content);new ApiClient(this).post("me/ratings",body,result->{Toast.makeText(this,"评价发布成功",Toast.LENGTH_SHORT).show();finish();},error->{binding.submit.setEnabled(true);Toast.makeText(this,error,Toast.LENGTH_SHORT).show();});}catch(Exception ignored){binding.submit.setEnabled(true);}}
}
