package com.huimeng.bookshop.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.huimeng.bookshop.BuildConfig;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Foreground-only account event subscription with reconnection and polling fallback. */
public final class AccountSync {
    private final Context context;private final Runnable refresh;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean running,connected;private volatile int generation;
    private volatile HttpURLConnection connection;private volatile long lastEvent;
    private final java.util.Set<String> resources;
    private final Runnable fallback=new Runnable(){public void run(){if(!running)return;if(!connected||System.currentTimeMillis()-lastEvent>25000)refresh.run();main.postDelayed(this,5000);}};
    public AccountSync(Context context,Runnable refresh){this(context,refresh,"users","merchants");}
    public AccountSync(Context context,Runnable refresh,String... resources){this.context=context.getApplicationContext();this.refresh=refresh;this.resources=new java.util.HashSet<>(java.util.Arrays.asList(resources));}
    public void start(){if(running)return;running=true;int id=++generation;refresh.run();main.postDelayed(fallback,5000);Thread worker=new Thread(()->listen(id),"huimeng-account-events");worker.setDaemon(true);worker.start();}
    public void stop(){running=false;++generation;connected=false;main.removeCallbacksAndMessages(null);HttpURLConnection old=connection;connection=null;if(old!=null)new Thread(old::disconnect,"huimeng-events-close").start();}
    private void signal(int id){main.post(()->{if(running&&id==generation)refresh.run();});}
    private void listen(int id){
        while(running&&id==generation){HttpURLConnection stream=null;try{
            String token=new SessionStore(context).token();if(token.isEmpty())return;
            stream=(HttpURLConnection)new URL(BuildConfig.API_BASE_URL+"me/events").openConnection();if(!running||id!=generation){stream.disconnect();return;}connection=stream;
            stream.setConnectTimeout(5000);stream.setReadTimeout(25000);stream.setRequestProperty("Authorization","Bearer "+token);stream.setRequestProperty("Accept","text/event-stream");
            if(stream.getResponseCode()!=200)throw new java.io.IOException("event stream unavailable");connected=true;lastEvent=System.currentTimeMillis();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream.getInputStream(),StandardCharsets.UTF_8))){String line,event="";
                while(running&&id==generation&&(line=reader.readLine())!=null){
                    if(line.startsWith("event:"))event=line.substring(6).trim();
                    if(line.startsWith("data:")){lastEvent=System.currentTimeMillis();if("ready".equals(event))signal(id);else if("update".equals(event)){String resource=new JSONObject(line.substring(5).trim()).optString("resource");if(resources.contains(resource))signal(id);}}
                }
            }
        }catch(Exception ignored){if(running&&id==generation)signal(id);}finally{if(id==generation)connected=false;if(stream!=null)stream.disconnect();if(connection==stream)connection=null;}
            if(running&&id==generation)try{Thread.sleep(5000);}catch(InterruptedException ignored){return;}
        }
    }
}
