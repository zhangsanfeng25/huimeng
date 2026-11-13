package com.huimeng.bookshop.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only server-verified accounts can be cached. Passwords are never stored. */
public final class OfflineAccounts {
    private static final String KEY="huimeng.offline-accounts.v1";
    private static final int ITERATIONS=120000;
    private final SharedPreferences preferences;
    public OfflineAccounts(Context context){preferences=context.getSharedPreferences("huimeng_offline_accounts",0);}
    private String accountKey(String origin,String account)throws Exception{return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest((origin+"\u0000"+account).getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private static byte[] verifier(String password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private static synchronized SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private static String encrypt(JSONObject record)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(record.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    private static JSONObject decrypt(String value)throws Exception{
        String[] parts=value.split(":",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    private static boolean matchesPassword(JSONObject record,String password)throws Exception{
        byte[] salt=Base64.decode(record.getString("salt"),Base64.NO_WRAP);
        byte[] expected=Base64.decode(record.getString("verifier"),Base64.NO_WRAP);
        return MessageDigest.isEqual(expected,verifier(password,salt));
    }
    private static boolean allows(JSONArray roles,String role){
        if(roles!=null)for(int i=0;i<roles.length();i++)if(role.equals(roles.optString(i)))return true;
        return false;
    }
    private static JSONObject verifiedResults(JSONObject record)throws Exception{
        JSONObject results=record.optJSONObject("results");
        if(results!=null)return results;
        JSONObject previous=record.getJSONObject("result");
        return new JSONObject().put(previous.getJSONObject("user").optString("activeRole","USER"),previous);
    }
    public void save(String origin,String account,String password,JSONObject result)throws Exception{
        JSONObject user=result.getJSONObject("user");if(user.optString("id").isEmpty()||result.optString("token").isEmpty())throw new IllegalArgumentException("请先连接服务器验证账号");
        String activeRole=user.optString("activeRole","USER");
        JSONObject results=new JSONObject();
        JSONArray roles=user.optJSONArray("roles");
        java.util.Set<String> aliases=new java.util.HashSet<>(), scanned=new java.util.HashSet<>();
        aliases.add(accountKey(origin,account));
        aliases.add(accountKey(origin,user.getString("username")));
        for(java.util.Map.Entry<String,?> entry:preferences.getAll().entrySet())try{
            String cached=String.valueOf(entry.getValue());
            JSONObject previous=decrypt(cached);
            if(!origin.equals(previous.optString("origin"))||!user.getString("id").equals(previous.getJSONObject("result").getJSONObject("user").optString("id")))continue;
            aliases.add(entry.getKey());
            if(!scanned.add(cached)||!matchesPassword(previous,password))continue;
            JSONObject grants=verifiedResults(previous);
            for(String role:new String[]{"USER","MERCHANT"}){
                JSONObject grant=grants.optJSONObject(role);
                if(grant!=null&&allows(roles,role))results.put(role,grant);
            }
        }catch(Exception ignored){ /* a damaged record cannot authenticate */ }
        results.put(activeRole,result);
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        JSONObject record=new JSONObject().put("origin",origin).put("salt",Base64.encodeToString(salt,Base64.NO_WRAP))
            .put("verifier",Base64.encodeToString(verifier(password,salt),Base64.NO_WRAP)).put("result",result).put("results",results);
        String encrypted=encrypt(record);SharedPreferences.Editor edit=preferences.edit();
        for(String alias:aliases)edit.putString(alias,encrypted);
        if(!edit.commit())throw new IllegalStateException("本机登录记录保存失败");
    }
    public void forgetAccount(String origin,String id)throws Exception{
        SharedPreferences.Editor edit=preferences.edit();
        for(java.util.Map.Entry<String,?> entry:preferences.getAll().entrySet())try{
            JSONObject record=decrypt(String.valueOf(entry.getValue()));
            if(origin.equals(record.optString("origin"))&&id.equals(record.getJSONObject("result").getJSONObject("user").optString("id")))edit.remove(entry.getKey());
        }catch(Exception ignored){ /* a damaged record cannot authenticate */ }
        if(!edit.commit())throw new IllegalStateException("本机登录记录更新失败");
    }
    public void forgetAlias(String origin,String account)throws Exception{
        String cached=preferences.getString(accountKey(origin,account),"");if(cached.isEmpty())return;
        JSONObject record=decrypt(cached);forgetAccount(origin,record.getJSONObject("result").getJSONObject("user").getString("id"));
    }
    public JSONObject login(String origin,String account,String password,String role)throws Exception{
        String encrypted=preferences.getString(accountKey(origin,account),"");if(encrypted.isEmpty())return null;
        JSONObject record=decrypt(encrypted);
        if(!matchesPassword(record,password))throw new IllegalArgumentException("账号或密码错误");
        JSONObject latest=record.getJSONObject("result"),user=latest.getJSONObject("user");
        if(!allows(user.optJSONArray("roles"),role))throw new IllegalArgumentException("此账号没有所选角色，请选择正确的登录身份");
        JSONObject verified=verifiedResults(record).optJSONObject(role);
        if("MERCHANT".equals(role)&&verified==null)throw new IllegalArgumentException("首次以商家身份登录需要连接服务器");
        JSONObject result=new JSONObject((verified==null?latest:verified).toString());
        String expires=result.optString("expiresAt");
        if(verified==null||expires.isEmpty()||!Instant.parse(expires).isAfter(Instant.now()))result.put("token","");
        result.put("user",new JSONObject(user.toString()).put("activeRole",role));
        return result.put("offline",true);
    }
}
