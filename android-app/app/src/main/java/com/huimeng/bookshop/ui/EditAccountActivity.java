package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityEditAccountBinding;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

public final class EditAccountActivity extends AppCompatActivity{
    private ActivityEditAccountBinding binding;private SessionStore session;
    private ActivityResultLauncher<String> picker;private ActivityResultLauncher<Intent> cropper;
    private int avatarRevision;private boolean avatarBusy,saving;
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        picker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{if(uri!=null){Intent intent=new Intent(this,CropImageActivity.class);intent.setData(uri);intent.putExtra("square_crop",true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);cropper.launch(intent);}});
        cropper=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{if(result.getResultCode()==Activity.RESULT_OK&&result.getData()!=null){String path=result.getData().getStringExtra("cropped_path");if(path!=null)uploadAvatar(path);}});
        binding=ActivityEditAccountBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());session=new SessionStore(this);
        binding.username.setText(session.username());binding.nickname.setText(session.name());buildDefaultAvatars();showAvatar(session.avatarUrl());
        final String requestedAccount=session.accountId(),requestedToken=session.token();
        new ApiClient(this).get("auth/me",result->{if(isFinishing()||isDestroyed()||!requestedToken.equals(session.token())||!requestedAccount.equals(session.accountId()))return;
            JSONObject profile=result.optJSONObject("user");if(profile!=null){String username=profile.optString("username"),name=profile.optString("name"),avatar=profile.optString("avatarUrl");
            if(!binding.username.hasFocus())binding.username.setText(username);if(!binding.nickname.hasFocus())binding.nickname.setText(name);
            session.updateProfile(username,name);if(avatarRevision==0){session.setAvatarUrl(avatar);showAvatar(avatar);}
        }},error->{if(!isFinishing()&&!isDestroyed())toast(error);});
        binding.back.setOnClickListener(v->finish());binding.saveAccount.setOnClickListener(v->save());
        binding.avatar.setOnClickListener(v->{if(!avatarBusy){binding.defaultAvatarGrid.requestRectangleOnScreen(new android.graphics.Rect(0,0,binding.defaultAvatarGrid.getWidth(),binding.defaultAvatarGrid.getHeight()),false);setAvatarStatus("选择下方默认头像，或从图库上传");}});
        binding.chooseAvatar.setOnClickListener(v->{if(!avatarBusy)picker.launch("image/*");});
    }
    private void buildDefaultAvatars(){
        for(int i=0;i<DefaultAvatars.OPTIONS.length;i++){
            DefaultAvatars.Option option=DefaultAvatars.OPTIONS[i];
            MaterialCardView card=new MaterialCardView(this);card.setTag(option.url());card.setRadius(dp(16));card.setCardElevation(0);
            card.setCardBackgroundColor(ContextCompat.getColor(this,R.color.surface_card));
            GridLayout.LayoutParams params=new GridLayout.LayoutParams(GridLayout.spec(i/3),GridLayout.spec(i%3,1f));
            params.width=0;params.height=GridLayout.LayoutParams.WRAP_CONTENT;params.setMargins(dp(3),dp(5),dp(3),dp(5));card.setLayoutParams(params);
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER);content.setPadding(dp(4),dp(12),dp(4),dp(10));
            AvatarImageView image=new AvatarImageView(this);content.addView(image,new LinearLayout.LayoutParams(dp(60),dp(60)));DefaultAvatars.show(image,option.url());
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);card.addView(content);card.setClickable(true);card.setFocusable(true);
            card.setOnClickListener(v->chooseDefaultAvatar(option.url()));binding.defaultAvatarGrid.addView(card);
        }
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void showAvatar(String url){
        DefaultAvatars.show(binding.avatar,url);
        for(int i=0;i<binding.defaultAvatarGrid.getChildCount();i++){
            MaterialCardView card=(MaterialCardView)binding.defaultAvatarGrid.getChildAt(i);boolean selected=card.getTag().equals(url);card.setSelected(selected);
            card.setStrokeWidth(dp(selected?2:1));card.setStrokeColor(ContextCompat.getColor(this,selected?R.color.cool_green:R.color.line_soft));
            card.setContentDescription(DefaultAvatars.OPTIONS[i].name()+(selected?"，当前头像":"，点击使用"));
        }
    }
    private void setAvatarBusy(boolean value){avatarBusy=value;binding.chooseAvatar.setEnabled(!value);binding.avatar.setEnabled(!value);for(int i=0;i<binding.defaultAvatarGrid.getChildCount();i++)binding.defaultAvatarGrid.getChildAt(i).setEnabled(!value);}
    private void chooseDefaultAvatar(String url){
        if(avatarBusy||saving)return;if(url.equals(session.avatarUrl())){binding.avatarStatus.setText("正在使用此默认头像");return;}
        avatarRevision++;setAvatarBusy(true);binding.avatarStatus.setText("正在保存默认头像…");saveAvatar(url,null,session.token());
    }
    private void saveAvatar(String url,File temporary,String requestedToken){
        try{new ApiClient(this).put("auth/me/avatar",new JSONObject().put("avatarUrl",url),saved->{
            if(isFinishing()||isDestroyed()||!requestedToken.equals(session.token()))return;
            String stored=saved.optString("avatarUrl",url);session.setAvatarUrl(stored);showAvatar(stored);setAvatarBusy(false);binding.avatarStatus.setText(saved.optBoolean("pending")?"头像已保存在本地，联网后同步":"头像已更新，网页将同步显示");if(temporary!=null)temporary.delete();
        },error->{if(requestedToken.equals(session.token()))avatarFailed(error);});}catch(Exception error){avatarFailed("保存头像失败");}
    }
    private void uploadAvatar(String path){File file=new File(path);final String requestedToken=session.token();avatarRevision++;setAvatarBusy(true);binding.avatarStatus.setText("正在上传头像…");
        try{byte[] bytes=Files.readAllBytes(file.toPath());new ApiClient(this).uploadJpeg(bytes,result->{if(isFinishing()||isDestroyed()||!requestedToken.equals(session.token()))return;String url=result.optString("url");if(url.isEmpty()){avatarFailed("头像上传失败");return;}
            saveAvatar(url,file,requestedToken);
        },error->{if(requestedToken.equals(session.token()))avatarFailed(error);});}catch(Exception error){avatarFailed("无法读取裁剪后的图片");}
    }
    private void avatarFailed(String message){if(isFinishing()||isDestroyed())return;setAvatarBusy(false);binding.avatarStatus.setText("头像未更改，请重新选择");toast(message);}
    private void setAvatarStatus(String message){binding.avatarStatus.setVisibility(View.VISIBLE);binding.avatarStatus.setText(message);}
    private void save(){
        if(saving||avatarBusy)return;
        String username=String.valueOf(binding.username.getText()).trim(),nickname=String.valueOf(binding.nickname.getText()).trim();
        String current=String.valueOf(binding.currentPassword.getText()),next=String.valueOf(binding.newPassword.getText()),confirm=String.valueOf(binding.confirmPassword.getText());
        if(!username.matches("[A-Za-z0-9_]{3,32}")||nickname.isEmpty()||nickname.length()>30){toast("用户名需为 3 至 32 位字母、数字或下划线，昵称需为 1 至 30 字");return;}
        boolean passwordChanged=!current.isEmpty()||!next.isEmpty()||!confirm.isEmpty();
        if(passwordChanged&&(current.isEmpty()||next.length()<8||next.length()>64)){toast("请输入当前密码和 8 至 64 位新密码");return;}
        if(passwordChanged&&!next.equals(confirm)){toast("两次输入的新密码不一致");return;}
        final String requestedToken=session.token();saving=true;binding.saveAccount.setEnabled(false);binding.saveAccount.setText("正在保存…");
        try{
            JSONObject value=new JSONObject().put("username",username).put("nickname",nickname);
            if(passwordChanged)value.put("currentPassword",current).put("newPassword",next);
            new ApiClient(this).put("auth/me",value,result->{
                if(isFinishing()||isDestroyed()||!requestedToken.equals(session.token()))return;
                session.updateProfile(username,nickname);
                if(result.optBoolean("passwordChanged")){
                    String changedAccount=session.accountId();com.huimeng.bookshop.core.local.LocalRepository.IO.execute(()->{try{new com.huimeng.bookshop.core.OfflineAccounts(this).forgetAccount(com.huimeng.bookshop.BuildConfig.API_BASE_URL,changedAccount);}catch(Exception e){android.util.Log.w("HuiMengLogin","Offline login cache invalidation failed");}});
                    com.huimeng.bookshop.core.sync.SyncCoordinator.get(this).stop();
                    session.clear();toast("修改成功，请重新登录");startActivity(new Intent(this,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK));
                }else{restoreSave();toast("账户信息已保存");}
            },error->{if(!isFinishing()&&!isDestroyed()&&requestedToken.equals(session.token())){restoreSave();toast(error);}});
        }catch(Exception error){restoreSave();toast("保存失败，请重试");}
    }
    private void restoreSave(){saving=false;binding.saveAccount.setEnabled(true);binding.saveAccount.setText("保存修改");}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
