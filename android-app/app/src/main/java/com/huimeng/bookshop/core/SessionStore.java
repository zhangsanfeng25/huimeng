package com.huimeng.bookshop.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {
    private static volatile String temporaryToken="";
    private final SharedPreferences preferences;
    public SessionStore(Context context){
        preferences=context.getSharedPreferences("huimeng_session",Context.MODE_PRIVATE);
        // Existing installs stored a token without opt-in. Require a fresh login once.
        if(!preferences.getBoolean("remember_choice_initialized",false)){
            preferences.edit().remove("token").putBoolean("remember_choice_initialized",true).apply();
            temporaryToken="";
        }
    }
    public void save(String token,String role,String name,boolean canMerchant,String accountId,String username,boolean remember){
        temporaryToken=remember?"":token;
        preferences.edit().putBoolean("remember_choice_initialized",true).putBoolean("remember_login",remember)
            .putString("token",remember?token:"").putString("role",role).putString("name",name)
            .putBoolean("can_merchant",canMerchant).putString("account_id",accountId).putString("username",username).apply();
    }
    public String token(){return !temporaryToken.isEmpty()?temporaryToken:(remembered()?preferences.getString("token",""):"");}
    public boolean remembered(){return preferences.getBoolean("remember_login",false);}
    public boolean hasRememberedSession(){return remembered()&&!preferences.getString("token","").isEmpty();}
    public String role(){return preferences.getString("role","USER");}
    public String name(){return preferences.getString("name","绘梦用户");}
    public String accountId(){return preferences.getString("account_id","");}
    public String username(){return preferences.getString("username","");}
    public String avatarUrl(){return preferences.getString("avatar_url","");}
    public void setAvatarUrl(String url){preferences.edit().putString("avatar_url",url).apply();}
    public void setAccountId(String id){preferences.edit().putString("account_id",id).apply();}
    public void updateProfile(String username,String name){preferences.edit().putString("username",username).putString("name",name).apply();}
    public boolean canMerchant(){return preferences.getBoolean("can_merchant",false);}
    public void updateAccess(org.json.JSONObject profile){org.json.JSONArray roles=profile.optJSONArray("roles");boolean merchant=false;if(roles!=null){for(int i=0;i<roles.length();i++)if("MERCHANT".equals(roles.optString(i)))merchant=true;preferences.edit().putBoolean("can_merchant",merchant).apply();}if(profile.has("activeRole"))setRole(profile.optString("activeRole","USER"));}
    public void setRole(String role){preferences.edit().putString("role",role).apply();}
    public void clear(){temporaryToken="";preferences.edit().clear().putBoolean("remember_choice_initialized",true).apply();}
}
