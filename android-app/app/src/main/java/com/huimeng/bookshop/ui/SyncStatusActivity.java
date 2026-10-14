package com.huimeng.bookshop.ui;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.local.*;
import com.huimeng.bookshop.databinding.ActivitySyncStatusBinding;
import org.json.*;
public class SyncStatusActivity extends AppCompatActivity {
    private ActivitySyncStatusBinding binding;
    @Override protected void onCreate(Bundle state){super.onCreate(state);binding=ActivitySyncStatusBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.back.setOnClickListener(v->finish());binding.login.setOnClickListener(v->startActivity(new Intent(this,LoginActivity.class)));binding.roles.setOnClickListener(v->startActivity(new Intent(this,RoleSwitchActivity.class)));binding.retry.setOnClickListener(v->{String space=new SessionStore(this).localSpaceId();LocalRepository.IO.execute(()->{new LocalConflictResolver(LocalDatabase.get(this)).retryBlocked(space);runOnUiThread(()->{com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).requestSync();refresh();});});});com.huimeng.bookshop.core.sync.SyncStatusStore.observe(this,value->{if(binding!=null)refresh();});}
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){SessionStore session=new SessionStore(this);String space=session.localSpaceId();LocalRepository.IO.execute(()->{try{
        LocalDatabase db=LocalDatabase.get(this);JSONObject info=new LocalSpaceStore(db).info(space);JSONArray pending=db.pending(space);String status=db.metadata("status:"+space),last=db.metadata("last-sync:"+space);
        runOnUiThread(()->{if(isFinishing()||isDestroyed()||!space.equals(session.localSpaceId()))return;
            binding.status.setText((info.optString("userId").isEmpty()?"本地模式 · 尚未关联网络账号":"网络账号："+info.optString("userId"))+"\n"+status+"\n待同步 / 需要处理："+pending.length()+" 条"+(last.isEmpty()?"":"\n最近完成同步："+last.replace('T',' ').replace("Z"," UTC"))+"\n关闭服务仍可使用；未确认的修改会继续保留本地。");
            binding.operations.removeAllViews();for(int i=0;i<pending.length();i++)addOperation(pending.optJSONObject(i),space);
        });
    }catch(Exception e){runOnUiThread(()->binding.status.setText("读取本地状态失败："+e.getMessage()));}});}
    private void addOperation(JSONObject op,String space){
        TextView row=new TextView(this);String state=op.optString("state"),error=op.optString("error");int separator=error.indexOf(" · ");if(separator>=0)error=error.substring(separator+3);
        String label=switch(op.optString("action").split("\\.")[0]){case "address"->"收货地址";case "profile"->"账户资料";case "cart"->"购物车";case "favorite"->"收藏";case "post"->"社区帖子";case "comment"->"评论";case "merchant"->"店铺资料";case "product"->"商家商品";case "checkout","order"->"订单操作";case "notice"->"消息状态";case "rating"->"商品评价";default->"本地修改";};
        row.setText(label+" · "+(state.equals("CONFLICT")?"需要处理冲突":state.equals("BLOCKED")?"需要处理":state.equals("WAITING_AUTH")?"需网络登录":state.equals("IN_FLIGHT")?"正在上传":"等待同步")+(error.isEmpty()?"":"\n"+error));row.setTextColor(getColor(com.huimeng.bookshop.R.color.ink));row.setPadding(0,24,0,8);binding.operations.addView(row);
        if(state.equals("CONFLICT"))addButton("查看并处理差异",()->showConflict(space,op));
        if(!state.equals("IN_FLIGHT"))addButton("保留最后网络版本",()->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("保留最后同步版本？").setMessage("将停止该记录的待上传修改并恢复最后同步内容。本地编辑保留在历史记录，图片不会被删除；此操作不会撤销可能已在服务器完成的操作。").setNegativeButton("取消",null).setPositiveButton("确认保留网络版本",(d,w)->resolve(space,op,false)).show());
    }
    private void addButton(String label,Runnable click){com.google.android.material.button.MaterialButton button=new com.google.android.material.button.MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);button.setText(label);button.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));button.setOnClickListener(v->click.run());binding.operations.addView(button);}
    private void showConflict(String space,JSONObject op){LocalRepository.IO.execute(()->{try{LocalDatabase db=LocalDatabase.get(this);String confirmed="尚无网络快照";try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT confirmed FROM local_entities WHERE space_id=? AND resource=? AND id=?",new String[]{space,op.optString("resource"),op.optString("entityId")})){if(c.moveToFirst()&&!c.isNull(0))confirmed=describe(LocalDatabase.object(c.getString(0)));}String content="本次本地修改\n"+describe(op.optJSONObject("payload"))+"\n\n最后同步版本\n"+confirmed;
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("核对本地与网络内容").setMessage(content+"\n\n订单、付款、发货等操作需返回对应页面核对后重新操作。").setNegativeButton("暂不处理",null).setPositiveButton("重新提交本地修改",(d,w)->resolve(space,op,true)).show();});
    }catch(Exception e){runOnUiThread(()->android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show());}});}
    private String describe(JSONObject data){if(data==null)return "暂无内容";StringBuilder text=new StringBuilder();String[][] fields={{"name","名称"},{"nickname","昵称"},{"username","用户名"},{"recipient","收货人"},{"phone","电话"},{"region","地区"},{"detail","地址"},{"title","标题"},{"content","内容"},{"summary","简介"},{"priceCents","价格（分）"},{"stock","库存"},{"shopName","店铺"},{"description","介绍"},{"status","状态"}};for(String[] field:fields)if(data.has(field[0]))text.append(field[1]).append("：").append(data.optString(field[0])).append('\n');return text.length()==0?"状态或图片修改；请返回对应详情核对":text.toString();}
    private void resolve(String space,JSONObject op,boolean keepLocal){LocalRepository.IO.execute(()->{try{if(!space.equals(new SessionStore(this).localSpaceId()))return;LocalConflictResolver resolver=new LocalConflictResolver(LocalDatabase.get(this));if(keepLocal)resolver.retryWithLocal(space,op.getString("operationId"));else resolver.keepServer(space,op.getString("operationId"));runOnUiThread(()->{refresh();com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).requestSync();});}catch(Exception e){runOnUiThread(()->android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show());}});}
}
