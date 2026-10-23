package com.huimeng.bookshop.core;

import android.test.AndroidTestCase;
import com.huimeng.bookshop.core.local.LocalDatabase;
import com.huimeng.bookshop.core.local.LocalSpaceStore;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs only in the isolated QA application; credentials and databases are fixtures. */
@SuppressWarnings("deprecation")
public class AccountLoginTest extends AndroidTestCase {
    private SessionStore session;
    @Override protected void setUp()throws Exception{super.setUp();session=new SessionStore(getContext());session.clear();getContext().getSharedPreferences("huimeng_offline_accounts",0).edit().clear().commit();}
    @Override protected void tearDown()throws Exception{session.clear();getContext().getSharedPreferences("huimeng_offline_accounts",0).edit().clear().commit();super.tearDown();}
    private JSONObject user()throws Exception{return new JSONObject().put("id","qa-user").put("username","qa-reader").put("name","本机读者").put("roles",new JSONArray().put("USER"));}
    public void testServerRevocationClearsOfflinePassword()throws Exception{
        OfflineAccounts accounts=new OfflineAccounts(getContext());String origin=com.huimeng.bookshop.BuildConfig.API_BASE_URL;
        accounts.save(origin,"qa-reader","old-password",new JSONObject().put("token","qa-token").put("user",user()));
        session.save("qa-token","USER","本机读者",false,"qa-user","qa-reader",true);session.invalidateNetworkCredentials(getContext());
        java.util.concurrent.CountDownLatch barrier=new java.util.concurrent.CountDownLatch(1);com.huimeng.bookshop.core.local.LocalRepository.IO.execute(barrier::countDown);assertTrue(barrier.await(5,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("",session.token());assertNull(accounts.login(origin,"qa-reader","old-password","USER"));
    }
    public void testLogoutClearsIdentityAndLocalSelection(){session.save("qa-token","USER","本机读者",false,"qa-user","qa-reader",true);session.localSpace("qa-space");session.clear();assertFalse(session.authenticated());assertFalse(session.hasLocalSpace());assertEquals("",session.accountId());assertEquals("",session.username());}
    public void testGuestCartNeverBecomesAnAccount(){session.localSpace("qa-guest");boolean[] resolved={false};LocalCartStore.resolve(getContext(),cart->resolved[0]=true,error->fail(error));assertTrue(resolved[0]);assertEquals("",session.accountId());assertFalse(session.authenticated());}
    public void testOfflinePasswordAndOriginIsolation()throws Exception{
        OfflineAccounts accounts=new OfflineAccounts(getContext());accounts.save("https://qa.invalid/api/v1/","qa-alias","fixture-password",new JSONObject().put("token","qa-token").put("expiresAt","2999-01-01T00:00:00Z").put("user",user()));
        assertEquals("qa-user",accounts.login("https://qa.invalid/api/v1/","qa-alias","fixture-password","USER").getJSONObject("user").getString("id"));
        assertEquals("qa-user",accounts.login("https://qa.invalid/api/v1/","qa-reader","fixture-password","USER").getJSONObject("user").getString("id"));
        assertNull(accounts.login("https://other.invalid/api/v1/","qa-reader","fixture-password","USER"));
        try{accounts.login("https://qa.invalid/api/v1/","qa-reader","wrong-password","USER");fail("A matching username must not bypass the password");}catch(IllegalArgumentException expected){}
        try{accounts.login("https://qa.invalid/api/v1/","qa-reader","fixture-password","MERCHANT");fail("Cached buyer must not become a merchant");}catch(IllegalArgumentException expected){}
        for(Object value:getContext().getSharedPreferences("huimeng_offline_accounts",0).getAll().values()){assertFalse(value.toString().contains("fixture-password"));assertFalse(value.toString().contains("qa-token"));}
    }
    public void testGuestSpaceDoesNotReusePrivateAccount()throws Exception{
        String name="guest-qa-"+java.util.UUID.randomUUID()+".db";LocalDatabase db=new LocalDatabase(getContext(),name);
        try{LocalSpaceStore spaces=new LocalSpaceStore(db);String account=spaces.forAccount("qa-origin","qa-user");spaces.select(account);db.saveConfirmed(account,"addresses","private",new JSONObject().put("recipient","private"),1);String guest=spaces.guestSpace();assertFalse(account.equals(guest));assertEquals(guest,spaces.guestSpace());assertNull(db.entity(guest,"addresses","private"));assertNotNull(db.entity(account,"addresses","private"));}finally{db.close();getContext().deleteDatabase(name);}
    }
    public void testVerifiedPasswordChangeRefreshesEveryAlias()throws Exception{
        OfflineAccounts accounts=new OfflineAccounts(getContext());String origin="https://qa.invalid/api/v1/";
        JSONObject result=new JSONObject().put("token","qa-token").put("expiresAt","2999-01-01T00:00:00Z").put("user",user());
        accounts.save(origin,"qa-email@example.invalid","old-password",result);accounts.save(origin,"qa-reader","new-password",result);
        assertNotNull(accounts.login(origin,"qa-email@example.invalid","new-password","USER"));
        try{accounts.login(origin,"qa-email@example.invalid","old-password","USER");fail("Old alias must not retain the old password");}catch(IllegalArgumentException expected){}
        accounts.forgetAccount(origin,"qa-user");assertNull(accounts.login(origin,"qa-reader","new-password","USER"));assertNull(accounts.login(origin,"qa-email@example.invalid","new-password","USER"));
    }
}
