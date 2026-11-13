package com.huimeng.bookshop.ui;
import android.test.AndroidTestCase;
import org.json.JSONObject;

public class MerchantWorkTest extends AndroidTestCase {
    public void testShippingTodosExcludeRefundsAndPendingWrites()throws Exception{
        JSONObject order=new JSONObject().put("status","PAID").put("logistics_status","PREPARING").put("total_cents",1000);
        assertTrue(MerchantWork.readyToShip(order));
        order.put("after_sale_state","MERCHANT_REVIEW");assertFalse(MerchantWork.readyToShip(order));order.remove("after_sale_state");
        order.put("refunded_cents",1000);assertFalse(MerchantWork.readyToShip(order));order.put("refunded_cents",300);assertTrue(MerchantWork.readyToShip(order));
        order.put("pendingAction","advance");assertFalse(MerchantWork.readyToShip(order));
    }
    public void testProductSearchIncludesRemoteIdAndCategoryAndWaitsForReview()throws Exception{
        JSONObject product=new JSONObject().put("id","local-P1").put("remoteId","P123").put("name","星空百科").put("categoryId","C1").put("category","科学探索").put("status","DRAFT");
        assertTrue(MerchantWork.productMatches(product,"p123","ACTION","C1"));
        assertTrue(MerchantWork.productMatches(product,"科学","",""));
        assertFalse(MerchantWork.productMatches(product,"p123","ONLINE","C1"));
        assertFalse(MerchantWork.productMatches(product,"p123","ACTION","C2"));
        product.put("pendingAction","submit");assertFalse(MerchantWork.productNeedsAction(product));assertTrue(MerchantWork.productMatches(product,"","PENDING_REVIEW",""));
    }
    public void testOrderSearchStatusAndActionFilterShareTheSameRules()throws Exception{
        JSONObject order=new JSONObject().put("id","HM1").put("buyer","测试买家").put("productName","星空百科").put("status","PAID").put("logistics_status","READY_TO_SHIP");
        assertTrue(MerchantWork.orderMatches(order,"买家","TO_SHIP"));assertTrue(MerchantWork.orderMatches(order,"hm1","TO_SHIP"));
        assertFalse(MerchantWork.orderMatches(order,"","DELIVERY"));
        order.put("after_sale_state","ADMIN_REVIEW");assertFalse(MerchantWork.orderMatches(order,"","TO_SHIP"));assertTrue(MerchantWork.orderMatches(order,"","AFTER_SALE"));
    }
    public void testMerchantAfterSaleOnlyCountsActionsTheyCanPerform(){
        assertTrue(MerchantWork.saleNeedsAction("MERCHANT_REVIEW"));assertTrue(MerchantWork.saleNeedsAction("RETURNING"));
        assertFalse(MerchantWork.saleNeedsAction("ADMIN_REVIEW"));assertFalse(MerchantWork.saleNeedsAction("COMPLETED"));
    }
}
