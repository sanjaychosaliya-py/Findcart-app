package com.example.findcartx1;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class ProfileActivity extends AppCompatActivity {

    EditText     etFullName, etMobile;
    RadioGroup   rgGender;
    TextView     tvDOB;
    LinearLayout btnPickDOB;
    Button       btnNext;
    String       selectedDOB = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_profile);

        etFullName = findViewById(R.id.etFullName);
        etMobile   = findViewById(R.id.etMobile);
        rgGender   = findViewById(R.id.rgGender);
        tvDOB      = findViewById(R.id.tvDOB);
        btnPickDOB = findViewById(R.id.btnPickDOB);
        btnNext    = findViewById(R.id.btnNext);

        btnPickDOB.setOnClickListener(v -> openDatePicker());
        btnNext.setOnClickListener(v -> validate());
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        int year  = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day   = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                android.R.style.Theme_Material_Light_Dialog,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format as DD-MM-YYYY
                    String dd   = String.format("%02d", selectedDay);
                    String mm   = String.format("%02d", selectedMonth + 1);
                    String yyyy = String.valueOf(selectedYear);

                    selectedDOB = dd + "-" + mm + "-" + yyyy;

                    // Show selected date in the box
                    tvDOB.setText(selectedDOB);
                    tvDOB.setTextColor(0xFF212121);
                },
                year, month, day
        );

        // Cannot pick a future date
        dialog.getDatePicker().setMaxDate(cal.getTimeInMillis());

        // Can go back 100 years
        Calendar minCal = Calendar.getInstance();
        minCal.add(Calendar.YEAR, -100);
        dialog.getDatePicker().setMinDate(minCal.getTimeInMillis());

        dialog.show();
    }

    private void validate() {
        String name   = etFullName.getText().toString().trim();
        String mobile = etMobile.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Name required");
            etFullName.requestFocus();
            return;
        }
        if (mobile.length() != 10) {
            etMobile.setError("Enter 10-digit mobile number");
            etMobile.requestFocus();
            return;
        }
        if (rgGender.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Please select gender",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(selectedDOB)) {
            Toast.makeText(this, "Please select date of birth",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb = findViewById(rgGender.getCheckedRadioButtonId());

        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        pref.edit()
                .putString("user_name",   name)
                .putString("user_mobile", mobile)
                .putString("user_gender", rb.getText().toString())
                .putString("user_dob",    selectedDOB)
                .apply();

        startActivity(new Intent(this, AddressActivity.class));
    }
}