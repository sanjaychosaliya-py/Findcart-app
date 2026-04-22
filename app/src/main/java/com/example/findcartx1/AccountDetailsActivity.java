package com.example.findcartx1;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AccountDetailsActivity extends AppCompatActivity {

    private ImageView         ivProfilePhoto;
    private TextView          tvInitial, tvNameHeader;
    private TextView          tvNameVal, tvMobileVal, tvDobVal, tvGenderVal, tvCityVal, tvStoreVal;
    private EditText          etEditName, etEditMobile, etEditDob;
    private Spinner           spinGender, spinCity, spinStore;
    private LinearLayout      editCard;
    private TextView          btnEdit;
    private String            selectedDob = "";
    private SharedPreferences pref;
    private FirebaseFirestore db;

    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) savePhotoToPrefs(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Profile");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.parseColor("#2E7D32")));
        }
        pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        db   = FirebaseFirestore.getInstance();
        buildUI();
    }

    private void buildUI() {
        String email    = pref.getString("saved_email",       "Not set");
        String name     = pref.getString("user_name",         "");
        String mobile   = pref.getString("user_mobile",       "Not set");
        String city     = pref.getString("user_city",         "Not set");
        String store    = pref.getString("user_store",        "Not set");
        String dob      = pref.getString("user_dob",          "Not set");
        String gender   = pref.getString("user_gender",       "Not set");
        String photoB64 = pref.getString("profile_photo_b64", null);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // ── HEADER ────────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Color.parseColor("#2E7D32"));
        header.setPadding(dp(24), dp(96), dp(24), dp(24));
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        // Avatar frame
        FrameLayout avatarFrame = new FrameLayout(this);
        LinearLayout.LayoutParams afLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        afLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarFrame.setLayoutParams(afLp);

        GradientDrawable ovalBg = new GradientDrawable();
        ovalBg.setShape(GradientDrawable.OVAL);
        ovalBg.setColor(Color.parseColor("#1B5E20"));
        View circleBg = new View(this);
        circleBg.setLayoutParams(new FrameLayout.LayoutParams(dp(96), dp(96)));
        circleBg.setBackground(ovalBg);
        avatarFrame.addView(circleBg);

        ivProfilePhoto = new ImageView(this);
        ivProfilePhoto.setLayoutParams(new FrameLayout.LayoutParams(dp(96), dp(96)));
        ivProfilePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivProfilePhoto.setClipToOutline(true);
        ivProfilePhoto.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        ivProfilePhoto.setVisibility(View.GONE);
        avatarFrame.addView(ivProfilePhoto);

        String src = name.isEmpty() ? email : name;
        tvInitial = new TextView(this);
        tvInitial.setLayoutParams(new FrameLayout.LayoutParams(dp(96), dp(96)));
        tvInitial.setGravity(Gravity.CENTER);
        tvInitial.setText(src.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(src.charAt(0))));
        tvInitial.setTextSize(32f);
        tvInitial.setTextColor(Color.WHITE);
        tvInitial.setTypeface(null, Typeface.BOLD);
        avatarFrame.addView(tvInitial);

        // Camera badge
        FrameLayout camBadge = new FrameLayout(this);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(dp(26), dp(26));
        camLp.gravity = Gravity.BOTTOM | Gravity.END;
        camBadge.setLayoutParams(camLp);
        GradientDrawable camGd = new GradientDrawable();
        camGd.setShape(GradientDrawable.OVAL);
        camGd.setColor(Color.parseColor("#FF6F00"));
        camBadge.setBackground(camGd);
        TextView camTv = new TextView(this);
        camTv.setLayoutParams(new FrameLayout.LayoutParams(dp(26), dp(26)));
        camTv.setGravity(Gravity.CENTER);
        camTv.setText("📷"); camTv.setTextSize(10f);
        camBadge.addView(camTv);
        avatarFrame.addView(camBadge);

        avatarFrame.setClickable(true); avatarFrame.setFocusable(true);
        avatarFrame.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        header.addView(avatarFrame);

        TextView tvHint = new TextView(this);
        tvHint.setText("Tap to change photo");
        tvHint.setTextSize(10f); tvHint.setTextColor(Color.parseColor("#A5D6A7"));
        tvHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(4); tvHint.setLayoutParams(hintLp);
        header.addView(tvHint);

        // Load saved photo from base64 — persists across logins
        if (photoB64 != null) loadPhotoFromBase64(photoB64);

        tvNameHeader = new TextView(this);
        tvNameHeader.setText(name.isEmpty() ? "FindCartx1 User" : name);
        tvNameHeader.setTextSize(18f); tvNameHeader.setTypeface(null, Typeface.BOLD);
        tvNameHeader.setTextColor(Color.WHITE); tvNameHeader.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10); tvNameHeader.setLayoutParams(nlp);
        header.addView(tvNameHeader);

        TextView tvEmailHdr = new TextView(this);
        tvEmailHdr.setText(email); tvEmailHdr.setTextSize(12f);
        tvEmailHdr.setTextColor(Color.parseColor("#C8E6C9")); tvEmailHdr.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(2); tvEmailHdr.setLayoutParams(elp);
        header.addView(tvEmailHdr);

        // ── INFO CARD ─────────────────────────────────────────────────────────
        LinearLayout infoCard = makeCard();

        // Title + Edit button row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = dp(8); titleRow.setLayoutParams(trLp);

        TextView tvCardTitle = new TextView(this);
        tvCardTitle.setText("Personal Details"); tvCardTitle.setTextSize(14f);
        tvCardTitle.setTypeface(null, Typeface.BOLD);
        tvCardTitle.setTextColor(Color.parseColor("#212121"));
        tvCardTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvCardTitle);

        btnEdit = new TextView(this);
        btnEdit.setText("✏  Edit"); btnEdit.setTextSize(12f);
        btnEdit.setTypeface(null, Typeface.BOLD); btnEdit.setTextColor(Color.WHITE);
        btnEdit.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(Color.parseColor("#1565C0")); editBg.setCornerRadius(dp(6));
        btnEdit.setBackground(editBg);
        btnEdit.setClickable(true); btnEdit.setFocusable(true);
        titleRow.addView(btnEdit);
        infoCard.addView(titleRow);
        infoCard.addView(dividerView());

        tvNameVal   = infoRow(infoCard, "Full Name",      name.isEmpty() ? "Not set" : name);
        infoCard.addView(dividerView());
        tvMobileVal = infoRow(infoCard, "Mobile",         mobile);
        infoCard.addView(dividerView());
        tvDobVal    = infoRow(infoCard, "Date of Birth",  dob);
        infoCard.addView(dividerView());
        tvGenderVal = infoRow(infoCard, "Gender",         gender);
        infoCard.addView(dividerView());
        tvCityVal   = infoRow(infoCard, "City",           city);
        infoCard.addView(dividerView());
        tvStoreVal  = infoRow(infoCard, "Preferred Store",store);
        root.addView(infoCard);

        // ── EDIT CARD (hidden initially) ──────────────────────────────────────
        editCard = makeCard();
        editCard.setVisibility(View.GONE);

        TextView tvEditTitle = new TextView(this);
        tvEditTitle.setText("Edit Profile"); tvEditTitle.setTextSize(14f);
        tvEditTitle.setTypeface(null, Typeface.BOLD);
        tvEditTitle.setTextColor(Color.parseColor("#212121"));
        LinearLayout.LayoutParams etitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etitleLp.bottomMargin = dp(10); tvEditTitle.setLayoutParams(etitleLp);
        editCard.addView(tvEditTitle);

        editCard.addView(fieldLabel("Full Name"));
        etEditName = makeEditText("Enter full name"); etEditName.setText(name);
        editCard.addView(etEditName);

        editCard.addView(fieldLabel("Mobile Number"));
        etEditMobile = makeEditText("10-digit mobile");
        etEditMobile.setInputType(InputType.TYPE_CLASS_PHONE);
        etEditMobile.setText(mobile.equals("Not set") ? "" : mobile);
        editCard.addView(etEditMobile);

        editCard.addView(fieldLabel("Date of Birth (tap to pick)"));
        etEditDob = makeEditText("Tap to select date");
        etEditDob.setFocusable(false); etEditDob.setClickable(true);
        etEditDob.setText(dob.equals("Not set") ? "" : dob);
        selectedDob = dob.equals("Not set") ? "" : dob;
        etEditDob.setOnClickListener(v -> openDatePicker());
        editCard.addView(etEditDob);

        editCard.addView(fieldLabel("Gender"));
        spinGender = makeSpinner(new String[]{"Select Gender","Male","Female","Other"});
        String[] gOpts = {"Select Gender","Male","Female","Other"};
        for (int i = 0; i < gOpts.length; i++) if (gOpts[i].equals(gender)) spinGender.setSelection(i);
        editCard.addView(spinGender);

        editCard.addView(fieldLabel("City"));
        spinCity = makeSpinner(new String[]{"Select City","Surat","Vadodara",
                "Ahmedabad","Rajkot","Bhavnagar"});
        String[] cOpts = {"Select City","Surat","Vadodara","Ahmedabad","Rajkot","Bhavnagar"};
        for (int i = 0; i < cOpts.length; i++) if (cOpts[i].equals(city)) spinCity.setSelection(i);
        editCard.addView(spinCity);

        editCard.addView(fieldLabel("Preferred Store"));
        spinStore = makeSpinner(new String[]{"Select Store","FindCartx1 Mega Store",
                "FindCartx1 City Mall","FindCartx1 Express"});
        String[] sOpts = {"Select Store","FindCartx1 Mega Store",
                "FindCartx1 City Mall","FindCartx1 Express"};
        for (int i = 0; i < sOpts.length; i++) if (sOpts[i].equals(store)) spinStore.setSelection(i);
        editCard.addView(spinStore);

        // Save + Cancel row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = dp(16); btnRow.setLayoutParams(brlp);

        TextView btnSaveEdit = makeBtnView("SAVE", "#2E7D32");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        slp.setMarginEnd(dp(8)); btnSaveEdit.setLayoutParams(slp);
        btnSaveEdit.setOnClickListener(v -> saveProfile());
        btnRow.addView(btnSaveEdit);

        TextView btnCancelEdit = makeBtnView("CANCEL", "#757575");
        btnCancelEdit.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnCancelEdit.setOnClickListener(v -> {
            editCard.setVisibility(View.GONE);
            btnEdit.setText("✏  Edit");
        });
        btnRow.addView(btnCancelEdit);
        editCard.addView(btnRow);
        root.addView(editCard);

        // Edit button click
        btnEdit.setOnClickListener(v -> {
            if (editCard.getVisibility() == View.GONE) {
                editCard.setVisibility(View.VISIBLE);
                btnEdit.setText("✕  Close");
                sv.post(() -> sv.smoothScrollTo(0, editCard.getTop()));
            } else {
                editCard.setVisibility(View.GONE);
                btnEdit.setText("✏  Edit");
            }
        });

        // ── CHANGE PASSWORD CARD ──────────────────────────────────────────────
        LinearLayout pwCard = makeCard();
        TextView pwTitle = new TextView(this);
        pwTitle.setText("Change Password"); pwTitle.setTextSize(14f);
        pwTitle.setTypeface(null, Typeface.BOLD); pwTitle.setTextColor(Color.parseColor("#212121"));
        LinearLayout.LayoutParams pwtp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pwtp.bottomMargin = dp(10); pwTitle.setLayoutParams(pwtp);
        pwCard.addView(pwTitle);

        EditText etPw = new EditText(this);
        etPw.setHint("New Password (min 6 characters)");
        etPw.setTextColor(Color.parseColor("#212121"));
        etPw.setHintTextColor(Color.parseColor("#BDBDBD"));
        etPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPw.setBackgroundColor(Color.parseColor("#F0F0F0"));
        etPw.setPadding(dp(12), dp(10), dp(12), dp(10));
        etPw.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pwCard.addView(etPw);

        TextView btnUpdatePw = makeBtnView("UPDATE PASSWORD", "#2E7D32");
        LinearLayout.LayoutParams pwBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pwBtnLp.topMargin = dp(12); btnUpdatePw.setLayoutParams(pwBtnLp);
        btnUpdatePw.setOnClickListener(v -> {
            String np = etPw.getText().toString().trim();
            if (np.length() < 6) {
                Toast.makeText(this, "Min 6 characters required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                FirebaseAuth.getInstance().getCurrentUser().updatePassword(np)
                        .addOnSuccessListener(u -> {
                            pref.edit().putString("saved_password", np).apply();
                            Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show();
                            etPw.setText("");
                        })
                        .addOnFailureListener(e -> {
                            pref.edit().putString("saved_password", np).apply();
                            Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show();
                            etPw.setText("");
                        });
            } else {
                pref.edit().putString("saved_password", np).apply();
                Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show();
                etPw.setText("");
            }
        });
        pwCard.addView(btnUpdatePw);
        root.addView(pwCard);

        // Bottom spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        root.addView(spacer);

        sv.addView(root);
        setContentView(sv);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SAVE PROFILE
    // ══════════════════════════════════════════════════════════════════════════

    private void saveProfile() {
        String newName   = etEditName.getText().toString().trim();
        String newMobile = etEditMobile.getText().toString().trim();
        String newDob    = selectedDob.isEmpty() ? etEditDob.getText().toString().trim() : selectedDob;
        String newGender = spinGender.getSelectedItemPosition() > 0
                ? spinGender.getSelectedItem().toString() : pref.getString("user_gender","Not set");
        String newCity   = spinCity.getSelectedItemPosition() > 0
                ? spinCity.getSelectedItem().toString() : pref.getString("user_city","Not set");
        String newStore  = spinStore.getSelectedItemPosition() > 0
                ? spinStore.getSelectedItem().toString() : pref.getString("user_store","Not set");

        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show(); return;
        }
        if (!newMobile.isEmpty() && newMobile.length() != 10) {
            Toast.makeText(this, "Enter valid 10-digit mobile", Toast.LENGTH_SHORT).show(); return;
        }

        pref.edit()
                .putString("user_name",   newName)
                .putString("user_mobile", newMobile)
                .putString("user_dob",    newDob)
                .putString("user_gender", newGender)
                .putString("user_city",   newCity)
                .putString("user_store",  newStore)
                .apply();

        // Update display rows
        tvNameVal.setText(newName);
        tvMobileVal.setText(newMobile.isEmpty() ? "Not set" : newMobile);
        tvDobVal.setText(newDob.isEmpty() ? "Not set" : newDob);
        tvGenderVal.setText(newGender);
        tvCityVal.setText(newCity);
        tvStoreVal.setText(newStore);
        tvNameHeader.setText(newName);

        // Save to Firestore
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            Map<String, Object> updates = new HashMap<>();
            updates.put("name",   newName);
            updates.put("mobile", newMobile);
            updates.put("dob",    newDob);
            updates.put("gender", newGender);
            updates.put("city",   newCity);
            updates.put("store",  newStore);
            db.collection("users").document(uid).update(updates);
        }

        editCard.setVisibility(View.GONE);
        btnEdit.setText("✏  Edit");
        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHOTO — Base64 so it persists across logins without re-uploading
    // ══════════════════════════════════════════════════════════════════════════

    private void savePhotoToPrefs(Uri uri) {
        try {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);

// Fix rotation — phone cameras save EXIF rotation data
            try {
                InputStream exifStream = getContentResolver().openInputStream(uri);
                androidx.exifinterface.media.ExifInterface exif =
                        new androidx.exifinterface.media.ExifInterface(exifStream);
                int orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
                int rotation = 0;
                if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90)  rotation = 90;
                if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                if (rotation != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(rotation);
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                }
                exifStream.close();
            } catch (Exception ignored) {}

// Resize to max 300px
            int max = 300, w = bmp.getWidth(), h = bmp.getHeight();
            if (w > max || h > max) {
                float s = Math.min((float)max/w, (float)max/h);
                bmp = Bitmap.createScaledBitmap(bmp, (int)(w*s), (int)(h*s), true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            // Save base64 — this survives logout/login
            // 1. Save to SharedPrefs
            pref.edit()
                    .putString("profile_photo_b64", b64)
                    .putString("profile_photo_uri", uri.toString())
                    .apply();

// 2. Save to Firestore so it loads on any device after re-login
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                java.util.Map<String, Object> photoData = new java.util.HashMap<>();
                photoData.put("photoB64", b64);
                db.collection("users").document(uid).update(photoData);
            }

            loadPhotoFromBase64(b64);
            Toast.makeText(this, "Photo saved! Stays after re-login ✓", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            pref.edit().putString("profile_photo_uri", uri.toString()).apply();
            ivProfilePhoto.setImageURI(uri);
            ivProfilePhoto.setVisibility(View.VISIBLE);
            if (tvInitial != null) tvInitial.setVisibility(View.GONE);
            Toast.makeText(this, "Photo updated!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhotoFromBase64(String b64) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (ivProfilePhoto != null && bmp != null) {
                ivProfilePhoto.setImageBitmap(bmp);
                ivProfilePhoto.setVisibility(View.VISIBLE);
                if (tvInitial != null) tvInitial.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DATE PICKER
    // ══════════════════════════════════════════════════════════════════════════

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this,
                android.R.style.Theme_Material_Light_Dialog,
                (view, y, m, d) -> {
                    selectedDob = String.format("%02d-%02d-%04d", d, m+1, y);
                    etEditDob.setText(selectedDob);
                },
                cal.get(Calendar.YEAR) - 20,
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        Calendar maxCal = Calendar.getInstance();
        maxCal.add(Calendar.YEAR, -5);
        dlg.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
        Calendar minCal = Calendar.getInstance();
        minCal.add(Calendar.YEAR, -100);
        dlg.getDatePicker().setMinDate(minCal.getTimeInMillis());
        dlg.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private TextView infoRow(LinearLayout p, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(11), 0, dp(11));
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvL = new TextView(this);
        tvL.setText(label); tvL.setTextSize(13f);
        tvL.setTextColor(Color.parseColor("#757575"));
        tvL.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvL);
        TextView tvV = new TextView(this);
        tvV.setText(value); tvV.setTextSize(13f);
        tvV.setTypeface(null, Typeface.BOLD);
        tvV.setTextColor(Color.parseColor("#212121"));
        tvV.setGravity(Gravity.END);
        row.addView(tvV);
        p.addView(row);
        return tvV;
    }

    private View dividerView() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#F0F0F0"));
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    private LinearLayout makeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(14), dp(12), dp(14), 0);
        c.setLayoutParams(lp);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        return c;
    }

    private TextView fieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(11f);
        tv.setTextColor(Color.parseColor("#757575"));
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10); lp.bottomMargin = dp(3);
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText makeEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setTextSize(13f);
        et.setTextColor(Color.parseColor("#212121"));
        et.setHintTextColor(Color.parseColor("#BDBDBD"));
        et.setBackgroundColor(Color.parseColor("#F5F5F5"));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private Spinner makeSpinner(String[] items) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2); sp.setLayoutParams(lp);
        return sp;
    }

    private TextView makeBtnView(String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.WHITE); tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(colorHex)); bg.setCornerRadius(dp(6));
        tv.setBackground(bg);
        tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}