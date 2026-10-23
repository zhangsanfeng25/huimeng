package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
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

/** A local payment intent is never a successful payment without the server's confirmation. */
public class PaymentActivity extends AppCompatActivity {
    private ActivityPaymentBinding binding;
    private String groupId,space,owner;
    private boolean canPay,paying,awaitingConfirmation,paid,closed,active,checking,reading,successReady;
    private long deadline;private int generation;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=this::checkPendingPayment;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);binding=ActivityPaymentBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        groupId=getIntent().getStringExtra("group_id");if(groupId==null||groupId.isEmpty()){finish();return;}
        SessionStore session=new SessionStore(this);space=session.localSpaceId();owner=session.accountId();awaitingConfirmation=state!=null&&state.getBoolean("awaiting_payment",false);
        binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",getIntent().getIntExtra("amount_cents",0)/100.0));binding.order.setText("结算单号  "+groupId);
        binding.back.setOnClickListener(v->finish());binding.confirm.setOnClickListener(v->confirmPayment());binding.confirm.setEnabled(false);
        com.huimeng.bookshop.core.sync.SyncStatusStore.observe(this,value->{if(active&&awaitingConfirmation&&!paying)checkPendingPayment();});
    }
    private boolean alive(){return !isFinishing()&&!isDestroyed()&&space!=null&&space.equals(new SessionStore(this).localSpaceId());}
    private void render(String message){if(!alive())return;binding.paymentStatus.setText(message);binding.loading.setVisibility(paying?View.VISIBLE:View.GONE);
        String label=paid?"查看付款结果":closed?"订单已关闭":new SessionStore(this).token().isEmpty()?"登录并继续付款":awaitingConfirmation?"重新确认付款状态":"确认模拟付款";
        binding.confirm.setText(paying?"正在确认，请稍候…":label);binding.confirm.setEnabled(!paying&&!closed&&(canPay||awaitingConfirmation||paid));
    }
    private boolean accept(int request,String token){if(request!=generation||!alive())return false;if(!token.equals(new SessionStore(this).token())){paying=false;checking=false;reading=false;render("账号登录状态已变化，请重新确认付款。");return false;}return true;}
    private void remoteGet(String path,java.util.function.Consumer<JSONObject> ok){int request=generation;String token=new SessionStore(this).token();new SyncTransport(this).json(0,path,null,result->{if(accept(request,token))ok.accept(result);},failure->{if(accept(request,token))networkError(failure);});}
    private void localGet(String path,java.util.function.Consumer<JSONObject> ok,java.util.function.Consumer<String> fail){int request=generation;String token=new SessionStore(this).token();new ApiClient(this).get(path,result->{if(accept(request,token))ok.accept(result);},message->{if(accept(request,token))fail.accept(message);});}
    private void localPost(String path,JSONObject body,Map<String,String> headers,java.util.function.Consumer<JSONObject> ok,java.util.function.Consumer<String> fail){int request=generation;String token=new SessionStore(this).token();new ApiClient(this).post(path,body,headers,result->{if(accept(request,token))ok.accept(result);},message->{if(accept(request,token))fail.accept(message);});}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("awaiting_payment",awaitingConfirmation);super.onSaveInstanceState(state);}
    private void loadCheckout(){if(!alive()||reading)return;reading=true;localGet("checkout/"+groupId,result->{reading=false;if(!alive())return;JSONObject data=result.optJSONObject("data");if(data==null){error("结算单不存在，请到订单列表核对");return;}
        binding.amount.setText(String.format(Locale.CHINA,"¥%.2f",data.optInt("totalCents")/100.0));String state=data.optString("status");paid=state.equals("PAID");closed=!state.equals("PENDING")&&!paid;awaitingConfirmation=state.equals("PENDING")&&(awaitingConfirmation||data.optString("pendingAction").equals("pay"));canPay=state.equals("PENDING")&&!awaitingConfirmation;
        render(paid?"服务端已确认模拟付款。":closed?"此结算单已关闭，不能继续付款。":new SessionStore(this).token().isEmpty()?"请登录本订单所属账号，登录后返回此页继续付款。":awaitingConfirmation?"正在核对服务端付款结果，请勿重复提交。":"付款将提交到当前连接的商城服务，并以服务端结果为准。");
        if(active&&awaitingConfirmation&&!new SessionStore(this).token().isEmpty())beginConfirmation();
    },message->{reading=false;error(message);});}
    private void login(){
        com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{
            SessionStore session=new SessionStore(this);String required=session.hasLocalSpace()?new com.huimeng.bookshop.core.local.LocalSpaceStore(com.huimeng.bookshop.core.local.LocalDatabase.get(this)).info(space).optString("userId"):owner;
            runOnUiThread(()->{if(!alive())return;owner=required;startActivity(new Intent(this,LoginActivity.class).putExtra("return_to_payment",true).putExtra("required_account_id",required).putExtra("login_notice","请登录本订单所属账号，完成后返回付款页面。").putExtra("login_account",session.username()));});
        }catch(Exception e){runOnUiThread(()->error("订单账号暂无法读取，请到账号与同步状态中核对"));}});
    }
    private void confirmPayment(){if(!alive()||paying)return;if(paid){showSuccess();return;}if(!canPay&&!awaitingConfirmation)return;
        generation++;checking=false;reading=false;handler.removeCallbacks(poll);SessionStore session=new SessionStore(this);if(session.token().isEmpty()){login();return;}paying=true;render("正在验证账号并连接商城服务…");String token=session.token();
        remoteGet("auth/me",result->{if(!alive()||!token.equals(new SessionStore(this).token()))return;JSONObject user=result.optJSONObject("user");if(user==null||(!owner.isEmpty()&&!owner.equals(user.optString("id")))){error("请登录本订单所属账号后付款");return;}
            localGet("checkout/"+groupId,checkout->{if(!alive())return;JSONObject data=checkout.optJSONObject("data");if(data==null){error("结算单不存在");return;}if(data.optBoolean("sample")){error("此订单属于本地示例，不能提交付款。请使用商城中的线上商品重新下单。");return;}
                String remote=data.optString("remoteId",groupId);if(remote.isEmpty()||remote.startsWith("local-")){submitPayment(data);return;}
                remoteGet("checkout/"+remote,server->{if(!alive())return;JSONObject confirmed=server.optJSONObject("data");if(confirmed!=null&&confirmed.optString("status").equals("PAID")){showSuccess();return;}if(confirmed==null||!confirmed.optString("status").equals("PENDING")){closed=true;canPay=false;error("服务端结算单已关闭，请重新下单");return;}submitPayment(data);});
            },this::error);
        });
    }
    private void submitPayment(JSONObject checkout){
        if(awaitingConfirmation||checkout.optString("pendingAction").equals("pay")){beginConfirmation();return;}
        String method=binding.methods.getCheckedRadioButtonId()==binding.alipay.getId()?"DEMO_ALIPAY":binding.methods.getCheckedRadioButtonId()==binding.wechat.getId()?"DEMO_WECHAT":"DEMO_BALANCE";
        try{localPost("checkout/"+groupId+"/pay",new JSONObject().put("method",method),Map.of("Idempotency-Key","pay-"+groupId),result->{if(!alive())return;awaitingConfirmation=true;canPay=false;beginConfirmation();},this::error);}catch(Exception e){error("付款参数错误");}
    }
    private void beginConfirmation(){if(!alive())return;paying=false;awaitingConfirmation=true;deadline=SystemClock.uptimeMillis()+30000;render("正在向服务端确认付款结果，请勿重复提交。");
        SessionStore session=new SessionStore(this);if(session.hasLocalSpace())SyncCoordinator.get(this).start(space);checkPendingPayment();}
    private void checkPendingPayment(){if(!active||!alive()||checking||!awaitingConfirmation)return;if(new SessionStore(this).token().isEmpty()){render("登录已失效，请登录本订单所属账号后继续确认付款。");return;}checking=true;
        localGet("checkout/"+groupId,result->{checking=false;if(!alive())return;JSONObject data=result.optJSONObject("data");if(data==null){error("结算单暂不可用，请到订单列表核对");return;}
            if(data.optString("status").equals("PAID")){showSuccess();return;}String remote=data.optString("remoteId",groupId);if(remote.isEmpty()||remote.startsWith("local-")){schedule();return;}
            checking=true;remoteGet("checkout/"+remote,server->{checking=false;if(!alive())return;JSONObject confirmed=server.optJSONObject("data");if(confirmed!=null&&confirmed.optString("status").equals("PAID")){showSuccess();return;}if(confirmed!=null&&!confirmed.optString("status").equals("PENDING")){closed=true;awaitingConfirmation=false;error("服务端结算单已关闭，请查看订单或重新下单");return;}schedule();});
        },message->{checking=false;error(message);});}
    private void schedule(){if(!active||!alive())return;if(SystemClock.uptimeMillis()>=deadline){render("服务端尚未确认付款。可以重新确认，或在账号与同步状态中查看需处理的订单；不会重复扣款。");return;}handler.removeCallbacks(poll);handler.postDelayed(poll,1200);}
    private void networkError(SyncTransport.TransportError failure){checking=false;if(!alive())return;if(failure.status==401){new SessionStore(this).clearNetworkSession();error("网络登录已失效，请登录本订单所属账号后继续付款。");}else error(failure.status==0?"无法连接商城服务，请确认控制中心已启动且模拟器能访问服务，再点击重试。":failure.message);}
    private void showSuccess(){if(!alive())return;paying=false;paid=true;awaitingConfirmation=false;canPay=false;handler.removeCallbacks(poll);render("服务端已确认模拟付款。");if(!active){successReady=true;return;}successReady=false;LocalCartStore.resolve(this,cart->{},ignored->{});startActivity(new Intent(this,PaymentSuccessActivity.class).putExtra("group_id",groupId).putExtra("return_product_id",getIntent().getStringExtra("return_product_id")).putExtra("return_to_cart",getIntent().getBooleanExtra("return_to_cart",false)));finish();}
    private void error(String message){if(!alive())return;paying=false;handler.removeCallbacks(poll);render(message);}
    @Override protected void onResume(){super.onResume();active=true;generation++;paying=false;checking=false;reading=false;if(binding==null||space==null)return;SessionStore session=new SessionStore(this);if(!space.equals(session.localSpaceId())){if(!owner.isEmpty()&&!owner.equals(session.accountId())){finish();return;}space=session.localSpaceId();reading=false;checking=false;}if(successReady){showSuccess();return;}if(session.hasLocalSpace())SyncCoordinator.get(this).start(space);loadCheckout();}
    @Override protected void onPause(){active=false;generation++;paying=false;checking=false;reading=false;handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
