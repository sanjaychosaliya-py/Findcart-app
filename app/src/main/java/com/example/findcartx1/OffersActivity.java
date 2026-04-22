package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Map;

public class OffersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Offers and Deals");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            Color.parseColor("#2E7D32")));
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F5F5F5"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // ── BANNER ────────────────────────────────────────────────────────────
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(Color.parseColor("#1B5E20"));
        banner.setPadding(dp(20), dp(28), dp(20), dp(24));
        banner.setGravity(Gravity.CENTER);

        TextView t1 = tv("🔥  Today's Best Deals", 21f, Color.WHITE, true);
        t1.setGravity(Gravity.CENTER);
        banner.addView(t1);

        TextView t2 = tv("Tap any offer to browse products on sale",
                12.5f, Color.parseColor("#A5D6A7"), false);
        t2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t2lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t2lp.topMargin = dp(5); t2.setLayoutParams(t2lp);
        banner.addView(t2);
        root.addView(banner);

        // ── PERSONALISED SECTION based on purchase history ───────────────────
        Map<String, Integer> stats = NotificationHelper.getPurchaseStats(this);
        if (!stats.isEmpty()) {
            sectionLabel(root, "⭐  RECOMMENDED FOR YOU");

            // Show top 2 categories from purchase history
            int shown = 0;
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                if (shown >= 2) break;
                String cat   = entry.getKey();
                int    count = entry.getValue();
                String offer = MapActivity.OFFERS.get(cat);
                String badge = offer != null ? offer : "DEAL";
                String color = getCategoryColor(cat);

                offerCard(root,
                        badge,
                        cat + " — Your Favourite! 🏆",
                        "You've bought " + count + " item" + (count > 1 ? "s" : "")
                                + " from " + cat + " · " + badge,
                        color, lighten(color), cat);
                shown++;
            }

            // Send personalised notification
            NotificationHelper.sendPersonalisedOffer(this);
        }

        // ── GROUND FLOOR OFFERS ──────────────────────────────────────────────
        sectionLabel(root, "🏪  GROUND FLOOR DEALS");

        offerCard(root, "20% OFF", "All Snacks and Chips",
                "Balaji, Haldiram's, Pringles and more",
                "#E65100", "#FFF3E0", "Snacks");

        offerCard(root, "BUY 2+1", "All Beverages",
                "Mix and match any 3 drinks",
                "#1565C0", "#E3F2FD", "Beverages");

        offerCard(root, "10% OFF", "Dairy Products",
                "Amul, Mother Dairy and more",
                "#4527A0", "#EDE7F6", "Dairy");

        offerCard(root, "25% OFF", "All Biscuits",
                "Parle, Britannia, Oreo and more",
                "#BF360C", "#FBE9E7", "Biscuits");

        offerCard(root, "FREE", "Dry Fruits with Rs999+ order",
                "Free 100g cashew pack on orders above Rs999",
                "#2E7D32", "#E8F5E9", "Grains");

        // ── 1ST FLOOR OFFERS ─────────────────────────────────────────────────
        sectionLabel(root, "🏬  1ST FLOOR DEALS");

        offerCard(root, "15% OFF", "Electronics Accessories",
                "Cables, chargers, earphones and power banks",
                "#0277BD", "#E1F5FE", "Electronics");

        offerCard(root, "10% OFF", "Kitchenware",
                "Cookware, bottles and storage containers",
                "#558B2F", "#F1F8E9", "Kitchenware");

        offerCard(root, "Rs100 OFF", "Baby Products",
                "On purchase above Rs499",
                "#AD1457", "#FCE4EC", "Baby");

        offerCard(root, "20% OFF", "Personal Care",
                "Shampoo, lotion, soap and more",
                "#00695C", "#E0F2F1", "Personal Care");

        offerCard(root, "12% OFF", "Stationery",
                "Notebooks, pens, calculators and more",
                "#37474F", "#ECEFF1", "Stationery");

        // ── EXCLUSIVE DEALS ───────────────────────────────────────────────────
        sectionLabel(root, "💎  EXCLUSIVE DEALS");

        exclusiveCard(root, "Rs50 OFF",
                "Orders above Rs500",
                "Use code FCX1SAVE50 at billing",
                "#C62828");

        exclusiveCard(root, "EARLY BIRD",
                "Shop 8AM to 10AM",
                "Extra 5% off all items in morning hours",
                "#F57F17");

        exclusiveCard(root, "2X POINTS",
                "Loyalty Points Today",
                "Double points on every purchase today only",
                "#6A1B9A");

        exclusiveCard(root, "FREE DEL",
                "Home Delivery",
                "Free delivery on orders above Rs999",
                "#00695C");

        // Bottom spacer
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        root.addView(sp);

        sv.addView(root);
        setContentView(sv);
    }

    // ── Offer card with colored badge + footer strip ──────────────────────────
    private void offerCard(LinearLayout parent, String badge, String title,
                           String subtitle, String badgeColor,
                           String bgColor, String category) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(bgColor));
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(14), 0, dp(14), dp(10));
        card.setLayoutParams(cardLp);
        card.setClickable(true); card.setFocusable(true);
        card.setElevation(dp(2));

        // Content row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Badge box
        LinearLayout badgeBox = new LinearLayout(this);
        badgeBox.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor(badgeColor));
        badgeBg.setCornerRadius(dp(8));
        badgeBox.setBackground(badgeBg);
        badgeBox.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(60)));
        TextView tvBadge = tv(badge, 10.5f, Color.WHITE, true);
        tvBadge.setGravity(Gravity.CENTER);
        tvBadge.setPadding(dp(3), dp(3), dp(3), dp(3));
        badgeBox.addView(tvBadge);
        row.addView(badgeBox);

        // Text
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams txlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txlp.setMarginStart(dp(14));
        txt.setLayoutParams(txlp);
        txt.addView(tv(title, 14f, Color.parseColor("#212121"), true));
        TextView tvSub = tv(subtitle, 11.5f, Color.parseColor("#616161"), false);
        LinearLayout.LayoutParams sublp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sublp.topMargin = dp(3); tvSub.setLayoutParams(sublp);
        txt.addView(tvSub);
        row.addView(txt);

        // Arrow
        TextView tvArrow = tv("›", 28f, Color.parseColor(badgeColor), true);
        row.addView(tvArrow);
        card.addView(row);

        // Footer strip
        LinearLayout footer = new LinearLayout(this);
        GradientDrawable footerBg = new GradientDrawable();
        footerBg.setColor(Color.parseColor(badgeColor));
        int[] radii = {0, 0, 0, 0, dp(10), dp(10), dp(10), dp(10)};
        footerBg.setCornerRadii(new float[]{0,0,0,0,dp(10),dp(10),dp(10),dp(10)});
        footer.setBackground(footerBg);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(7), 0, dp(7));
        TextView tvShop = tv("TAP TO VIEW PRODUCTS  →", 10f, Color.WHITE, true);
        tvShop.setGravity(Gravity.CENTER);
        tvShop.setLetterSpacing(0.05f);
        footer.addView(tvShop);
        card.addView(footer);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra("OFFER_CATEGORY", category);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        parent.addView(card);
    }

    // ── Exclusive deals card (no navigation, just info) ───────────────────────
    private void exclusiveCard(LinearLayout parent, String badge,
                               String title, String subtitle, String color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(10));
        cardBg.setStroke(dp(1), Color.parseColor("#E0E0E0"));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(14), 0, dp(14), dp(10));
        card.setLayoutParams(lp);
        card.setElevation(dp(1));

        // Badge
        LinearLayout badgeBox = new LinearLayout(this);
        badgeBox.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor(color));
        badgeBg.setCornerRadius(dp(8));
        badgeBox.setBackground(badgeBg);
        badgeBox.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView tvB = tv(badge, 9.5f, Color.WHITE, true);
        tvB.setGravity(Gravity.CENTER); tvB.setPadding(dp(4),dp(4),dp(4),dp(4));
        badgeBox.addView(tvB);
        card.addView(badgeBox);

        // Text
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams txlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txlp.setMarginStart(dp(14)); txt.setLayoutParams(txlp);
        txt.addView(tv(title, 13.5f, Color.parseColor("#212121"), true));
        TextView tvSub = tv(subtitle, 11.5f, Color.parseColor(color), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(3); tvSub.setLayoutParams(slp);
        txt.addView(tvSub);
        card.addView(txt);

        card.setOnClickListener(v ->
                Toast.makeText(this, "Applied automatically at checkout! 🎉",
                        Toast.LENGTH_LONG).show());

        parent.addView(card);
    }

    // ── Section label ─────────────────────────────────────────────────────────
    private void sectionLabel(LinearLayout parent, String label) {
        TextView tv = tv(label, 11.5f, Color.parseColor("#424242"), true);
        tv.setLetterSpacing(0.05f);
        tv.setPadding(dp(16), dp(18), dp(16), dp(8));
        parent.addView(tv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private String getCategoryColor(String cat) {
        switch (cat) {
            case "Snacks":       return "#E65100";
            case "Beverages":    return "#1565C0";
            case "Dairy":        return "#4527A0";
            case "Biscuits":     return "#BF360C";
            case "Electronics":  return "#0277BD";
            case "Kitchenware":  return "#558B2F";
            case "Baby":         return "#AD1457";
            case "Personal Care":return "#00695C";
            case "Stationery":   return "#37474F";
            case "Clothing":     return "#4A148C";
            case "Chocolates":   return "#4E342E";
            case "Cleaning":     return "#006064";
            case "Grains":       return "#33691E";
            default:             return "#2E7D32";
        }
    }

    private String lighten(String hexColor) {
        // Return a very light version for card background
        switch (hexColor) {
            case "#E65100": return "#FFF3E0";
            case "#1565C0": return "#E3F2FD";
            case "#4527A0": return "#EDE7F6";
            case "#BF360C": return "#FBE9E7";
            case "#0277BD": return "#E1F5FE";
            case "#558B2F": return "#F1F8E9";
            case "#AD1457": return "#FCE4EC";
            case "#00695C": return "#E0F2F1";
            case "#37474F": return "#ECEFF1";
            case "#4A148C": return "#F3E5F5";
            case "#4E342E": return "#EFEBE9";
            case "#006064": return "#E0F7FA";
            case "#33691E": return "#F9FBE7";
            default:        return "#E8F5E9";
        }
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}