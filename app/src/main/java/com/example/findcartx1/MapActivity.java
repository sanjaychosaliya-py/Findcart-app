package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.tabs.TabLayout;
import java.util.*;

public class MapActivity extends AppCompatActivity {

    private com.google.firebase.firestore.ListenerRegistration profileListener;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG       = Color.parseColor("#C5CAE9");
    private static final int C_RACK_GF  = Color.parseColor("#1565C0");
    private static final int C_RACK_FF  = Color.parseColor("#6A1B9A");
    private static final int C_SELECTED = Color.parseColor("#4CAF50");
    private static final int C_OOS      = Color.parseColor("#E53935");
    private static final int C_ENTRY    = Color.parseColor("#FFC107");
    private static final int C_CHECKOUT = Color.parseColor("#FF7043");
    private static final int C_STAIRS   = Color.parseColor("#8D6E63");

    // ── Data ──────────────────────────────────────────────────────────────────
    private final List<Product>     allProducts     = new ArrayList<>();
    private final List<Product>     cartItems       = new ArrayList<>();
    private final Set<String>       activeRacks     = new HashSet<>();
    private final Map<String, View> groundRackViews = new HashMap<>();
    private final Map<String, View> firstRackViews  = new HashMap<>();

    // ── UI refs ───────────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private TextView     tvCartBadge;
    private EditText     etSearch;
    private GridLayout   productGrid;
    private LinearLayout chipContainer;
    private String       selectedCategory = "All";
    private int          screenW;

    // ── Public static OFFERS map so CartActivity can access it ───────────────
    public static final Map<String, String> OFFERS = new HashMap<>();
    static {
        OFFERS.put("Snacks",       "20% OFF");
        OFFERS.put("Beverages",    "BUY 2+1");
        OFFERS.put("Dairy",        "10% OFF");
        OFFERS.put("Biscuits",     "25% OFF");
        OFFERS.put("Electronics",  "15% OFF");
        OFFERS.put("Kitchenware",  "10% OFF");
        OFFERS.put("Baby",         "Rs100 OFF");
        OFFERS.put("Personal Care","20% OFF");
        OFFERS.put("Stationery",   "12% OFF");
        OFFERS.put("Sports",       "15% OFF");
        OFFERS.put("Toys",         "10% OFF");
    }

    public static double getDiscountedPriceStatic(double original, String offer) {
        if (offer == null) return original;
        if (offer.contains("%")) {
            try {
                String digits = offer.replaceAll("[^0-9]", "").trim();
                int pct = Integer.parseInt(digits.length() >= 2 ? digits.substring(0, 2) : digits);
                return original * (1.0 - pct / 100.0);
            } catch (Exception e) { return original; }
        } else if (offer.startsWith("Rs") && offer.contains("OFF")) {
            try {
                double off = Double.parseDouble(offer.replaceAll("[^0-9]", ""));
                return Math.max(0, original - off);
            } catch (Exception e) { return original; }
        }
        return original;
    }

    // Real-time listener — auto updates sidebar when Firestore changes


    // ══════════════════════════════════════════════════════════════════════════
    //  onCreate
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_map);

        screenW       = getResources().getDisplayMetrics().widthPixels;
        drawerLayout  = findViewById(R.id.drawerLayout);
        tvCartBadge   = findViewById(R.id.tvCartBadge);
        etSearch      = findViewById(R.id.etSearch);
        productGrid   = findViewById(R.id.productGrid);
        chipContainer = findViewById(R.id.chipContainer);

        View ivMenu  = findViewById(R.id.ivMenu);
        View btnCart = findViewById(R.id.btnViewCart);
        View ivHelp  = findViewById(R.id.ivHelp);

        if (ivMenu  != null) ivMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        if (btnCart != null) btnCart.setOnClickListener(v -> showCartDialog());
        if (ivHelp  != null) ivHelp.setOnClickListener(v -> showLegend());

        loadToolbarProfile();

        View fabGround = findViewById(R.id.fabCartGround);
        View fabFirst  = findViewById(R.id.fabCartFirst);
        if (fabGround != null) fabGround.setOnClickListener(v -> showCartDialog());
        if (fabFirst  != null) fabFirst.setOnClickListener(v -> showCartDialog());

        setupSidebarUser();
        setupSidebarMenu();
        buildProducts();
        buildCategoryChips();
        renderProductGrid(allProducts);
        buildGroundFloorMap();
        buildFirstFloorMap();
        setupTabs();
        setupSearch();

        String offerCat = getIntent().getStringExtra("OFFER_CATEGORY");
        if (offerCat != null && !offerCat.isEmpty()) {
            selectedCategory = offerCat;
            buildCategoryChips();
            applyFilters();
            TabLayout tabs = findViewById(R.id.tabLayout);
            if (tabs != null && tabs.getTabCount() > 0) {
                TabLayout.Tab t = tabs.getTabAt(0);
                if (t != null) t.select();
            }
        }

        if (getIntent().getBooleanExtra("CLEAR_CART", false)) {
            clearCart();
            Toast.makeText(this, "Order placed! Cart cleared.", Toast.LENGTH_LONG).show();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ══════════════════════════════════════════════════════════════════════════

    private void setupSidebarUser() {
        SharedPreferences p = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String email    = p.getString("saved_email", "user@findcartx1.com");
        String name     = p.getString("user_name", "");
        String photoUri = p.getString("profile_photo_uri", null);
        String photoB64 = p.getString("profile_photo_b64", null);

        TextView te = findViewById(R.id.tvUserEmail);
        TextView ti = findViewById(R.id.tvAvatarInitial);
        if (te != null) te.setText(email);

        View avatarContainer = findViewById(R.id.tvAvatarInitial);
        if (avatarContainer != null
                && avatarContainer.getParent() instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout af =
                    (android.widget.FrameLayout) avatarContainer.getParent();
            View ex = af.findViewWithTag("sidebarPhoto");
            if (ex != null) af.removeView(ex);

            if (photoB64 != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(photoB64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                            .decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        ImageView iv = new ImageView(this);
                        iv.setTag("sidebarPhoto");
                        iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(72), dp(72)));
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iv.setClipToOutline(true);
                        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                            @Override public void getOutline(View v, android.graphics.Outline o) {
                                o.setOval(0, 0, v.getWidth(), v.getHeight());
                            }
                        });
                        iv.setImageBitmap(bmp);
                        af.addView(iv);
                        if (ti != null) ti.setVisibility(View.INVISIBLE);
                    } else { setInitial(ti, name, email); }
                } catch (Exception e) { setInitial(ti, name, email); }
            } else if (photoUri != null) {
                try {
                    ImageView iv = new ImageView(this);
                    iv.setTag("sidebarPhoto");
                    iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(72), dp(72)));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setClipToOutline(true);
                    iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override public void getOutline(View v, android.graphics.Outline o) {
                            o.setOval(0, 0, v.getWidth(), v.getHeight());
                        }
                    });
                    iv.setImageURI(Uri.parse(photoUri));
                    af.addView(iv);
                    if (ti != null) ti.setVisibility(View.INVISIBLE);
                } catch (Exception e) { setInitial(ti, name, email); }
            } else { setInitial(ti, name, email); }
        } else { setInitial(ti, name, email); }

        com.google.firebase.auth.FirebaseUser fbUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            profileListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(fbUser.getUid())
                    .addSnapshotListener((doc, error) -> {
                        if (doc != null && doc.exists()) {
                            SharedPreferences.Editor ed =
                                    getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();
                            if (doc.getString("name")     != null) ed.putString("user_name",   doc.getString("name"));
                            if (doc.getString("mobile")   != null) ed.putString("user_mobile", doc.getString("mobile"));
                            if (doc.getString("city")     != null) ed.putString("user_city",   doc.getString("city"));
                            if (doc.getString("store")    != null) ed.putString("user_store",  doc.getString("store"));
                            if (doc.getString("dob")      != null) ed.putString("user_dob",    doc.getString("dob"));
                            if (doc.getString("gender")   != null) ed.putString("user_gender", doc.getString("gender"));
                            if (doc.getString("photoB64") != null) ed.putString("profile_photo_b64", doc.getString("photoB64"));
                            ed.apply();
                            // Refresh toolbar photo
                            runOnUiThread(this::loadToolbarProfile);
                        }
                    });
        }
    }

    private void setInitial(TextView ti, String name, String email) {
        if (ti == null) return;
        ti.setVisibility(View.VISIBLE);
        String src = name.isEmpty() ? email : name;
        ti.setText(src.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(src.charAt(0))));
    }

    private void setupSidebarMenu() {
        nav(R.id.menuProfile, AccountDetailsActivity.class);
        nav(R.id.menuOffers,  OffersActivity.class);
        nav(R.id.menuHistory, HistoryActivity.class);
        View ml = findViewById(R.id.menuLogout);
        if (ml != null) ml.setOnClickListener(v -> { drawerLayout.closeDrawers(); confirmLogout(); });
    }

    private void nav(int id, Class<?> cls) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> {
            startActivity(new Intent(this, cls));
            drawerLayout.closeDrawers();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TABS
    // ══════════════════════════════════════════════════════════════════════════

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.tabLayout);
        if (tabs == null) return;
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                show(R.id.productView,     pos == 0);
                show(R.id.groundFloorView, pos == 1);
                show(R.id.firstFloorView,  pos == 2);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void show(int id, boolean vis) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUND FLOOR MAP
    // ══════════════════════════════════════════════════════════════════════════

    private void buildGroundFloorMap() {
        LinearLayout container = findViewById(R.id.groundMapContainer);
        if (container == null) return;
        container.removeAllViews();

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        sv.setFillViewport(true);

        RelativeLayout map = new RelativeLayout(this);
        map.setBackgroundColor(C_BG);
        map.setLayoutParams(new ScrollView.LayoutParams(screenW, dp(560)));

        // Checkout row
        int cW = dp(28), cH = dp(14), cGap = dp(4);
        int cStartX = (screenW - (8 * cW + 7 * cGap)) / 2;
        for (int i = 0; i < 8; i++) {
            View cv = rackView(cW, cH, C_CHECKOUT, "C" + (i + 1), 7f);
            RelativeLayout.LayoutParams lp = absLp(cW, cH);
            lp.leftMargin = cStartX + i * (cW + cGap);
            lp.topMargin  = dp(10);
            map.addView(cv, lp);
        }

        // Exit
        View exit = solidRect(dp(38), dp(6), C_ENTRY);
        RelativeLayout.LayoutParams eLp = absLp(dp(38), dp(6));
        eLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        eLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        map.addView(exit, eLp);

        // Entry
        View entry = solidRect(dp(6), dp(50), C_ENTRY);
        RelativeLayout.LayoutParams entLp = absLp(dp(6), dp(50));
        entLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        entLp.topMargin = dp(175);
        map.addView(entry, entLp);

        // A racks — left wall
        int[] aTops = {dp(50), dp(112), dp(310), dp(372)};
        for (int i = 0; i < 4; i++) {
            String rid = "A" + (i + 1);
            View av = rackView(dp(14), dp(52), C_RACK_GF, rid, 7f);
            RelativeLayout.LayoutParams lp = absLp(dp(14), dp(52));
            lp.leftMargin = 0; lp.topMargin = aTops[i];
            map.addView(av, lp);
            groundRackViews.put("ground_" + rid, av);
            av.setOnClickListener(v -> showRackDialog(rid, "ground"));
        }

        // P O N M L fridges — right wall
        int frW = dp(18), frH = dp(36);
        int[] frTops = {dp(38), dp(80), dp(122), dp(164), dp(206)};
        String[] frIds = {"P", "O", "N", "M", "L"};
        for (int i = 0; i < 5; i++) {
            View fv = rackView(frW, frH, C_RACK_GF, frIds[i], 7f);
            RelativeLayout.LayoutParams lp = absLp(frW, frH);
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.topMargin = frTops[i];
            map.addView(fv, lp);
            groundRackViews.put("ground_" + frIds[i], fv);
            final String rfr = frIds[i];
            fv.setOnClickListener(v -> showRackDialog(rfr, "ground"));
        }

        // K grain storage — right wall 2x2
        int ksz = dp(18), kGap = dp(3);
        String[] kIds = {"K1", "K2", "K3", "K4"};
        int[] kRows = {dp(258), dp(283)};
        int ki = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                View kv = rackView(ksz, ksz, C_RACK_GF, kIds[ki], 7f);
                RelativeLayout.LayoutParams lp = absLp(ksz, ksz);
                lp.topMargin   = kRows[row];
                lp.rightMargin = (col == 0) ? (ksz + kGap) : 0;
                lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                map.addView(kv, lp);
                groundRackViews.put("ground_" + kIds[ki], kv);
                final String rk = kIds[ki];
                kv.setOnClickListener(v -> showRackDialog(rk, "ground"));
                ki++;
            }
        }

        // Center racks calculation
        int rW = dp(24), rH = dp(44), rGap = dp(4), aisleW = dp(40);
        int groupW     = rW * 2 + rGap;
        int totalRackW = groupW * 2 + aisleW;
        int leftWall   = dp(14), rightWall = screenW - dp(18);
        int centerX    = (leftWall + rightWall) / 2;
        int sx         = centerX - totalRackW / 2;
        int lg1X = sx, lg2X = sx + rW + rGap;
        int rg1X = sx + groupW + aisleW, rg2X = sx + groupW + aisleW + rW + rGap;

        // B racks — 3 rows
        int[] bTops = {dp(38), dp(88), dp(138)};
        String[][] bGrps = {
                {"B1","B2","B3"}, {"B4","B5","B6"}, {"B7","B8","B9"}, {"E1","E2","E3"}
        };
        for (int i = 0; i < 3; i++) {
            addGRack(map, rW, rH, lg1X, bTops[i], bGrps[0][i]);
            addGRack(map, rW, rH, lg2X, bTops[i], bGrps[1][i]);
            addGRack(map, rW, rH, rg1X, bTops[i], bGrps[2][i]);
            addGRack(map, rW, rH, rg2X, bTops[i], bGrps[3][i]);
        }

        // F+G racks — 2 rows small
        int sW = dp(24), sH = dp(32);
        int[] fTops = {dp(205), dp(242)};
        String[][] fGrps = {{"F1","F2"},{"F3","F4"},{"G1","G2"},{"G3","G4"}};
        for (int i = 0; i < 2; i++) {
            addGRackS(map, sW, sH, lg1X, fTops[i], fGrps[0][i]);
            addGRackS(map, sW, sH, lg2X, fTops[i], fGrps[1][i]);
            addGRackS(map, sW, sH, rg1X, fTops[i], fGrps[2][i]);
            addGRackS(map, sW, sH, rg2X, fTops[i], fGrps[3][i]);
        }

        // D racks — 3 rows
        int[] dTops = {dp(310), dp(360), dp(410)};
        String[][] dGrps = {
                {"D1","D2","D3"}, {"D4","D5","D6"}, {"D7","D8","D9"}, {"E4","E5","E6"}
        };
        for (int i = 0; i < 3; i++) {
            addGRack(map, rW, rH, lg1X, dTops[i], dGrps[0][i]);
            addGRack(map, rW, rH, lg2X, dTops[i], dGrps[1][i]);
            addGRack(map, rW, rH, rg1X, dTops[i], dGrps[2][i]);
            addGRack(map, rW, rH, rg2X, dTops[i], dGrps[3][i]);
        }

        // Q rack — bottom
        int qW = screenW - dp(40);
        View qv = rackView(qW, dp(16), C_RACK_GF, "Q", 8f);
        RelativeLayout.LayoutParams qLp = absLp(qW, dp(16));
        qLp.bottomMargin = dp(8); qLp.leftMargin = dp(20);
        qLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        map.addView(qv, qLp);
        groundRackViews.put("ground_Q", qv);
        qv.setOnClickListener(v -> showRackDialog("Q", "ground"));

        // Stairs button
        LinearLayout stBtn = makeStairsBtn("↑", "1st\nFloor");
        RelativeLayout.LayoutParams stLp = absLp(dp(40), dp(48));
        stLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        stLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stLp.bottomMargin = dp(30); stLp.rightMargin = dp(4);
        map.addView(stBtn, stLp);
        stBtn.setOnClickListener(v -> {
            TabLayout tabs = findViewById(R.id.tabLayout);
            if (tabs != null && tabs.getTabCount() > 2) {
                TabLayout.Tab t = tabs.getTabAt(2);
                if (t != null) t.select();
            }
        });

        sv.addView(map);
        container.addView(sv);
    }

    private void addGRack(RelativeLayout map, int w, int h, int x, int y, String id) {
        View v = rackView(w, h, C_RACK_GF, id, 7f);
        RelativeLayout.LayoutParams lp = absLp(w, h);
        lp.leftMargin = x; lp.topMargin = y;
        map.addView(v, lp);
        groundRackViews.put("ground_" + id, v);
        v.setOnClickListener(x2 -> showRackDialog(id, "ground"));
    }

    private void addGRackS(RelativeLayout map, int w, int h, int x, int y, String id) {
        View v = rackView(w, h, C_RACK_GF, id, 7f);
        RelativeLayout.LayoutParams lp = absLp(w, h);
        lp.leftMargin = x; lp.topMargin = y;
        map.addView(v, lp);
        groundRackViews.put("ground_" + id, v);
        v.setOnClickListener(x2 -> showRackDialog(id, "ground"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  1ST FLOOR MAP
    //  Layout:
    //  LEFT WALL  — I racks (Cleaning) top to bottom
    //  RIGHT WALL — P racks (Clothing) top to bottom
    //  CENTER     — J K L M in 2 columns, 3 rows each
    //  BOTTOM BAR — N O (Appliances/Baby) along bottom wall
    //  Fits on screen without scrolling
    // ══════════════════════════════════════════════════════════════════════════

    private void buildFirstFloorMap() {
        LinearLayout container = findViewById(R.id.firstMapContainer);
        if (container == null) return;
        container.removeAllViews();

        RelativeLayout map = new RelativeLayout(this);
        map.setBackgroundColor(Color.parseColor("#EDE7F6"));
        map.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // ── Dimensions ────────────────────────────────────────────────────────
        int wallW = dp(18), wallH = dp(34), wallG = dp(4);  // wall racks
        int rW = dp(22), rH = dp(34), rGap = dp(4);         // center racks
        int bW = dp(22), bH = dp(28), bGap = dp(4);         // bottom racks
        int aisleW = dp(36);                                  // gap between 2 center groups

        // Center columns X positions
        int groupW   = rW * 2 + rGap;
        int totalW   = groupW * 2 + aisleW;
        int centerX  = screenW / 2;
        int startX   = centerX - totalW / 2;
        int lg1X = startX,                  lg2X = startX + rW + rGap;
        int rg1X = startX + groupW + aisleW, rg2X = startX + groupW + aisleW + rW + rGap;

        // ── Calculate vertical spacing to fill screen evenly ──────────────────
        // We have 4 sections from top to bottom:
        // Section A (J+K) — 3 rows at top
        // Section B (L+M) — 3 rows in upper-middle
        // Section C (R+S) — 1 row small racks
        // Section D (N+O) — 1 row small racks at bottom
        //
        // Wall racks (I left, P right) span full height alongside center sections

        int screenH = getResources().getDisplayMetrics().heightPixels;
        // Subtract header bar (~36dp) and FAB area (~70dp)
        int mapH    = screenH - dp(36) - dp(70);

        // Total height used by 4 sections + stairs
        // A: 3 rows rH + 2 gaps = 3*34 + 2*4 = 110dp
        // B: 3 rows rH + 2 gaps = 110dp
        // C: 1 row bH = 28dp
        // D: 1 row bH = 28dp
        // Total content = 110+110+28+28 = 276dp
        // Available for gaps between sections = mapH - 276dp
        // We have 3 section gaps + top padding + bottom padding
        // Use 5 equal gaps: top, A-B, B-C, C-D, bottom

        int contentH    = dp(110 + 110 + 28 + 28);
        int totalGap    = mapH - contentH;
        int sectionGap  = Math.max(dp(12), totalGap / 5);
        int topPad      = sectionGap;
        int rowGap      = dp(4);

        // ── Section A: J (left group) + K (right group) — top ─────────────────
        int secAY = topPad;
        int[] jTops = {secAY, secAY + rH + rowGap, secAY + (rH + rowGap) * 2};
        String[] jL = {"J1","J2","J3"}, jR = {"J4","J5","J6"};
        String[] kL = {"K1","K2","K3"}, kR = {"K4","K5","K6"};
        for (int i = 0; i < 3; i++) {
            addFRack(map, rW, rH, lg1X, jTops[i], jL[i]);
            addFRack(map, rW, rH, lg2X, jTops[i], jR[i]);
            addFRack(map, rW, rH, rg1X, jTops[i], kL[i]);
            addFRack(map, rW, rH, rg2X, jTops[i], kR[i]);
        }

        // ── Section B: L (left group) + M (right group) — upper middle ─────────
        int secBY = jTops[2] + rH + sectionGap;
        int[] lmTops = {secBY, secBY + rH + rowGap, secBY + (rH + rowGap) * 2};
        String[] lL = {"L1","L2","L3"}, lR = {"L4","L5","L6"};
        String[] mL = {"M1","M2","M3"}, mR = {"M4","M5","M6"};
        for (int i = 0; i < 3; i++) {
            addFRack(map, rW, rH, lg1X, lmTops[i], lL[i]);
            addFRack(map, rW, rH, lg2X, lmTops[i], lR[i]);
            addFRack(map, rW, rH, rg1X, lmTops[i], mL[i]);
            addFRack(map, rW, rH, rg2X, lmTops[i], mR[i]);
        }

        // ── Section C: R + S racks — centered, above N/O ──────────────────────
        int secCY = lmTops[2] + rH + sectionGap;
        String[] rsIds = {"R1","R2","R3","S1","S2","S3"};
        // 6 racks centered with gap between R and S groups
        int rsTotal = 6 * bW + 5 * bGap + dp(12); // extra gap between groups
        int rsStartX = (screenW - rsTotal) / 2;
        for (int i = 0; i < 6; i++) {
            View v = rackView(bW, bH, C_RACK_FF, rsIds[i], 6.5f);
            RelativeLayout.LayoutParams lp = absLp(bW, bH);
            lp.topMargin  = secCY;
            int extraGap  = (i >= 3) ? dp(12) : 0;
            lp.leftMargin = rsStartX + i * (bW + bGap) + extraGap;
            map.addView(v, lp);
            firstRackViews.put("first_" + rsIds[i], v);
            final String rid = rsIds[i];
            v.setOnClickListener(x -> showRackDialog(rid, "first"));
        }

        // ── Section D: N + O racks — centered, above FAB ──────────────────────
        int secDY = secCY + bH + sectionGap;
        String[] nIds = {"N1","N2","N3","N4"};
        String[] oIds = {"O1","O2","O3"};
        // 7 racks (4 N + gap + 3 O) centered
        int noTotal = 7 * bW + 6 * bGap + dp(12);
        int noStartX = (screenW - noTotal) / 2;
        for (int i = 0; i < 4; i++) {
            View v = rackView(bW, bH, C_RACK_FF, nIds[i], 6.5f);
            RelativeLayout.LayoutParams lp = absLp(bW, bH);
            lp.topMargin  = secDY;
            lp.leftMargin = noStartX + i * (bW + bGap);
            map.addView(v, lp);
            firstRackViews.put("first_" + nIds[i], v);
            final String rid = nIds[i];
            v.setOnClickListener(x -> showRackDialog(rid, "first"));
        }
        for (int i = 0; i < 3; i++) {
            View v = rackView(bW, bH, C_RACK_FF, oIds[i], 6.5f);
            RelativeLayout.LayoutParams lp = absLp(bW, bH);
            lp.topMargin  = secDY;
            lp.leftMargin = noStartX + 4 * (bW + bGap) + dp(12) + i * (bW + bGap);
            map.addView(v, lp);
            firstRackViews.put("first_" + oIds[i], v);
            final String rid = oIds[i];
            v.setOnClickListener(x -> showRackDialog(rid, "first"));
        }

        // ── LEFT WALL: I racks spanning full height (Cleaning) ────────────────
        // Distribute 6 racks evenly from top to bottom of content area
        String[] iIds = {"I1","I2","I3","I4","I5","I6"};
        int iSpan     = secDY + bH - topPad; // total span
        int iStep     = iSpan / 5;           // gap between each rack
        for (int i = 0; i < 6; i++) {
            View v = rackView(wallW, wallH, C_RACK_FF, iIds[i], 6.5f);
            RelativeLayout.LayoutParams lp = absLp(wallW, wallH);
            lp.leftMargin = 0;
            lp.topMargin  = topPad + i * iStep;
            map.addView(v, lp);
            firstRackViews.put("first_" + iIds[i], v);
            final String rid = iIds[i];
            v.setOnClickListener(x -> showRackDialog(rid, "first"));
        }

        // ── RIGHT WALL: P racks spanning full height (Clothing) ───────────────
        String[] pIds = {"P1","P2","P3","P4","P5"};
        int pStep = iSpan / 4;
        for (int i = 0; i < 5; i++) {
            View v = rackView(wallW, wallH, Color.parseColor("#00695C"), pIds[i], 6.5f);
            RelativeLayout.LayoutParams lp = absLp(wallW, wallH);
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.topMargin = topPad + i * pStep;
            map.addView(v, lp);
            firstRackViews.put("first_" + pIds[i], v);
            final String rid = pIds[i];
            v.setOnClickListener(x -> showRackDialog(rid, "first"));
        }

        // ── Section labels (small text above each section) ─────────────────────
        addSectionLabel(map, "Personal Care  |  Electronics",
                centerX, jTops[0] - dp(13));
        addSectionLabel(map, "Stationery  |  Kitchenware",
                centerX, lmTops[0] - dp(13));
        addSectionLabel(map, "Books  |  Sports",
                centerX, secCY - dp(13));
        addSectionLabel(map, "Baby  |  Appliances",
                centerX, secDY - dp(13));

        // ── STAIRS bottom right ────────────────────────────────────────────────
        LinearLayout stBtn = makeStairsBtn("↓", "Ground\nFloor");
        RelativeLayout.LayoutParams stLp = absLp(dp(46), dp(48));
        stLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        stLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stLp.bottomMargin = dp(14); stLp.rightMargin = dp(4);
        map.addView(stBtn, stLp);
        stBtn.setOnClickListener(v -> {
            TabLayout tabs = findViewById(R.id.tabLayout);
            if (tabs != null && tabs.getTabCount() > 1) {
                TabLayout.Tab t = tabs.getTabAt(1);
                if (t != null) t.select();
            }
        });

        container.addView(map);
    }

    private void addSectionLabel(RelativeLayout map, String text, int centerX, int y) {
        if (y < 0) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(8.5f);
        tv.setTextColor(Color.parseColor("#9C27B0"));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        int w = dp(200);
        RelativeLayout.LayoutParams lp = absLp(w, dp(14));
        lp.leftMargin = centerX - w / 2;
        lp.topMargin  = Math.max(0, y);
        map.addView(tv, lp);
    }

    private void addFRack(RelativeLayout map, int w, int h, int x, int y, String id) {
        View v = rackView(w, h, C_RACK_FF, id, 7f);
        RelativeLayout.LayoutParams lp = absLp(w, h);
        lp.leftMargin = x; lp.topMargin = y;
        map.addView(v, lp);
        firstRackViews.put("first_" + id, v);
        v.setOnClickListener(x2 -> showRackDialog(id, "first"));
    }

    private LinearLayout makeStairsBtn(String arrow, String label) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(C_STAIRS); gd.setCornerRadius(dp(6));
        btn.setBackground(gd);
        btn.setClickable(true); btn.setFocusable(true);
        TextView ta = new TextView(this);
        ta.setText(arrow); ta.setTextSize(14f);
        ta.setTextColor(Color.WHITE); ta.setTypeface(null, Typeface.BOLD);
        ta.setGravity(Gravity.CENTER); btn.addView(ta);
        TextView tl = new TextView(this);
        tl.setText(label); tl.setTextSize(7.5f);
        tl.setTextColor(Color.WHITE); tl.setGravity(Gravity.CENTER);
        btn.addView(tl);
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RACK DIALOG
    // ══════════════════════════════════════════════════════════════════════════

    private void showRackDialog(String rackId, String floor) {
        List<Product> items = new ArrayList<>();
        for (Product p : allProducts)
            if (p.rackId.equals(rackId) && p.floor.equals(floor)) items.add(p);

        if (items.isEmpty()) {
            Toast.makeText(this, "No products on rack " + rackId, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rack " + rackId + " — "
                + (floor.equals("ground") ? "Ground Floor" : "1st Floor"));

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (Product p : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(p.isSelected ? Color.parseColor("#E8F5E9")
                    : p.isOutOfStock ? Color.parseColor("#FFEBEE") : Color.WHITE);

            TextView tvDot = new TextView(this);
            tvDot.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
            tvDot.setGravity(Gravity.CENTER);
            tvDot.setTextSize(12f);
            tvDot.setTypeface(null, Typeface.BOLD);
            if (p.isOutOfStock) {
                tvDot.setText("✕"); tvDot.setTextColor(Color.WHITE);
                setRoundBg(tvDot, Color.parseColor("#E53935"));
            } else if (p.isSelected) {
                tvDot.setText("✓"); tvDot.setTextColor(Color.WHITE);
                setRoundBg(tvDot, Color.parseColor("#4CAF50"));
            } else {
                tvDot.setText("+"); tvDot.setTextColor(Color.WHITE);
                setRoundBg(tvDot, Color.parseColor("#1565C0"));
            }
            row.addView(tvDot);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ilp.setMarginStart(dp(10));
            info.setLayoutParams(ilp);

            TextView tvName = new TextView(this);
            tvName.setText(p.name); tvName.setTextSize(13f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(p.isOutOfStock ? Color.parseColor("#9E9E9E") : Color.parseColor("#212121"));
            info.addView(tvName);

            TextView tvCat = new TextView(this);
            tvCat.setText(p.category
                    + (p.isOutOfStock ? "  •  OUT OF STOCK" : "")
                    + (p.isSelected   ? "  •  IN CART"      : ""));
            tvCat.setTextSize(10.5f);
            tvCat.setTextColor(p.isOutOfStock ? Color.parseColor("#E53935")
                    : p.isSelected ? Color.parseColor("#388E3C") : Color.parseColor("#757575"));
            info.addView(tvCat);
            row.addView(info);

            TextView tvPrice = new TextView(this);
            tvPrice.setText(p.price); tvPrice.setTextSize(13f);
            tvPrice.setTypeface(null, Typeface.BOLD);
            tvPrice.setTextColor(Color.parseColor("#2E7D32"));
            row.addView(tvPrice);

            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            wrapper.addView(row);
            View div = new View(this);
            div.setBackgroundColor(Color.parseColor("#F0F0F0"));
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            wrapper.addView(div);

            final Product fp = p;
            final LinearLayout fRow = row;
            final TextView fDot = tvDot;
            final TextView fCat = tvCat;

            wrapper.setOnClickListener(v -> {
                if (fp.isOutOfStock) {
                    Toast.makeText(this, fp.name + " out of stock", Toast.LENGTH_SHORT).show();
                    return;
                }
                toggleProduct(fp);
                refreshRackColor(rackId, floor);
                if (fp.isSelected) {
                    fRow.setBackgroundColor(Color.parseColor("#E8F5E9"));
                    fDot.setText("✓"); setRoundBg(fDot, Color.parseColor("#4CAF50"));
                    fCat.setText(fp.category + "  •  IN CART");
                    fCat.setTextColor(Color.parseColor("#388E3C"));
                } else {
                    fRow.setBackgroundColor(Color.WHITE);
                    fDot.setText("+"); setRoundBg(fDot, Color.parseColor("#1565C0"));
                    fCat.setText(fp.category);
                    fCat.setTextColor(Color.parseColor("#757575"));
                }
            });

            ll.addView(wrapper);
        }

        sv.addView(ll);
        builder.setView(sv);
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void setRoundBg(TextView tv, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        tv.setBackground(gd);
    }

    private void toggleProduct(Product p) {
        p.isSelected = !p.isSelected;
        String key = p.floor + "_" + p.rackId;
        if (p.isSelected) {
            activeRacks.add(key);
            if (!cartItems.contains(p)) cartItems.add(p);
            Toast.makeText(this, "Added: " + p.name, Toast.LENGTH_SHORT).show();
        } else {
            cartItems.remove(p);
            boolean still = false;
            for (Product q : cartItems)
                if ((q.floor + "_" + q.rackId).equals(key)) { still = true; break; }
            if (!still) activeRacks.remove(key);
        }
        updateCartBadge();
        applyFilters();
    }

    private void refreshRackColor(String rackId, String floor) {
        String key = floor + "_" + rackId;
        Map<String, View> rMap = floor.equals("ground") ? groundRackViews : firstRackViews;
        View v = rMap.get(key);
        if (v == null) return;
        int base  = floor.equals("ground") ? C_RACK_GF : C_RACK_FF;
        int color = activeRacks.contains(key) ? C_SELECTED : allOos(rackId, floor) ? C_OOS : base;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color); gd.setCornerRadius(dp(4));
        v.setBackground(gd);
    }

    private boolean allOos(String rack, String floor) {
        int t = 0, o = 0;
        for (Product p : allProducts)
            if (p.rackId.equals(rack) && p.floor.equals(floor))
            { t++; if (p.isOutOfStock) o++; }
        return t > 0 && t == o;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRODUCT GRID
    // ══════════════════════════════════════════════════════════════════════════

    private void buildCategoryChips() {
        if (chipContainer == null) return;
        chipContainer.removeAllViews();
        String[] cats = {"All","Snacks","Biscuits","Beverages","Dairy","Chocolates",
                "Grains","Noodles","Spices","Cleaning","Personal Care",
                "Electronics","Stationery","Kitchenware","Baby","Appliances","Clothing"};
        for (String cat : cats) {
            TextView chip = new TextView(this);
            chip.setText(cat);
            chip.setPadding(dp(20), dp(8), dp(20), dp(8));
            chip.setTextSize(11f); chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundColor(cat.equals(selectedCategory)
                    ? Color.parseColor("#2E7D32") : Color.parseColor("#78909C"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(7)); chip.setLayoutParams(lp);
            chip.setClickable(true); chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                selectedCategory = cat;
                for (int i = 0; i < chipContainer.getChildCount(); i++)
                    chipContainer.getChildAt(i).setBackgroundColor(Color.parseColor("#78909C"));
                chip.setBackgroundColor(Color.parseColor("#2E7D32"));
                applyFilters();
            });
            chipContainer.addView(chip);
        }
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilters() {
        String q = etSearch != null ? etSearch.getText().toString().trim().toLowerCase() : "";
        List<Product> r = new ArrayList<>();
        for (Product p : allProducts) {
            boolean catOk  = selectedCategory.equals("All") || p.category.equals(selectedCategory);
            boolean nameOk = q.isEmpty() || p.name.toLowerCase().contains(q);
            if (catOk && nameOk) r.add(p);
        }
        renderProductGrid(r);
    }

    private void renderProductGrid(List<Product> items) {
        if (productGrid == null) return;
        productGrid.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No products found");
            empty.setTextColor(Color.GRAY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(60));
            productGrid.addView(empty);
            return;
        }
        for (Product p : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(11), dp(11), dp(11), dp(11));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0; gp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(gp);
            card.setBackgroundColor(p.isOutOfStock ? Color.parseColor("#FFCDD2")
                    : p.isSelected ? Color.parseColor("#C8E6C9") : Color.parseColor("#F5F5F5"));

            String offerText = OFFERS.get(p.category);
            if (offerText != null && !p.isOutOfStock) {
                LinearLayout offerRow = new LinearLayout(this);
                offerRow.setOrientation(LinearLayout.HORIZONTAL);
                offerRow.setBackgroundColor(Color.parseColor("#FF6F00"));
                offerRow.setPadding(dp(6), dp(3), dp(6), dp(3));
                offerRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams orlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                orlp.bottomMargin = dp(4); offerRow.setLayoutParams(orlp);
                TextView tvOffer = new TextView(this);
                tvOffer.setText(offerText); tvOffer.setTextSize(9.5f);
                tvOffer.setTypeface(null, Typeface.BOLD); tvOffer.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams tolp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                tolp.setMarginEnd(dp(4)); tvOffer.setLayoutParams(tolp);
                offerRow.addView(tvOffer);
                TextView tvTag = new TextView(this);
                tvTag.setText("OFFER"); tvTag.setTextSize(8f);
                tvTag.setTextColor(Color.parseColor("#FFE0B2"));
                offerRow.addView(tvTag);
                card.addView(offerRow);
            }

            addLabel(card, p.floor.equals("ground") ? "Ground Floor" : "1st Floor",
                    8f, Color.WHITE,
                    p.floor.equals("ground") ? Color.parseColor("#1565C0") : Color.parseColor("#6A1B9A"), 0);
            addLabel(card, p.category, 8.5f, Color.WHITE, Color.parseColor("#607D8B"), dp(3));

            TextView tvName = new TextView(this);
            tvName.setText(p.name); tvName.setTextSize(13f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#212121"));
            tvName.setPadding(0, dp(6), 0, dp(2));
            card.addView(tvName);

            // Price with offer
            LinearLayout priceRow = new LinearLayout(this);
            priceRow.setOrientation(LinearLayout.HORIZONTAL);
            priceRow.setGravity(Gravity.CENTER_VERTICAL);

            if (offerText != null && !p.isOutOfStock) {
                double disc = getDiscountedPriceStatic(p.priceValue, offerText);
                if (disc < p.priceValue) {
                    TextView tvOrig = new TextView(this);
                    tvOrig.setText(p.price); tvOrig.setTextSize(10.5f);
                    tvOrig.setTextColor(Color.parseColor("#E53935"));
                    tvOrig.setPaintFlags(tvOrig.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    ol.setMarginEnd(dp(6)); tvOrig.setLayoutParams(ol);
                    priceRow.addView(tvOrig);
                    TextView tvDisc = new TextView(this);
                    tvDisc.setText("Rs" + String.format("%.0f", disc));
                    tvDisc.setTextSize(16f); tvDisc.setTypeface(null, Typeface.BOLD);
                    tvDisc.setTextColor(Color.parseColor("#2E7D32"));
                    priceRow.addView(tvDisc);
                } else {
                    addPriceNormal(priceRow, p.price);
                }
            } else {
                addPriceNormal(priceRow, p.price);
            }
            card.addView(priceRow);

            TextView tvLoc = new TextView(this);
            tvLoc.setText("Rack " + p.rackId + "  " + (p.floor.equals("ground") ? "G Floor" : "1F"));
            tvLoc.setTextSize(9.5f); tvLoc.setTextColor(Color.parseColor("#1565C0"));
            tvLoc.setPadding(0, dp(2), 0, 0);
            card.addView(tvLoc);

            if (p.isOutOfStock) addLabel(card, "OUT OF STOCK", 8.5f,
                    Color.parseColor("#B71C1C"), -1, dp(3));

            TextView btnAdd = new TextView(this);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.topMargin = dp(8); btnAdd.setLayoutParams(bl);
            btnAdd.setGravity(Gravity.CENTER);
            btnAdd.setPadding(0, dp(8), 0, dp(8));
            btnAdd.setTextSize(10.5f); btnAdd.setTypeface(null, Typeface.BOLD);
            btnAdd.setTextColor(Color.WHITE);
            refreshBtn(btnAdd, p);

            card.setOnClickListener(v -> {
                if (p.isOutOfStock) {
                    Toast.makeText(this, p.name + " out of stock", Toast.LENGTH_SHORT).show();
                    return;
                }
                toggleProduct(p);
                card.setBackgroundColor(p.isSelected
                        ? Color.parseColor("#C8E6C9") : Color.parseColor("#F5F5F5"));
                refreshBtn(btnAdd, p);
                refreshRackColor(p.rackId, p.floor);
            });
            card.addView(btnAdd);
            productGrid.addView(card);
        }
    }

    private void addPriceNormal(LinearLayout parent, String price) {
        TextView tv = new TextView(this);
        tv.setText(price); tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#2E7D32"));
        parent.addView(tv);
    }

    private void addLabel(LinearLayout parent, String text, float size,
                          int textColor, int bgColor, int topMargin) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(size); tv.setTextColor(textColor);
        if (bgColor != -1) { tv.setBackgroundColor(bgColor); tv.setPadding(dp(6), dp(2), dp(6), dp(2)); }
        if (topMargin > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = topMargin; tv.setLayoutParams(lp);
        }
        parent.addView(tv);
    }

    private void refreshBtn(TextView btn, Product p) {
        if (p.isOutOfStock) { btn.setText("Unavailable"); btn.setBackgroundColor(Color.parseColor("#BDBDBD")); }
        else if (p.isSelected) { btn.setText("✓  ADDED"); btn.setBackgroundColor(Color.parseColor("#388E3C")); }
        else { btn.setText("+  ADD TO CART"); btn.setBackgroundColor(Color.parseColor("#2E7D32")); }
    }

    // ── Toolbar profile ───────────────────────────────────────────────────────

    private void loadToolbarProfile() {
        SharedPreferences p = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name     = p.getString("user_name", "");
        String email    = p.getString("saved_email", "");
        String photoUri = p.getString("profile_photo_uri", null);

        TextView  tvInitial = findViewById(R.id.tvToolbarInitial);
        ImageView ivPhoto   = findViewById(R.id.ivProfilePhoto);

        if (ivPhoto != null) {
            ivPhoto.setClipToOutline(true);
            ivPhoto.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
        }

        if (photoUri != null && ivPhoto != null) {
            try {
                ivPhoto.setImageURI(Uri.parse(photoUri));
                ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivPhoto.setVisibility(View.VISIBLE);
                if (tvInitial != null) tvInitial.setVisibility(View.GONE);
            } catch (Exception e) { showInitial(tvInitial, ivPhoto, name, email); }
        } else { showInitial(tvInitial, ivPhoto, name, email); }
    }

    private void showInitial(TextView tvInitial, ImageView ivPhoto, String name, String email) {
        if (ivPhoto   != null) ivPhoto.setVisibility(View.GONE);
        if (tvInitial != null) {
            String src = name.isEmpty() ? email : name;
            tvInitial.setText(src.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(src.charAt(0))));
            tvInitial.setVisibility(View.VISIBLE);
        }
    }

    private void updateCartBadge() {
        if (tvCartBadge != null) {
            if (cartItems.isEmpty()) tvCartBadge.setVisibility(View.GONE);
            else { tvCartBadge.setVisibility(View.VISIBLE); tvCartBadge.setText(String.valueOf(cartItems.size())); }
        }
        updateFabBadge(R.id.tvFabBadgeGround);
        updateFabBadge(R.id.tvFabBadgeFirst);
    }

    private void updateFabBadge(int badgeId) {
        TextView badge = findViewById(badgeId);
        if (badge == null) return;
        if (cartItems.isEmpty()) badge.setVisibility(View.GONE);
        else { badge.setVisibility(View.VISIBLE); badge.setText(String.valueOf(cartItems.size())); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CART
    // ══════════════════════════════════════════════════════════════════════════

    private void showCartDialog() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Cart is empty! Add products first.", Toast.LENGTH_SHORT).show();
            return;
        }
        int n = cartItems.size();
        String[] names  = new String[n], prices = new String[n];
        double[] values = new double[n];
        String[] racks  = new String[n], floors = new String[n], cats = new String[n];

        for (int k = 0; k < n; k++) {
            Product p = cartItems.get(k);
            names[k] = p.name; prices[k] = p.price; values[k] = p.priceValue;
            racks[k] = p.rackId; floors[k] = p.floor; cats[k] = p.category;
        }
        Intent i = new Intent(this, CartActivity.class);
        i.putExtra("names", names); i.putExtra("prices", prices); i.putExtra("values", values);
        i.putExtra("racks", racks); i.putExtra("floors", floors); i.putExtra("cats",   cats);
        startActivityForResult(i, 1001);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) profileListener.remove();
    }

    private void clearCart() {
        for (Product p : allProducts) p.isSelected = false;
        cartItems.clear(); activeRacks.clear();
        updateCartBadge(); applyFilters();
        selectedCategory = "All"; buildCategoryChips();
        if (etSearch != null) etSearch.setText("");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("CLEAR_CART", false)) {
                clearCart();
                Toast.makeText(this, "Cart cleared ✓", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LEGEND
    // ══════════════════════════════════════════════════════════════════════════

    private void showLegend() {
        new AlertDialog.Builder(this)
                .setTitle("Map Guide")
                .setMessage("COLORS\n\nBLUE   — Available (tap to browse)\n"
                        + "GREEN  — Item in cart\nRED    — Out of stock\n"
                        + "YELLOW — Entry or Exit\nORANGE — Checkout\nBROWN  — Stairs\n\n"
                        + "HOW TO ADD ITEMS\n\nTap any rack → list appears\n"
                        + "Tap any item to add to cart\n"
                        + "Green tick = in cart  |  Red cross = out of stock")
                .setPositiveButton("Got it!", null).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> doLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doLogout() {
        // 1. Sign out Firebase immediately
        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); }
        catch (Exception ignored) {}

        // 2. Clear SharedPrefs immediately
        // 2. Clear SharedPrefs but KEEP the profile photo
        android.content.SharedPreferences pref =
                getSharedPreferences("UserPrefs", MODE_PRIVATE);
        pref.edit().clear().apply();

        // 3. Clear cart data
        cartItems.clear();
        activeRacks.clear();

        // 4. Sign out Google — then navigate regardless of result
        try {
            com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                    new com.google.android.gms.auth.api.signin.GoogleSignInOptions
                            .Builder(com.google.android.gms.auth.api.signin
                            .GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail().build();
            com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getClient(this, gso)
                    .signOut()
                    .addOnCompleteListener(task -> goToLogin())
                    .addOnFailureListener(e -> goToLogin());
        } catch (Exception e) {
            // If Google signout fails, still go to login
            goToLogin();
        }
    }

    private void goToLogin() {
        try {
            Intent i = new Intent(MapActivity.this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VIEW HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private View rackView(int w, int h, int color, String label, float ts) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new ViewGroup.LayoutParams(w, h));
        tv.setGravity(Gravity.CENTER);
        tv.setText(label); tv.setTextColor(Color.WHITE); tv.setTextSize(ts);
        tv.setTypeface(null, Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color); gd.setCornerRadius(dp(4));
        tv.setBackground(gd);
        tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    private View solidRect(int w, int h, int color) {
        View v = new View(this);
        v.setLayoutParams(new ViewGroup.LayoutParams(w, h));
        v.setBackgroundColor(color);
        return v;
    }

    private RelativeLayout.LayoutParams absLp(int w, int h) {
        return new RelativeLayout.LayoutParams(w, h);
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    private void add(String name, String price, double val,
                     String rack, String floor, boolean oos, String cat) {
        allProducts.add(new Product(name, price, val, rack, floor, oos, cat));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRODUCT CATALOGUE
    // ══════════════════════════════════════════════════════════════════════════

    private void buildProducts() {
        // ── GROUND FLOOR ──────────────────────────────────────────────────────
        // A1 — Snacks left wall top
        add("Balaji Masala Wafers","Rs10",10,"A1","ground",false,"Snacks");
        add("Lay's Magic Masala","Rs20",20,"A1","ground",false,"Snacks");
        add("Balaji Tikha Mitha","Rs10",10,"A1","ground",false,"Snacks");
        add("Haldiram's Peanuts 200g","Rs30",30,"A1","ground",false,"Snacks");
        add("Bikano Namkeen Mix","Rs40",40,"A1","ground",false,"Snacks");
        // A2
        add("Kurkure Masala Munch","Rs20",20,"A2","ground",false,"Snacks");
        add("Bingo Mad Angles","Rs20",20,"A2","ground",true,"Snacks");
        add("Too Yumm Multigrain","Rs30",30,"A2","ground",false,"Snacks");
        add("Haldiram's Bhujia 200g","Rs55",55,"A2","ground",false,"Snacks");
        add("Bingo Tedhe Medhe","Rs20",20,"A2","ground",false,"Snacks");
        // A3
        add("Haldiram's Ratlami Sev","Rs45",45,"A3","ground",false,"Snacks");
        add("Haldiram's Aloo Bhujia","Rs55",55,"A3","ground",false,"Snacks");
        add("Balaji Gathiya","Rs40",40,"A3","ground",false,"Snacks");
        add("Haldiram's Moong Dal","Rs40",40,"A3","ground",false,"Snacks");
        add("Bikaji Bikaneri Bhujia","Rs50",50,"A3","ground",false,"Snacks");
        // A4
        add("Act II Popcorn Butter","Rs30",30,"A4","ground",false,"Snacks");
        add("Haldiram's Khatta Meetha","Rs50",50,"A4","ground",false,"Snacks");
        add("Balaji Chatkazz","Rs10",10,"A4","ground",false,"Snacks");
        add("Kurkure Green Chutney","Rs20",20,"A4","ground",false,"Snacks");
        add("Lays Classic Salted","Rs20",20,"A4","ground",false,"Snacks");
        // B1 — Snacks center
        add("Doritos Nacho Cheese","Rs50",50,"B1","ground",false,"Snacks");
        add("Pringles Original","Rs105",105,"B1","ground",false,"Snacks");
        add("Cornitos Nacho Crisps","Rs45",45,"B1","ground",true,"Snacks");
        add("Parle Wafers Cream Onion","Rs20",20,"B1","ground",false,"Snacks");
        add("Balaji Sev Murmura","Rs15",15,"B1","ground",false,"Snacks");
        // B2
        add("Haldiram Namkeen Mix","Rs60",60,"B2","ground",false,"Snacks");
        add("Bikano Chanachur","Rs45",45,"B2","ground",false,"Snacks");
        add("Haldiram's Kaju Mixture","Rs75",75,"B2","ground",false,"Snacks");
        add("Bikaji Khatta Meetha","Rs40",40,"B2","ground",false,"Snacks");
        add("Aminit Banana Chips 150g","Rs35",35,"B2","ground",false,"Snacks");
        // B3
        add("Parle-G Gold","Rs10",10,"B3","ground",false,"Biscuits");
        add("Britannia Marie Gold","Rs20",20,"B3","ground",false,"Biscuits");
        add("Parle Monaco","Rs15",15,"B3","ground",false,"Biscuits");
        add("Britannia Good Day Cashew","Rs30",30,"B3","ground",false,"Biscuits");
        add("Sunfeast Farmlite Oats","Rs40",40,"B3","ground",false,"Biscuits");
        // B4
        add("Oreo Original","Rs35",35,"B4","ground",false,"Biscuits");
        add("Sunfeast Dark Fantasy","Rs50",50,"B4","ground",false,"Biscuits");
        add("Hide and Seek Fab","Rs40",40,"B4","ground",false,"Biscuits");
        add("Mcvitie's Digestive","Rs55",55,"B4","ground",false,"Biscuits");
        add("Britannia NutriChoice","Rs60",60,"B4","ground",false,"Biscuits");
        // B5
        add("Parle Krackjack","Rs20",20,"B5","ground",false,"Biscuits");
        add("Bourbon Biscuits","Rs30",30,"B5","ground",false,"Biscuits");
        add("50-50 Biscuits","Rs20",20,"B5","ground",false,"Biscuits");
        add("Jim-Jam Biscuits","Rs30",30,"B5","ground",false,"Biscuits");
        add("Tiger Glucose Biscuits","Rs10",10,"B5","ground",false,"Biscuits");
        // B6-B9 chocolates
        add("Cadbury Dairy Milk 50g","Rs50",50,"B6","ground",false,"Chocolates");
        add("Cadbury Dairy Milk Silk","Rs100",100,"B6","ground",false,"Chocolates");
        add("5 Star 40g","Rs20",20,"B6","ground",false,"Chocolates");
        add("KitKat 4F 37g","Rs30",30,"B6","ground",false,"Chocolates");
        add("Munch 35g","Rs20",20,"B6","ground",false,"Chocolates");
        add("Perk 30g","Rs15",15,"B7","ground",false,"Chocolates");
        add("Amul Fruit N Nut 120g","Rs120",120,"B7","ground",false,"Chocolates");
        add("Cadbury Gems Pouch","Rs25",25,"B7","ground",false,"Chocolates");
        add("Nutella 350g","Rs380",380,"B7","ground",false,"Chocolates");
        add("Hershey's Kisses","Rs145",145,"B7","ground",false,"Chocolates");
        add("Snickers 50g","Rs50",50,"B8","ground",false,"Chocolates");
        add("Twix 50g","Rs60",60,"B8","ground",false,"Chocolates");
        add("Mars 50g","Rs60",60,"B8","ground",false,"Chocolates");
        add("Bounty 57g","Rs65",65,"B8","ground",false,"Chocolates");
        add("Toblerone 100g","Rs180",180,"B8","ground",false,"Chocolates");
        add("Amul Dark Chocolate 150g","Rs150",150,"B9","ground",false,"Chocolates");
        add("Lindt Excellence 100g","Rs350",350,"B9","ground",false,"Chocolates");
        add("Bournvita 500g","Rs260",260,"B9","ground",false,"Chocolates");
        add("Horlicks 500g","Rs240",240,"B9","ground",false,"Chocolates");
        add("Complan 500g","Rs280",280,"B9","ground",false,"Chocolates");
        // D1-D9 Noodles + Spices
        add("Maggi 2-Min Masala 12pk","Rs168",168,"D1","ground",false,"Noodles");
        add("Ching's Veg Hakka Noodles","Rs105",105,"D1","ground",false,"Noodles");
        add("Yippee Magic Masala 8pk","Rs90",90,"D1","ground",false,"Noodles");
        add("Top Ramen Curry 4pk","Rs80",80,"D1","ground",true,"Noodles");
        add("Knorr Soupy Noodles","Rs35",35,"D1","ground",false,"Noodles");
        add("Maggi Pazzta Cheese","Rs28",28,"D2","ground",false,"Noodles");
        add("Maggi Veg Atta 4pk","Rs96",96,"D2","ground",false,"Noodles");
        add("Indomie Mi Goreng","Rs45",45,"D2","ground",false,"Noodles");
        add("Wai Wai Noodles 75g","Rs15",15,"D2","ground",false,"Noodles");
        add("Nissin Cup Noodles","Rs35",35,"D2","ground",false,"Noodles");
        add("MDH Chhole Masala 100g","Rs55",55,"D3","ground",false,"Spices");
        add("Everest Kitchen King 100g","Rs68",68,"D3","ground",false,"Spices");
        add("MDH Rajma Masala 100g","Rs60",60,"D3","ground",false,"Spices");
        add("Catch Cumin Seeds 100g","Rs42",42,"D3","ground",false,"Spices");
        add("MDH Garam Masala 100g","Rs72",72,"D3","ground",false,"Spices");
        add("Everest Sambhar Masala","Rs62",62,"D4","ground",false,"Spices");
        add("Maggi Hot and Sweet Sauce","Rs90",90,"D4","ground",false,"Spices");
        add("Ching's Schezwan Chutney","Rs85",85,"D4","ground",false,"Spices");
        add("Kissan Tomato Ketchup 1kg","Rs145",145,"D4","ground",false,"Spices");
        add("Heinz Tomato Ketchup 450g","Rs165",165,"D4","ground",false,"Spices");
        add("Tata Sampann Haldi 200g","Rs55",55,"D5","ground",false,"Spices");
        add("Catch Red Chilli 100g","Rs48",48,"D5","ground",false,"Spices");
        add("MDH Dhaniya Powder 100g","Rs52",52,"D5","ground",false,"Spices");
        add("Everest Meat Masala 100g","Rs72",72,"D5","ground",false,"Spices");
        add("Saffola Oats Masala 400g","Rs95",95,"D5","ground",false,"Spices");
        add("Patanjali Ghee 1L","Rs550",550,"D6","ground",false,"Dairy");
        add("Amul Ghee 1L","Rs585",585,"D6","ground",false,"Dairy");
        add("Fortune Sunflower Oil 1L","Rs145",145,"D6","ground",false,"Spices");
        add("Saffola Gold Oil 1L","Rs185",185,"D6","ground",false,"Spices");
        add("Dhara Mustard Oil 1L","Rs165",165,"D6","ground",false,"Spices");
        add("India Gate Basmati 5kg","Rs550",550,"D7","ground",false,"Grains");
        add("Dawat Basmati 1kg","Rs120",120,"D7","ground",false,"Grains");
        add("Fortune Chakki Atta 10kg","Rs430",430,"D7","ground",false,"Grains");
        add("Aashirvaad Atta 5kg","Rs265",265,"D7","ground",false,"Grains");
        add("Tata Salt 1kg","Rs25",25,"D7","ground",false,"Grains");
        add("Toor Dal 1kg","Rs140",140,"D8","ground",false,"Grains");
        add("Chana Dal 1kg","Rs85",85,"D8","ground",false,"Grains");
        add("Moong Dal 1kg","Rs130",130,"D8","ground",false,"Grains");
        add("Urad Dal 1kg","Rs135",135,"D8","ground",false,"Grains");
        add("Masoor Dal 1kg","Rs100",100,"D8","ground",false,"Grains");
        add("Saffola Oats 400g","Rs90",90,"D9","ground",false,"Grains");
        add("Quaker Oats 1kg","Rs185",185,"D9","ground",false,"Grains");
        add("Kellogg's Corn Flakes 875g","Rs280",280,"D9","ground",false,"Grains");
        add("Muesli Fruit and Nut 400g","Rs210",210,"D9","ground",false,"Grains");
        add("Poha Thick 1kg","Rs45",45,"D9","ground",false,"Grains");
        // E — Beverages
        add("Coca-Cola 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Pepsi 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Thums Up 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Sprite 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Fanta Orange 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Real Mango Juice 1L","Rs85",85,"E2","ground",false,"Beverages");
        add("Paper Boat Aamras 200ml","Rs30",30,"E2","ground",false,"Beverages");
        add("Tropicana Mixed Fruit 1L","Rs90",90,"E2","ground",false,"Beverages");
        add("Minute Maid Guava 1L","Rs75",75,"E2","ground",false,"Beverages");
        add("B Natural Litchi 1L","Rs85",85,"E2","ground",false,"Beverages");
        add("Red Bull Energy 250ml","Rs115",115,"E3","ground",true,"Beverages");
        add("Sting Energy 250ml","Rs20",20,"E3","ground",false,"Beverages");
        add("Monster Energy 500ml","Rs120",120,"E3","ground",false,"Beverages");
        add("Bisleri Water 1L","Rs20",20,"E3","ground",false,"Beverages");
        add("Kinley Water 1L","Rs20",20,"E3","ground",false,"Beverages");
        add("Mountain Dew 600ml","Rs35",35,"E4","ground",false,"Beverages");
        add("7UP 600ml","Rs35",35,"E4","ground",false,"Beverages");
        add("Limca 600ml","Rs35",35,"E4","ground",false,"Beverages");
        add("Maaza Mango 600ml","Rs40",40,"E4","ground",false,"Beverages");
        add("Frooti Mango 600ml","Rs35",35,"E4","ground",false,"Beverages");
        add("Nescafe Classic 50g","Rs230",230,"E5","ground",false,"Beverages");
        add("Bru Coffee 50g","Rs180",180,"E5","ground",false,"Beverages");
        add("Tata Tea Gold 250g","Rs135",135,"E5","ground",false,"Beverages");
        add("Red Label Tea 250g","Rs120",120,"E5","ground",false,"Beverages");
        add("Wagh Bakri Tea 250g","Rs125",125,"E5","ground",false,"Beverages");
        add("Amul Milk Tetra 1L","Rs68",68,"E6","ground",false,"Dairy");
        add("Nestle Munch Milkshake 180ml","Rs30",30,"E6","ground",false,"Beverages");
        add("Lassi Amul Mango 200ml","Rs25",25,"E6","ground",false,"Beverages");
        add("Chaas Amul 200ml","Rs15",15,"E6","ground",false,"Beverages");
        add("Boost Chocolate 500g","Rs245",245,"E6","ground",false,"Beverages");
        // F — Spices small racks
        add("MDH Chhole Masala 50g","Rs35",35,"F1","ground",false,"Spices");
        add("Everest Pav Bhaji Masala","Rs55",55,"F1","ground",false,"Spices");
        add("Shan Biryani Masala","Rs65",65,"F1","ground",false,"Spices");
        add("Badshah Rajwadi Masala","Rs70",70,"F1","ground",false,"Spices");
        add("Eastern Garam Masala","Rs52",52,"F1","ground",false,"Spices");
        add("MDH Rajma Masala","Rs60",60,"F2","ground",false,"Spices");
        add("Catch Cumin Seeds","Rs42",42,"F2","ground",false,"Spices");
        add("Catch Black Pepper Powder","Rs65",65,"F2","ground",false,"Spices");
        add("Everest Tea Masala 50g","Rs45",45,"F2","ground",false,"Spices");
        add("MDH Meat Masala 100g","Rs68",68,"F2","ground",false,"Spices");
        add("Maggi Hot Sweet Sauce","Rs90",90,"F3","ground",false,"Spices");
        add("Ching's Schezwan Sauce","Rs65",65,"F3","ground",false,"Spices");
        add("Kissan Mixed Fruit Jam 700g","Rs165",165,"F3","ground",false,"Spices");
        add("Dr. Oetker Mayo 875g","Rs195",195,"F3","ground",false,"Spices");
        add("Veeba Chipotle Sauce","Rs125",125,"F3","ground",false,"Spices");
        add("Catch Saunf 100g","Rs35",35,"F4","ground",false,"Spices");
        add("MDH Kasoori Methi 25g","Rs30",30,"F4","ground",false,"Spices");
        add("Everest Jeera Powder 100g","Rs48",48,"F4","ground",false,"Spices");
        add("Tata Sampann Haldi 100g","Rs35",35,"F4","ground",false,"Spices");
        add("Catch Hing 10g","Rs28",28,"F4","ground",false,"Spices");
        // G — Snacks small racks
        add("Haldiram Namkeen Mix","Rs60",60,"G1","ground",false,"Snacks");
        add("Bikano Chana Jor Garam","Rs35",35,"G1","ground",false,"Snacks");
        add("Haldiram's Nut Cracker","Rs80",80,"G1","ground",false,"Snacks");
        add("Aminit Soya Sticks","Rs25",25,"G1","ground",false,"Snacks");
        add("Balaji Wafer Pudina","Rs10",10,"G1","ground",false,"Snacks");
        add("Lays Magic Masala 26g","Rs10",10,"G2","ground",false,"Snacks");
        add("Kurkure Triangles","Rs20",20,"G2","ground",false,"Snacks");
        add("Parle Wafers Salted","Rs20",20,"G2","ground",false,"Snacks");
        add("Bingo Yumitos","Rs20",20,"G2","ground",false,"Snacks");
        add("Haldiram Bhujia 150g","Rs45",45,"G2","ground",false,"Snacks");
        add("Pringles Sour Cream","Rs105",105,"G3","ground",false,"Snacks");
        add("Doritos Lime","Rs50",50,"G3","ground",false,"Snacks");
        add("Walkers Crisps","Rs60",60,"G3","ground",false,"Snacks");
        add("Ritz Crackers 250g","Rs95",95,"G3","ground",false,"Snacks");
        add("McVitie's Oats Biscuits","Rs80",80,"G3","ground",false,"Snacks");
        add("Britannia Biscotti","Rs65",65,"G4","ground",false,"Biscuits");
        add("Oreo Golden","Rs45",45,"G4","ground",false,"Biscuits");
        add("Unibic Cashew Cookie","Rs55",55,"G4","ground",false,"Biscuits");
        add("Parle Hide n Seek Milano","Rs60",60,"G4","ground",false,"Biscuits");
        add("Sunfeast Yippee Power Up","Rs35",35,"G4","ground",false,"Biscuits");
        // K — Grains right wall
        add("India Gate Basmati 1kg","Rs120",120,"K1","ground",false,"Grains");
        add("Dawat Rozana 5kg","Rs380",380,"K1","ground",false,"Grains");
        add("Fortune Sona Masoori 5kg","Rs290",290,"K1","ground",false,"Grains");
        add("Kohinoor Basmati 1kg","Rs130",130,"K1","ground",false,"Grains");
        add("24 Mantra Organic Rice 1kg","Rs145",145,"K1","ground",false,"Grains");
        add("Aashirvaad Atta 10kg","Rs520",520,"K2","ground",false,"Grains");
        add("Pillsbury Chakki Atta 5kg","Rs260",260,"K2","ground",false,"Grains");
        add("Rajdhani Besan 1kg","Rs75",75,"K2","ground",false,"Grains");
        add("MDH Besan 500g","Rs45",45,"K2","ground",false,"Grains");
        add("Aashirvaad Multigrain Atta 5kg","Rs300",300,"K2","ground",false,"Grains");
        add("Fortune Chakki Atta 10kg","Rs430",430,"K3","ground",false,"Grains");
        add("Sooji Fine 1kg","Rs40",40,"K3","ground",false,"Grains");
        add("Maida 1kg","Rs35",35,"K3","ground",false,"Grains");
        add("Poha Thin 500g","Rs30",30,"K3","ground",false,"Grains");
        add("Vermicelli 200g","Rs25",25,"K3","ground",false,"Grains");
        add("Tata Salt Lite 1kg","Rs28",28,"K4","ground",false,"Grains");
        add("Catch Black Salt 100g","Rs25",25,"K4","ground",false,"Grains");
        add("Sugar 1kg","Rs45",45,"K4","ground",false,"Grains");
        add("Organic Jaggery 500g","Rs55",55,"K4","ground",false,"Grains");
        add("Raw Honey 250ml","Rs180",180,"K4","ground",false,"Grains");
        // L M N O P — Dairy fridges right wall
        add("Amul Butter 500g","Rs270",270,"P","ground",false,"Dairy");
        add("Amul Butter 100g","Rs56",56,"P","ground",false,"Dairy");
        add("Amul Salted Butter 100g","Rs58",58,"P","ground",false,"Dairy");
        add("Britannia Butter 100g","Rs54",54,"P","ground",false,"Dairy");
        add("Go Butter 100g","Rs62",62,"P","ground",false,"Dairy");
        add("Amul Milk 1L Toned","Rs60",60,"O","ground",false,"Dairy");
        add("Amul Taaza 500ml","Rs30",30,"O","ground",false,"Dairy");
        add("Mother Dairy Full Cream 1L","Rs66",66,"O","ground",false,"Dairy");
        add("Amul Gold Full Cream 1L","Rs68",68,"O","ground",false,"Dairy");
        add("Nandini Milk 1L","Rs60",60,"O","ground",false,"Dairy");
        add("Mother Dairy Curd 400g","Rs48",48,"N","ground",false,"Dairy");
        add("Amul Curd 400g","Rs44",44,"N","ground",false,"Dairy");
        add("Nestle Creamy Curd 400g","Rs52",52,"N","ground",false,"Dairy");
        add("GO Curd 400g","Rs50",50,"N","ground",false,"Dairy");
        add("Epigamia Greek Yogurt 90g","Rs40",40,"N","ground",false,"Dairy");
        add("Amul Paneer 200g","Rs90",90,"M","ground",false,"Dairy");
        add("Nestle Milkmaid 400g","Rs110",110,"M","ground",false,"Dairy");
        add("Mother Dairy Paneer 200g","Rs95",95,"M","ground",false,"Dairy");
        add("Amul Processed Cheese 200g","Rs140",140,"M","ground",false,"Dairy");
        add("Amul Ice Cream Vanilla 1L","Rs280",280,"M","ground",false,"Dairy");
        add("Britannia Cheese Slices","Rs100",100,"L","ground",false,"Dairy");
        add("Amul Mozzarella 200g","Rs120",120,"L","ground",false,"Dairy");
        add("Go Processed Cheese 200g","Rs145",145,"L","ground",false,"Dairy");
        add("Amul Cream 200ml","Rs55",55,"L","ground",false,"Dairy");
        add("Nestle Fresh n Natural Dahi 1kg","Rs95",95,"L","ground",false,"Dairy");
        // Q — bottom rack
        add("Q Rack Bulk Dry Fruits","Rs450",450,"Q","ground",false,"Grains");
        add("Q Rack Mukhwas Mix","Rs80",80,"Q","ground",false,"Snacks");
        add("Q Rack Cashew 200g","Rs280",280,"Q","ground",false,"Grains");
        add("Q Rack Almonds 200g","Rs320",320,"Q","ground",false,"Grains");
        add("Q Rack Raisins 200g","Rs120",120,"Q","ground",false,"Grains");

        // ── 1ST FLOOR ──────────────────────────────────────────────────────────
        // I — Cleaning left wall
        add("Surf Excel Matic 2kg","Rs390",390,"I1","first",false,"Cleaning");
        add("Ariel Matic 4kg","Rs670",670,"I1","first",false,"Cleaning");
        add("Tide Plus 3kg","Rs310",310,"I1","first",false,"Cleaning");
        add("Rin Advance 4kg","Rs270",270,"I1","first",false,"Cleaning");
        add("Ghadi Detergent 4kg","Rs210",210,"I1","first",false,"Cleaning");
        add("Vim Dishwash Bar 300g","Rs42",42,"I2","first",false,"Cleaning");
        add("Pril Liquid 750ml","Rs175",175,"I2","first",false,"Cleaning");
        add("Exo Dishwash Bar 200g","Rs35",35,"I2","first",false,"Cleaning");
        add("Vim Lemon Gel 250ml","Rs90",90,"I2","first",false,"Cleaning");
        add("Scotch-Brite Scrub 3pk","Rs75",75,"I2","first",false,"Cleaning");
        add("Harpic Toilet 1L","Rs175",175,"I3","first",false,"Cleaning");
        add("Domex Floor 1L","Rs145",145,"I3","first",false,"Cleaning");
        add("Lizol Disinfectant 1L","Rs300",300,"I3","first",false,"Cleaning");
        add("Phenyl Black 1L","Rs60",60,"I3","first",false,"Cleaning");
        add("Laxon Floor Cleaner 2L","Rs120",120,"I3","first",false,"Cleaning");
        add("Colin Glass 500ml","Rs155",155,"I4","first",false,"Cleaning");
        add("Odonil Room Freshener 75g","Rs75",75,"I4","first",false,"Cleaning");
        add("Hit Cockroach Spray 200ml","Rs155",155,"I4","first",false,"Cleaning");
        add("Good Knight Coil","Rs50",50,"I4","first",false,"Cleaning");
        add("Febreze Fabric Spray 300ml","Rs349",349,"I4","first",false,"Cleaning");
        add("Scotch-Brite Scrub 2pk","Rs55",55,"I5","first",false,"Cleaning");
        add("Prestige Dishwash Pad 6pk","Rs60",60,"I5","first",false,"Cleaning");
        add("3M Sponge Wipes 3pk","Rs85",85,"I5","first",false,"Cleaning");
        add("Gloves Rubber Pair","Rs45",45,"I5","first",false,"Cleaning");
        add("Mop Refill Cotton 400g","Rs120",120,"I5","first",false,"Cleaning");
        add("Rin Bar 250g","Rs30",30,"I6","first",false,"Cleaning");
        add("Nirma Powder 1kg","Rs65",65,"I6","first",false,"Cleaning");
        add("Surf Excel Bar 200g","Rs40",40,"I6","first",false,"Cleaning");
        add("Active Wheel 1kg","Rs60",60,"I6","first",false,"Cleaning");
        add("Henko Stain 1kg","Rs90",90,"I6","first",false,"Cleaning");
        // J — Personal Care center left
        add("Colgate Strong 200g","Rs120",120,"J1","first",false,"Personal Care");
        add("Colgate MaxFresh 150g","Rs100",100,"J1","first",false,"Personal Care");
        add("Pepsodent 200g","Rs95",95,"J1","first",false,"Personal Care");
        add("Sensodyne 70g","Rs180",180,"J1","first",false,"Personal Care");
        add("Oral-B Pro 150g","Rs115",115,"J1","first",false,"Personal Care");
        add("Dove Shampoo 340ml","Rs280",280,"J2","first",false,"Personal Care");
        add("Head and Shoulders 340ml","Rs320",320,"J2","first",false,"Personal Care");
        add("Pantene Pro-V 340ml","Rs290",290,"J2","first",false,"Personal Care");
        add("Clinic Plus 340ml","Rs175",175,"J2","first",false,"Personal Care");
        add("TRESemme Keratin 340ml","Rs345",345,"J2","first",false,"Personal Care");
        add("Dove Body Lotion 400ml","Rs340",340,"J3","first",false,"Personal Care");
        add("Vaseline 400ml","Rs295",295,"J3","first",false,"Personal Care");
        add("Nivea Body Lotion 400ml","Rs280",280,"J3","first",false,"Personal Care");
        add("Parachute Lotion 250ml","Rs165",165,"J3","first",false,"Personal Care");
        add("Himalaya Cream 100ml","Rs130",130,"J3","first",false,"Personal Care");
        add("Dettol Handwash 200ml","Rs80",80,"J4","first",false,"Personal Care");
        add("Lifebuoy Soap 4pk","Rs110",110,"J4","first",false,"Personal Care");
        add("Savlon Handwash 200ml","Rs85",85,"J4","first",false,"Personal Care");
        add("Pears Soap 3pk","Rs120",120,"J4","first",false,"Personal Care");
        add("Lux Soap 4pk","Rs115",115,"J4","first",false,"Personal Care");
        add("Gillette Mach3","Rs225",225,"J5","first",false,"Personal Care");
        add("Gillette Fusion","Rs350",350,"J5","first",false,"Personal Care");
        add("Veet Cream 25g","Rs75",75,"J5","first",false,"Personal Care");
        add("Schick Hydro 5","Rs299",299,"J5","first",false,"Personal Care");
        add("Braun Shaver Series 1","Rs1299",1299,"J5","first",false,"Personal Care");
        add("Himalaya Face Wash 150ml","Rs110",110,"J6","first",false,"Personal Care");
        add("Garnier Micellar 400ml","Rs275",275,"J6","first",false,"Personal Care");
        add("Pond's Cold Cream 55g","Rs90",90,"J6","first",false,"Personal Care");
        add("Neutrogena Moisturizer 50ml","Rs380",380,"J6","first",false,"Personal Care");
        add("Biotique Honey Gel 50g","Rs165",165,"J6","first",false,"Personal Care");
        // K — Electronics center right
        add("Philips USB-C Cable 1m","Rs299",299,"K1","first",false,"Electronics");
        add("Syska 20W Charger","Rs499",499,"K1","first",false,"Electronics");
        add("Anker USB-C Cable","Rs349",349,"K1","first",false,"Electronics");
        add("Belkin Car Charger 18W","Rs599",599,"K1","first",false,"Electronics");
        add("Portronics Type-C","Rs199",199,"K1","first",false,"Electronics");
        add("Mi 10000mAh Power Bank","Rs799",799,"K2","first",false,"Electronics");
        add("boAt Bassheads","Rs399",399,"K2","first",false,"Electronics");
        add("Syska Power Bank 20100mAh","Rs999",999,"K2","first",false,"Electronics");
        add("Ambrane Power Bank 20000mAh","Rs849",849,"K2","first",false,"Electronics");
        add("Realme Wired Earphones","Rs349",349,"K2","first",false,"Electronics");
        add("JBL Clip3 Speaker","Rs2499",2499,"K3","first",true,"Electronics");
        add("Ambrane Wireless Charger","Rs599",599,"K3","first",false,"Electronics");
        add("boAt Rockerz 255 Pro","Rs1299",1299,"K3","first",false,"Electronics");
        add("Noise Shots X3","Rs999",999,"K3","first",false,"Electronics");
        add("Zebronics Sound Bomb","Rs799",799,"K3","first",false,"Electronics");
        add("Portronics USB Hub 4-port","Rs499",499,"K4","first",false,"Electronics");
        add("Havells Extension 3m","Rs379",379,"K4","first",false,"Electronics");
        add("TP-Link WiFi Adapter","Rs599",599,"K4","first",false,"Electronics");
        add("Logitech M185 Mouse","Rs699",699,"K4","first",false,"Electronics");
        add("Redgear Mouse Pad","Rs199",199,"K4","first",false,"Electronics");
        add("Samsung 32GB MicroSD","Rs399",399,"K5","first",false,"Electronics");
        add("SanDisk 64GB Pen Drive","Rs499",499,"K5","first",false,"Electronics");
        add("Cosmic Byte Keyboard","Rs799",799,"K5","first",false,"Electronics");
        add("Fingers Wireless Mouse","Rs499",499,"K5","first",false,"Electronics");
        add("Ugreen USB Hub 4-port","Rs599",599,"K5","first",false,"Electronics");
        add("Realme USB-C Charger 33W","Rs599",599,"K6","first",false,"Electronics");
        add("Mi 65W Charger","Rs799",799,"K6","first",false,"Electronics");
        add("Anker PowerCore 10000mAh","Rs999",999,"K6","first",false,"Electronics");
        add("boAt Airdopes 131","Rs1299",1299,"K6","first",false,"Electronics");
        add("Noise Buds VS104","Rs999",999,"K6","first",false,"Electronics");
        // L — Stationery center left mid
        add("Classmate Notebook 200pg","Rs60",60,"L1","first",false,"Stationery");
        add("Navneet Notebook A4","Rs50",50,"L1","first",false,"Stationery");
        add("Sundaram Long Notebook","Rs45",45,"L1","first",false,"Stationery");
        add("Spiral Notebook A5","Rs35",35,"L1","first",false,"Stationery");
        add("Grid Graph Notebook A4","Rs55",55,"L1","first",false,"Stationery");
        add("Reynolds Pen 10pk","Rs70",70,"L2","first",false,"Stationery");
        add("Camlin Geometry Box","Rs80",80,"L2","first",false,"Stationery");
        add("Cello Pen 5pk","Rs40",40,"L2","first",false,"Stationery");
        add("Natraj Pencil 12pk","Rs30",30,"L2","first",false,"Stationery");
        add("Apsara Eraser 5pk","Rs20",20,"L2","first",false,"Stationery");
        add("Fevicol SH 200g","Rs65",65,"L3","first",false,"Stationery");
        add("A4 Paper 500 sheets","Rs295",295,"L3","first",false,"Stationery");
        add("Scotch Tape 24mm","Rs55",55,"L3","first",false,"Stationery");
        add("Stapler Kangaroo","Rs120",120,"L3","first",false,"Stationery");
        add("Fevistik Glue 3pk","Rs45",45,"L3","first",false,"Stationery");
        add("Casio FX-82MS","Rs650",650,"L4","first",false,"Stationery");
        add("Maped Scissor 17cm","Rs75",75,"L4","first",false,"Stationery");
        add("Faber-Castell 24pk","Rs145",145,"L4","first",false,"Stationery");
        add("Camlin Acrylic 6pk","Rs199",199,"L4","first",false,"Stationery");
        add("Stapler + 1000 pins","Rs120",120,"L4","first",false,"Stationery");
        add("Classmate Spiral A4","Rs75",75,"L5","first",false,"Stationery");
        add("Oxford Notebook 200pg","Rs85",85,"L5","first",false,"Stationery");
        add("Sticky Notes 3x3 100pk","Rs45",45,"L5","first",false,"Stationery");
        add("Highlighter Set 5pk","Rs60",60,"L5","first",false,"Stationery");
        add("Pilot Pen Fine 0.7","Rs35",35,"L5","first",false,"Stationery");
        add("Atlas of India","Rs299",299,"L6","first",false,"Stationery");
        add("English Oxford Dictionary","Rs399",399,"L6","first",false,"Stationery");
        add("Atomic Habits Book","Rs349",349,"L6","first",false,"Stationery");
        add("Rich Dad Poor Dad","Rs299",299,"L6","first",false,"Stationery");
        add("English Grammar Book","Rs150",150,"L6","first",false,"Stationery");
        // M — Kitchenware center right mid
        add("Prestige Non-stick Tawa 28cm","Rs699",699,"M1","first",false,"Kitchenware");
        add("Hawkins Cooker 3L","Rs1199",1199,"M1","first",false,"Kitchenware");
        add("Pigeon Kadai 2L","Rs549",549,"M1","first",false,"Kitchenware");
        add("Vinod Stainless Tawa","Rs450",450,"M1","first",false,"Kitchenware");
        add("Prestige Induction Kadai 2.5L","Rs649",649,"M1","first",false,"Kitchenware");
        add("Tupperware Lunch Box Set","Rs799",799,"M2","first",false,"Kitchenware");
        add("Borosil Glass Bowls","Rs450",450,"M2","first",false,"Kitchenware");
        add("Signoraware Lunch Box 3 tier","Rs395",395,"M2","first",false,"Kitchenware");
        add("Milton Steel Lunch Box 2pk","Rs349",349,"M2","first",false,"Kitchenware");
        add("Vaya Tyffyn Lunch Box 600ml","Rs1299",1299,"M2","first",false,"Kitchenware");
        add("Cello Bottle 750ml","Rs350",350,"M3","first",false,"Kitchenware");
        add("Milton Flask 1L","Rs499",499,"M3","first",false,"Kitchenware");
        add("Puma Steel Sipper 700ml","Rs349",349,"M3","first",false,"Kitchenware");
        add("Nayasa Plastic Bottle 1L","Rs120",120,"M3","first",false,"Kitchenware");
        add("Borosil Hydra Glass Bottle","Rs395",395,"M3","first",false,"Kitchenware");
        add("Prestige Knife Set 3pc","Rs399",399,"M4","first",false,"Kitchenware");
        add("Pigeon Chopping Board","Rs199",199,"M4","first",false,"Kitchenware");
        add("Tramontina Chef Knife 8 inch","Rs549",549,"M4","first",false,"Kitchenware");
        add("Bamboo Cutting Board Large","Rs249",249,"M4","first",false,"Kitchenware");
        add("Zebra Peeler Steel","Rs85",85,"M4","first",false,"Kitchenware");
        add("Cello Jar 5L","Rs249",249,"M5","first",false,"Kitchenware");
        add("Nayasa Container 3pc","Rs299",299,"M5","first",false,"Kitchenware");
        add("Steelo Steel Jar 1L","Rs199",199,"M5","first",false,"Kitchenware");
        add("Solimo Dish Rack","Rs349",349,"M5","first",false,"Kitchenware");
        add("Prime Cook Trivet 3pk","Rs149",149,"M5","first",false,"Kitchenware");
        add("Lakshmi Tadka Pan 22cm","Rs349",349,"M6","first",false,"Kitchenware");
        add("Wonderchef Granite Pan 24cm","Rs899",899,"M6","first",false,"Kitchenware");
        add("Meyer Forgecraft Pan 20cm","Rs749",749,"M6","first",false,"Kitchenware");
        add("Nirlep Handi 3L","Rs499",499,"M6","first",false,"Kitchenware");
        add("Pigeon Ladle Set 3pk","Rs199",199,"M6","first",false,"Kitchenware");
        // N — Baby bottom bar
        add("Pampers Diapers NB 20pk","Rs499",499,"N1","first",false,"Baby");
        add("MamyPoko Pants S 30pk","Rs549",549,"N1","first",false,"Baby");
        add("Huggies Wonder Pants M 30pk","Rs579",579,"N1","first",false,"Baby");
        add("Wipro Baby Rash Cream","Rs120",120,"N1","first",false,"Baby");
        add("Himalaya Diaper Pants M 20pk","Rs399",399,"N1","first",false,"Baby");
        add("Johnson's Baby Shampoo 200ml","Rs175",175,"N2","first",false,"Baby");
        add("Himalaya Baby Cream 100ml","Rs130",130,"N2","first",false,"Baby");
        add("Mee Mee Baby Powder 200g","Rs155",155,"N2","first",false,"Baby");
        add("Johnson's Baby Oil 200ml","Rs185",185,"N2","first",false,"Baby");
        add("Sebamed Baby Wash 200ml","Rs299",299,"N2","first",false,"Baby");
        add("Cerelac Stage 1 300g","Rs270",270,"N3","first",false,"Baby");
        add("Pigeon Baby Bottle 250ml","Rs399",399,"N3","first",false,"Baby");
        add("Nestum Rice Cereal 300g","Rs185",185,"N3","first",false,"Baby");
        add("Nuby Sippy Cup 270ml","Rs299",299,"N3","first",false,"Baby");
        add("Fisher-Price Rattle Set","Rs349",349,"N3","first",false,"Baby");
        add("Pampers New Born 40pk","Rs599",599,"N4","first",false,"Baby");
        add("Mamaearth Baby Wash","Rs249",249,"N4","first",false,"Baby");
        add("Chicco Baby Shampoo 200ml","Rs299",299,"N4","first",false,"Baby");
        add("Himalaya Baby Massage Oil","Rs165",165,"N4","first",false,"Baby");
        add("Mothercare Baby Lotion 200ml","Rs349",349,"N4","first",false,"Baby");
        // O — Appliances bottom bar
        add("Philips Iron GC1905","Rs1299",1299,"O1","first",false,"Appliances");
        add("Havells Fan 400mm","Rs1599",1599,"O1","first",false,"Appliances");
        add("Bajaj Majesty Iron 1000W","Rs899",899,"O1","first",false,"Appliances");
        add("Orient Fan 400mm","Rs1399",1399,"O1","first",false,"Appliances");
        add("Philips Mixer 500W","Rs2199",2199,"O1","first",false,"Appliances");
        add("Usha Pop-up Toaster","Rs1199",1199,"O2","first",false,"Appliances");
        add("Prestige Kettle 1.5L","Rs699",699,"O2","first",false,"Appliances");
        add("Bajaj Toaster 2 Slice","Rs899",899,"O2","first",false,"Appliances");
        add("Pigeon Kettle 1.5L","Rs549",549,"O2","first",false,"Appliances");
        add("Inalsa Sandwich Maker","Rs1199",1199,"O2","first",false,"Appliances");
        add("Morphy Richards Blender","Rs1099",1099,"O3","first",true,"Appliances");
        add("Crompton LED 9W 4pk","Rs199",199,"O3","first",false,"Appliances");
        add("Syska LED 12W 4pk","Rs219",219,"O3","first",false,"Appliances");
        add("Wipro LED 9W 3pk","Rs185",185,"O3","first",false,"Appliances");
        add("Havells LED Tube 22W","Rs299",299,"O3","first",false,"Appliances");
        // P — Clothing right wall
        add("Jockey Men Trunks M 2pk","Rs399",399,"P1","first",false,"Clothing");
        add("Jockey Women Briefs S 2pk","Rs299",299,"P1","first",false,"Clothing");
        add("Dollar Brief L 3pk","Rs249",249,"P1","first",false,"Clothing");
        add("VIP Men Trunk XL","Rs199",199,"P1","first",false,"Clothing");
        add("Rupa Frontline Brief M 3pk","Rs179",179,"P1","first",false,"Clothing");
        add("Lux Men Vest L","Rs149",149,"P2","first",false,"Clothing");
        add("VIP Women Camisole M","Rs199",199,"P2","first",false,"Clothing");
        add("Lux Cozi Vest XL 2pk","Rs229",229,"P2","first",false,"Clothing");
        add("Jockey Women Camisole M","Rs299",299,"P2","first",false,"Clothing");
        add("Dollar Bigboss Vest L","Rs159",159,"P2","first",false,"Clothing");
        add("Dollar Bigboss Brief M 3pk","Rs249",249,"P3","first",false,"Clothing");
        add("HiG Ankle Socks 3pk","Rs129",129,"P3","first",false,"Clothing");
        add("Adidas Ankle Socks 3pk","Rs299",299,"P3","first",false,"Clothing");
        add("Hanes Men Trunks M 2pk","Rs349",349,"P3","first",false,"Clothing");
        add("Fruit of the Loom Brief 3pk","Rs279",279,"P3","first",false,"Clothing");
        add("Jockey Sports T-Shirt M","Rs349",349,"P4","first",false,"Clothing");
        add("Adidas Track Pant M","Rs899",899,"P4","first",false,"Clothing");
        add("Jockey Women Shorts M","Rs449",449,"P4","first",false,"Clothing");
        add("Puma Men Shorts M","Rs699",699,"P4","first",false,"Clothing");
        add("Chromozome Brief 2pk","Rs199",199,"P4","first",false,"Clothing");
        add("VIP Feelings Women Brief","Rs249",249,"P5","first",false,"Clothing");
        add("Jockey Sports Bra M","Rs549",549,"P5","first",false,"Clothing");
        add("Enamor Padded Bra 34B","Rs599",599,"P5","first",false,"Clothing");
        add("Zivame Seamless Bra 34C","Rs649",649,"P5","first",false,"Clothing");
        add("Lovable Bra 34B Cotton","Rs399",399,"P5","first",false,"Clothing");
        // R — books bottom row 2
        add("Atomic Habits James Clear","Rs349",349,"R1","first",false,"Stationery");
        add("Wings of Fire Kalam","Rs199",199,"R1","first",false,"Stationery");
        add("The Alchemist Coelho","Rs250",250,"R1","first",false,"Stationery");
        add("Harry Potter Book 1","Rs499",499,"R1","first",false,"Stationery");
        add("Ikigai Book","Rs299",299,"R1","first",false,"Stationery");
        add("Rich Dad Poor Dad","Rs299",299,"R2","first",false,"Stationery");
        add("Think and Grow Rich","Rs199",199,"R2","first",false,"Stationery");
        add("Deep Work Cal Newport","Rs349",349,"R2","first",false,"Stationery");
        add("The Power of Now","Rs249",249,"R2","first",false,"Stationery");
        add("Sapiens Yuval Harari","Rs499",499,"R2","first",false,"Stationery");
        add("Class 10 Maths NCERT","Rs120",120,"R3","first",false,"Stationery");
        add("Class 12 Physics NCERT","Rs130",130,"R3","first",false,"Stationery");
        add("RD Sharma Class 10","Rs380",380,"R3","first",false,"Stationery");
        add("English Grammar Book","Rs150",150,"R3","first",false,"Stationery");
        add("RS Aggarwal Reasoning","Rs299",299,"R3","first",false,"Stationery");
        // S — sports bottom row 2
        add("Cosco Football Size 5","Rs499",499,"S1","first",false,"Appliances");
        add("Yonex Badminton Racket","Rs699",699,"S1","first",false,"Appliances");
        add("Nivia Cricket Ball","Rs299",299,"S1","first",false,"Appliances");
        add("SG Cricket Bat Kashmir","Rs899",899,"S1","first",false,"Appliances");
        add("Cosco Basketball Size 7","Rs599",599,"S1","first",false,"Appliances");
        add("Boldfit Yoga Mat 6mm","Rs599",599,"S2","first",false,"Appliances");
        add("Nivia Skipping Rope","Rs199",199,"S2","first",false,"Appliances");
        add("Strauss Resistance Bands","Rs349",349,"S2","first",false,"Appliances");
        add("Strauss Dumbbell 2kg Pair","Rs499",499,"S2","first",false,"Appliances");
        add("Cosco Volleyball Size 4","Rs449",449,"S2","first",false,"Appliances");
        add("Adidas Slippers 9","Rs1299",1299,"S3","first",false,"Appliances");
        add("Nivia Sports Socks 3pk","Rs199",199,"S3","first",false,"Appliances");
        add("Whey Protein 1kg","Rs1299",1299,"S3","first",false,"Appliances");
        add("Gatorade Drink 500ml","Rs80",80,"S3","first",false,"Appliances");
        add("Badminton Shuttle 6pk","Rs199",199,"S3","first",false,"Appliances");



        add("Lay's Magic Masala","Rs20",20,"A1","ground",false,"Snacks");
        add("Kurkure Masala Munch","Rs20",20,"A2","ground",false,"Snacks");
        add("Bingo Mad Angles","Rs20",20,"A2","ground",true,"Snacks");
        add("Haldiram's Ratlami Sev","Rs45",45,"B1","ground",false,"Snacks");
        add("Haldiram's Aloo Bhujia","Rs55",55,"B1","ground",false,"Snacks");
        add("Haldiram's Moong Dal","Rs40",40,"B2","ground",false,"Snacks");
        add("Balaji Chatkazz","Rs10",10,"B2","ground",false,"Snacks");
        add("Doritos Nacho Cheese","Rs50",50,"B3","ground",false,"Snacks");
        add("Pringles Original","Rs105",105,"B3","ground",false,"Snacks");
        add("Cornitos Nacho Crisps","Rs45",45,"B4","ground",true,"Snacks");
        add("Bikaji Bikaneri Bhujia","Rs50",50,"B4","ground",false,"Snacks");
        add("Balaji Gathiya","Rs40",40,"B5","ground",false,"Snacks");
        add("Haldiram's Khatta Meetha","Rs50",50,"B5","ground",false,"Snacks");
        add("Parle-G Gold","Rs10",10,"B6","ground",false,"Biscuits");
        add("Britannia Marie Gold","Rs20",20,"B6","ground",false,"Biscuits");
        add("Parle Monaco","Rs15",15,"B7","ground",false,"Biscuits");
        add("Britannia Good Day Cashew","Rs30",30,"B7","ground",false,"Biscuits");
        add("Oreo Original","Rs35",35,"B8","ground",false,"Biscuits");
        add("Sunfeast Dark Fantasy","Rs50",50,"B8","ground",false,"Biscuits");
        add("Hide and Seek Fab","Rs40",40,"B9","ground",false,"Biscuits");
        add("Mcvitie's Digestive","Rs55",55,"B9","ground",false,"Biscuits");
        add("Coca-Cola 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Pepsi 750ml","Rs40",40,"E1","ground",false,"Beverages");
        add("Thums Up 750ml","Rs40",40,"E2","ground",false,"Beverages");
        add("Sprite 750ml","Rs40",40,"E2","ground",false,"Beverages");
        add("Real Mango Juice 1L","Rs85",85,"E3","ground",false,"Beverages");
        add("Paper Boat Aamras 200ml","Rs30",30,"E3","ground",false,"Beverages");
        add("Red Bull Energy 250ml","Rs115",115,"E4","ground",true,"Beverages");
        add("Sting Energy 250ml","Rs20",20,"E4","ground",false,"Beverages");
        add("Bisleri Water 1L","Rs20",20,"E5","ground",false,"Beverages");
        add("Mountain Dew 600ml","Rs35",35,"E5","ground",false,"Beverages");
        add("Tropicana Mixed Fruit 1L","Rs90",90,"E6","ground",false,"Beverages");
        add("Amul Butter 500g","Rs270",270,"P","ground",false,"Dairy");
        add("Amul Butter 100g","Rs56",56,"P","ground",false,"Dairy");
        add("Amul Milk 1L Toned","Rs60",60,"O","ground",false,"Dairy");
        add("Amul Taaza 500ml","Rs30",30,"O","ground",false,"Dairy");
        add("Mother Dairy Curd 400g","Rs48",48,"N","ground",false,"Dairy");
        add("Amul Curd 400g","Rs44",44,"N","ground",false,"Dairy");
        add("Amul Paneer 200g","Rs90",90,"M","ground",false,"Dairy");
        add("Nestle Milkmaid 400g","Rs110",110,"M","ground",false,"Dairy");
        add("Britannia Cheese Slices","Rs100",100,"L","ground",false,"Dairy");
        add("Amul Mozzarella 200g","Rs120",120,"L","ground",false,"Dairy");
        add("Cadbury Dairy Milk 50g","Rs50",50,"D1","ground",false,"Chocolates");
        add("Cadbury Dairy Milk Silk","Rs100",100,"D1","ground",false,"Chocolates");
        add("5 Star Chocolate 40g","Rs20",20,"D2","ground",false,"Chocolates");
        add("KitKat 4F 37g","Rs30",30,"D2","ground",false,"Chocolates");
        add("Munch 35g","Rs20",20,"D3","ground",false,"Chocolates");
        add("Perk 30g","Rs15",15,"D3","ground",false,"Chocolates");
        add("Amul Fruit N Nut 120g","Rs120",120,"D4","ground",false,"Chocolates");
        add("Cadbury Gems Pouch","Rs25",25,"D4","ground",false,"Chocolates");
        add("Nutella Hazelnut 350g","Rs380",380,"D5","ground",false,"Chocolates");
        add("Hershey's Kisses Hazelnut","Rs145",145,"D5","ground",false,"Chocolates");
        add("Maggi 2-Min Masala 12pk","Rs168",168,"D6","ground",false,"Noodles");
        add("Ching's Veg Hakka Noodles","Rs105",105,"D6","ground",false,"Noodles");
        add("Yippee Magic Masala 8pk","Rs90",90,"D7","ground",false,"Noodles");
        add("Top Ramen Curry 4pk","Rs80",80,"D7","ground",true,"Noodles");
        add("Maggi Pazzta Cheese","Rs28",28,"D8","ground",false,"Noodles");
        add("Knorr Soupy Noodles","Rs35",35,"D8","ground",false,"Noodles");
        add("Maggi Veg Atta 4pk","Rs96",96,"D9","ground",false,"Noodles");
        add("Britannia NutriChoice","Rs60",60,"D9","ground",false,"Biscuits");
        add("India Gate Basmati 5kg","Rs550",550,"K1","ground",false,"Grains");
        add("Dawat Basmati 1kg","Rs120",120,"K2","ground",false,"Grains");
        add("Fortune Chakki Atta 10kg","Rs430",430,"K3","ground",false,"Grains");
        add("Aashirvaad Atta 5kg","Rs265",265,"K4","ground",false,"Grains");
        add("MDH Chhole Masala 100g","Rs55",55,"F1","ground",false,"Spices");
        add("Everest Kitchen King 100g","Rs68",68,"F1","ground",false,"Spices");
        add("MDH Rajma Masala 100g","Rs60",60,"F2","ground",false,"Spices");
        add("Catch Cumin Seeds 100g","Rs42",42,"F2","ground",false,"Spices");
        add("Everest Sambhar Masala","Rs62",62,"F3","ground",false,"Spices");
        add("Maggi Hot and Sweet Sauce","Rs90",90,"F3","ground",false,"Spices");
        add("MDH Garam Masala 100g","Rs72",72,"F4","ground",false,"Spices");
        add("Haldiram Namkeen Mix","Rs60",60,"G1","ground",false,"Snacks");
        add("Bingo Tedhe Medhe","Rs20",20,"G1","ground",false,"Snacks");
        add("Lays Classic Salted","Rs20",20,"G2","ground",false,"Snacks");
        add("Parle Wafers Cream Onion","Rs20",20,"G2","ground",false,"Snacks");
        add("Too Yumm Multigrain","Rs30",30,"G3","ground",false,"Snacks");
        add("Balaji Sev Murmura","Rs15",15,"G3","ground",false,"Snacks");
        add("Kurkure Green Chutney","Rs20",20,"G4","ground",false,"Snacks");
        add("Act II Popcorn Butter","Rs30",30,"G4","ground",false,"Snacks");
        add("Q Rack Bulk Dry Fruits","Rs450",450,"Q","ground",false,"Grains");
        add("Q Rack Mukhwas Mix","Rs80",80,"Q","ground",false,"Snacks");

// ── 1ST FLOOR ──────────────────────────────────────────────────────────
// I — Cleaning (left wall)
        add("Surf Excel Matic 2kg","Rs390",390,"I1","first",false,"Cleaning");
        add("Ariel Matic 4kg","Rs670",670,"I1","first",false,"Cleaning");
        add("Tide Plus 3kg","Rs310",310,"I1","first",false,"Cleaning");
        add("Vim Dishwash Bar 300g","Rs42",42,"I2","first",false,"Cleaning");
        add("Pril Liquid 750ml","Rs175",175,"I2","first",false,"Cleaning");
        add("Harpic Toilet 1L","Rs175",175,"I3","first",false,"Cleaning");
        add("Domex Floor 1L","Rs145",145,"I3","first",false,"Cleaning");
        add("Colin Glass 500ml","Rs155",155,"I4","first",false,"Cleaning");
        add("Lizol Disinfectant 1L","Rs300",300,"I4","first",false,"Cleaning");
        add("Scotch-Brite Scrub 2pk","Rs55",55,"I5","first",false,"Cleaning");
        add("Prestige Dishwash Pad 6pk","Rs60",60,"I5","first",false,"Cleaning");
        add("Rin Bar 250g","Rs30",30,"I6","first",false,"Cleaning");
        add("Nirma Powder 1kg","Rs65",65,"I6","first",false,"Cleaning");

// J — Personal Care (center left)
        add("Colgate Strong 200g","Rs120",120,"J1","first",false,"Personal Care");
        add("Colgate MaxFresh 150g","Rs100",100,"J1","first",false,"Personal Care");
        add("Pepsodent 200g","Rs95",95,"J1","first",false,"Personal Care");
        add("Dove Shampoo 340ml","Rs280",280,"J2","first",false,"Personal Care");
        add("Head and Shoulders 340ml","Rs320",320,"J2","first",false,"Personal Care");
        add("Pantene Pro-V 340ml","Rs290",290,"J2","first",false,"Personal Care");
        add("Dove Body Lotion 400ml","Rs340",340,"J3","first",false,"Personal Care");
        add("Vaseline 400ml","Rs295",295,"J3","first",false,"Personal Care");
        add("Nivea Body Lotion 400ml","Rs280",280,"J3","first",false,"Personal Care");
        add("Dettol Handwash 200ml","Rs80",80,"J4","first",false,"Personal Care");
        add("Lifebuoy Soap 4pk","Rs110",110,"J4","first",false,"Personal Care");
        add("Gillette Mach3","Rs225",225,"J5","first",false,"Personal Care");
        add("Himalaya Face Wash 150ml","Rs110",110,"J6","first",false,"Personal Care");

// K — Electronics (center right)
        add("Philips USB-C Cable 1m","Rs299",299,"K1","first",false,"Electronics");
        add("Syska 20W Charger","Rs499",499,"K1","first",false,"Electronics");
        add("Anker Cable","Rs349",349,"K1","first",false,"Electronics");
        add("Mi 10000mAh Power Bank","Rs799",799,"K2","first",false,"Electronics");
        add("boAt Bassheads","Rs399",399,"K2","first",false,"Electronics");
        add("Syska Power Bank 20100mAh","Rs999",999,"K2","first",false,"Electronics");
        add("JBL Clip3 Speaker","Rs2499",2499,"K3","first",true,"Electronics");
        add("Ambrane Wireless Charger","Rs599",599,"K3","first",false,"Electronics");
        add("boAt Rockerz 255 Pro","Rs1299",1299,"K3","first",false,"Electronics");
        add("Portronics USB Hub 4-port","Rs499",499,"K4","first",false,"Electronics");
        add("Havells Extension 3m","Rs379",379,"K4","first",false,"Electronics");
        add("Logitech M185 Mouse","Rs699",699,"K4","first",false,"Electronics");
        add("Redgear Mouse Pad","Rs199",199,"K5","first",false,"Electronics");
        add("TP-Link USB Adapter","Rs599",599,"K5","first",false,"Electronics");
        add("Belkin Car Charger 18W","Rs599",599,"K6","first",false,"Electronics");
        add("Portronics Type-C Cable","Rs199",199,"K6","first",false,"Electronics");

// L — Stationery (center left mid)
        add("Classmate Notebook 200pg","Rs60",60,"L1","first",false,"Stationery");
        add("Navneet Notebook A4","Rs50",50,"L1","first",false,"Stationery");
        add("Reynolds Ball Pen 10pk","Rs70",70,"L2","first",false,"Stationery");
        add("Camlin Geometry Box","Rs80",80,"L2","first",false,"Stationery");
        add("Fevicol SH 200g","Rs65",65,"L3","first",false,"Stationery");
        add("A4 Paper 500 sheets","Rs295",295,"L3","first",false,"Stationery");
        add("Casio FX-82MS","Rs650",650,"L4","first",false,"Stationery");
        add("Maped Scissor 17cm","Rs75",75,"L4","first",false,"Stationery");
        add("Faber-Castell 24pk","Rs145",145,"L5","first",false,"Stationery");
        add("Cello Pen 5pk","Rs40",40,"L5","first",false,"Stationery");
        add("Stapler + 1000 pins","Rs120",120,"L6","first",false,"Stationery");
        add("Natraj Pencil 12pk","Rs30",30,"L6","first",false,"Stationery");

// M — Kitchenware (center right mid)
        add("Prestige Non-stick Tawa 28cm","Rs699",699,"M1","first",false,"Kitchenware");
        add("Hawkins Cooker 3L","Rs1199",1199,"M1","first",false,"Kitchenware");
        add("Tupperware Lunch Box Set","Rs799",799,"M2","first",false,"Kitchenware");
        add("Borosil Glass Bowls","Rs450",450,"M2","first",false,"Kitchenware");
        add("Cello Bottle 750ml","Rs350",350,"M3","first",false,"Kitchenware");
        add("Milton Flask 1L","Rs499",499,"M3","first",false,"Kitchenware");
        add("Prestige Knife Set 3pc","Rs399",399,"M4","first",false,"Kitchenware");
        add("Pigeon Chopping Board","Rs199",199,"M4","first",false,"Kitchenware");
        add("Cello Jar 5L","Rs249",249,"M5","first",false,"Kitchenware");
        add("Nayasa Container 3pc","Rs299",299,"M5","first",false,"Kitchenware");
        add("Lakshmi Tadka Pan 22cm","Rs349",349,"M6","first",false,"Kitchenware");
        add("Wonderchef Granite Pan 24cm","Rs899",899,"M6","first",false,"Kitchenware");

// N — Baby (bottom bar left)
        add("Pampers Diapers NB 20pk","Rs499",499,"N1","first",false,"Baby");
        add("MamyPoko Pants S 30pk","Rs549",549,"N1","first",false,"Baby");
        add("Johnson's Baby Shampoo 200ml","Rs175",175,"N2","first",false,"Baby");
        add("Himalaya Baby Cream 100ml","Rs130",130,"N2","first",false,"Baby");
        add("Cerelac Stage 1 300g","Rs270",270,"N3","first",false,"Baby");
        add("Pigeon Baby Bottle 250ml","Rs399",399,"N3","first",false,"Baby");
        add("Johnson's Baby Oil 200ml","Rs185",185,"N4","first",false,"Baby");
        add("Mee Mee Baby Powder 200g","Rs155",155,"N4","first",false,"Baby");

// O — Appliances (bottom bar right)
        add("Philips Iron GC1905","Rs1299",1299,"O1","first",false,"Appliances");
        add("Havells Fan 400mm","Rs1599",1599,"O1","first",false,"Appliances");
        add("Usha Pop-up Toaster","Rs1199",1199,"O2","first",false,"Appliances");
        add("Prestige Kettle 1.5L","Rs699",699,"O2","first",false,"Appliances");
        add("Morphy Richards Blender","Rs1099",1099,"O3","first",true,"Appliances");
        add("Crompton LED 9W 4pk","Rs199",199,"O3","first",false,"Appliances");

// P — Clothing (right wall)
        add("Jockey Men Trunks M 2pk","Rs399",399,"P1","first",false,"Clothing");
        add("Jockey Women Briefs S 2pk","Rs299",299,"P1","first",false,"Clothing");
        add("Lux Men Vest L","Rs149",149,"P2","first",false,"Clothing");
        add("VIP Women Camisole M","Rs199",199,"P2","first",false,"Clothing");
        add("Dollar Brief M 3pk","Rs249",249,"P3","first",false,"Clothing");
        add("HiG Ankle Socks 3pk","Rs129",129,"P3","first",false,"Clothing");
        add("Jockey Sports T-Shirt M","Rs349",349,"P4","first",false,"Clothing");
        add("Adidas Track Pant M","Rs899",899,"P4","first",false,"Clothing");
        add("VIP Women Brief","Rs249",249,"P5","first",false,"Clothing");
        add("Jockey Sports Bra M","Rs549",549,"P5","first",false,"Clothing");

// R — Books (bottom row 2)
        add("Atomic Habits","Rs349",349,"R1","first",false,"Stationery");
        add("Rich Dad Poor Dad","Rs299",299,"R2","first",false,"Stationery");
        add("English Grammar Book","Rs150",150,"R3","first",false,"Stationery");

// S — Sports/Health (bottom row 2)
        add("Cosco Football Size 5","Rs499",499,"S1","first",false,"Appliances");
        add("Yonex Badminton Racket","Rs699",699,"S2","first",false,"Appliances");
        add("Boldfit Yoga Mat 6mm","Rs599",599,"S3","first",false,"Appliances");
    }
}