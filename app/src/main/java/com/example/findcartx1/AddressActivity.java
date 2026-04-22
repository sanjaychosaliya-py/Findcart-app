package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddressActivity extends AppCompatActivity {

    EditText etAddress;
    Spinner  spinnerCity, spinnerStore;
    Button   btnSave;

    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_address);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etAddress    = findViewById(R.id.etAddress);
        spinnerCity  = findViewById(R.id.spinnerCity);
        spinnerStore = findViewById(R.id.spinnerSupermarket);
        btnSave      = findViewById(R.id.btnNextAddress);

        setupSpinner(spinnerCity,
                Arrays.asList("Select City", "Surat", "Vadodara",
                        "Ahmedabad", "Rajkot", "Bhavnagar"));
        setupSpinner(spinnerStore,
                Arrays.asList("Select Store", "FindCartx1 Mega Store",
                        "FindCartx1 City Mall", "FindCartx1 Express"));

        btnSave.setOnClickListener(v -> {
            String addr = etAddress.getText().toString().trim();
            if (addr.isEmpty()) {
                etAddress.setError("Address required"); return;
            }
            if (spinnerCity.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Please select a city",
                        Toast.LENGTH_SHORT).show(); return;
            }
            if (spinnerStore.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Please select a store",
                        Toast.LENGTH_SHORT).show(); return;
            }
            saveToFirestore(addr);
        });
    }

    private void saveToFirestore(String addr) {
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String city  = spinnerCity.getSelectedItem().toString();
        String store = spinnerStore.getSelectedItem().toString();
        String name  = pref.getString("user_name",   "");
        String mobile = pref.getString("user_mobile","");
        String gender = pref.getString("user_gender","");
        String dob    = pref.getString("user_dob",   "");

        // Save to SharedPrefs
        pref.edit()
                .putString("user_address", addr)
                .putString("user_city",    city)
                .putString("user_store",   store)
                .apply();

        // Save complete profile to Firestore
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("uid",     user.getUid());
            profile.put("email",   pref.getString("saved_email", ""));
            profile.put("name",    name);
            profile.put("mobile",  mobile);
            profile.put("gender",  gender);
            profile.put("dob",     dob);
            profile.put("address", addr);
            profile.put("city",    city);
            profile.put("store",   store);
            profile.put("updatedAt", System.currentTimeMillis());

            db.collection("users").document(user.getUid())
                    .set(profile)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this,
                                "Profile saved! Please log in.",
                                Toast.LENGTH_LONG).show();
                        Intent i = new Intent(this, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // Even if Firebase fails save locally and continue
                        Toast.makeText(this,
                                "Registration complete! Please log in.",
                                Toast.LENGTH_LONG).show();
                        Intent i = new Intent(this, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    });
        } else {
            // No Firebase user — save locally
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }
    }

    private void setupSpinner(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }
}