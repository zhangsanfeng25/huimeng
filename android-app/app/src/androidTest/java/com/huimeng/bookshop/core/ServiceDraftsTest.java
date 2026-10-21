package com.huimeng.bookshop.core;
@SuppressWarnings("deprecation")
public class ServiceDraftsTest extends android.test.AndroidTestCase {
 public void testSameRecordUsesSameKeyFromContactAndList(){assertEquals(ServiceDrafts.key("origin|buyer","conversations","CV1","ORDER1"),ServiceDrafts.key("origin|buyer","conversations","CV1",null));assertFalse(ServiceDrafts.key("origin|buyer","feedback","FB1",null).equals(ServiceDrafts.key("origin|buyer","feedback","FB2",null)));}
 public void testLostResponseKeepsMessageIdentityAcrossRecreation()throws Exception{
  var prefs=getContext().getSharedPreferences("test_service_drafts",0);prefs.edit().clear().commit();var a=new ServiceDrafts(prefs,"account1|CV1");var first=a.prepare("原消息","ORDER1");
  var recreated=new ServiceDrafts(prefs,"account1|CV1");assertEquals(first.getString("clientId"),recreated.prepare("原消息","ORDER1").getString("clientId"));
  assertFalse(ServiceDrafts.canClear("发送期间新输入的内容","原消息"));assertTrue(ServiceDrafts.canClear("原消息","原消息"));
  var next=recreated.prepare("新消息","ORDER1");recreated.acknowledge(first.getString("clientId"));assertEquals(next.getString("clientId"),recreated.prepare("新消息","ORDER1").getString("clientId"));prefs.edit().clear().commit();
 }
}
