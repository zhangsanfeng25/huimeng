package com.huimeng.bookshop.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.R;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.LocalCartStore;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.FragmentHomeBinding;
import com.huimeng.bookshop.model.CommunityPost;
import com.huimeng.bookshop.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment implements LocalDataRefresh {
    private FragmentHomeBinding binding;
    private ProductAdapter products;
    private HomePostAdapter posts;
    private String age="",theme="",query="",category="",categoryName="",sort="comprehensive";
    private int productRequest,page=1;
    private final List<Product> displayed=new ArrayList<>();
    private final Set<String> adding=new HashSet<>();
    private boolean loading;
    private androidx.activity.result.ActivityResultLauncher<Intent> shoppingReturn;
    @Override public void onCreate(@Nullable Bundle state){super.onCreate(state);shoppingReturn=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->refreshCartBadge());if(state!=null){category=state.getString("category","");categoryName=state.getString("category_name","");age=state.getString("age","");theme=state.getString("theme","");query=state.getString("query","");sort=state.getString("sort","comprehensive");}}
    @Override public void onSaveInstanceState(@NonNull Bundle state){super.onSaveInstanceState(state);state.putString("category",category);state.putString("category_name",categoryName);state.putString("age",age);state.putString("theme",theme);state.putString("query",query);state.putString("sort",sort);}

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentHomeBinding.inflate(inflater,container,false);
        binding.greeting.setText("你好，"+new SessionStore(requireContext()).name());
        binding.bannerCarousel.setBanners(List.of(
            new BannerCarouselView.Banner(R.drawable.banner_reading,"翻开绘本，看见更大的世界"),
            new BannerCarouselView.Banner(R.drawable.banner_discover,"每个年龄，都有好故事"),
            new BannerCarouselView.Banner(R.drawable.banner_community,"分享共读时光，遇见同路的朋友"),
            new BannerCarouselView.Banner(R.drawable.banner_favorites,"收藏心动绘本，留住阅读期待"),
            new BannerCarouselView.Banner(R.drawable.banner_delivery,"让好故事，来到孩子身边")
        ));
        binding.bannerCarousel.setOnBannerClickListener(this::openBanner);
        products=new ProductAdapter(this::openProduct,this::quickAdd);
        binding.productList.setLayoutManager(new GridLayoutManager(requireContext(),2));binding.productList.setAdapter(products);binding.productList.setNestedScrollingEnabled(false);binding.productList.setItemAnimator(null);
        posts=new HomePostAdapter(row->startActivity(new Intent(requireContext(),CommunityDetailActivity.class).putExtra("post_id",row.id())));
        binding.communityPosts.setLayoutManager(new LinearLayoutManager(requireContext()));binding.communityPosts.setAdapter(posts);binding.communityPosts.setNestedScrollingEnabled(false);
        bindChoice(binding.ageBaby,"0-3",true);bindChoice(binding.agePreschool,"3-6",true);bindChoice(binding.ageSchool,"6-9",true);bindChoice(binding.ageOlder,"9-plus",true);
        bindChoice(binding.themeBedtime,"bedtime",false);bindChoice(binding.themeEmotion,"emotion",false);bindChoice(binding.themeScience,"science",false);
        binding.sortComprehensive.setOnClickListener(v->chooseSort("comprehensive"));binding.sortNewest.setOnClickListener(v->chooseSort("newest"));
        binding.sortPrice.setOnClickListener(v->chooseSort("price_asc".equals(sort)?"price_desc":"price_asc"));
        binding.searchButton.setOnClickListener(v->startActivity(new Intent(requireContext(),SearchActivity.class).putExtra("query",String.valueOf(binding.searchInput.getText()).trim())));binding.searchInput.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});
        binding.searchInput.setOnItemClickListener((parent,view,position,id)->search());updateHistory();
        binding.allCategories.setOnClickListener(v->startActivity(new Intent(requireContext(),CategoriesActivity.class)));binding.clearFilters.setOnClickListener(v->clearFilters());binding.allProducts.setOnClickListener(v->startActivity(new Intent(requireContext(),SearchActivity.class).putExtra("browse_title","全部绘本")));
        binding.retry.setOnClickListener(v->loadProducts(false));binding.loadMore.setOnClickListener(v->loadProducts(true));
        binding.cartButton.setOnClickListener(v->shoppingReturn.launch(new Intent(requireContext(),CartActivity.class)));
        binding.communityMore.setOnClickListener(v->((MainActivity)requireActivity()).selectTab(2));
        binding.getRoot().setOnRefreshListener(()->{loadProducts(false);loadPosts();refreshCartBadge();});
        for(View button:new View[]{binding.searchButton,binding.cartButton,binding.allProducts,binding.communityMore})TapMotion.attach(button);
        binding.searchInput.setText(query);binding.sortGroup.check("newest".equals(sort)?R.id.sortNewest:sort.startsWith("price")?R.id.sortPrice:R.id.sortComprehensive);binding.sortPrice.setText("price_asc".equals(sort)?"价格 ↑":"price_desc".equals(sort)?"价格 ↓":"价格 ↑↓");renderFilters();loadProducts(false);loadPosts();refreshCartBadge();return binding.getRoot();
    }
    private void bindChoice(TextView view,String value,boolean isAge){view.setOnClickListener(v->startActivity(new Intent(requireContext(),SearchActivity.class).putExtra(isAge?"age":"theme",value).putExtra("browse_title",view.getText().toString()+(isAge?"绘本":""))));TapMotion.attach(view);}
    private void chooseSort(String value){sort=value;binding.sortGroup.check("newest".equals(sort)?R.id.sortNewest:sort.startsWith("price")?R.id.sortPrice:R.id.sortComprehensive);binding.sortPrice.setText("price_asc".equals(sort)?"价格 ↑":"price_desc".equals(sort)?"价格 ↓":"价格 ↑↓");loadProducts(false);}
    private void renderFilters(){binding.ageBaby.setSelected("0-3".equals(age));binding.agePreschool.setSelected("3-6".equals(age));binding.ageSchool.setSelected("6-9".equals(age));binding.ageOlder.setSelected("9-plus".equals(age));binding.themeBedtime.setSelected("bedtime".equals(theme));binding.themeEmotion.setSelected("emotion".equals(theme));binding.themeScience.setSelected("science".equals(theme));binding.clearFilters.setVisibility(age.isEmpty()&&theme.isEmpty()&&query.isEmpty()&&category.isEmpty()?View.GONE:View.VISIBLE);}
    private void search(){String value=String.valueOf(binding.searchInput.getText()).trim();if(value.length()>100){toast("搜索关键词最多 100 字");return;}query=value;binding.searchInput.dismissDropDown();
        if(!query.isEmpty()){List<String> values=history();values.remove(query);values.add(0,query);if(values.size()>8)values=values.subList(0,8);requireContext().getSharedPreferences("huimeng_search",Context.MODE_PRIVATE).edit().putString(historyKey(),new JSONArray(values).toString()).apply();updateHistory();}
        ((InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(binding.searchInput.getWindowToken(),0);binding.searchInput.clearFocus();renderFilters();loadProducts(false);
    }
    private String historyKey(){return "history_"+new SessionStore(requireContext()).accountId();}
    private List<String> history(){List<String> values=new ArrayList<>();try{JSONArray rows=new JSONArray(requireContext().getSharedPreferences("huimeng_search",Context.MODE_PRIVATE).getString(historyKey(),"[]"));for(int i=0;i<rows.length();i++)values.add(rows.optString(i));}catch(Exception ignored){}return values;}
    private void updateHistory(){binding.searchInput.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_dropdown_item_1line,history()));}
    private void clearFilters(){age="";theme="";query="";category="";categoryName="";binding.searchInput.setText("");renderFilters();loadProducts(false);}
    private void loadProducts(boolean append){
        if(binding==null||(append&&loading))return;final int request=++productRequest,targetPage=append?page+1:1;loading=true;
        binding.loading.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.GONE);binding.empty.setVisibility(View.GONE);binding.loadMore.setEnabled(false);
        String path="catalog/products?q="+Uri.encode(query)+"&category="+Uri.encode(category)+"&age="+Uri.encode(age)+"&theme="+Uri.encode(theme)+"&sort="+sort+"&page="+targetPage+"&pageSize=6";
        new ApiClient(requireContext()).get(path,result->{if(binding==null||request!=productRequest)return;loading=false;page=targetPage;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);
            List<Product> next=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row!=null)next.add(Product.from(row));}
            if(!append)displayed.clear();displayed.addAll(next);products.submit(new ArrayList<>(displayed));int total=result.optInt("total",displayed.size());binding.filterSummary.setText(filterText()+"共 "+total+" 本");binding.empty.setText("暂无符合条件的绘本，试试其他年龄或主题");binding.empty.setVisibility(displayed.isEmpty()?View.VISIBLE:View.GONE);binding.loadMore.setEnabled(true);binding.loadMore.setVisibility(result.optBoolean("hasMore")?View.VISIBLE:View.GONE);
        },error->{if(binding==null||request!=productRequest)return;loading=false;binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);binding.loadMore.setEnabled(true);binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);binding.retry.setVisibility(View.VISIBLE);});
    }
    private String filterText(){String value=categoryName.isEmpty()?"":categoryName+" · ";if(!query.isEmpty())value+="“"+query+"” · ";if(!age.isEmpty())value+=age.replace("-plus","岁以上").replace("-","–")+(age.endsWith("plus")?"":"岁")+" · ";if(!theme.isEmpty())value+=("bedtime".equals(theme)?"睡前故事":"emotion".equals(theme)?"情绪成长":"科普启蒙")+" · ";return value;}
    private void loadPosts(){new ApiClient(requireContext()).get("community/posts",result->{if(binding==null)return;List<CommunityPost> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");if(data!=null)for(int i=0;i<Math.min(2,data.length());i++){JSONObject row=data.optJSONObject(i);if(row!=null)rows.add(CommunityPost.from(row));}posts.submit(rows);binding.communityEmpty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);binding.communityEmpty.setText("分享一次共读，留下阅读时光");},error->{if(binding!=null){binding.communityEmpty.setText("笔记暂时加载失败，可进入社区重试");binding.communityEmpty.setVisibility(View.VISIBLE);}});}
    private void quickAdd(Product item){if(!adding.add(item.id()))return;products.setAdding(item.id(),true);LocalCartStore.resolve(requireContext(),cart->{if(!isAdded()){finishAdd(item,"");return;}int quantity=cart.quantity(item.id())+1;if(quantity>Math.min(99,item.stock())){finishAdd(item,"已达到当前库存上限");return;}
        try{new ApiClient(requireContext()).put("me/cart/"+item.id(),new JSONObject().put("quantity",quantity).put("selected",true),result->{cart.upsert(item,quantity,true);finishAdd(item,"已加入购物车");refreshCartBadge();},error->finishAdd(item,error));}catch(Exception error){finishAdd(item,"加购失败，请重试");}
    },error->finishAdd(item,error));}
    private void finishAdd(Product item,String message){adding.remove(item.id());products.setAdding(item.id(),false);if(!message.isEmpty())toast(message);}
    private void refreshCartBadge(){if(binding==null)return;LocalCartStore.resolve(requireContext(),this::renderCartBadge,error->{});}
    private void renderCartBadge(LocalCartStore cart){if(binding==null)return;int count=0;for(LocalCartStore.Entry row:cart.list())count+=row.quantity();binding.cartBadge.setText(count>99?"99+":String.valueOf(count));binding.cartBadge.setVisibility(count>0?View.VISIBLE:View.GONE);binding.cartButton.setContentDescription("打开购物车，共 "+count+" 件商品");}
    private void openProduct(Product item){shoppingReturn.launch(new Intent(requireContext(),ProductDetailActivity.class).putExtra("product_id",item.id()));}
    private void openBanner(int position){if(binding==null)return;switch(position){case 0->{theme="bedtime";renderFilters();loadProducts(false);binding.scroll.smoothScrollTo(0,((View)binding.sectionTitle.getParent()).getTop());}case 1->{binding.scroll.smoothScrollTo(0,((View)binding.ageBaby.getParent()).getTop());toast("选择适龄范围，查找孩子的好故事");}case 2->((MainActivity)requireActivity()).selectTab(2);case 3->startActivity(new Intent(requireContext(),DataListActivity.class).putExtra("title","我的收藏").putExtra("subtitle","收藏心动绘本，留住阅读期待"));case 4->startActivity(new Intent(requireContext(),OrdersActivity.class).putExtra("status","SHIPPED"));}}
    private void toast(String value){if(isAdded())Toast.makeText(requireContext(),value,Toast.LENGTH_SHORT).show();}
    @Override public void onResume(){super.onResume();if(binding!=null)binding.bannerCarousel.setActive(true);}
    @Override public void onPause(){if(binding!=null)binding.bannerCarousel.setActive(false);super.onPause();}
    @Override public void onDestroyView(){productRequest++;if(binding!=null)binding.bannerCarousel.setActive(false);super.onDestroyView();binding=null;}
    @Override public void refreshLocalData(){if(binding!=null && isAdded()){loadProducts(false);loadPosts();refreshCartBadge();}}
}
