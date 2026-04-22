package com.example.findcartx1;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.List;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private LinearLayout      ordersContainer;
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Purchase History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            Color.parseColor("#2E7D32")));
        }

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // Header
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.VERTICAL);
        hdr.setBackgroundColor(Color.parseColor("#2E7D32"));
        hdr.setPadding(dp(20), dp(28), dp(20), dp(20));
        TextView tvT = new TextView(this);
        tvT.setText("Purchase History"); tvT.setTextSize(22f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setTextColor(Color.WHITE);
        hdr.addView(tvT);
        TextView tvS = new TextView(this);
        tvS.setText("Your FindCartx1 shopping trips");
        tvS.setTextSize(13f); tvS.setTextColor(Color.parseColor("#C8E6C9"));
        tvS.setPadding(0, dp(4), 0, 0);
        hdr.addView(tvS);
        root.addView(hdr);

        // Loading indicator
        TextView tvLoading = new TextView(this);
        tvLoading.setText("Loading orders from Firebase...");
        tvLoading.setTextSize(13f); tvLoading.setTextColor(Color.parseColor("#757575"));
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(dp(20), dp(40), dp(20), dp(20));
        root.addView(tvLoading);

        // Container for order cards
        ordersContainer = new LinearLayout(this);
        ordersContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(ordersContainer);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
        root.addView(spacer);

        sv.addView(root);
        setContentView(sv);

        // Load from Firestore
        loadOrdersFromFirestore(tvLoading);
    }

    private void loadOrdersFromFirestore(TextView tvLoading) {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            // Not logged in — load from local SharedPrefs
            tvLoading.setText("Loading local orders...");
            loadLocalOrders(tvLoading);
            return;
        }

        // Load from Firestore — user's orders subcollection, newest first
        db.collection("users").document(user.getUid())
                .collection("orders")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    tvLoading.setVisibility(View.GONE);

                    if (querySnapshot.isEmpty()) {
                        // No Firestore orders — try local
                        loadLocalOrders(tvLoading);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        addOrderCardFromMap(doc.getData());
                    }
                })
                .addOnFailureListener(e -> {
                    // Firebase failed — fall back to local
                    tvLoading.setText("Could not reach Firebase. Showing local orders.");
                    loadLocalOrders(tvLoading);
                });
    }

    private void loadLocalOrders(TextView tvLoading) {
        try {
            SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            String histJson = pref.getString("order_history", "[]");
            org.json.JSONArray history = new org.json.JSONArray(histJson);

            if (history.length() == 0) {
                tvLoading.setText("No orders yet!\nStart shopping to see history here.");
                tvLoading.setVisibility(View.VISIBLE);
                return;
            }

            tvLoading.setVisibility(View.GONE);
            for (int o = 0; o < history.length(); o++) {
                org.json.JSONObject order = history.getJSONObject(o);
                addOrderCardFromJson(order);
            }
        } catch (Exception e) {
            tvLoading.setText("Could not load history.");
        }
    }

    // Build card from Firestore Map
    @SuppressWarnings("unchecked")
    private void addOrderCardFromMap(Map<String, Object> data) {
        String orderId  = str(data.get("orderId"));
        String date     = str(data.get("date"));
        String method   = str(data.get("paymentMethod"));
        String store    = str(data.get("userStore"));
        double total    = num(data.get("totalAmount"));
        int itemCount   = (int) num(data.get("itemCount"));
        String status   = str(data.get("status"));
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) data.get("items");

        LinearLayout card = buildCard(orderId, date, method, store,
                total, itemCount, status);

        if (items != null) {
            int show = Math.min(items.size(), 3);
            for (int k = 0; k < show; k++) {
                Map<String, Object> item = items.get(k);
                String iName = str(item.get("name"));
                int    iQty  = (int) num(item.get("quantity"));
                double iSub  = num(item.get("subtotal"));
                addItemRow(card, iName, iQty, iSub);
            }
            if (items.size() > 3) addMoreRow(card, items.size() - 3);
        }

        addTotalRow(card, itemCount, total);
        ordersContainer.addView(card);
    }

    // Build card from local JSON
    private void addOrderCardFromJson(org.json.JSONObject order) {
        try {
            String orderId  = order.optString("orderId",       "Unknown");
            String date     = order.optString("date",          "");
            String method   = order.optString("paymentMethod", "");
            String store    = order.optString("userStore",     "FindCartx1");
            double total    = order.optDouble("totalAmount",   0);
            int itemCount   = order.optInt("itemCount",        0);
            String status   = order.optString("status",        "completed");

            LinearLayout card = buildCard(orderId, date, method, store,
                    total, itemCount, status);

            org.json.JSONArray items = order.optJSONArray("items");
            if (items != null) {
                int show = Math.min(items.length(), 3);
                for (int k = 0; k < show; k++) {
                    org.json.JSONObject item = items.getJSONObject(k);
                    addItemRow(card,
                            item.optString("name", ""),
                            item.optInt("quantity", 1),
                            item.optDouble("subtotal", 0));
                }
                if (items.length() > 3) addMoreRow(card, items.length() - 3);
            }
            addTotalRow(card, itemCount, total);
            ordersContainer.addView(card);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout buildCard(String orderId, String date, String method,
                                   String store, double total, int itemCount,
                                   String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(10), dp(12), 0);
        card.setLayoutParams(lp);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Date + status row
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvDate = new TextView(this);
        tvDate.setText(date); tvDate.setTextSize(11.5f);
        tvDate.setTextColor(Color.parseColor("#757575"));
        tvDate.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(tvDate);
        TextView tvStatus = new TextView(this);
        tvStatus.setText(status.equals("completed") ? "✓ Paid" : "Cancelled");
        tvStatus.setTextSize(11f); tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setTextColor(status.equals("completed")
                ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        topRow.addView(tvStatus);
        card.addView(topRow);

        // Order ID
        TextView tvId = new TextView(this);
        tvId.setText("Order #" + orderId); tvId.setTextSize(13f);
        tvId.setTypeface(null, Typeface.BOLD);
        tvId.setTextColor(Color.parseColor("#212121"));
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        idLp.topMargin = dp(5); idLp.bottomMargin = dp(6);
        tvId.setLayoutParams(idLp);
        card.addView(tvId);

        // Store + method
        TextView tvStore = new TextView(this);
        tvStore.setText(store + "  ·  " + method);
        tvStore.setTextSize(11.5f); tvStore.setTextColor(Color.parseColor("#1565C0"));
        card.addView(tvStore);

        div(card);
        return card;
    }

    private void addItemRow(LinearLayout card, String name, int qty, double sub) {
        TextView tv = new TextView(this);
        tv.setText("• " + name + "  ×" + qty + "  =  Rs" + String.format("%.0f", sub));
        tv.setTextSize(12.5f); tv.setTextColor(Color.parseColor("#424242"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5); tv.setLayoutParams(lp);
        card.addView(tv);
    }

    private void addMoreRow(LinearLayout card, int count) {
        TextView tv = new TextView(this);
        tv.setText("  + " + count + " more items...");
        tv.setTextSize(11.5f); tv.setTextColor(Color.parseColor("#9E9E9E"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4); tv.setLayoutParams(lp);
        card.addView(tv);
    }

    private void addTotalRow(LinearLayout card, int count, double total) {
        div(card);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        TextView tvL = new TextView(this);
        tvL.setText(count + " item" + (count != 1 ? "s" : "") + " · incl. GST");
        tvL.setTextSize(12f); tvL.setTextColor(Color.parseColor("#757575"));
        tvL.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvL);
        TextView tvV = new TextView(this);
        tvV.setText("Rs" + String.format("%.2f", total));
        tvV.setTextSize(14f); tvV.setTypeface(null, Typeface.BOLD);
        tvV.setTextColor(Color.parseColor("#2E7D32"));
        row.addView(tvV);
        card.addView(row);
    }

    private void div(LinearLayout p) {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#F0F0F0"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(8); d.setLayoutParams(lp);
        p.addView(d);
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }
    private double num(Object o) {
        if (o == null) return 0;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}