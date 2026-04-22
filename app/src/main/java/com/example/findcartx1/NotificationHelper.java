package com.example.findcartx1;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class NotificationHelper {

    private static final String CHANNEL_OFFERS   = "findcartx1_offers";
    private static final String CHANNEL_FLIRT    = "findcartx1_flirt";
    private static final String CHANNEL_PERSONAL = "findcartx1_personal";
    private static int notifId = 1000;

    // ── Flirting / Fun lines shown randomly ──────────────────────────────────
    private static final String[] FLIRT_LINES = {
            "Hey! Your cart misses you 😍 Come back and shop!",
            "You + FindCartx1 = Perfect match 💚",
            "We saved your favourite items just for you 🛒",
            "Looking for something? We have everything you need 😊",
            "Your wishlist is calling… answer it! 📞",
            "Good things don't last long — grab your items today! ⚡",
            "You have great taste! Come see today's deals 🌟",
            "Special person, special offers — just for you 💚",
            "We missed you! Your favourite items are still here 🛍",
            "Today feels like a perfect shopping day, doesn't it? 😄",
            "Your items are waiting at the rack… don't keep them waiting! 😅",
            "Psst… we have a secret deal just for you today 🤫",
            "Shopping is self-care and you deserve it! 💆",
            "Your last order was amazing — ready for the next one? 🚀",
            "FindCartx1 — because you deserve the best! ⭐",
    };

    // ── Offer notifications based on most bought category ────────────────────
    private static final Map<String, String[]> CATEGORY_OFFERS = new HashMap<>();
    static {
        CATEGORY_OFFERS.put("Snacks",       new String[]{"20% OFF on all Snacks today! 🍟", "Grab your favourite chips — 20% off right now!"});
        CATEGORY_OFFERS.put("Beverages",    new String[]{"BUY 2 GET 1 FREE on Beverages! 🥤", "Stay refreshed — Buy 2 drinks get 1 free today!"});
        CATEGORY_OFFERS.put("Dairy",        new String[]{"Fresh Dairy — 10% OFF today! 🥛", "Stock up on Amul and Mother Dairy at 10% off!"});
        CATEGORY_OFFERS.put("Biscuits",     new String[]{"25% OFF on all Biscuits! 🍪", "Biscuit lovers — 25% off your favourites today!"});
        CATEGORY_OFFERS.put("Electronics",  new String[]{"Electronics sale — 15% OFF! ⚡", "Grab cables, chargers and earphones at 15% off!"});
        CATEGORY_OFFERS.put("Kitchenware",  new String[]{"10% OFF on all Kitchenware! 🍳", "Upgrade your kitchen — 10% off today only!"});
        CATEGORY_OFFERS.put("Baby",         new String[]{"Rs100 OFF on Baby Products! 👶", "Stock up on baby essentials — Rs100 off today!"});
        CATEGORY_OFFERS.put("Personal Care",new String[]{"20% OFF on Personal Care! 💆", "Pamper yourself — 20% off shampoos and lotions!"});
        CATEGORY_OFFERS.put("Stationery",   new String[]{"12% OFF on Stationery! 📚", "Back to school deals — 12% off notebooks and pens!"});
        CATEGORY_OFFERS.put("Clothing",     new String[]{"Special deals on Clothing! 👕", "Fresh fashion deals — check our clothing section!"});
        CATEGORY_OFFERS.put("Chocolates",   new String[]{"Sweet deals on Chocolates! 🍫", "Chocolate lovers — special offers just for you!"});
        CATEGORY_OFFERS.put("Cleaning",     new String[]{"Home cleaning deals today! 🧹", "Keep it clean — special offers on cleaning products!"});
        CATEGORY_OFFERS.put("Grains",       new String[]{"Best prices on Rice and Dal! 🌾", "Stock your pantry — great deals on grains today!"});
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SETUP — call once from SplashActivity or MapActivity
    // ══════════════════════════════════════════════════════════════════════════

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

            // Offers channel
            NotificationChannel offersChannel = new NotificationChannel(
                    CHANNEL_OFFERS, "Special Offers",
                    NotificationManager.IMPORTANCE_DEFAULT);
            offersChannel.setDescription("Discounts and deals from FindCartx1");
            nm.createNotificationChannel(offersChannel);

            // Flirt/fun channel
            NotificationChannel flirtChannel = new NotificationChannel(
                    CHANNEL_FLIRT, "FindCartx1 Updates",
                    NotificationManager.IMPORTANCE_LOW);
            flirtChannel.setDescription("Fun updates and reminders");
            nm.createNotificationChannel(flirtChannel);

            // Personal recommendations channel
            NotificationChannel personalChannel = new NotificationChannel(
                    CHANNEL_PERSONAL, "For You",
                    NotificationManager.IMPORTANCE_DEFAULT);
            personalChannel.setDescription("Personalised offers based on your shopping");
            nm.createNotificationChannel(personalChannel);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SEND RANDOM FLIRT NOTIFICATION
    // ══════════════════════════════════════════════════════════════════════════

    public static void sendFlirtNotification(Context ctx) {
        String line = FLIRT_LINES[new Random().nextInt(FLIRT_LINES.length)];
        sendNotif(ctx, CHANNEL_FLIRT, "FindCartx1 💚", line, notifId++);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SEND WELCOME BACK NOTIFICATION (after login)
    // ══════════════════════════════════════════════════════════════════════════

    public static void sendWelcomeBack(Context ctx, String userName) {
        String name = (userName != null && !userName.isEmpty()) ? userName : "Shopper";
        sendNotif(ctx, CHANNEL_FLIRT, "Welcome back, " + name + "! 👋",
                "Your favourite products are waiting for you. Come check today's deals!",
                notifId++);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SEND ORDER SUCCESS NOTIFICATION
    // ══════════════════════════════════════════════════════════════════════════

    public static void sendOrderSuccess(Context ctx, String orderId, double total) {
        sendNotif(ctx, CHANNEL_OFFERS,
                "Order Placed Successfully! ✅",
                "Order #" + orderId + " · Rs" + String.format("%.0f", total)
                        + " · Thank you for shopping at FindCartx1!",
                notifId++);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ANALYSE PURCHASE HISTORY + SEND PERSONALISED OFFER
    //  Reads local order history, finds most bought category,
    //  sends targeted offer notification
    // ══════════════════════════════════════════════════════════════════════════

    public static void sendPersonalisedOffer(Context ctx) {
        try {
            SharedPreferences pref = ctx.getSharedPreferences("UserPrefs",
                    Context.MODE_PRIVATE);
            String histJson = pref.getString("order_history", "[]");
            JSONArray history = new JSONArray(histJson);

            if (history.length() == 0) {
                // No history — send generic offer
                sendNotif(ctx, CHANNEL_OFFERS,
                        "Today's Special Deals! 🔥",
                        "20% OFF on Snacks · BUY 2+1 on Beverages · 10% OFF on Dairy",
                        notifId++);
                return;
            }

            // Count category purchases
            Map<String, Integer> catCount = new HashMap<>();
            for (int o = 0; o < history.length(); o++) {
                JSONObject order = history.getJSONObject(o);
                JSONArray items = order.optJSONArray("items");
                if (items == null) continue;
                for (int k = 0; k < items.length(); k++) {
                    String cat = items.getJSONObject(k).optString("category", "");
                    if (!cat.isEmpty()) {
                        catCount.put(cat, catCount.getOrDefault(cat, 0) + 1);
                    }
                }
            }

            if (catCount.isEmpty()) {
                sendFlirtNotification(ctx);
                return;
            }

            // Find top category
            String topCat = Collections.max(catCount.entrySet(),
                    Map.Entry.comparingByValue()).getKey();

            // Get offer messages for that category
            String[] msgs = CATEGORY_OFFERS.get(topCat);
            if (msgs == null) {
                // Category not in offer map — send generic
                sendNotif(ctx, CHANNEL_PERSONAL,
                        "Special offer for you! 🎁",
                        "Based on your shopping — check today's deals on " + topCat,
                        notifId++);
                return;
            }

            // Send personalised offer
            String title = "Just for you — " + topCat + " lover! 🎯";
            String msg   = msgs[new Random().nextInt(msgs.length)];
            sendNotif(ctx, CHANNEL_PERSONAL, title, msg, notifId++);

            // Also save what we sent so we can show in app
            pref.edit().putString("last_offer_notif",
                    title + "\n" + msg).apply();

        } catch (Exception e) {
            e.printStackTrace();
            sendFlirtNotification(ctx);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET PURCHASE STATS — for showing in UI
    //  Returns map of category → purchase count
    // ══════════════════════════════════════════════════════════════════════════

    public static Map<String, Integer> getPurchaseStats(Context ctx) {
        Map<String, Integer> catCount = new LinkedHashMap<>();
        try {
            SharedPreferences pref = ctx.getSharedPreferences("UserPrefs",
                    Context.MODE_PRIVATE);
            String histJson = pref.getString("order_history", "[]");
            JSONArray history = new JSONArray(histJson);

            Map<String, Integer> raw = new HashMap<>();
            for (int o = 0; o < history.length(); o++) {
                JSONObject order = history.getJSONObject(o);
                JSONArray items = order.optJSONArray("items");
                if (items == null) continue;
                for (int k = 0; k < items.length(); k++) {
                    String cat = items.getJSONObject(k).optString("category", "");
                    String name = items.getJSONObject(k).optString("name", "");
                    if (!cat.isEmpty())
                        raw.put(cat, raw.getOrDefault(cat, 0) + 1);
                }
            }

            // Sort by count descending
            raw.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> catCount.put(e.getKey(), e.getValue()));

        } catch (Exception ignored) {}
        return catCount;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INTERNAL — build and show notification
    // ══════════════════════════════════════════════════════════════════════════

    private static void sendNotif(Context ctx, String channel,
                                  String title, String message, int id) {
        try {
            Intent intent = new Intent(ctx, MapActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}