package com.huimeng.bookshop.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import com.huimeng.bookshop.R;
import org.json.JSONObject;

/** Refunds must not leave contradictory fulfillment instructions in either role's header. */
public class RefundStatusDisplayTest extends RefreshNavigationTest {
    private String saleState = "COMPLETED";
    private int refundedCents = 2800;

    @Override protected String fixture(com.android.volley.Request<?> request) {
        String url = request.getUrl();
        if (!url.endsWith("me/orders/order-test") && !url.endsWith("merchant/orders/order-test"))
            return super.fixture(request);
        try {
            JSONObject result = new JSONObject(super.fixture("me/orders/order-test"));
            result.getJSONObject("data").put("status", "PAID").put("logistics_status", "PREPARING")
                .put("after_sale_state", saleState).put("refunded_cents", refundedCents).put("buyer", "测试买家");
            return result.toString();
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private void verifyHeader(boolean merchant, String expected, boolean fulfillmentVisible, boolean actionVisible) throws Exception {
        Class<? extends Activity> screen = merchant ? MerchantOrderDetailActivity.class : OrderDetailActivity.class;
        Activity opened = getInstrumentation().startActivitySync(new Intent(activity, screen)
            .putExtra("order_id", "order-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        try {
            String[] header = {""};
            long end = SystemClock.uptimeMillis() + 5000;
            while (SystemClock.uptimeMillis() < end) {
                getInstrumentation().runOnMainSync(() -> header[0] = ((TextView)opened.findViewById(R.id.status)).getText().toString());
                if (((TextView)opened.findViewById(R.id.orderId)).getText().toString().contains("order-test")) break;
                SystemClock.sleep(80);
            }
            getInstrumentation().waitForIdleSync();
            assertTrue(header[0], header[0].contains(expected));
            assertEquals(header[0], fulfillmentVisible, header[0].contains("准备出库"));
            assertEquals(header[0], actionVisible, opened.findViewById(R.id.action).getVisibility() == View.VISIBLE);
            if ("COMPLETED".equals(saleState)) assertTrue(header[0], header[0].contains("售后完成"));
            assertFalse(header[0], header[0].contains("\n ·"));
            if (refundedCents == 2800) {
                SystemClock.sleep(350);
                android.graphics.Bitmap shot = getInstrumentation().getUiAutomation().takeScreenshot();
                try (var out = new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),
                        merchant ? "refund-header-merchant.png" : "refund-header-buyer.png"))) {
                    shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                } finally { shot.recycle(); }
            }
        } finally { getInstrumentation().runOnMainSync(opened::finish); }
    }

    public void testFullRefundReplacesFulfillmentForBothRoles() throws Exception {
        verifyHeader(false, "已全额退款", false, false);
        verifyHeader(true, "已全额退款", false, false);
    }
    public void testPartialRefundKeepsRemainingFulfillmentForBothRoles() throws Exception {
        refundedCents = 350;
        verifyHeader(false, "部分退款", true, false);
        verifyHeader(true, "部分退款", true, true);
    }
    public void testActiveSaleShowsProcessingForBothRoles() throws Exception {
        saleState = "ADMIN_REVIEW"; refundedCents = 0;
        verifyHeader(false, "售后处理中", false, false);
        verifyHeader(true, "售后处理中", false, false);
    }
    public void testRejectedSaleKeepsFulfillmentForBothRoles() throws Exception {
        saleState = "REJECTED"; refundedCents = 0;
        verifyHeader(false, "售后：", true, false);
        verifyHeader(true, "售后：", true, true);
    }
    public void testCancelledSaleKeepsFulfillmentForBothRoles() throws Exception {
        saleState = "CANCELLED"; refundedCents = 0;
        verifyHeader(false, "售后：", true, false);
        verifyHeader(true, "售后：", true, true);
    }
    public void testCompletedWithoutRefundAvoidsEmptySeparator() throws Exception {
        refundedCents = 0;
        verifyHeader(false, "售后完成", true, false);
        verifyHeader(true, "售后完成", true, true);
    }
    public void testOrdinaryOrderKeepsFulfillmentForBothRoles() throws Exception {
        saleState = ""; refundedCents = 0;
        verifyHeader(false, "准备出库", true, false);
        verifyHeader(true, "准备出库", true, true);
    }
}
