package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class CartActivity extends AppCompatActivity {

    private final List<String>  itemNames  = new ArrayList<>();
    private final List<String>  itemPrices = new ArrayList<>();
    private final List<Double>  itemValues = new ArrayList<>();
    private final List<String>  itemRacks  = new ArrayList<>();
    private final List<String>  itemFloors = new ArrayList<>();
    private final List<String>  itemCats   = new ArrayList<>();
    private final List<Integer> quantities = new ArrayList<>();

    private LinearLayout cartListLayout;
    private TextView     tvSubtotal, tvGst, tvDiscount, tvTotal;
    private TextView     tvEmptyMsg;
    private double       totalDiscount = 0.0;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Initialize Firebase
        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        Intent i = getIntent();
        String[] names  = i.getStringArrayExtra("names");
        String[] prices = i.getStringArrayExtra("prices");
        double[] values = i.getDoubleArrayExtra("values");
        String[] racks  = i.getStringArrayExtra("racks");
        String[] floors = i.getStringArrayExtra("floors");
        String[] cats   = i.getStringArrayExtra("cats");

        if (names != null) {
            for (int k = 0; k < names.length; k++) {
                itemNames.add(names[k]);
                itemPrices.add(prices != null ? prices[k] : "");
                itemValues.add(values != null ? values[k] : 0);
                itemRacks.add(racks != null ? racks[k] : "");
                itemFloors.add(floors != null ? floors[k] : "");
                itemCats.add(cats != null ? cats[k] : "");
                quantities.add(1);
            }
        }
        buildCartScreen();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CART SCREEN
    // ══════════════════════════════════════════════════════════════════════════

    private void buildCartScreen() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.parseColor("#F5F5F5"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(buildHeader());

        tvEmptyMsg = new TextView(this);
        tvEmptyMsg.setText("Your cart is empty!\nGo back and add products.");
        tvEmptyMsg.setTextSize(15f);
        tvEmptyMsg.setTextColor(Color.parseColor("#757575"));
        tvEmptyMsg.setGravity(Gravity.CENTER);
        tvEmptyMsg.setPadding(dp(20), dp(80), dp(20), dp(80));
        tvEmptyMsg.setVisibility(itemNames.isEmpty() ? View.VISIBLE : View.GONE);
        root.addView(tvEmptyMsg);

        cartListLayout = new LinearLayout(this);
        cartListLayout.setOrientation(LinearLayout.VERTICAL);
        cartListLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        rebuildCartRows();
        root.addView(cartListLayout);

        // Bill summary
        LinearLayout billCard = makeCard(dp(12), dp(12));
        row(billCard, "Items in cart", String.valueOf(itemNames.size()),
                Color.parseColor("#424242"), false);
        divider(billCard);
        tvSubtotal = summaryRow(billCard, "Subtotal",
                "Rs0.00", Color.parseColor("#424242"), false);
        divider(billCard);
        tvDiscount = summaryRow(billCard, "Discount applied",
                "- Rs0.00", Color.parseColor("#2E7D32"), false);
        divider(billCard);
        tvGst = summaryRow(billCard, "GST (5%)",
                "Rs0.00", Color.parseColor("#757575"), false);
        divider(billCard);
        tvTotal = summaryRow(billCard, "Grand Total",
                "Rs0.00", Color.parseColor("#2E7D32"), true);
        root.addView(billCard);
        recalculate();

        View btnProceed = buildBtn("PROCEED TO BILL  →",
                Color.parseColor("#2E7D32"));
        btnProceed.setOnClickListener(v -> {
            if (itemNames.isEmpty()) {
                Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            showReceipt();
        });
        root.addView(btnProceed);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
        root.addView(spacer);

        sv.addView(root);
        setContentView(sv);
    }

    private LinearLayout buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBackgroundColor(Color.parseColor("#2E7D32"));
        h.setPadding(dp(14), dp(14), dp(14), dp(14));
        h.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvB = new TextView(this);
        tvB.setText("←"); tvB.setTextSize(22f);
        tvB.setTextColor(Color.WHITE); tvB.setTypeface(null, Typeface.BOLD);
        tvB.setPadding(0, 0, dp(14), 0);
        tvB.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        h.addView(tvB);

        TextView tvT = new TextView(this);
        tvT.setText("My Cart"); tvT.setTextSize(20f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setTextColor(Color.WHITE);
        tvT.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        h.addView(tvT);

        TextView tvC = new TextView(this);
        tvC.setText(itemNames.size() + " items");
        tvC.setTextSize(12f); tvC.setTextColor(Color.parseColor("#C8E6C9"));
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tcLp.setMarginEnd(dp(8)); tvC.setLayoutParams(tcLp);
        h.addView(tvC);

        // Clear cart button
        TextView tvClear = new TextView(this);
        tvClear.setText("🗑 Clear");
        tvClear.setTextSize(11.5f); tvClear.setTypeface(null, Typeface.BOLD);
        tvClear.setTextColor(Color.WHITE);
        tvClear.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(Color.parseColor("#C62828"));
        clearBg.setCornerRadius(dp(6));
        tvClear.setBackground(clearBg);
        tvClear.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Clear Cart")
                        .setMessage("Remove all items from cart?")
                        .setPositiveButton("Clear All", (d, w) -> {
                            itemNames.clear(); itemPrices.clear(); itemValues.clear();
                            itemRacks.clear(); itemFloors.clear(); itemCats.clear();
                            quantities.clear();
                            // Send result so MapActivity clears its cart too
                            Intent result = new Intent();
                            result.putExtra("CLEAR_CART", true);
                            setResult(RESULT_OK, result);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );
        h.addView(tvClear);
        return h;
    }

    private void rebuildCartRows() {
        if (cartListLayout == null) return;
        cartListLayout.removeAllViews();

        for (int k = 0; k < itemNames.size(); k++) {
            final int idx = k;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(12), dp(14), dp(12), dp(14));
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, dp(6));
            card.setLayoutParams(cardLp);

            // Remove button
            TextView btnRemove = new TextView(this);
            btnRemove.setText("−"); btnRemove.setTextSize(18f);
            btnRemove.setTextColor(Color.WHITE);
            btnRemove.setTypeface(null, Typeface.BOLD);
            btnRemove.setGravity(Gravity.CENTER);
            GradientDrawable remGd = new GradientDrawable();
            remGd.setShape(GradientDrawable.OVAL);
            remGd.setColor(Color.parseColor("#E53935"));
            btnRemove.setBackground(remGd);
            btnRemove.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
            btnRemove.setOnClickListener(v -> removeItem(idx));
            card.addView(btnRemove);

            // Category icon circle (replaces the blue box)
            FrameLayout iconFrame = new FrameLayout(this);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(52), dp(52));
            iconLp.setMarginStart(dp(10)); iconLp.setMarginEnd(dp(12));
            iconFrame.setLayoutParams(iconLp);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(dp(10));
            iconBg.setColor(getCategoryColor(itemCats.get(k)));
            iconFrame.setBackground(iconBg);
            TextView tvIcon = new TextView(this);
            tvIcon.setLayoutParams(new FrameLayout.LayoutParams(dp(52), dp(52)));
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setText(getCategoryEmoji(itemCats.get(k)));
            tvIcon.setTextSize(22f);
            iconFrame.addView(tvIcon);
            card.addView(iconFrame);

            // Info
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(itemNames.get(k));
            tvName.setTextSize(13f); tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#212121"));
            info.addView(tvName);

            TextView tvLoc = new TextView(this);
            tvLoc.setText("Rack " + itemRacks.get(k) + "  ·  "
                    + (itemFloors.get(k).equals("ground") ? "Ground Floor" : "1st Floor"));
            tvLoc.setTextSize(10f);
            tvLoc.setTextColor(Color.parseColor("#1565C0"));
            info.addView(tvLoc);

            double itemTotal = itemValues.get(k) * quantities.get(k);
            TextView tvPrice = new TextView(this);
            tvPrice.setTag("price_" + k);
            tvPrice.setText("Rs" + String.format("%.0f", itemValues.get(k))
                    + " × " + quantities.get(k)
                    + " = Rs" + String.format("%.0f", itemTotal));
            tvPrice.setTextSize(11f); tvPrice.setTypeface(null, Typeface.BOLD);
            tvPrice.setTextColor(Color.parseColor("#2E7D32"));
            tvPrice.setPadding(0, dp(4), 0, 0);
            info.addView(tvPrice);
            card.addView(info);

            // Qty control
            LinearLayout qtyBox = new LinearLayout(this);
            qtyBox.setOrientation(LinearLayout.HORIZONTAL);
            qtyBox.setGravity(Gravity.CENTER_VERTICAL);
            qtyBox.setBackgroundColor(Color.parseColor("#F5F5F5"));
            qtyBox.setPadding(dp(6), dp(6), dp(6), dp(6));

            TextView btnMinus = qtyBtn("−", Color.parseColor("#E53935"));
            TextView tvQty    = new TextView(this);
            TextView btnPlus  = qtyBtn("+", Color.parseColor("#2E7D32"));

            tvQty.setText(String.valueOf(quantities.get(k)));
            tvQty.setTextSize(14f); tvQty.setTypeface(null, Typeface.BOLD);
            tvQty.setTextColor(Color.parseColor("#212121"));
            tvQty.setGravity(Gravity.CENTER);
            tvQty.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));

            btnMinus.setOnClickListener(v -> {
                int q = quantities.get(idx);
                if (q > 1) {
                    quantities.set(idx, q - 1);
                    tvQty.setText(String.valueOf(quantities.get(idx)));
                    updatePriceText(info, idx);
                    recalculate();
                }
            });
            btnPlus.setOnClickListener(v -> {
                int q = quantities.get(idx);
                if (q < 20) {
                    quantities.set(idx, q + 1);
                    tvQty.setText(String.valueOf(quantities.get(idx)));
                    updatePriceText(info, idx);
                    recalculate();
                }
            });

            qtyBox.addView(btnMinus);
            qtyBox.addView(tvQty);
            qtyBox.addView(btnPlus);
            card.addView(qtyBox);
            cartListLayout.addView(card);
        }
    }

    private void updatePriceText(LinearLayout info, int idx) {
        if (info.getChildCount() >= 3) {
            TextView tvP = (TextView) info.getChildAt(2);
            double t = itemValues.get(idx) * quantities.get(idx);
            tvP.setText("Rs" + String.format("%.0f", itemValues.get(idx))
                    + " × " + quantities.get(idx)
                    + " = Rs" + String.format("%.0f", t));
        }
    }

    private void removeItem(int idx) {
        if (idx >= itemNames.size()) return;
        itemNames.remove(idx);
        itemPrices.remove(idx);
        itemValues.remove(idx);
        itemRacks.remove(idx);
        itemFloors.remove(idx);
        itemCats.remove(idx);
        quantities.remove(idx);
        rebuildCartRows();
        recalculate();
        if (itemNames.isEmpty() && tvEmptyMsg != null)
            tvEmptyMsg.setVisibility(View.VISIBLE);
    }

    private void recalculate() {
        double subtotal = 0; totalDiscount = 0;
        for (int k = 0; k < itemValues.size(); k++) {
            double u = itemValues.get(k); int q = quantities.get(k);
            subtotal += u * q;
            String offer = MapActivity.OFFERS.get(itemCats.get(k));
            if (offer != null) {
                double disc = u - MapActivity.getDiscountedPriceStatic(u, offer);
                totalDiscount += disc * q;
            }
        }
        double discSub = subtotal - totalDiscount;
        double gst = discSub * 0.05, total = discSub + gst;
        if (tvSubtotal != null) tvSubtotal.setText("Rs" + String.format("%.2f", subtotal));
        if (tvDiscount != null) {
            tvDiscount.setText("- Rs" + String.format("%.2f", totalDiscount));
            tvDiscount.setVisibility(totalDiscount > 0 ? View.VISIBLE : View.GONE);
        }
        if (tvGst != null)   tvGst.setText("Rs" + String.format("%.2f", gst));
        if (tvTotal != null) tvTotal.setText("Rs" + String.format("%.2f", total));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RECEIPT SCREEN
    // ══════════════════════════════════════════════════════════════════════════

    private void showReceipt() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F5F5F5"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(screenHeader("Bill Receipt", this::buildCartScreen));

        LinearLayout receipt = makeCard(dp(16), dp(16));
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String store = pref.getString("user_store", "FindCartx1 Mega Store");
        String name  = pref.getString("user_name",  "Customer");

        centerText(receipt, "FindCartx1", 20f, Color.parseColor("#2E7D32"), true);
        centerText(receipt, store, 12f, Color.parseColor("#757575"), false);
        centerText(receipt, "─────────────────────────", 8f, Color.parseColor("#BDBDBD"), false);

        String orderId = "FCX1-" + System.currentTimeMillis() % 100000;
        String dateStr = new SimpleDateFormat("dd MMM yyyy  hh:mm a",
                Locale.getDefault()).format(new Date());

        twoCol(receipt, "Order ID", orderId);
        twoCol(receipt, "Date", dateStr);
        twoCol(receipt, "Customer", name);
        centerText(receipt, "─────────────────────────", 8f, Color.parseColor("#BDBDBD"), false);

        double subtotal = 0;
        for (int k = 0; k < itemNames.size(); k++) {
            double u = itemValues.get(k); int q = quantities.get(k);
            String offer = MapActivity.OFFERS.get(itemCats.get(k));
            double discUnit = MapActivity.getDiscountedPriceStatic(u, offer);
            double lineTotal = discUnit * q;
            subtotal += lineTotal;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(5), 0, dp(5));

            TextView tvN = new TextView(this);
            tvN.setText(itemNames.get(k)); tvN.setTextSize(12f);
            tvN.setTextColor(Color.parseColor("#212121"));
            tvN.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvN);

            TextView tvQ = new TextView(this);
            tvQ.setText("×" + q); tvQ.setTextSize(12f);
            tvQ.setTextColor(Color.parseColor("#424242")); tvQ.setGravity(Gravity.CENTER);
            tvQ.setLayoutParams(new LinearLayout.LayoutParams(dp(36),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tvQ);

            LinearLayout priceCol = new LinearLayout(this);
            priceCol.setOrientation(LinearLayout.VERTICAL); priceCol.setGravity(Gravity.END);
            priceCol.setLayoutParams(new LinearLayout.LayoutParams(dp(72),
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            if (offer != null && discUnit < u) {
                TextView tvOrig = new TextView(this);
                tvOrig.setText("Rs" + String.format("%.0f", u * q));
                tvOrig.setTextSize(9.5f); tvOrig.setTextColor(Color.parseColor("#E53935"));
                tvOrig.setPaintFlags(tvOrig.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvOrig.setGravity(Gravity.END);
                priceCol.addView(tvOrig);
            }
            TextView tvP = new TextView(this);
            tvP.setText("Rs" + String.format("%.0f", lineTotal));
            tvP.setTextSize(12f); tvP.setTypeface(null, Typeface.BOLD);
            tvP.setTextColor(Color.parseColor("#2E7D32")); tvP.setGravity(Gravity.END);
            priceCol.addView(tvP);
            row.addView(priceCol);
            receipt.addView(row);
        }

        centerText(receipt, "─────────────────────────", 8f, Color.parseColor("#BDBDBD"), false);
        double gst = subtotal * 0.05, total = subtotal + gst;
        twoCol(receipt, "Subtotal (after offers)", "Rs" + String.format("%.2f", subtotal));
        twoCol(receipt, "GST @ 5%", "Rs" + String.format("%.2f", gst));

        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setBackgroundColor(Color.parseColor("#E8F5E9"));
        totalRow.setPadding(dp(8), dp(10), dp(8), dp(10));
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.topMargin = dp(6); totalRow.setLayoutParams(trLp);
        TextView tvTL = new TextView(this);
        tvTL.setText("GRAND TOTAL"); tvTL.setTextSize(14f);
        tvTL.setTypeface(null, Typeface.BOLD); tvTL.setTextColor(Color.parseColor("#1B5E20"));
        tvTL.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        totalRow.addView(tvTL);
        TextView tvTV = new TextView(this);
        tvTV.setText("Rs" + String.format("%.2f", total));
        tvTV.setTextSize(16f); tvTV.setTypeface(null, Typeface.BOLD);
        tvTV.setTextColor(Color.parseColor("#1B5E20"));
        totalRow.addView(tvTV);
        receipt.addView(totalRow);
        root.addView(receipt);

        final double finalTotal = total;
        final String finalOrderId = orderId;
        View btnPay = buildBtn("CHOOSE PAYMENT  →", Color.parseColor("#2E7D32"));
        btnPay.setOnClickListener(v -> showPaymentScreen(finalTotal, finalOrderId));
        root.addView(btnPay);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
        root.addView(sp);
        sv.addView(root);
        setContentView(sv);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PAYMENT SCREEN
    // ══════════════════════════════════════════════════════════════════════════

    private void showPaymentScreen(double total, String orderId) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F5F5F5"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(screenHeader("Payment", () -> showReceipt()));

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(Color.parseColor("#2E7D32"));
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(20), dp(24), dp(20), dp(24));
        centerText(banner, "Amount to Pay", 13f, Color.parseColor("#C8E6C9"), false);
        centerText(banner, "Rs" + String.format("%.2f", total), 32f, Color.WHITE, true);
        centerText(banner, "Order #" + orderId, 11f, Color.parseColor("#A5D6A7"), false);
        root.addView(banner);

        LinearLayout optCard = makeCard(dp(16), dp(12));
        centerText(optCard, "Select Payment Method", 15f, Color.parseColor("#212121"), true);

        payOption(optCard, "📱", "UPI Payment", "PhonePe, GPay, Paytm, BHIM",
                Color.parseColor("#6200EA"),
                v -> simulatePayment(total, orderId, "UPI"));
        divider(optCard);
        payOption(optCard, "💳", "Debit / Credit Card", "Visa, Mastercard, RuPay",
                Color.parseColor("#1565C0"),
                v -> simulatePayment(total, orderId, "Card"));
        divider(optCard);
        payOption(optCard, "🏦", "Net Banking", "All major banks",
                Color.parseColor("#00695C"),
                v -> simulatePayment(total, orderId, "Net Banking"));
        divider(optCard);
        payOption(optCard, "🏪", "Pay at Counter", "Go to counter and pay cash",
                Color.parseColor("#E65100"),
                v -> {
                    saveOrderToFirestore(orderId, total, "Counter Cash");
                    showThankYou(orderId, total, "Counter Cash");
                });
        root.addView(optCard);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
        root.addView(sp);
        sv.addView(root);
        setContentView(sv);
    }

    private void simulatePayment(double total, String orderId, String method) {
        AlertDialog processing = new AlertDialog.Builder(this)
                .setTitle("Processing Payment")
                .setMessage("Please wait...\nDo not press back.")
                .setCancelable(false).create();
        processing.show();
        new android.os.Handler().postDelayed(() -> {
            processing.dismiss();
            saveOrderToFirestore(orderId, total, method);
            showThankYou(orderId, total, method);
        }, 1500);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SAVE ORDER TO FIRESTORE  ← this is the key Firebase method
    // ══════════════════════════════════════════════════════════════════════════

    private void saveOrderToFirestore(String orderId, double total, String method) {
        try {
            SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            String userEmail = pref.getString("saved_email",  "unknown");
            String userName  = pref.getString("user_name",    "Customer");
            String userCity  = pref.getString("user_city",    "");
            String userStore = pref.getString("user_store",   "");

            FirebaseUser user = mAuth.getCurrentUser();
            String userId = user != null ? user.getUid() : userEmail;

            // Build items list for Firestore
            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (int k = 0; k < itemNames.size(); k++) {
                Map<String, Object> item = new HashMap<>();
                item.put("name",     itemNames.get(k));
                item.put("price",    itemValues.get(k));
                item.put("quantity", quantities.get(k));
                item.put("rack",     itemRacks.get(k));
                item.put("floor",    itemFloors.get(k));
                item.put("category", itemCats.get(k));
                item.put("subtotal", itemValues.get(k) * quantities.get(k));
                itemsList.add(item);
            }

            // Build full order document
            Map<String, Object> order = new HashMap<>();
            order.put("orderId",       orderId);
            order.put("userId",        userId);
            order.put("userEmail",     userEmail);
            order.put("userName",      userName);
            order.put("userCity",      userCity);
            order.put("userStore",     userStore);
            order.put("totalAmount",   total);
            order.put("paymentMethod", method);
            order.put("timestamp",     System.currentTimeMillis());
            order.put("date", new SimpleDateFormat("dd-MM-yyyy HH:mm",
                    Locale.getDefault()).format(new Date()));
            order.put("items",         itemsList);
            order.put("itemCount",     itemNames.size());
            order.put("status",        "completed");

            // Save to Firestore → orders collection → document = orderId
            db.collection("orders").document(orderId)
                    .set(order)
                    .addOnSuccessListener(unused ->
                            android.util.Log.d("Firebase",
                                    "Order saved to Firestore: " + orderId))
                    .addOnFailureListener(e ->
                            android.util.Log.e("Firebase",
                                    "Failed to save order: " + e.getMessage()));

            // Also save to user's subcollection for easy querying
            db.collection("users").document(userId)
                    .collection("orders").document(orderId)
                    .set(order);

            // Also save locally to SharedPrefs as backup
            saveLocalHistory(orderId, total, method, userEmail,
                    userName, userCity, userStore);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLocalHistory(String orderId, double total, String method,
                                  String email, String name, String city, String store) {
        try {
            SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            JSONArray itemsArr = new JSONArray();
            for (int k = 0; k < itemNames.size(); k++) {
                JSONObject item = new JSONObject();
                item.put("name",     itemNames.get(k));
                item.put("price",    itemValues.get(k));
                item.put("quantity", quantities.get(k));
                item.put("rack",     itemRacks.get(k));
                item.put("floor",    itemFloors.get(k));
                item.put("category", itemCats.get(k));
                item.put("subtotal", itemValues.get(k) * quantities.get(k));
                itemsArr.put(item);
            }
            JSONObject order = new JSONObject();
            order.put("orderId",      orderId);
            order.put("userId",       email);
            order.put("userName",     name);
            order.put("userCity",     city);
            order.put("userStore",    store);
            order.put("totalAmount",  total);
            order.put("paymentMethod", method);
            order.put("timestamp",    System.currentTimeMillis());
            order.put("date", new SimpleDateFormat("dd-MM-yyyy HH:mm",
                    Locale.getDefault()).format(new Date()));
            order.put("items",        itemsArr);
            order.put("itemCount",    itemNames.size());
            order.put("status",       "completed");

            String histJson = pref.getString("order_history", "[]");
            JSONArray history = new JSONArray(histJson);
            JSONArray newHistory = new JSONArray();
            newHistory.put(order);
            for (int h = 0; h < Math.min(49, history.length()); h++)
                newHistory.put(history.get(h));
            pref.edit().putString("order_history", newHistory.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  THANK YOU SCREEN
    // ══════════════════════════════════════════════════════════════════════════

    private void showThankYou(String orderId, double total, String method) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));
        root.setGravity(Gravity.CENTER);

        LinearLayout card = makeCard(dp(24), dp(32));
        card.setGravity(Gravity.CENTER);

        TextView tvCheck = new TextView(this);
        tvCheck.setText("✓"); tvCheck.setTextSize(52f);
        tvCheck.setTypeface(null, Typeface.BOLD);
        tvCheck.setTextColor(Color.WHITE); tvCheck.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor("#4CAF50"));
        tvCheck.setBackground(gd);
        LinearLayout.LayoutParams ckLp = new LinearLayout.LayoutParams(dp(90), dp(90));
        ckLp.gravity = Gravity.CENTER_HORIZONTAL; ckLp.bottomMargin = dp(20);
        tvCheck.setLayoutParams(ckLp);
        card.addView(tvCheck);

        centerText(card, "Payment Successful!", 22f,
                Color.parseColor("#2E7D32"), true);
        centerText(card, "Thank you for shopping at FindCartx1",
                13f, Color.parseColor("#757575"), false);
        centerText(card, "Order saved to Firebase ✓",
                11f, Color.parseColor("#4CAF50"), false);

        LinearLayout detCard = makeCard(dp(0), dp(8));
        LinearLayout.LayoutParams dcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dcLp.topMargin = dp(20); detCard.setLayoutParams(dcLp);
        twoCol(detCard, "Order ID", orderId);
        divider(detCard);
        twoCol(detCard, "Amount Paid", "Rs" + String.format("%.2f", total));
        divider(detCard);
        twoCol(detCard, "Payment via", method);
        divider(detCard);
        String dateStr = new SimpleDateFormat("dd MMM yyyy  hh:mm a",
                Locale.getDefault()).format(new Date());
        twoCol(detCard, "Date", dateStr);
        card.addView(detCard);

        View btnShop = buildBtn("BACK TO SHOPPING",
                Color.parseColor("#2E7D32"));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(20); btnShop.setLayoutParams(btnLp);
        btnShop.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("CLEAR_CART", true);
            setResult(RESULT_OK, result);
            finish();
        });
        card.addView(btnShop);
        root.addView(card);
        setContentView(root);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private LinearLayout screenHeader(String title, Runnable onBack) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setBackgroundColor(Color.parseColor("#2E7D32"));
        h.setPadding(dp(14), dp(14), dp(14), dp(14));
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvB = new TextView(this);
        tvB.setText("←"); tvB.setTextSize(22f);
        tvB.setTextColor(Color.WHITE); tvB.setTypeface(null, Typeface.BOLD);
        tvB.setPadding(0, 0, dp(14), 0); tvB.setClickable(true); tvB.setFocusable(true);
        tvB.setOnClickListener(v -> onBack.run());
        h.addView(tvB);
        TextView tvT = new TextView(this);
        tvT.setText(title); tvT.setTextSize(18f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setTextColor(Color.WHITE);
        h.addView(tvT);
        return h;
    }

    private LinearLayout makeCard(int hPad, int vPad) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(10), dp(12), 0);
        c.setLayoutParams(lp);
        c.setPadding(hPad, vPad, hPad, vPad);
        return c;
    }

    private void centerText(LinearLayout p, String text, float size,
                            int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(size);
        tv.setTextColor(color); tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4); tv.setLayoutParams(lp);
        p.addView(tv);
    }

    private TextView summaryRow(LinearLayout p, String label, String val,
                                int color, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView tvL = new TextView(this);
        tvL.setText(label); tvL.setTextSize(13f);
        tvL.setTextColor(Color.parseColor("#757575"));
        tvL.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvL);
        TextView tvV = new TextView(this);
        tvV.setText(val); tvV.setTextSize(14f); tvV.setTextColor(color);
        if (bold) tvV.setTypeface(null, Typeface.BOLD);
        row.addView(tvV);
        p.addView(row);
        return tvV;
    }

    private void twoCol(LinearLayout p, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView tvL = new TextView(this);
        tvL.setText(label); tvL.setTextSize(12f);
        tvL.setTextColor(Color.parseColor("#757575"));
        tvL.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvL);
        TextView tvV = new TextView(this);
        tvV.setText(value); tvV.setTextSize(12f);
        tvV.setTypeface(null, Typeface.BOLD);
        tvV.setTextColor(Color.parseColor("#212121"));
        row.addView(tvV);
        p.addView(row);
    }

    private void row(LinearLayout p, String label, String value, int color, boolean bold) {
        twoCol(p, label, value);
    }

    private void divider(LinearLayout p) {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#F0F0F0"));
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        p.addView(d);
    }

    private TextView qtyBtn(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(16f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.WHITE); tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(color);
        tv.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    private View buildBtn(String text, int color) {
        TextView btn = new TextView(this);
        btn.setText(text); btn.setTextSize(15f);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(Color.WHITE); btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(18), 0, dp(18));
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(16), dp(12), 0);
        btn.setLayoutParams(lp);
        btn.setClickable(true); btn.setFocusable(true);
        return btn;
    }

    private void payOption(LinearLayout p, String icon, String title,
                           String sub, int color, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true); row.setFocusable(true);
        row.setOnClickListener(onClick);
        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon); tvIcon.setTextSize(22f); tvIcon.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL); gd.setColor(color);
        tvIcon.setBackground(gd);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        ilp.setMarginEnd(dp(14)); tvIcon.setLayoutParams(ilp);
        row.addView(tvIcon);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvT = new TextView(this);
        tvT.setText(title); tvT.setTextSize(14f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setTextColor(Color.parseColor("#212121"));
        txt.addView(tvT);
        TextView tvS = new TextView(this);
        tvS.setText(sub); tvS.setTextSize(11f);
        tvS.setTextColor(Color.parseColor("#757575"));
        txt.addView(tvS);
        row.addView(txt);
        TextView tvArr = new TextView(this);
        tvArr.setText("›"); tvArr.setTextSize(22f);
        tvArr.setTextColor(Color.parseColor("#BDBDBD"));
        row.addView(tvArr);
        p.addView(row);
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    private int getCategoryColor(String cat) {
        if (cat == null) return Color.parseColor("#607D8B");
        switch (cat) {
            case "Snacks":        return Color.parseColor("#E65100");
            case "Biscuits":      return Color.parseColor("#BF360C");
            case "Beverages":     return Color.parseColor("#1565C0");
            case "Dairy":         return Color.parseColor("#4527A0");
            case "Chocolates":    return Color.parseColor("#4E342E");
            case "Grains":        return Color.parseColor("#33691E");
            case "Noodles":       return Color.parseColor("#F57F17");
            case "Spices":        return Color.parseColor("#880E4F");
            case "Cleaning":      return Color.parseColor("#006064");
            case "Personal Care": return Color.parseColor("#00695C");
            case "Electronics":   return Color.parseColor("#0277BD");
            case "Stationery":    return Color.parseColor("#37474F");
            case "Kitchenware":   return Color.parseColor("#558B2F");
            case "Baby":          return Color.parseColor("#AD1457");
            case "Appliances":    return Color.parseColor("#4527A0");
            case "Clothing":      return Color.parseColor("#4A148C");
            default:              return Color.parseColor("#2E7D32");
        }
    }

    private String getCategoryEmoji(String cat) {
        if (cat == null) return "🛒";
        switch (cat) {
            case "Snacks":        return "🍟";
            case "Biscuits":      return "🍪";
            case "Beverages":     return "🥤";
            case "Dairy":         return "🥛";
            case "Chocolates":    return "🍫";
            case "Grains":        return "🌾";
            case "Noodles":       return "🍜";
            case "Spices":        return "🌶";
            case "Cleaning":      return "🧹";
            case "Personal Care": return "💆";
            case "Electronics":   return "⚡";
            case "Stationery":    return "📚";
            case "Kitchenware":   return "🍳";
            case "Baby":          return "👶";
            case "Appliances":    return "🔌";
            case "Clothing":      return "👕";
            default:              return "🛒";
        }
    }
}