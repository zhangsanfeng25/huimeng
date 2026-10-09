package com.huimeng.bookshop.ui;

import android.content.Context;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;
import com.huimeng.bookshop.HuiMengApp;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.SessionStore;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device tests use a local Volley network fixture, never the business API/database. */
@SuppressWarnings("deprecation")
public class RefreshNavigationTest extends InstrumentationTestCase {
    protected MainActivity activity;
    private RequestQueue fixtureQueue, previousQueue;
    private SharedPreferences preferences;
    private Map<String, ?> previousPreferences;
    private String previousTemporaryToken;
    private final Map<String,Map<String,?>> auxiliaryPreferences=new HashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger nonNoticeCalls = new AtomicInteger();
    private final AtomicInteger logisticsVersion = new AtomicInteger();
    private volatile String lastCatalog="";
    private final AtomicInteger addedQuantity=new AtomicInteger();
    private final AtomicInteger communityReads=new AtomicInteger(),merchantReads=new AtomicInteger();
    private volatile String editedDetail="测试地址",addressWriteMethod="";
    private volatile boolean extraAddress,populatedCart;
    private volatile JSONObject savedAccount;
    private final AtomicInteger accountWrites=new AtomicInteger();
    private volatile String savedAvatar="/public/default-avatars/sprout.png";

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        for(String name:new String[]{"huimeng_appearance","huimeng_search","huimeng_cart"})auxiliaryPreferences.put(name,new HashMap<>(context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll()));
        String visualTheme=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("visualTheme","LIGHT");
        context.getSharedPreferences("huimeng_appearance",Context.MODE_PRIVATE).edit().putString("theme",visualTheme).putString("wallpaper","DEFAULT").commit();
        getInstrumentation().runOnMainSync(()->new com.huimeng.bookshop.core.AppearanceStore(context).apply());
        preferences = context.getSharedPreferences("huimeng_session", Context.MODE_PRIVATE);
        previousPreferences = new HashMap<>(preferences.getAll());
        Field token = SessionStore.class.getDeclaredField("temporaryToken"); token.setAccessible(true);
        previousTemporaryToken = (String)token.get(null);
        new SessionStore(context);token.set(null,"device-test");
        preferences.edit().putString("role","USER").putString("account_id","device-test").commit();
        HuiMengApp app = (HuiMengApp)context.getApplicationContext();
        Field requests = HuiMengApp.class.getDeclaredField("requestQueue"); requests.setAccessible(true);
        previousQueue = (RequestQueue)requests.get(app);
        fixtureQueue = new RequestQueue(new NoCache(), request -> {
            calls.incrementAndGet();
            if(!request.getUrl().endsWith("me/notifications"))nonNoticeCalls.incrementAndGet();
            if(request.getUrl().endsWith("auth/me")&&request.getMethod()==com.android.volley.Request.Method.PUT)try{savedAccount=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8));accountWrites.incrementAndGet();}catch(Exception error){throw new RuntimeException(error);}
            if(request.getUrl().endsWith("auth/me/avatar"))try{savedAvatar=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8)).getString("avatarUrl");}catch(Exception error){throw new RuntimeException(error);}
            if(request.getUrl().endsWith("community/posts"))communityReads.incrementAndGet();
            if(request.getUrl().endsWith("merchant/products"))merchantReads.incrementAndGet();
            if(request.getUrl().contains("me/addresses")&&(request.getMethod()==com.android.volley.Request.Method.PUT||request.getMethod()==com.android.volley.Request.Method.POST))try{JSONObject value=new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8));addressWriteMethod=request.getMethod()==com.android.volley.Request.Method.PUT?"PUT":"POST";if("PUT".equals(addressWriteMethod))editedDetail=value.optString("detail");else extraAddress=true;}catch(Exception error){throw new RuntimeException(error);}
            if(request.getUrl().contains("catalog/products?"))lastCatalog=request.getUrl();
            if(request.getUrl().contains("q=offline"))throw new com.android.volley.ClientError(new NetworkResponse(400,"{\"message\":\"测试搜索失败\"}".getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList()));
            if(request.getUrl().contains("merchant/media/uploads")){SystemClock.sleep(4000);return new NetworkResponse(200,"{\"url\":\"/public/community-12345678.jpg\"}".getBytes(StandardCharsets.UTF_8),false,0,Collections.emptyList());}
            if(request.getUrl().contains("me/cart/book-")&&request.getMethod()==com.android.volley.Request.Method.PUT)try{addedQuantity.set(new JSONObject(new String(request.getBody(),StandardCharsets.UTF_8)).optInt("quantity"));}catch(Exception error){throw new RuntimeException(error);}
            String body = fixture(request);
            return new NetworkResponse(200, body.getBytes(StandardCharsets.UTF_8), false, 0, Collections.emptyList());
        });
        requests.set(app, fixtureQueue); fixtureQueue.start();
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (MainActivity)getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(300);
    }

    protected String fixture(com.android.volley.Request<?> request){return fixture(request.getUrl());}
    protected String fixture(String url) {
        if(url.endsWith("auth/me/avatar"))return "{\"avatarUrl\":\""+savedAvatar+"\"}";
        if(url.endsWith("auth/me"))return "{\"user\":{\"username\":\"reader_test\",\"name\":\"测试读者\",\"avatarUrl\":\""+savedAvatar+"\"}}";
        if(url.endsWith("me/cart"))return populatedCart?"{\"data\":[{\"id\":\"book-0\",\"name\":\"一套适合亲子共读的长标题绘本：和小熊一起认识森林中的朋友\",\"quantity\":2,\"selected\":1,\"stock\":20,\"priceCents\":2800}]}":"{\"data\":[]}";
        if(url.contains("me/cart/book-"))return "{\"ok\":true}";
        if(url.endsWith("me/addresses"))return "{\"id\":\"address-2\",\"data\":[{\"id\":\"address-1\",\"recipient\":\"测试收货人\",\"phone\":\"13800000000\",\"region\":\"辽宁省\",\"detail\":\""+editedDetail+"\",\"is_default\":1}"+(extraAddress?",{\"id\":\"address-2\",\"recipient\":\"新增收货人\",\"phone\":\"13800000001\",\"region\":\"辽宁省\",\"detail\":\"新增地址\",\"is_default\":0}":"")+"]}";
        if(url.contains("me/addresses/"))return "{\"ok\":true}";
        if(url.contains("catalog/products/book-"))return "{\"data\":{\"id\":\"book-0\",\"name\":\"晚安，小熊\",\"shop\":\"测试书店\",\"ageRange\":\"3–6岁\",\"priceCents\":2800,\"stock\":20,\"ratings\":[]}}";
        if(url.contains("me/orders/order-test"))return "{\"data\":{\"id\":\"order-test\",\"shop\":\"测试书店\",\"status\":\"SHIPPED\",\"logistics_status\":\""+(logisticsVersion.get()==0?"SHIPPED":"IN_TRANSIT")+"\",\"total_cents\":2800,\"items\":[{\"id\":\"item-test\",\"productName\":\"晚安，小熊\",\"quantity\":1,\"subtotalCents\":2800,\"unitPriceCents\":2800}],\"events\":[]}}";
        String post="{\"id\":\"post-test\",\"title\":\"今晚的亲子共读时光\",\"content\":\"今晚的亲子共读时光，记录一段温暖、真实的阅读体验。\",\"authorId\":\"reader-test\",\"authorName\":\"星星妈妈\",\"authorRole\":\"USER\",\"avatarUrl\":\"\",\"likeCount\":2,\"commentCount\":0}";
        if(url.contains("community/users/"))return "{\"data\":{\"user\":{\"id\":\"reader-test\",\"nickname\":\"星星妈妈\",\"avatarUrl\":\"\"},\"postCount\":1,\"likeCount\":2,\"posts\":["+post+"]}}";
        if(url.contains("community/posts/post-test"))return "{\"data\":"+post.substring(0,post.length()-1)+",\"comments\":[]}}";
        if(url.endsWith("community/posts"))return "{\"data\":["+post+"]}";
        if(url.contains("categories"))return "{\"data\":[{\"id\":\"\",\"name\":\"精选\"},{\"id\":\"age3\",\"name\":\"3–6岁启蒙\"}]}";
        if(url.contains("catalog/products"))try {
            JSONArray products=new JSONArray();
            for(int i=0;i<12;i++)products.put(new JSONObject().put("id","book-"+i).put("name",i%2==0?"晚安，小熊":"森林里的邮递员").put("category","精选").put("ageRange","3–6岁").put("priceCents",2800).put("stock",20));
            return new JSONObject().put("data",products).put("total",24).put("hasMore",true).toString();
        }catch(Exception ignored){}
        return "{\"data\":[]}";
    }
    private void screenshot(String name) throws Exception {
        getInstrumentation().waitForIdleSync();SystemClock.sleep(800);
        File file=new File(activity.getExternalFilesDir(null),name+".png");
        android.graphics.Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();
        try(FileOutputStream output=new FileOutputStream(file)){
            if(image!=null){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);image.recycle();}
            else try(android.os.ParcelFileDescriptor descriptor=getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
                byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
            }
        }
        assertTrue("设备截图必须非空",file.length()>100);
    }
    public void testDefaultAvatarSelectionSavesAndKeepsRoundEdges() throws Throwable {
        Activity edit=getInstrumentation().startActivitySync(new Intent(activity,EditAccountActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        int gridId=activity.getResources().getIdentifier("defaultAvatarGrid","id",activity.getPackageName());
        assertTrue("编辑账户应有默认头像选择区",gridId!=0);
        android.widget.GridLayout grid=edit.findViewById(gridId);assertNotNull(grid);assertEquals(5,grid.getChildCount());
        String[] names={"sprout","whale","house","fox","cat"};
        for(int i=0;i<names.length;i++){
            final int choice=i;runTestOnUiThread(()->grid.getChildAt(choice).performClick());
            getInstrumentation().waitForIdleSync();SystemClock.sleep(200);
            assertEquals("/public/default-avatars/"+names[i]+".png",new SessionStore(edit).avatarUrl());
            runTestOnUiThread(()->assertTrue(grid.getChildAt(choice).isSelected()));
        }
        runTestOnUiThread(()->{
            View avatar=edit.findViewById(R.id.avatar);assertEquals(avatar.getWidth(),avatar.getHeight());assertNull("不得保留方形背景",avatar.getBackground());
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(avatar.getWidth(),avatar.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);avatar.draw(new android.graphics.Canvas(bitmap));
            assertEquals("圆形外侧四角必须透明",0,android.graphics.Color.alpha(bitmap.getPixel(0,0)));
            assertTrue("头像主体必须可见",android.graphics.Color.alpha(bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2))>0);bitmap.recycle();
            for(int i=0;i<grid.getChildCount();i++){android.widget.LinearLayout content=(android.widget.LinearLayout)((com.google.android.material.card.MaterialCardView)grid.getChildAt(i)).getChildAt(0);assertEquals("头像下方不再显示名称",1,content.getChildCount());assertTrue("选项不得超出选择区",grid.getChildAt(i).getRight()<=grid.getWidth());}
        });
        screenshot("default-avatar-selection");runTestOnUiThread(edit::finish);
        getInstrumentation().waitForIdleSync();
    }
    public void testAccountHasOneSaveAndOptionalPassword() throws Throwable {
        Activity edit=getInstrumentation().startActivitySync(new Intent(activity,EditAccountActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        int oldButton=edit.getResources().getIdentifier("changePassword","id",edit.getPackageName());
        assertNull("账户页只能保留一个提交按钮",edit.findViewById(oldButton));
        assertNotNull("可选密码区",edit.findViewById(R.id.currentPassword));
        assertNotNull(edit.findViewById(R.id.newPassword));
        runTestOnUiThread(()->{((android.widget.TextView)edit.findViewById(R.id.username)).setText("reader_test");((android.widget.TextView)edit.findViewById(R.id.nickname)).setText("修改昵称");edit.findViewById(R.id.saveAccount).performClick();edit.findViewById(R.id.saveAccount).performClick();});
        long deadline=SystemClock.uptimeMillis()+3000;while(accountWrites.get()<1&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(30);
        assertEquals("重复点击只提交一次",1,accountWrites.get());assertFalse(savedAccount.has("newPassword"));assertEquals("修改昵称",savedAccount.optString("nickname"));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(100);
        runTestOnUiThread(()->{((android.widget.TextView)edit.findViewById(R.id.newPassword)).setText("updated123");edit.findViewById(R.id.saveAccount).performClick();});
        getInstrumentation().waitForIdleSync();assertEquals("缺少旧密码不得提交",1,accountWrites.get());
        runTestOnUiThread(()->{((android.widget.TextView)edit.findViewById(R.id.newPassword)).setText("");((android.widget.ScrollView)((android.view.ViewGroup)edit.findViewById(android.R.id.content)).getChildAt(0)).fullScroll(View.FOCUS_DOWN);});
        SystemClock.sleep(2000);
        runTestOnUiThread(()->{View button=edit.findViewById(R.id.saveAccount);android.graphics.Rect visible=new android.graphics.Rect();assertTrue(button.getGlobalVisibleRect(visible));assertEquals("保存按钮必须完整显示",button.getHeight(),visible.height());});
        screenshot("account-unified-save");runTestOnUiThread(edit::finish);
    }
    public void testSearchAgeThemeAndPriceToggleKeepCombinedQuery() throws Throwable {
        Activity browse=getInstrumentation().startActivitySync(new Intent(activity,SearchActivity.class).putExtra("query","晚安").putExtra("age","3-6").putExtra("theme","bedtime").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        runTestOnUiThread(()->assertTrue(clickText(browse.getWindow().getDecorView(),"价格 ↑↓")));getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        android.net.Uri uri=android.net.Uri.parse(lastCatalog);assertEquals("晚安",uri.getQueryParameter("q"));assertEquals("3-6",uri.getQueryParameter("age"));assertEquals("bedtime",uri.getQueryParameter("theme"));assertEquals("price_asc",uri.getQueryParameter("sort"));
        runTestOnUiThread(()->assertTrue(clickText(browse.getWindow().getDecorView(),"价格 ↑")));getInstrumentation().waitForIdleSync();SystemClock.sleep(300);assertEquals("price_desc",android.net.Uri.parse(lastCatalog).getQueryParameter("sort"));
        runTestOnUiThread(browse::finish);
    }
    public void testQuickAddAndHomeCommunityOpenRealDestinations() throws Throwable {
        runTestOnUiThread(()->{androidx.recyclerview.widget.RecyclerView list=activity.findViewById(R.id.productList);list.getChildAt(0).findViewById(R.id.quickAdd).performClick();});getInstrumentation().waitForIdleSync();SystemClock.sleep(400);assertEquals(1,addedQuantity.get());
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(CommunityDetailActivity.class.getName(),null,false);
        runTestOnUiThread(()->((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.communityPosts)).getChildAt(0).performClick());
        Activity detail=getInstrumentation().waitForMonitorWithTimeout(monitor,3000);assertNotNull(detail);assertEquals("post-test",detail.getIntent().getStringExtra("post_id"));runTestOnUiThread(detail::finish);getInstrumentation().removeMonitor(monitor);
    }
    public void testA3HomepageLayoutAndDirectCheckout() throws Throwable {
        runTestOnUiThread(()->{float density=activity.getResources().getDisplayMetrics().density;assertTrue(activity.findViewById(R.id.ageBaby).getHeight()<=64*density);assertTrue(activity.findViewById(R.id.themeBedtime).getHeight()<=56*density);});
        runTestOnUiThread(()->{for(int id:new int[]{R.id.themeBedtime,R.id.themeEmotion,R.id.themeScience}){android.widget.TextView choice=activity.findViewById(id);assertEquals(1,choice.getLineCount());assertEquals(0,choice.getLayout().getEllipsisCount(0));}});
        runTestOnUiThread(()->assertEquals(1,((android.widget.TextView)activity.findViewById(R.id.searchButton)).getLineCount()));
        runTestOnUiThread(()->assertEquals(0,((android.widget.TextView)activity.findViewById(R.id.sortPrice)).getLayout().getEllipsisCount(0)));
        runTestOnUiThread(()->{View search=activity.findViewById(R.id.searchInput),button=activity.findViewById(R.id.searchButton);assertTrue(search.getWidth()>button.getWidth());int[] inputPosition=new int[2],buttonPosition=new int[2];search.getLocationOnScreen(inputPosition);button.getLocationOnScreen(buttonPosition);assertTrue(buttonPosition[0]>inputPosition[0]+search.getWidth());assertTrue(button.getHeight()>=48*activity.getResources().getDisplayMetrics().density-1);});screenshot("home-a3");
        Intent intent=new Intent(activity,CheckoutActivity.class).putExtra("product_id","book-0").putExtra("quantity",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity checkout=getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        runTestOnUiThread(()->{assertEquals("¥56.00",((android.widget.TextView)checkout.findViewById(R.id.total)).getText().toString());assertTrue(checkout.findViewById(R.id.submit).isEnabled());android.widget.TextView address=checkout.findViewById(R.id.address);assertTrue("地址文字必须完整显示",address.getHeight()>=address.getLayout().getHeight()+address.getCompoundPaddingTop()+address.getCompoundPaddingBottom());});screenshot("checkout-direct");runTestOnUiThread(checkout::finish);
    }
    public void testLongCheckoutAddressExpandsWithoutClipping() throws Throwable {
        editedDetail="沈阳市和平区亲子阅读街道绘梦社区第三栋二单元十八楼1802室，请送到门口，联系电话与收货人信息以页面显示为准";
        Activity checkout=getInstrumentation().startActivitySync(new Intent(activity,CheckoutActivity.class).putExtra("product_id","book-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
        runTestOnUiThread(()->{android.widget.TextView address=checkout.findViewById(R.id.address);assertTrue(address.getLineCount()>=3);assertTrue("长地址每一行都必须在控件内",address.getHeight()>=address.getLayout().getHeight()+address.getCompoundPaddingTop()+address.getCompoundPaddingBottom());int[] addressPosition=new int[2],pickerPosition=new int[2];address.getLocationOnScreen(addressPosition);checkout.findViewById(R.id.changeAddress).getLocationOnScreen(pickerPosition);assertTrue("选择地址入口不得覆盖地址",pickerPosition[1]>=addressPosition[1]+address.getHeight());});
        screenshot("checkout-long-address");runTestOnUiThread(checkout::finish);
    }
    public void testThemeIconAndLabelAreCenteredTogether() throws Throwable {
        runTestOnUiThread(()->{for(int id:new int[]{R.id.themeBedtime,R.id.themeEmotion,R.id.themeScience}){android.widget.TextView choice=activity.findViewById(id);android.graphics.drawable.Drawable icon=choice.getCompoundDrawablesRelative()[0];assertNotNull(icon);float left=choice.getPaddingLeft()+icon.getBounds().left;float contentWidth=icon.getBounds().width()+choice.getCompoundDrawablePadding()+choice.getPaint().measureText(choice.getText().toString());assertEquals("图标与标签整体居中",choice.getWidth()/2f,left+contentWidth/2f,2*choice.getResources().getDisplayMetrics().density);}});
        screenshot("home-theme-centered");
    }
    public void testAllCategoriesUseServerDirectoryAndCombineSearch() throws Throwable {
        Instrumentation.ActivityMonitor categoriesMonitor=getInstrumentation().addMonitor(CategoriesActivity.class.getName(),null,false);runTestOnUiThread(()->activity.findViewById(R.id.allCategories).performClick());Activity categories=getInstrumentation().waitForMonitorWithTimeout(categoriesMonitor,3000);assertNotNull(categories);SystemClock.sleep(400);
        Instrumentation.ActivityMonitor searchMonitor=getInstrumentation().addMonitor(SearchActivity.class.getName(),null,false);runTestOnUiThread(()->clickText(categories.getWindow().getDecorView(),"3–6岁启蒙"));Activity search=getInstrumentation().waitForMonitorWithTimeout(searchMonitor,3000);assertNotNull(search);SystemClock.sleep(400);assertEquals("age3",android.net.Uri.parse(lastCatalog).getQueryParameter("category"));
        runTestOnUiThread(()->{findSearchInput(search.getWindow().getDecorView()).setText("晚安");clickText(search.getWindow().getDecorView(),"搜索");});SystemClock.sleep(400);assertEquals("age3",android.net.Uri.parse(lastCatalog).getQueryParameter("category"));assertEquals("晚安",android.net.Uri.parse(lastCatalog).getQueryParameter("q"));screenshot("independent-categories-search");runTestOnUiThread(()->{search.finish();categories.finish();});getInstrumentation().removeMonitor(searchMonitor);getInstrumentation().removeMonitor(categoriesMonitor);
    }
    private boolean clickText(View view,String label){if(view instanceof android.widget.TextView&&label.equals(((android.widget.TextView)view).getText().toString())){view.performClick();return true;}if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)if(clickText(((android.view.ViewGroup)view).getChildAt(i),label))return true;return false;}
    private com.google.android.material.textfield.TextInputEditText findSearchInput(View view){if(view instanceof com.google.android.material.textfield.TextInputEditText)return (com.google.android.material.textfield.TextInputEditText)view;if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++){com.google.android.material.textfield.TextInputEditText found=findSearchInput(((android.view.ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    public void testNewSearchFailureCannotAppendOldResults() throws Throwable {
        Activity search=getInstrumentation().startActivitySync(new Intent(activity,SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1600);runTestOnUiThread(()->assertEquals(View.VISIBLE,search.findViewById(R.id.more).getVisibility()));
        runTestOnUiThread(()->{findSearchInput(search.getWindow().getDecorView()).setText("offline");clickText(search.getWindow().getDecorView(),"搜索");});SystemClock.sleep(1600);runTestOnUiThread(()->{assertEquals("新查询失败时不能用旧分页加载更多",View.GONE,search.findViewById(R.id.more).getVisibility());assertEquals(View.VISIBLE,search.findViewById(R.id.retry).getVisibility());});runTestOnUiThread(search::finish);
    }
    public void testProductUploadSurvivesActivityRecreation() throws Throwable {
        Activity editor=getInstrumentation().startActivitySync(new Intent(activity,AddProductActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(400);File file=File.createTempFile("product-rotation-",".jpg",editor.getCacheDir());try(FileOutputStream output=new FileOutputStream(file)){byte[] bytes=new byte[150];bytes[0]=(byte)255;bytes[1]=(byte)216;bytes[148]=(byte)255;bytes[149]=(byte)217;output.write(bytes);}
        androidx.activity.result.ActivityResultRegistry registry=((androidx.activity.ComponentActivity)editor).getActivityResultRegistry();Field codes=androidx.activity.result.ActivityResultRegistry.class.getDeclaredField("keyToRc");codes.setAccessible(true);Map<String,Integer> entries=(Map<String,Integer>)codes.get(registry);Integer code=entries.get("activity_rq#1");assertNotNull(code);Field launched=androidx.activity.result.ActivityResultRegistry.class.getDeclaredField("launchedKeys");launched.setAccessible(true);((java.util.List<String>)launched.get(registry)).add("activity_rq#1");
        runTestOnUiThread(()->registry.dispatchResult(code,Activity.RESULT_OK,new Intent().putExtra("cropped_path",file.getAbsolutePath())));SystemClock.sleep(200);Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AddProductActivity.class.getName(),null,false);runTestOnUiThread(editor::recreate);Activity recreated=getInstrumentation().waitForMonitorWithTimeout(monitor,3000);assertNotNull(recreated);SystemClock.sleep(4500);runTestOnUiThread(()->assertTrue("旋转后的页面必须保留已上传图片",((android.widget.TextView)recreated.findViewById(R.id.imageHint)).getText().toString().contains("已选 1/9")));runTestOnUiThread(recreated::finish);getInstrumentation().removeMonitor(monitor);
    }
    public void testCheckoutEditUpdatesExistingAddressAndAddSelectsNewAddress() throws Throwable {
        Activity checkout=getInstrumentation().startActivitySync(new Intent(activity,CheckoutActivity.class).putExtra("product_id","book-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        Instrumentation.ActivityMonitor editMonitor=getInstrumentation().addMonitor(AddAddressActivity.class.getName(),null,false);
        runTestOnUiThread(()->checkout.findViewById(R.id.editAddress).performClick());
        Activity edit=getInstrumentation().waitForMonitorWithTimeout(editMonitor,3000);assertNotNull(edit);getInstrumentation().waitForIdleSync();assertEquals("address-1",new JSONObject(edit.getIntent().getStringExtra("copy_address")).optString("id"));
        runTestOnUiThread(()->{((android.widget.TextView)edit.findViewById(R.id.detail)).setText("门牌号微调 20 号");edit.findViewById(R.id.save).performClick();});getInstrumentation().waitForIdleSync();SystemClock.sleep(600);assertEquals("PUT",addressWriteMethod);
        runTestOnUiThread(()->assertTrue(((android.widget.TextView)checkout.findViewById(R.id.address)).getText().toString().contains("门牌号微调 20 号")));getInstrumentation().removeMonitor(editMonitor);
        Instrumentation.ActivityMonitor addMonitor=getInstrumentation().addMonitor(AddAddressActivity.class.getName(),null,false);runTestOnUiThread(()->checkout.findViewById(R.id.addAddress).performClick());
        Activity add=getInstrumentation().waitForMonitorWithTimeout(addMonitor,3000);assertNotNull(add);getInstrumentation().waitForIdleSync();assertNull(add.getIntent().getStringExtra("copy_address"));
        runTestOnUiThread(()->{((android.widget.TextView)add.findViewById(R.id.recipient)).setText("新增收货人");((android.widget.TextView)add.findViewById(R.id.phone)).setText("13800000001");((android.widget.TextView)add.findViewById(R.id.region)).setText("辽宁省");((android.widget.TextView)add.findViewById(R.id.detail)).setText("新增地址");add.findViewById(R.id.save).performClick();});getInstrumentation().waitForIdleSync();SystemClock.sleep(600);assertEquals("POST",addressWriteMethod);
        runTestOnUiThread(()->assertTrue(((android.widget.TextView)checkout.findViewById(R.id.address)).getText().toString().contains("新增收货人")));screenshot("checkout-edited");getInstrumentation().removeMonitor(addMonitor);runTestOnUiThread(checkout::finish);
    }
    public void testViewerLikeChangesAreNotHiddenByUnchangedCount() throws Exception {
        JSONObject original=new JSONObject().put("id","profile-test").put("title","阅读笔记").put("likeCount",2).put("liked",0);
        com.huimeng.bookshop.model.CommunityPost neutral=com.huimeng.bookshop.model.CommunityPost.from(original);
        original.put("liked",1);
        assertFalse("当前用户的点赞状态变化应被列表识别，不能只根据点赞总数判断",neutral.equals(com.huimeng.bookshop.model.CommunityPost.from(original)));
    }
    public void testAddressModifyOpensExistingRecordDirectly() throws Throwable {
        Activity addresses=getInstrumentation().startActivitySync(new Intent(activity,AddressesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AddAddressActivity.class.getName(),null,false);
        runTestOnUiThread(()->{androidx.recyclerview.widget.RecyclerView list=addresses.findViewById(R.id.list);list.getChildAt(0).findViewById(R.id.edit).performClick();});
        Activity editor=getInstrumentation().waitForMonitorWithTimeout(monitor,3000);
        assertNotNull("修改按钮必须直接打开现有地址，而不是弹出操作菜单",editor);
        assertEquals("address-1",new JSONObject(editor.getIntent().getStringExtra("copy_address")).optString("id"));
        runTestOnUiThread(()->{editor.finish();addresses.finish();});getInstrumentation().removeMonitor(monitor);
    }
    public void testUserAndMerchantTabsAndFormsRender() throws Throwable {
        for(int tab=1;tab<=4;tab++){final int position=tab;runTestOnUiThread(()->activity.selectTab(position));getInstrumentation().waitForIdleSync();screenshot("user-tab-"+tab);}
        preferences.edit().putString("role","MERCHANT").commit();
        MainActivity merchant=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));getInstrumentation().waitForIdleSync();
        for(int tab=1;tab<=5;tab++){final int position=tab;runTestOnUiThread(()->merchant.selectTab(position));getInstrumentation().waitForIdleSync();screenshot("merchant-tab-"+tab);}
        preferences.edit().putString("role","USER").commit();runTestOnUiThread(merchant::finish);getInstrumentation().waitForIdleSync();
        populatedCart=true;
        Class<?>[] pages={CartActivity.class,OrdersActivity.class,AddressesActivity.class,AddAddressActivity.class,EditAccountActivity.class,SettingsActivity.class,PublishPostActivity.class,RatingActivity.class,DataListActivity.class,AddProductActivity.class,RoleSwitchActivity.class,ProductDetailActivity.class};
        for(Class<?> page:pages){Activity screen=getInstrumentation().startActivitySync(new Intent(activity,page).putExtra("product_id","book-0").putExtra("title","我的收藏").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();screenshot(page.getSimpleName());runTestOnUiThread(screen::finish);getInstrumentation().waitForIdleSync();}
    }
    public void testReturningFromPublisherAndProductEditorRefreshesLists() throws Throwable {
        runTestOnUiThread(()->activity.selectTab(2));getInstrumentation().waitForIdleSync();int before=communityReads.get();
        Instrumentation.ActivityMonitor publishMonitor=getInstrumentation().addMonitor(PublishPostActivity.class.getName(),null,false);
        runTestOnUiThread(()->activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.publish).performClick());Activity publish=getInstrumentation().waitForMonitorWithTimeout(publishMonitor,3000);assertNotNull(publish);getInstrumentation().waitForIdleSync();runTestOnUiThread(publish::finish);getInstrumentation().waitForIdleSync();SystemClock.sleep(400);assertTrue(communityReads.get()>before);getInstrumentation().removeMonitor(publishMonitor);
        preferences.edit().putString("role","MERCHANT").commit();MainActivity merchant=(MainActivity)getInstrumentation().startActivitySync(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));runTestOnUiThread(()->merchant.selectTab(2));getInstrumentation().waitForIdleSync();int initial=merchantReads.get();
        Instrumentation.ActivityMonitor productMonitor=getInstrumentation().addMonitor(AddProductActivity.class.getName(),null,false);runTestOnUiThread(()->merchant.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.add).performClick());Activity product=getInstrumentation().waitForMonitorWithTimeout(productMonitor,3000);assertNotNull(product);getInstrumentation().waitForIdleSync();runTestOnUiThread(product::finish);getInstrumentation().waitForIdleSync();SystemClock.sleep(400);assertTrue(merchantReads.get()>initial);getInstrumentation().removeMonitor(productMonitor);preferences.edit().putString("role","USER").commit();runTestOnUiThread(merchant::finish);
    }
    public void testUnavailableCartCanBeDeselectedAndCannotIncreaseQuantity() throws Throwable {
        runTestOnUiThread(()->{
            CartAdapter adapter=new CartAdapter(new CartAdapter.Listener(){public void change(CartAdapter.CartLine line,int quantity){}public void select(CartAdapter.CartLine line,boolean selected){}public void remove(CartAdapter.CartLine line){}});
            CartAdapter.Holder holder=adapter.onCreateViewHolder(new android.widget.FrameLayout(activity),0);
            try{com.huimeng.bookshop.model.Product product=com.huimeng.bookshop.model.Product.from(new JSONObject().put("id","unavailable").put("name","测试失效商品").put("stock",0));
                adapter.submit(java.util.List.of(new CartAdapter.CartLine(product,2,true)));adapter.onBindViewHolder(holder,0);
                assertFalse(holder.itemView.findViewById(R.id.plus).isEnabled());assertTrue(holder.itemView.findViewById(R.id.selected).isEnabled());
                adapter.submit(java.util.List.of(new CartAdapter.CartLine(product,2,false)));adapter.onBindViewHolder(holder,0);assertFalse(holder.itemView.findViewById(R.id.selected).isEnabled());
            }catch(org.json.JSONException error){throw new AssertionError(error);}
        });
    }
    public void testLoginHelpExplainsRecoveryWithoutOfferingUnverifiedReset() throws Throwable {
        Activity login=getInstrumentation().startActivitySync(new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();
        Instrumentation.ActivityMonitor recovery=getInstrumentation().addMonitor(ForgotPasswordActivity.class.getName(),null,false);
        runTestOnUiThread(()->{android.widget.TextView help=login.findViewById(R.id.forgotPassword);assertEquals("账号帮助",help.getText().toString());help.performClick();});getInstrumentation().waitForIdleSync();assertEquals(0,recovery.getHits());getInstrumentation().removeMonitor(recovery);runTestOnUiThread(login::finish);
    }

    public void testAuthorAvatarAndNameOpenProfileAndPost() throws Throwable {
        runTestOnUiThread(()->activity.selectTab(2));getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        Instrumentation.ActivityMonitor profileMonitor=getInstrumentation().addMonitor(UserProfileActivity.class.getName(),null,false);
        runTestOnUiThread(()->activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.avatar).performClick());
        Activity profile=getInstrumentation().waitForMonitorWithTimeout(profileMonitor,3000);assertNotNull(profile);
        getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        assertEquals("reader-test",profile.getIntent().getStringExtra("user_id"));
        runTestOnUiThread(()->assertEquals("星星妈妈",((android.widget.TextView)profile.findViewById(R.id.name)).getText().toString()));
        screenshot("user-profile");
        Instrumentation.ActivityMonitor detailMonitor=getInstrumentation().addMonitor(CommunityDetailActivity.class.getName(),null,false);
        runTestOnUiThread(()->((androidx.recyclerview.widget.RecyclerView)profile.findViewById(R.id.posts)).getChildAt(0).performClick());
        Activity detail=getInstrumentation().waitForMonitorWithTimeout(detailMonitor,3000);assertNotNull(detail);getInstrumentation().waitForIdleSync();SystemClock.sleep(300);screenshot("community-detail");
        profileMonitor=getInstrumentation().addMonitor(UserProfileActivity.class.getName(),null,false);
        runTestOnUiThread(()->detail.findViewById(R.id.author).performClick());
        Activity second=getInstrumentation().waitForMonitorWithTimeout(profileMonitor,3000);assertNotNull(second);
        runTestOnUiThread(()->{second.finish();detail.finish();profile.finish();});getInstrumentation().waitForIdleSync();
        profileMonitor=getInstrumentation().addMonitor(UserProfileActivity.class.getName(),null,false);
        runTestOnUiThread(()->activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView().findViewById(R.id.author).performClick());
        Activity third=getInstrumentation().waitForMonitorWithTimeout(profileMonitor,3000);assertNotNull(third);
        runTestOnUiThread(third::finish);getInstrumentation().removeMonitor(profileMonitor);getInstrumentation().removeMonitor(detailMonitor);
    }

    public void testAddressButtonIsCircularAndAtBottomRight() throws Throwable {
        Intent intent=new Intent(activity,AddressesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity addresses=getInstrumentation().startActivitySync(intent);getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        runTestOnUiThread(()->{
            View add=addresses.findViewById(R.id.add);assertTrue(add instanceof com.google.android.material.floatingactionbutton.FloatingActionButton);
            assertEquals(add.getWidth(),add.getHeight());
            int[] position=new int[2];add.getLocationOnScreen(position);assertTrue(position[0]>addresses.getResources().getDisplayMetrics().widthPixels/2);
            assertTrue(position[1]>addresses.getResources().getDisplayMetrics().heightPixels/2);
        });
        screenshot("addresses");
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AddAddressActivity.class.getName(),null,false);
        runTestOnUiThread(()->addresses.findViewById(R.id.add).performClick());
        Activity form=getInstrumentation().waitForMonitorWithTimeout(monitor,3000);assertNotNull(form);
        runTestOnUiThread(()->{form.finish();addresses.finish();});getInstrumentation().removeMonitor(monitor);
    }

    public void testScrollPositionSurvivesAndPullAtTopReloads() throws Throwable {
        final androidx.core.widget.NestedScrollView[] scroll=new androidx.core.widget.NestedScrollView[1];
        runTestOnUiThread(()->{scroll[0]=activity.findViewById(R.id.scroll);scroll[0].scrollTo(0,300);});
        getInstrumentation().waitForIdleSync();assertTrue(scroll[0].getScrollY()>0);int position=scroll[0].getScrollY();
        runTestOnUiThread(()->activity.selectTab(2));getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->activity.selectTab(1));getInstrumentation().waitForIdleSync();assertEquals(position,scroll[0].getScrollY());
        runTestOnUiThread(()->scroll[0].scrollTo(0,0));getInstrumentation().waitForIdleSync();
        assertEquals("刷新手势开始前应位于顶部",0,scroll[0].getScrollY());
        int before=calls.get();float density=activity.getResources().getDisplayMetrics().density;
        runTestOnUiThread(()->{View root=activity.getSupportFragmentManager().getPrimaryNavigationFragment().requireView();drag((FrameLayout)root,10*density,230*density,true);});
        getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        assertTrue("手动下拉应重新读取首页",calls.get()>=before+2);
        screenshot("home");
    }
    public void testHomeCarouselUsesFivePostersAndPausesAcrossTabs() throws Throwable {
        final BannerCarouselView[] carousel=new BannerCarouselView[1];
        final androidx.recyclerview.widget.RecyclerView[] pages=new androidx.recyclerview.widget.RecyclerView[1];
        runTestOnUiThread(()->{
            int id=activity.getResources().getIdentifier("bannerCarousel","id",activity.getPackageName());
            View banner=activity.findViewById(id);
            assertTrue("首页必须接入轮播海报",banner instanceof BannerCarouselView);
            carousel[0]=(BannerCarouselView)banner;
            pages[0]=(androidx.recyclerview.widget.RecyclerView)carousel[0].getChildAt(0);
            assertEquals("展示五张已确认海报",5,((android.view.ViewGroup)carousel[0].getChildAt(1)).getChildCount());
            carousel[0].setActive(false);
        });
        for(int i=0;i<5;i++){
            final int page=i;
            runTestOnUiThread(()->pages[0].scrollToPosition(page+1));
            getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
            runTestOnUiThread(()->{
                assertNotNull("海报必须成功解码",((android.widget.ImageView)pages[0].findViewHolderForAdapterPosition(page+1).itemView).getDrawable());
                android.view.ViewGroup dots=(android.view.ViewGroup)carousel[0].getChildAt(1);
                assertEquals("页码圆点跟随海报",android.graphics.Color.WHITE,((android.graphics.drawable.GradientDrawable)dots.getChildAt(page).getBackground()).getColor().getDefaultColor());
            });
            screenshot("home-banner-"+(i+1));
        }
        runTestOnUiThread(()->{pages[0].scrollToPosition(1);carousel[0].setActive(true);});
        getInstrumentation().waitForIdleSync();SystemClock.sleep(5700);
        runTestOnUiThread(()->{assertEquals(1,carousel[0].currentPage());activity.selectTab(2);});
        getInstrumentation().waitForIdleSync();SystemClock.sleep(5500);
        runTestOnUiThread(()->{assertEquals("离开首页暂停轮播",1,carousel[0].currentPage());activity.selectTab(1);});
        getInstrumentation().waitForIdleSync();SystemClock.sleep(5700);
        runTestOnUiThread(()->assertEquals("回到首页恢复轮播",2,carousel[0].currentPage()));
        int before=calls.get();
        final int[] position=new int[2];
        runTestOnUiThread(()->carousel[0].getLocationOnScreen(position));
        long down=SystemClock.uptimeMillis();
        for(int step=0;step<=12;step++){
            int action=step==0?MotionEvent.ACTION_DOWN:step==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            float x=position[0]+carousel[0].getWidth()*(.85f-.7f*step/12);
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,position[1]+carousel[0].getHeight()/2f,0);
            getInstrumentation().sendPointerSync(event);event.recycle();SystemClock.sleep(16);
        }
        getInstrumentation().waitForIdleSync();SystemClock.sleep(700);
        runTestOnUiThread(()->assertEquals("真实首页支持横向切换",3,carousel[0].currentPage()));
        assertEquals("滑动海报不刷新页面",before,calls.get());
    }
    public void testCarouselAdvancesAndPausesWhenInactive() throws Throwable {
        Class<?> type=Class.forName("com.huimeng.bookshop.ui.BannerCarouselView");
        Class<?> banner=Class.forName("com.huimeng.bookshop.ui.BannerCarouselView$Banner");
        Object first=banner.getConstructor(int.class,String.class).newInstance(R.drawable.app_icon,"阅读预览");
        Object second=banner.getConstructor(int.class,String.class).newInstance(R.drawable.ic_book_placeholder,"社区预览");
        final View[] view=new View[1];
        runTestOnUiThread(()->{try{view[0]=(View)type.getConstructor(Context.class).newInstance(activity);activity.setContentView(view[0]);type.getMethod("setBanners",java.util.List.class).invoke(view[0],java.util.Arrays.asList(first,second));type.getMethod("setActive",boolean.class).invoke(view[0],true);}catch(Exception error){throw new RuntimeException(error);}});
        getInstrumentation().waitForIdleSync();SystemClock.sleep(5700);
        runTestOnUiThread(()->{try{assertEquals(1,type.getMethod("currentPage").invoke(view[0]));type.getMethod("setActive",boolean.class).invoke(view[0],false);}catch(Exception error){throw new RuntimeException(error);}});
        SystemClock.sleep(5200);
        runTestOnUiThread(()->{try{assertEquals("页面不活跃时暂停轮播",1,type.getMethod("currentPage").invoke(view[0]));}catch(Exception error){throw new RuntimeException(error);}});
    }
    public void testLogisticsUpdatesSilentlyAndSkipsUnchangedViews() throws Throwable {
        Activity order=getInstrumentation().startActivitySync(new Intent(activity,OrderDetailActivity.class).putExtra("order_id","order-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        final View[] first=new View[1];
        runTestOnUiThread(()->{assertEquals("商品已发货",((android.widget.TextView)order.findViewById(R.id.status)).getText().toString());first[0]=((android.view.ViewGroup)order.findViewById(R.id.itemList)).getChildAt(0);assertNotNull(first[0]);});
        SystemClock.sleep(5500);
        runTestOnUiThread(()->assertSame("未变化的物流数据不应重建商品区域",first[0],((android.view.ViewGroup)order.findViewById(R.id.itemList)).getChildAt(0)));
        logisticsVersion.incrementAndGet();SystemClock.sleep(5500);
        runTestOnUiThread(()->{assertEquals("运输途中",((android.widget.TextView)order.findViewById(R.id.status)).getText().toString());assertEquals(View.GONE,order.findViewById(R.id.loading).getVisibility());});
        screenshot("logistics");runTestOnUiThread(order::finish);
    }
    public void testHorizontalCarouselSwipeDoesNotTriggerRefresh() throws Throwable {
        final PullRefreshLayout[] root=new PullRefreshLayout[1];final BannerCarouselView[] carousel=new BannerCarouselView[1];AtomicInteger refreshed=new AtomicInteger();
        runTestOnUiThread(()->{root[0]=new PullRefreshLayout(activity);carousel[0]=new BannerCarouselView(activity);root[0].addView(carousel[0],new FrameLayout.LayoutParams(-1,-1));root[0].setOnRefreshListener(refreshed::incrementAndGet);activity.setContentView(root[0]);carousel[0].setBanners(java.util.Arrays.asList(new BannerCarouselView.Banner(R.drawable.app_icon,"阅读预览"),new BannerCarouselView.Banner(R.drawable.ic_book_placeholder,"社区预览")));});
        getInstrumentation().waitForIdleSync();
        long down=SystemClock.uptimeMillis();float width=activity.getResources().getDisplayMetrics().widthPixels;
        for(int step=0;step<=12;step++){
            int action=step==0?MotionEvent.ACTION_DOWN:step==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            float x=width*(.85f-.7f*step/12);long now=SystemClock.uptimeMillis();
            runTestOnUiThread(()->{MotionEvent event=MotionEvent.obtain(down,now,action,x,100,0);root[0].dispatchTouchEvent(event);event.recycle();});SystemClock.sleep(16);
        }
        SystemClock.sleep(700);getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{assertEquals("横向手势切到下一张",1,carousel[0].currentPage());assertEquals("横向滑动不触发下拉刷新",0,refreshed.get());});
    }

    @Override protected void tearDown() throws Exception {
        if(activity != null)getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
        if(fixtureQueue != null)fixtureQueue.stop();
        Field requests = HuiMengApp.class.getDeclaredField("requestQueue"); requests.setAccessible(true);
        requests.set(getInstrumentation().getTargetContext().getApplicationContext(), previousQueue);
        SharedPreferences.Editor editor = preferences.edit().clear();
        if("device-test".equals(previousPreferences.get("username"))&&"test-user".equals(previousPreferences.get("account_id")))previousPreferences=Collections.emptyMap();
        for(Map.Entry<String, ?> entry : previousPreferences.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if(value instanceof String)editor.putString(key,(String)value);
            else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);
            else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);
        }
        editor.commit();
        Field token = SessionStore.class.getDeclaredField("temporaryToken"); token.setAccessible(true); token.set(null, previousTemporaryToken);
        for(Map.Entry<String,Map<String,?>> saved:auxiliaryPreferences.entrySet())restorePreferences(saved.getKey(),saved.getValue());
        getInstrumentation().runOnMainSync(()->new com.huimeng.bookshop.core.AppearanceStore(getInstrumentation().getTargetContext()).apply());
        super.tearDown();
    }
    private void restorePreferences(String name,Map<String,?> saved){SharedPreferences.Editor edit=getInstrumentation().getTargetContext().getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object value=entry.getValue();if(value instanceof String)edit.putString(entry.getKey(),(String)value);else if(value instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)value);else if(value instanceof Integer)edit.putInt(entry.getKey(),(Integer)value);else if(value instanceof Long)edit.putLong(entry.getKey(),(Long)value);else if(value instanceof Float)edit.putFloat(entry.getKey(),(Float)value);}edit.commit();}

    public void testSwitchTabsRetainsHomeAndDoesNotReload() throws Throwable {
        final Fragment[] home = new Fragment[1];
        runTestOnUiThread(() -> home[0] = activity.getSupportFragmentManager().getPrimaryNavigationFragment());
        int initialCalls = nonNoticeCalls.get();
        runTestOnUiThread(() -> activity.selectTab(2)); getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> activity.selectTab(3)); getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> activity.selectTab(4)); getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> activity.selectTab(1)); getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            assertTrue("首页实例应被保留", activity.getSupportFragmentManager().getFragments().contains(home[0]));
            assertFalse("首页应重新显示", home[0].isHidden());
        });
        SystemClock.sleep(5500);
        assertEquals("切换时保留现有页面数据；消息页允许实时刷新", initialCalls, nonNoticeCalls.get());
    }

    public void testPullIndicatorFollowsClampsAndRefreshesOnlyOnRelease() throws Throwable {
        Class<?> type = Class.forName("com.huimeng.bookshop.ui.PullRefreshLayout");
        final FrameLayout[] layout = new FrameLayout[1]; final AtomicInteger refreshes = new AtomicInteger();
        runTestOnUiThread(() -> {
            try {
                layout[0] = (FrameLayout)type.getConstructor(Context.class).newInstance(activity);
                View content = new View(activity);layout[0].addView(content, new FrameLayout.LayoutParams(-1,-1));
                type.getMethod("setOnRefreshListener", Runnable.class).invoke(layout[0], (Runnable)refreshes::incrementAndGet);
                activity.setContentView(layout[0]);
            } catch(Exception error) {throw new RuntimeException(error);}
        });
        getInstrumentation().waitForIdleSync();
        float density = activity.getResources().getDisplayMetrics().density;
        runTestOnUiThread(() -> {
            drag(layout[0], 0, 30*density, false);
            View indicator = layout[0].getChildAt(layout[0].getChildCount()-1);
            assertEquals(View.VISIBLE, indicator.getVisibility());
            float partial = indicator.getTranslationY();
            move(layout[0], 220*density);
            float clamped = indicator.getTranslationY();
            assertTrue("图标跟随手势向下移动", clamped > partial);
            move(layout[0], 400*density);
            assertEquals("超过阈值后图标位置固定", clamped, indicator.getTranslationY(), .5f);
            assertEquals(0, refreshes.get());
            release(layout[0], 400*density);
            assertEquals("松手触发一次刷新", 1, refreshes.get());
            try {type.getMethod("setRefreshing", boolean.class).invoke(layout[0], false);}catch(Exception error){throw new RuntimeException(error);}
        });
        SystemClock.sleep(300);
        runTestOnUiThread(() -> {
            assertEquals(View.GONE,layout[0].getChildAt(layout[0].getChildCount()-1).getVisibility());
            drag(layout[0], 0, 24*density, true);
            assertEquals("未达到阈值不刷新",1,refreshes.get());
        });
    }
    private static void drag(FrameLayout target,float start,float end,boolean release) {
        send(target,MotionEvent.ACTION_DOWN,start);move(target,end);if(release)release(target,end);
    }
    private static void move(FrameLayout target,float y){send(target,MotionEvent.ACTION_MOVE,y);}
    private static void release(FrameLayout target,float y){send(target,MotionEvent.ACTION_UP,y);}
    private static void send(FrameLayout target,int action,float y){long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,50,y,0);target.dispatchTouchEvent(event);event.recycle();}
}
