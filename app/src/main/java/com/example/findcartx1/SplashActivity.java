package com.example.findcartx1;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int[] CARD_IDS = {
            R.id.card1, R.id.card2, R.id.card3, R.id.card4, R.id.card5,
            R.id.card6, R.id.card7, R.id.card8, R.id.card9, R.id.card10
    };
    private static final int[] DOT_IDS = {
            R.id.dot1, R.id.dot2, R.id.dot3
    };

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);

        // Setup notification channels
        NotificationHelper.createChannels(this);

        // Make logo circular clip
        ImageView ivLogo = findViewById(R.id.ivLogo);
        if (ivLogo != null) {
            ivLogo.setClipToOutline(true);
            ivLogo.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
                }
            });
        }

        // Set top section height to 68% of screen
        View topSection = findViewById(R.id.topSection);
        if (topSection != null) {
            topSection.post(() -> {
                int screenH = getResources().getDisplayMetrics().heightPixels;
                android.view.ViewGroup.LayoutParams lp = topSection.getLayoutParams();
                lp.height = (int)(screenH * 0.68f);
                topSection.setLayoutParams(lp);
            });
        }

        // Start animation sequence
        animateCards();
    }

    // ── Animate cards one by one then fade in bottom section ─────────────────
    private void animateCards() {
        for (int k = 0; k < CARD_IDS.length; k++) {
            final int idx = k;
            handler.postDelayed(() -> animateCard(CARD_IDS[idx]), 80 + idx * 130L);
        }

        // After all cards appear, fade in bottom section
        handler.postDelayed(() -> {
            animateBottom();
        }, 80 + CARD_IDS.length * 130L + 100);

        // Start dot pulse
        handler.postDelayed(this::pulseDots, 1800);

        // Redirect after 3.2 seconds
        handler.postDelayed(this::redirect, 3200);
    }

    private void animateCard(int id) {
        View card = findViewById(id);
        if (card == null) return;
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setScaleX(0.6f);
        card.setScaleY(0.6f);
        card.setTranslationY(dp(18));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(card, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(card, "scaleY", 0.6f, 1f),
                ObjectAnimator.ofFloat(card, "translationY", dp(18), 0f)
        );
        set.setDuration(380);
        set.setInterpolator(new OvershootInterpolator(1.2f));
        set.start();
    }

    private void animateBottom() {
        View bottom = findViewById(R.id.bottomSection);
        if (bottom == null) return;

        bottom.setAlpha(0f);
        bottom.setTranslationY(dp(24));

        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(bottom, "alpha", 0f, 1f);
        ObjectAnimator transAnim = ObjectAnimator.ofFloat(bottom, "translationY", dp(24), 0f);
        alphaAnim.setDuration(500);
        transAnim.setDuration(500);
        alphaAnim.setInterpolator(new DecelerateInterpolator());
        transAnim.setInterpolator(new DecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alphaAnim, transAnim);
        set.start();

        // Start ripple animation
        animateRipple();
    }

    private void animateRipple() {
        View r1 = findViewById(R.id.ripple1);
        View r2 = findViewById(R.id.ripple2);
        if (r1 == null || r2 == null) return;

        startRippleRing(r1, 0);
        startRippleRing(r2, 600);
    }

    private void startRippleRing(View ring, long delay) {
        handler.postDelayed(() -> {
            ring.setAlpha(0.5f);
            ring.setScaleX(1f); ring.setScaleY(1f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(ring, "alpha", 0.5f, 0f),
                    ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.2f),
                    ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.2f)
            );
            set.setDuration(900);
            set.setInterpolator(new DecelerateInterpolator());
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    startRippleRing(ring, 0); // loop
                }
            });
            set.start();
        }, delay);
    }

    // ── Dot pulse animation ───────────────────────────────────────────────────
    private void pulseDots() {
        for (int k = 0; k < DOT_IDS.length; k++) {
            final int idx = k;
            handler.postDelayed(() -> pulseDot(DOT_IDS[idx]), idx * 200L);
        }
    }

    private void pulseDot(int id) {
        View dot = findViewById(id);
        if (dot == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.8f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.8f, 1f);
        scaleX.setDuration(500); scaleY.setDuration(500);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    // ── Redirect ──────────────────────────────────────────────────────────────
    private void redirect() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean localLoggedIn = pref.getBoolean("isLoggedIn", false);
        boolean isLoggedIn    = firebaseUser != null || localLoggedIn;

        if (firebaseUser != null && !localLoggedIn) {
            pref.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("saved_email",
                            firebaseUser.getEmail() != null
                                    ? firebaseUser.getEmail() : "")
                    .apply();
        }

        if (isLoggedIn) {
            String name = pref.getString("user_name", "");
            NotificationHelper.sendWelcomeBack(this, name);
        }

        Intent intent = new Intent(this,
                isLoggedIn ? MapActivity.class : LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}