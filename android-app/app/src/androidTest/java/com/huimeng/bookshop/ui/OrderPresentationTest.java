package com.huimeng.bookshop.ui;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

public class OrderPresentationTest extends AndroidTestCase {
    public void testRemoteNumberWinsAndUnsyncedNumberIsExplicit() throws Exception {
        JSONObject order=new JSONObject().put("id","local-123").put("remoteId","HM123");
        assertEquals("订单号：HM123",OrderPresentation.number(order));
        order.remove("remoteId");assertTrue(OrderPresentation.number(order).contains("本地待同步"));
        order.put("remoteId","local-123");assertTrue(OrderPresentation.number(order).contains("本地待同步"));
    }
    public void testTimelineDefaultsToLatestAndExpansionIncludesHistory() throws Exception {
        JSONObject order=new JSONObject().put("status","SHIPPED").put("logistics_status","OUT_FOR_DELIVERY")
            .put("events",new JSONArray().put(new JSONObject().put("toStatus","SHIPPED").put("createdAt","2026-10-24T00:00:00Z"))
                .put(new JSONObject().put("toStatus","OUT_FOR_DELIVERY").put("createdAt","2026-10-25T00:00:00Z")));
        assertTrue(OrderProgress.hasHistory(order));
        assertFalse(OrderProgress.format(order,false).contains("商家已发货"));
        assertTrue(OrderProgress.format(order,true).contains("商家已发货"));
        assertTrue(OrderProgress.format(order,false).contains("订单正在派送"));
    }
    public void testReceiptCountdownUsesShipmentAndAfterSale() throws Exception {
        JSONObject order=new JSONObject().put("status","SHIPPED").put("logistics_status","OUT_FOR_DELIVERY").put("shipped_at","2026-10-25T00:00:00Z");
        Instant now=Instant.parse("2026-10-25T00:00:00Z");
        assertTrue(OrderPresentation.receipt(order,now).contains("30天后"));
        assertTrue(OrderPresentation.receipt(order,now.plusSeconds(86400)).contains("29天后"));
        order.put("after_sale_state","MERCHANT_REVIEW");assertTrue(OrderPresentation.receipt(order,now).contains("暂停"));
        order.remove("after_sale_state");order.put("logistics_status","COMPLETED");assertEquals("",OrderPresentation.receipt(order,now));
    }
}
