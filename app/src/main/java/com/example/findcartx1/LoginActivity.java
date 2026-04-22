package com.example.findcartx1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    EditText etEmail, etPass;
    Button   btnLogin;
    TextView tvSignUp;

    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)

                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // If already logged in via Firebase → go straight to Map
        if (mAuth.getCurrentUser() != null) {
            goToMap();
            return;
        }

        // Load XML layout
        setContentView(R.layout.activity_login);

        // Wire up views from XML
        etEmail  = findViewById(R.id.etLoginUser);
        etPass   = findViewById(R.id.etLoginPass);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);

        // Sign up link
        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        // Email sign in button
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass  = etPass.getText().toString().trim();
            if (!validate(email, pass)) return;
            signInWithEmail(email, pass);
        });

        // Google sign in button from XML
        android.view.View btnGoogle = findViewById(R.id.btnGoogleSignIn);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> signInWithGoogle());
        }
    }

    // ── Email / Password Sign-In ──────────────────────────────────────────────

    private void signInWithEmail(String email, String pass) {
        btnLogin.setEnabled(false);
        btnLogin.setText("Signing in...");

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    SharedPreferences pref =
                            getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    pref.edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("saved_email", email)
                            .remove("user_name")
                            .remove("user_mobile")
                            .remove("user_city")
                            .remove("user_store")
                            .remove("user_dob")
                            .remove("user_gender")
                            .remove("profile_photo_b64")
                            .remove("profile_photo_uri")
                            .apply();

                    // Load full profile from Firestore
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        SharedPreferences.Editor ed = pref.edit();
                                        if (doc.getString("name")     != null) ed.putString("user_name",         doc.getString("name"));
                                        if (doc.getString("mobile")   != null) ed.putString("user_mobile",       doc.getString("mobile"));
                                        if (doc.getString("city")     != null) ed.putString("user_city",         doc.getString("city"));
                                        if (doc.getString("store")    != null) ed.putString("user_store",        doc.getString("store"));
                                        if (doc.getString("dob")      != null) ed.putString("user_dob",          doc.getString("dob"));
                                        if (doc.getString("gender")   != null) ed.putString("user_gender",       doc.getString("gender"));
                                        if (doc.getString("photoB64") != null) ed.putString("profile_photo_b64", doc.getString("photoB64"));
                                        ed.apply();
                                    }
                                    goToMap();
                                })
                                .addOnFailureListener(e -> goToMap());
                    } else {
                        goToMap();
                    }
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Sign In");
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("no user record"))
                        Toast.makeText(this,
                                "No account found. Please sign up first.",
                                Toast.LENGTH_LONG).show();
                    else if (msg != null && msg.contains("password is invalid"))
                        Toast.makeText(this,
                                "Wrong password. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(this,
                                "Login failed: " + msg,
                                Toast.LENGTH_LONG).show();
                });
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private void signInWithGoogle() {
        // Sign out first to always show account picker
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
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
                Toast.makeText(this,
                        "Google sign-in failed: " + e.getMessage(),
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

                    String displayName = name  != null ? name  : "";
                    String userEmail   = email != null ? email : "";

                    SharedPreferences pref =
                            getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    pref.edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("saved_email", userEmail)
                            .putString("user_name",   displayName)
                            .apply();

                    // Check if user already exists in Firestore
                    db.collection("users").document(user.getUid())
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    // New Google user → save to Firestore
                                    Map<String, Object> userData = new HashMap<>();
                                    userData.put("uid",          user.getUid());
                                    userData.put("email",        userEmail);
                                    userData.put("name",         displayName);
                                    userData.put("photoUrl",
                                            user.getPhotoUrl() != null
                                                    ? user.getPhotoUrl().toString() : "");
                                    userData.put("signInMethod", "Google");
                                    userData.put("createdAt",
                                            System.currentTimeMillis());

                                    db.collection("users")
                                            .document(user.getUid())
                                            .set(userData)
                                            .addOnSuccessListener(unused -> {
                                                // New user → go fill profile
                                                Intent i = new Intent(this,
                                                        ProfileActivity.class);
                                                i.putExtra("email", userEmail);
                                                startActivity(i);
                                            })
                                            .addOnFailureListener(e -> goToMap());
                                } else {
                                    // Existing user → load profile and go to map
                                    pref.edit()
                                            .putString("user_name",
                                                    doc.getString("name") != null
                                                            ? doc.getString("name") : displayName)
                                            .putString("user_city",
                                                    doc.getString("city") != null
                                                            ? doc.getString("city") : "")
                                            .putString("user_store",
                                                    doc.getString("store") != null
                                                            ? doc.getString("store") : "")
                                            .apply();
                                    goToMap();
                                }
                            })
                            .addOnFailureListener(e -> goToMap());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Auth failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    // ── Logout helper — called from MapActivity ───────────────────────────────
    // Signs out from both Firebase and Google so logout works properly

    public static void signOut(android.content.Context context,
                               GoogleSignInClient googleClient) {
        FirebaseAuth.getInstance().signOut();
        if (googleClient != null) {
            googleClient.signOut();
        }
        SharedPreferences pref = context.getSharedPreferences(
                "UserPrefs", android.content.Context.MODE_PRIVATE);
        pref.edit()
                .putBoolean("isLoggedIn", false)
                .remove("saved_email")
                .remove("user_name")
                .apply();
    }

    // ── Navigate to Map ───────────────────────────────────────────────────────

    private void goToMap() {
        Intent i = new Intent(this, MapActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validate(String email, String pass) {
        if (email.isEmpty()) {
            etEmail.setError("Enter email"); return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format"); return false;
        }
        if (pass.isEmpty()) {
            etPass.setError("Enter password"); return false;
        }
        if (pass.length() < 6) {
            etPass.setError("Min 6 characters"); return false;
        }
        return true;
    }
}