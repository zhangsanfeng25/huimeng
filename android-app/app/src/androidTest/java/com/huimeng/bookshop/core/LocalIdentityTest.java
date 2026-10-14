package com.huimeng.bookshop.core;
import android.test.AndroidTestCase;
import java.util.Map;
@SuppressWarnings("deprecation")
public class LocalIdentityTest extends AndroidTestCase {
    public void testNoRememberTokenRetainsLocalSpace(){
        android.content.SharedPreferences prefs=getContext().getSharedPreferences("huimeng_session",0);
        Map<String,?> old=prefs.getAll();try{
            prefs.edit().putBoolean("local_ready",true).putString("local_space","isolated-test-space").commit();
            SessionStore session=new SessionStore(getContext());session.save("temporary","USER","local",false,"id","name",false);
            session.clearNetworkSession();assertTrue(session.hasLocalSpace());assertEquals("",session.token());assertEquals("isolated-test-space",session.localSpaceId());
        }finally{android.content.SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:old.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);}edit.commit();}
    }
}
