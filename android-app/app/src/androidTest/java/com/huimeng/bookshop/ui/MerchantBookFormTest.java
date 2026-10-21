package com.huimeng.bookshop.ui;
import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import com.android.volley.Request;
import com.google.android.material.textfield.TextInputLayout;
import org.json.*;

/** Exercises the actual merchant save buttons using the isolated Volley fixture. */
public class MerchantBookFormTest extends RefreshNavigationTest {
    private volatile JSONObject saved;private volatile boolean submitted;
    private JSONObject product;
    private JSONObject product()throws Exception{
        if(product==null)product=new JSONObject().put("id","book-0").put("name","测试绘本").put("summary","商家表单测试简介").put("ageRange","3–6岁").put("categoryId","C01").put("stock",10).put("priceCents",2980).put("status","DRAFT").put("authorName","原作者").put("publisher","测试出版社").put("publicationDate","2025-09-01").put("tableOfContents",new JSONArray().put("旧目录"));
        return product;
    }
    @Override protected String fixture(Request<?> request){try{
        String url=request.getUrl();
        if(url.endsWith("catalog/categories"))return "{\"data\":[{\"id\":\"C01\",\"name\":\"3–6岁启蒙\"}]}";
        if(url.endsWith("merchant/products/book-0/submit")){submitted=true;product().put("status","PENDING_REVIEW");return "{\"ok\":true}";}
        if((url.endsWith("merchant/products")||url.endsWith("merchant/products/book-0"))&&(request.getMethod()==Request.Method.POST||request.getMethod()==Request.Method.PUT)){
            saved=new JSONObject(new String(request.getBody(),java.nio.charset.StandardCharsets.UTF_8));product=new JSONObject(saved.toString()).put("id","book-0").put("status","DRAFT");return "{\"id\":\"book-0\",\"ok\":true}";
        }
        if(url.endsWith("merchant/products/book-0"))return new JSONObject().put("data",product()).toString();
    }catch(Exception error){throw new RuntimeException(error);}return super.fixture(request);}
    private Activity open(Class<?> type){return getInstrumentation().startActivitySync(new Intent(activity,type).putExtra("product_id","book-0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private TextView input(View view,String hint){if(view instanceof TextInputLayout box&&hint.contentEquals(box.getHint()))return box.getEditText();if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=input(group.getChildAt(i),hint);if(found!=null)return found;}return null;}
    private void fill(Activity page)throws Exception{
        input(page.getWindow().getDecorView(),"文字作者").setText("商家填写的新作者");input(page.getWindow().getDecorView(),"发行出版社").setText("商家童书出版社");input(page.getWindow().getDecorView(),"发售日期 YYYY-MM-DD").setText("2025-10-02");input(page.getWindow().getDecorView(),"图书目录（每行一项，最多80项）").setText("新的第一节\n新的第二节");
    }
    private void awaitSave(){long deadline=SystemClock.uptimeMillis()+6000;while(saved==null&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);assertNotNull("真实保存按钮应提交图书资料",saved);}
    private TextView toggle(View view){if(view instanceof TextView text&&text.getText().toString().startsWith("图书资料（选填）"))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=toggle(group.getChildAt(i));if(found!=null)return found;}return null;}
    public void testMerchantFieldsAreAccessibleAndPreview()throws Throwable{
        Activity page=open(ManageProductActivity.class);try{getInstrumentation().waitForIdleSync();SystemClock.sleep(700);runTestOnUiThread(()->{
            View root=page.getWindow().getDecorView();TextView entry=toggle(root);assertNotNull("商家表单应有独立图书资料入口",entry);entry.performClick();TextView author=input(root,"文字作者");assertTrue("展开后商家应可编辑作者",author.isShown());assertEquals("原作者",author.getText().toString());author.requestRectangleOnScreen(new android.graphics.Rect(0,0,author.getWidth(),author.getHeight()),true);
        });getInstrumentation().waitForIdleSync();SystemClock.sleep(500);try(var descriptor=getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");var in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);var out=new java.io.FileOutputStream(new java.io.File(page.getExternalFilesDir(null),"book-merchant-form.png"))){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}}finally{runTestOnUiThread(page::finish);}
    }
    public void testCreateIncludesMerchantBookFields()throws Throwable{
        Activity page=open(AddProductActivity.class);try{getInstrumentation().waitForIdleSync();SystemClock.sleep(500);runTestOnUiThread(()->{try{
            ((TextView)page.findViewById(R.id.name)).setText("商家新增绘本");((TextView)page.findViewById(R.id.summary)).setText("商家自己填写的绘本介绍");((TextView)page.findViewById(R.id.age)).setText("3–6岁");((TextView)page.findViewById(R.id.price)).setText("29.80");((TextView)page.findViewById(R.id.stock)).setText("10");fill(page);page.findViewById(R.id.save).performClick();
        }catch(Exception error){throw new RuntimeException(error);}});awaitSave();assertEquals("商家填写的新作者",saved.getString("authorName"));assertEquals("2025-10-02",saved.getString("publicationDate"));assertEquals(2,saved.getJSONArray("tableOfContents").length());}finally{runTestOnUiThread(page::finish);}
    }
    public void testEditSavesFieldsBeforeReviewSubmission()throws Throwable{
        Activity page=open(ManageProductActivity.class);try{getInstrumentation().waitForIdleSync();SystemClock.sleep(700);runTestOnUiThread(()->{try{assertEquals("原作者",input(page.getWindow().getDecorView(),"文字作者").getText().toString());fill(page);page.findViewById(R.id.save).performClick();}catch(Exception error){throw new RuntimeException(error);}});awaitSave();assertEquals("商家填写的新作者",saved.getString("authorName"));assertEquals("商家童书出版社",saved.getString("publisher"));assertEquals(2,saved.getJSONArray("tableOfContents").length());getInstrumentation().waitForIdleSync();SystemClock.sleep(350);runTestOnUiThread(()->page.findViewById(R.id.submitReview).performClick());long deadline=SystemClock.uptimeMillis()+6000;while(!submitted&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);assertTrue("保存后的商品应可提交管理员审核",submitted);assertEquals("商家填写的新作者",product().getString("authorName"));}finally{runTestOnUiThread(page::finish);}
    }
}
