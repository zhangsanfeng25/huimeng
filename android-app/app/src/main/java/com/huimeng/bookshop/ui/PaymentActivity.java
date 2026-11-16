package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.core.sync.SyncCoordinator;
import com.huimeng.bookshop.core.sync.SyncTransport;
import com.huimeng.bookshop.databinding.ActivityPaymentBinding;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {
    private ActivityPaymentBinding binding;
    private String groupId,space,owner,observedToken;
    private boolean canPay,paying,paid,closed,active,reading,successReady,successOpened;
    private String paidCheckoutId="";
    private int generation;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);binding=ActivityPaymentBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        groupId=getIntent().getStringExtra("group_id");if(groupId==null||groupId.isEmpty()){finish();return;}
        SessionStore session=new SessionStore(this);space=session.localSpaceId();owner=session.accountId();
        binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",getIntent().getIntExtra("amount_cents",0)/100.0));binding.order.setText("结算单号  "+OrderPresentation.displayNumber(groupId,"JS"));
        binding.back.setOnClickListener(v->finish());
        binding.confirm.setOnClickListener(v->{if(paid){showSuccess();return;}confirmPayment();});binding.confirm.setEnabled(false);
        observedToken=session.token();
        com.huimeng.bookshop.core.sync.SyncStatusStore.observe(this,value->{
            if(!active||!alive())return;
            String token=new SessionStore(this).token();
            if(!token.equals(observedToken)){
                observedToken=token;generation++;paying=false;reading=false;loadCheckout();
            }
        });
    }
    private boolean alive(){return !isFinishing()&&!isDestroyed()&&space!=null&&space.equals(new SessionStore(this).localSpaceId());}
    private void render(){
        if(!alive())return;
        String label=paid?"查看付款结果":closed?"订单已关闭":
            !new SessionStore(this).authenticated()?"登录并继续付款":"确认付款";
        binding.confirm.setText(paying?"正在确认…":label);
        binding.confirm.setEnabled(!paying&&!closed&&(canPay||paid));
    }
    private boolean accept(int request,String token){
        if(request!=generation||!alive())return false;
        if(token.equals(new SessionStore(this).token()))return true;
        paying=false;reading=false;render();return false;
    }
    private void remoteGet(String path,java.util.function.Consumer<JSONObject> ok){int request=generation;String token=new SessionStore(this).token();new SyncTransport(this).json(0,path,null,result->{if(accept(request,token))ok.accept(result);},failure->{if(accept(request,token))networkError(failure);});}
    private void localGet(String path,java.util.function.Consumer<JSONObject> ok,java.util.function.Consumer<String> fail){int request=generation;String token=new SessionStore(this).token();new ApiClient(this).get(path,result->{if(accept(request,token))ok.accept(result);},message->{if(accept(request,token))fail.accept(message);});}
    private void localPost(String path,JSONObject body,Map<String,String> headers,java.util.function.Consumer<JSONObject> ok,java.util.function.Consumer<String> fail){int request=generation;String token=new SessionStore(this).token();new ApiClient(this).post(path,body,headers,result->{if(accept(request,token))ok.accept(result);},message->{if(accept(request,token))fail.accept(message);});}
    private void loadCheckout(){
        if(!alive()||reading)return;
        reading=true;canPay=false;render();
        localGet("checkout/"+groupId,result->{
            reading=false;
            JSONObject data=result.optJSONObject("data");
            if(data==null){error("结算单不存在");return;}
            binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",data.optInt("totalCents")/100.0));
            binding.order.setText("结算单号  "+OrderPresentation.displayNumber(data.optString("id",groupId),"JS"));
            String state=data.optString("status");
            paid=state.equals("PAID");
            canPay=state.equals("PENDING");
            closed=!canPay&&!paid;
            render();
        },message->{reading=false;error(message);});
    }
    private void login(){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            SessionStore session=new SessionStore(this);String required=session.hasLocalSpace()?new com.huimeng.bookshop.core.local.LocalSpaceStore(com.huimeng.bookshop.core.local.LocalDatabase.get(this)).info(space).optString("userId"):owner;
            runOnUiThread(()->{if(!alive())return;owner=required;startActivity(new Intent(this,LoginActivity.class).putExtra("return_to_payment",true).putExtra("required_account_id",required).putExtra("login_notice","请登录本订单所属账号，完成后返回付款页面。").putExtra("login_account",session.username()));});
        }catch(Exception e){runOnUiThread(()->error("订单账号暂无法读取，请重新打开订单"));}});
    }
    private void confirmPayment(){if(!alive()||paying)return;if(paid){showSuccess();return;}if(!canPay)return;
        generation++;reading=false;SessionStore session=new SessionStore(this);if(!session.authenticated()){login();return;}paying=true;render();if(session.hasLocalSpace()){prepareLocalPayment();return;}if(session.token().isEmpty()){error("本地订单尚未准备好，请重新打开订单后重试。");return;}String token=session.token();
        remoteGet("auth/me",result->{if(!alive()||!token.equals(new SessionStore(this).token()))return;JSONObject user=result.optJSONObject("user");if(user==null||(!owner.isEmpty()&&!owner.equals(user.optString("id")))){error("请登录本订单所属账号后付款");return;}
            localGet("checkout/"+groupId,checkout->{if(!alive())return;JSONObject data=checkout.optJSONObject("data");if(data==null){error("结算单不存在");return;}
                String remote=data.optString("remoteId",groupId);if(remote.isEmpty()||remote.startsWith("local-")){submitPayment(data);return;}
                remoteGet("checkout/"+remote,server->{if(!alive())return;JSONObject confirmed=server.optJSONObject("data");if(confirmed!=null&&confirmed.optString("status").equals("PAID")){showSuccess();return;}if(confirmed==null||!confirmed.optString("status").equals("PENDING")){closed=true;canPay=false;error("服务端结算单已关闭，请重新下单");return;}submitPayment(data);});
            },this::error);
        });
    }
    private void prepareLocalPayment(){
        int request=generation;SessionStore session=new SessionStore(this);String account=session.accountId(),token=session.token();
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{
            try{
                JSONObject info=new com.huimeng.bookshop.core.local.LocalSpaceStore(com.huimeng.bookshop.core.local.LocalDatabase.get(this)).info(space);
                boolean belongs=!account.isEmpty()&&account.equals(info.optString("userId"))&&"USER".equals(info.optString("role"))&&com.huimeng.bookshop.BuildConfig.API_BASE_URL.equals(info.optString("origin"));
                runOnUiThread(()->{
                    if(!accept(request,token))return;
                    if(!belongs||!account.equals(new SessionStore(this).accountId())||!new SessionStore(this).authenticated()){error("当前账号与订单所属账号不一致，请切换回原账号。");return;}
                    localGet("checkout/"+groupId,result->{JSONObject checkout=result.optJSONObject("data");if(checkout==null){error("结算单不存在");return;}submitPayment(checkout);},this::error);
                });
            }catch(Exception e){runOnUiThread(()->{if(accept(request,token))error("订单账号暂无法读取，请重新打开订单。");});}
        });
    }
    private void submitPayment(JSONObject checkout){
        if(checkout.optString("status").equals("PAID")){showSuccess();return;}
        if(!checkout.optString("status").equals("PENDING")){closed=true;canPay=false;error("结算单已关闭，不能继续付款。");return;}
        String method=binding.methods.getCheckedRadioButtonId()==binding.alipay.getId()?"DEMO_ALIPAY":
            binding.methods.getCheckedRadioButtonId()==binding.wechat.getId()?"DEMO_WECHAT":"DEMO_BALANCE";
        try{
            localPost("checkout/"+groupId+"/pay",new JSONObject().put("method",method),
                Map.of("Idempotency-Key","pay-"+groupId),result->showSuccess(),this::error);
        }catch(org.json.JSONException e){error("付款参数错误");}
    }
    private void networkError(SyncTransport.TransportError failure){
        if(!alive())return;
        if(failure.status==401)new SessionStore(this).clearNetworkSession();
        error(failure.status==0?"付款暂未完成，请重试":failure.message);
    }
    private void showSuccess(){
        if(!alive())return;
        paying=false;paid=true;canPay=false;
        if(!active){successReady=true;return;}
        if(successOpened||!paidCheckoutId.isEmpty())return;
        paidCheckoutId=groupId;successReady=false;render();
        clearPurchasedCart();
        openSuccess();
    }
    private void clearPurchasedCart(){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            com.huimeng.bookshop.core.local.LocalDatabase db=
                com.huimeng.bookshop.core.local.LocalDatabase.get(this);
            org.json.JSONArray orders=db.list(space,"buyerOrders");
            java.util.Set<String> purchased=new java.util.HashSet<>();
            for(int i=0;i<orders.length();i++){
                JSONObject order=orders.optJSONObject(i);
                if(order==null||!groupId.equals(order.optString("checkout_group_id")))continue;
                org.json.JSONArray items=order.optJSONArray("items");
                for(int k=0;items!=null&&k<items.length();k++){
                    JSONObject item=items.optJSONObject(k);
                    if(item!=null)purchased.add(item.optString("productId",item.optString("product_id")));
                }
            }
            if(purchased.isEmpty())return;
            db.removePurchasedCartItems(space,purchased);
            LocalCartStore.removePurchased(this,purchased);
        }catch(Exception ignored){}});
    }
    private void openSuccess(){
        if(!alive()||isFinishing()||successOpened)return;
        successOpened=true;
        paidCheckoutId="";
        startActivity(new Intent(this,PaymentSuccessActivity.class).putExtra("group_id",groupId).putExtra("return_product_id",getIntent().getStringExtra("return_product_id")).putExtra("return_to_cart",getIntent().getBooleanExtra("return_to_cart",false)));finish();}
    private void error(String message){if(!alive())return;paying=false;binding.paymentStatus.setText(message);render();Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();active=true;generation++;paying=false;reading=false;if(binding==null||space==null)return;SessionStore session=new SessionStore(this);if(!space.equals(session.localSpaceId())){if(!owner.isEmpty()&&!owner.equals(session.accountId())){finish();return;}space=session.localSpaceId();reading=false;}if(successReady){showSuccess();return;}if(session.hasLocalSpace())SyncCoordinator.get(this).start(space);loadCheckout();}
    @Override protected void onPause(){active=false;generation++;paying=false;reading=false;super.onPause();}
}
