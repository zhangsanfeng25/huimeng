package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.huimeng.bookshop.core.ApiClient;
import com.huimeng.bookshop.core.AccountSync;
import com.huimeng.bookshop.databinding.FragmentNotificationBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NotificationFragment extends Fragment implements LocalDataRefresh{
    private final android.os.Handler inboxHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean conversations,chatReading;private int chatRequest;private ConversationAdapter chats;
    private final Runnable inboxTick=new Runnable(){public void run(){if(binding==null||!isAdded())return;refreshLocalData();inboxHandler.postDelayed(this,5000);}};
    private FragmentNotificationBinding binding;private NotificationAdapter adapter;private AccountSync sync;
    private boolean reading,queuedRead;private int requestId;private int unreadChats=-1,unreadNotices=-1;
    private final androidx.activity.result.ActivityResultLauncher<Intent> detail=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{if(binding!=null)load();});
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        binding=FragmentNotificationBinding.inflate(inflater,container,false);
        sync=new AccountSync(requireContext(),this::load,"notices","orders");
        adapter=new NotificationAdapter(item->{Intent intent=new Intent(requireContext(),NotificationDetailActivity.class);
            intent.putExtra("notice_id",item.id()).putExtra("title",item.title()).putExtra("content",item.content()).putExtra("created_at",item.createdAt()).putExtra("read",item.read());detail.launch(intent);},this::renderSelection);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));binding.list.setAdapter(adapter);
        chats=new ConversationAdapter("MERCHANT".equals(new com.huimeng.bookshop.core.SessionStore(requireContext()).role()),id->startActivity(new Intent(requireContext(),ServiceActivity.class).putExtra("kind","conversations").putExtra("record_id",id)));
        conversations=state!=null?state.getBoolean("conversations",false):"MERCHANT".equals(new com.huimeng.bookshop.core.SessionStore(requireContext()).role());
        binding.conversations.setOnClickListener(v->selectMessages(true));binding.systemNotices.setOnClickListener(v->selectMessages(false));
        binding.conversations.setCheckable(true);binding.systemNotices.setCheckable(true);
        selectMessages(conversations);
        binding.selectAll.setOnClickListener(v->adapter.selectAll());
        binding.cancelSelection.setOnClickListener(v->adapter.exitSelection());
        binding.markRead.setOnClickListener(v->bulk("read"));
        binding.deleteSelected.setOnClickListener(v->{if(adapter.selectedCount()>0)new AlertDialog.Builder(requireContext())
            .setMessage("删除选中的 "+adapter.selectedCount()+" 条消息？此操作仅影响当前账户。")
            .setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->bulk("delete")).show();});
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){super.onViewCreated(view,state);binding.getRoot().setOnRefreshListener(this::refreshLocalData);}
    @Override public void onSaveInstanceState(@NonNull Bundle state){state.putBoolean("conversations",conversations);super.onSaveInstanceState(state);}
    private void selectMessages(boolean showChats){conversations=showChats;if(binding==null)return;adapter.exitSelection();binding.list.setAdapter(showChats?chats:adapter);binding.conversations.setSelected(showChats);binding.systemNotices.setSelected(!showChats);binding.conversations.setChecked(showChats);binding.systemNotices.setChecked(!showChats);renderSelection();binding.empty.setVisibility(View.GONE);if(showChats)loadChats();else load();}
    private void loadChats(){if(binding==null||chatReading)return;chatReading=true;int id=++chatRequest;
        if(conversations&&chats.getItemCount()==0)binding.loading.setVisibility(View.VISIBLE);
        new com.huimeng.bookshop.core.ServiceClient(requireContext()).get("service/conversations",r->{if(binding==null||id!=chatRequest)return;chatReading=false;JSONArray rows=r.optJSONArray("data");int count=0;if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)count+=row.optInt("unread");}boolean merchant="MERCHANT".equals(new com.huimeng.bookshop.core.SessionStore(requireContext()).role());String label=merchant?"买家消息":"商家消息";binding.conversations.setText(label+(count>0?" · "+count:""));binding.conversations.setContentDescription(label+"，"+count+"条未读消息");unreadChats=count;updateBadge();chats.submit(rows);if(conversations){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);binding.empty.setText("暂无会话，可从商品或订单详情联系对方");binding.empty.setVisibility(chats.getItemCount()==0?View.VISIBLE:View.GONE);}},e->{if(binding==null||id!=chatRequest)return;chatReading=false;if(conversations){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);binding.empty.setText(e);binding.empty.setVisibility(chats.getItemCount()==0?View.VISIBLE:View.GONE);}});
    }
    @Override public void onResume(){super.onResume();if(sync!=null)sync.start();inboxHandler.removeCallbacks(inboxTick);inboxHandler.post(inboxTick);}
    @Override public void onPause(){if(sync!=null)sync.stop();inboxHandler.removeCallbacks(inboxTick);super.onPause();}
    private void renderSelection(){if(binding==null)return;boolean mode=!conversations&&adapter.selecting();binding.heading.setText(mode?"已选择 "+adapter.selectedCount()+" 条":"消息中心");
        binding.selectionBar.setVisibility(mode?View.VISIBLE:View.GONE);binding.selectAll.setVisibility(mode?View.VISIBLE:View.GONE);
        binding.markRead.setEnabled(adapter.selectedCount()>0);binding.deleteSelected.setEnabled(adapter.selectedCount()>0);}
    private void bulk(String action){List<String> ids=adapter.selectedIds();if(ids.isEmpty())return;
        binding.markRead.setEnabled(false);binding.deleteSelected.setEnabled(false);
        try{new ApiClient(requireContext()).post("me/notifications/bulk/"+action,new JSONObject().put("ids",new JSONArray(ids)),result->{if(binding==null)return;adapter.exitSelection();load();},error->{if(binding==null)return;renderSelection();Toast.makeText(requireContext(),error,Toast.LENGTH_SHORT).show();});}
        catch(Exception error){renderSelection();Toast.makeText(requireContext(),"操作失败，请重试",Toast.LENGTH_SHORT).show();}
    }
    private void load(){if(binding==null)return;if(reading){queuedRead=true;return;}reading=true;int id=++requestId;if(!conversations&&adapter.getItemCount()==0)binding.loading.setVisibility(View.VISIBLE);new ApiClient(requireContext()).get("me/notifications",result->{if(binding==null||id!=requestId)return;
        if(!conversations){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);}List<NotificationAdapter.Notice> rows=new ArrayList<>();JSONArray data=result.optJSONArray("data");
        if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)rows.add(new NotificationAdapter.Notice(item.optString("id"),item.optString("title"),item.optString("content"),item.optString("created_at"),item.optInt("is_read")==1));}
        unreadNotices=0;if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null&&item.optInt("is_read")==0)unreadNotices++;}updateBadge();adapter.submit(rows);if(!conversations){binding.empty.setText("暂无消息");binding.empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);}
        afterRead();
    },error->{if(binding==null||id!=requestId)return;if(!conversations){binding.loading.setVisibility(View.GONE);binding.getRoot().setRefreshing(false);binding.empty.setText(error);binding.empty.setVisibility(View.VISIBLE);}afterRead();});}
    private void updateBadge(){if(getActivity() instanceof MainActivity main)main.setMerchantMessageUnread(unreadChats,unreadNotices);}
    private void afterRead(){reading=false;boolean again=queuedRead;queuedRead=false;if(again&&binding!=null&&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))load();}
    @Override public void onDestroyView(){if(sync!=null)sync.stop();inboxHandler.removeCallbacksAndMessages(null);requestId++;chatRequest++;chatReading=false;reading=false;queuedRead=false;super.onDestroyView();binding=null;}
    @Override public void refreshLocalData(){if(binding!=null && isAdded()){load();loadChats();}}
}
