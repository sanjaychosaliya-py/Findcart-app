package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    EditText etEmail, etPassword;
    Button   btnSignUp;
    TextView tvLogin;

    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("16322131747-vvlclhacmfd73bq5s1dh9jri0d2vc37e.apps.googleusercontent.com")
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        setContentView(R.layout.activity_signup);

        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignUp  = findViewById(R.id.btnSignUp);
        tvLogin    = findViewById(R.id.tvLogin);

        tvLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        btnSignUp.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass  = etPassword.getText().toString().trim();
            if (!validate(email, pass)) return;
            createAccount(email, pass);
        });

        // Add Google Sign-In button below the signup button in XML
        // Here we wire it up programmatically if it exists
        android.view.View btnGoogle = findViewById(R.id.btnGoogleSignIn);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> signInWithGoogle());
        }
    }

    private void createAccount(String email, String pass) {
        btnSignUp.setEnabled(false);
        btnSignUp.setText("Creating account...");

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    pref.edit()
                            .putString("saved_email",    email)
                            .putString("saved_password", pass)
                            .apply();

                    if (user != null) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("uid",          user.getUid());
                        userData.put("email",        email);
                        userData.put("signInMethod", "Email");
                        userData.put("createdAt",    System.currentTimeMillis());

                        db.collection("users").document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Account created!",
                                            Toast.LENGTH_SHORT).show();
                                    Intent i = new Intent(this, ProfileActivity.class);
                                    i.putExtra("email", email);
                                    startActivity(i);
                                })
                                .addOnFailureListener(e ->
                                        startActivity(new Intent(this, ProfileActivity.class)));
                    }
                })
                .addOnFailureListener(e -> {
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText("Create Account");
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("email address is already in use"))
                        Toast.makeText(this, "Account already exists. Please log in.",
                                Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(this, "Signup failed: " + msg,
                                Toast.LENGTH_LONG).show();
                });
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken(),
                        account.getEmail(), account.getDisplayName());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String email, String name) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    String displayName = name != null ? name : "";
                    String userEmail   = email != null ? email : "";

                    SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    pref.edit()
                            .putString("saved_email", userEmail)
                            .putString("user_name",   displayName)
                            .putBoolean("isLoggedIn", true)
                            .apply();

                    // Check if new user
                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    // New Google user — save to Firestore + go to profile
                                    Map<String, Object> userData = new HashMap<>();
                                    userData.put("uid",          user.getUid());
                                    userData.put("email",        userEmail);
                                    userData.put("name",         displayName);
                                    userData.put("signInMethod", "Google");
                                    userData.put("createdAt",    System.currentTimeMillis());
                                    db.collection("users").document(user.getUid())
                                            .set(userData)
                                            .addOnSuccessListener(unused -> {
                                                // New Google user → go fill profile
                                                Intent i = new Intent(this, ProfileActivity.class);
                                                i.putExtra("email", userEmail);
                                                startActivity(i);
                                            });
                                } else {
                                    // Existing user → go to map
                                    Intent i = new Intent(this, MapActivity.class);
                                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(i);
                                    finish();
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Auth failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private boolean validate(String email, String pass) {
        if (email.isEmpty()) { etEmail.setError("Email required"); return false; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email"); return false; }
        if (pass.length() < 6) { etPassword.setError("Min 6 characters"); return false; }
        return true;
    }
}