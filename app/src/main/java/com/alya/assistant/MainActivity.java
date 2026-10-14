package com.alya.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_MICROPHONE = 21;
    private static final int REQUEST_CAMERA = 22;
    private static final int REQUEST_NOTIFICATIONS = 23;
    private static final int REQUEST_LOCATION = 24;
    private static final int REQUEST_CONTACTS = 25;
    private static final int REQUEST_SMS = 26;
    private static final int REQUEST_CALL = 27;
    private static final int REQUEST_PHONE_STATE = 28;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<JSONObject> messages = new ArrayList<>();
    private final List<String> savedMemories = new ArrayList<>();
    private JSONArray conversations = new JSONArray();
    private String activeConversationId = "";
    private String currentPage = "Chat";
    private String language = "Automatic";
    private String model = "gemini-2.5-flash";
    private String cameraId;
    private EditText composer;
    private LinearLayout content;
    private ScrollView chatScroll;
    private Button sendButton;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private boolean speakReplies;
    private boolean busy;
    private boolean dark;
    private volatile HttpURLConnection activeConnection;
    private volatile int requestGeneration;
    private String pendingCallNumber = "";

    @Override
    protected void onCreate(Bundle state) {
        dark = getPreferences(0).getBoolean("dark", false);
        setTheme(dark ? R.style.DarkTheme : R.style.AppTheme);
        super.onCreate(state);
        loadConversations();
        startNewConversationIfNeeded();
        loadSettings();
        try {
            if (SecureKeyStore.getUserApiKey(this).isEmpty()) currentPage = "Settings";
        } catch (Exception ignored) {
            currentPage = "Settings";
        }
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) selectNaturalVoice();
        });
        render();
    }

    private int color(int light, int night) { return getColor(dark ? night : light); }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(0xFFF8F8F5, 0xFF202724));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(15), dp(20), dp(12));
        TextView brand = text("✦  alya", 20, 0xFF315644);
        brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text("LOCAL HISTORY · NO ACCOUNT", 9, color(0xFF879187, 0xFFADB8B0));
        header.addView(state);
        root.addView(header);
        View rule = new View(this);
        rule.setBackgroundColor(color(0xFFE5E8E2, 0xFF3C4741));
        root.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(19), dp(18), dp(19), dp(12));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        renderPage();

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(5), dp(7), dp(5), dp(7));
        navigation.setBackgroundColor(color(0xFFFFFFFF, 0xFF282F2B));
        for (String page : new String[]{"Chat", "History", "Device", "Settings"}) {
            Button button = button(page, currentPage.equals(page) ? 0xFFE7F0E8 : 0x00000000);
            button.setTextSize(11);
            button.setOnClickListener(view -> { currentPage = page; render(); });
            navigation.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        root.addView(navigation);
    }

    private void renderPage() {
        content.removeAllViews();
        switch (currentPage) {
            case "History": renderHistory(); break;
            case "Device": renderDevice(); break;
            case "Settings": renderSettings(); break;
            default: renderChat(); break;
        }
    }

    private void renderChat() {
        TextView heading = text("Alya Assistant", 18, color(0xFF25302D, 0xFFE8EEE9));
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);
        TextView subheading = text("A virtual assistant · " + language, 11, color(0xFF818B85, 0xFFA0AAA4));
        subheading.setPadding(0, dp(4), 0, dp(12));
        content.addView(subheading);

        chatScroll = new ScrollView(this);
        LinearLayout transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        if (messages.isEmpty()) {
            TextView welcome = text("A fresh start, whenever you need.\n\nAdd your Gemini API key in Settings to begin.", 16, color(0xFF66776B, 0xFFB3C4B8));
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(20), dp(45), dp(20), dp(45));
            transcript.addView(welcome);
        }
        for (JSONObject message : messages) addMessageBubble(transcript, message.optString("role"), message.optString("text"));
        chatScroll.addView(transcript);
        content.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        composer = new EditText(this);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setHint("Write a message…");
        composer.setTextSize(14);
        composer.setPadding(dp(12), dp(8), dp(8), dp(8));
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));
        Button microphone = button("Mic", 0xFFE7F0E8);
        microphone.setContentDescription("Speech to text");
        microphone.setOnClickListener(view -> requestSpeech());
        inputRow.addView(microphone);
        sendButton = button(busy ? "Stop" : "Send", busy ? 0xFFBA6756 : 0xFF315644);
        sendButton.setTextColor(0xFFFFFFFF);
        sendButton.setOnClickListener(view -> { if (busy) cancelRequest(); else sendMessage(); });
        composer.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });
        inputRow.addView(sendButton);
        content.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        TextView notice = text("Microphone used only when tapped · Live streaming unavailable", 9, color(0xFF818B85, 0xFFA0AAA4));
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(0, dp(7), 0, 0);
        content.addView(notice);
    }

    private void addMessageBubble(LinearLayout transcript, String role, String message) {
        TextView bubble = text(message, 13, color(0xFF25302D, 0xFFE8EEE9));
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackgroundColor(role.equals("user") ? color(0xFFEAF1E9, 0xFF34453A) : color(0xFFFFFFFF, 0xFF282F2B));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(9));
        transcript.addView(bubble, params);
        if (role.equals("model")) {
            Button play = button("Read aloud", 0x00000000);
            play.setTextSize(10);
            play.setOnClickListener(view -> readAloud(message));
            transcript.addView(play, new LinearLayout.LayoutParams(-2, dp(34)));
        }
    }

    private void sendMessage() {
        if (busy || composer == null) return;
        String prompt = composer.getText().toString().trim();
        if (prompt.isEmpty()) return;
        String apiKey;
        try {
            apiKey = SecureKeyStore.getUserApiKey(this);
        } catch (Exception error) {
            currentPage = "Settings";
            render();
            showError("Could not read the encrypted API key. Re-enter it in Settings.");
            return;
        }
        if (apiKey.isEmpty()) {
            currentPage = "Settings";
            render();
            showError("Add your Gemini API key in Settings to start a conversation.");
            return;
        }
        composer.setText("");
        hideKeyboard();
        JSONObject userMessage = geminiMessage("user", prompt);
        messages.add(userMessage);
        if (messages.size() == 1) updateConversationTitle(prompt);
        persistMessages();
        List<JSONObject> requestMessages = new ArrayList<>(messages);
        List<String> requestMemories = new ArrayList<>(savedMemories);
        String requestConversationId = activeConversationId;
        int requestId = ++requestGeneration;
        busy = true;
        render();
        try {
            executor.execute(() -> {
                try {
                    String answer = GeminiClient.generate(apiKey, model, language, requestMessages, requestMemories, connection -> {
                        if (requestId == requestGeneration) activeConnection = connection;
                        if (connection != null && requestId != requestGeneration) connection.disconnect();
                    });
                    runOnUiThread(() -> {
                        if (requestId != requestGeneration) return;
                        activeConnection = null;
                        JSONObject response = geminiMessage("model", answer);
                        saveResponse(requestConversationId, requestMessages, response);
                        busy = false;
                        render();
                        if (speakReplies) readAloud(answer);
                    });
                } catch (Exception error) {
                    String safeMessage = error instanceof IllegalStateException ? error.getMessage() : "Could not reach Gemini. Check your connection and try again.";
                    runOnUiThread(() -> {
                        if (requestId != requestGeneration) return;
                        activeConnection = null;
                        busy = false;
                        persistMessages();
                        showError(safeMessage == null ? "Gemini request failed." : safeMessage);
                        render();
                    });
                }
            });
        } catch (Exception error) {
            busy = false;
            showError("Could not start the request.");
        }
    }

    private JSONObject geminiMessage(String role, String text) {
        try { return new JSONObject().put("role", role).put("parts", new JSONArray().put(new JSONObject().put("text", text))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void renderHistory() {
        pageHeading("Conversation history", "Stored locally on this device");
        Button create = button("+  New conversation", 0xFFE7F0E8);
        create.setOnClickListener(view -> { startNewConversation(); currentPage = "Chat"; render(); });
        content.addView(create);
        ScrollView list = new ScrollView(this);
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        for (int index = conversations.length() - 1; index >= 0; index--) {
            JSONObject conversation = conversations.optJSONObject(index);
            if (conversation == null) continue;
            String id = conversation.optString("id");
            Button item = button(conversation.optString("title", "Conversation"), 0xFFFFFFFF);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setOnClickListener(view -> openConversation(id));
            item.setOnLongClickListener(view -> {
                new AlertDialog.Builder(this).setTitle("Delete conversation?")
                        .setMessage("This removes its locally saved messages.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> deleteConversation(id)).show();
                return true;
            });
            items.addView(item, new LinearLayout.LayoutParams(-1, dp(49)));
        }
        list.addView(items);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        Button clear = button("Clear all history", 0x00000000);
        clear.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Clear all history?")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    conversations = new JSONArray();
                    persistConversations();
                    messages.clear();
                    activeConversationId = "";
                    startNewConversationIfNeeded();
                    render();
                }).show());
        content.addView(clear);
    }

    private void renderDevice() {
        pageHeading("Device capabilities", "Actual permission and hardware state");
        boolean micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean notificationGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean contactsGranted = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        addCapability("Microphone", micGranted ? "Granted · only used after you tap Mic" : "Not granted · requested when voice input starts", micGranted);
        addCapability("Notifications", notificationGranted ? "Available" : "Not granted · requested when setting a timer", notificationGranted);
        addCapability("Location", locationGranted ? "Granted · location read only after you request it" : "Not granted", locationGranted);
        addCapability("Contacts", contactsGranted ? "Granted · read only after you request it" : "Not granted", contactsGranted);
        boolean notificationsEnabled = getSystemService(android.app.NotificationManager.class)
            .isNotificationListenerAccessGranted(new ComponentName(this, AlyaNotificationListenerService.class));
        addCapability("Notification access", notificationsEnabled ? "Enabled · WhatsApp/phone alerts stay on device" : "Not enabled", notificationsEnabled);
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        addCapability("Accessibility", accessibilityEnabled ? "Enabled · screen actions only when you tap a control" : "Not enabled", accessibilityEnabled);
        addCapability("Floating orb", Settings.canDrawOverlays(this) ? "Overlay access granted" : "Overlay access not granted", Settings.canDrawOverlays(this));
        addCapability("Foreground session", AlyaForegroundService.isRunning() ? "Running · no background microphone" : "Stopped", AlyaForegroundService.isRunning());
        PowerManager powerManager = getSystemService(PowerManager.class);
        boolean batteryExempt = powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        addCapability("Battery optimization", batteryExempt ? "Exempted by system" : "System battery policy applies", batteryExempt);
        boolean assistantRole = getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_ASSISTANT);
        addCapability("Default assistant", assistantRole ? "Alya is selected" : "Not selected", assistantRole);
        addCapability("Flashlight", getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                ? (cameraGranted ? "Available · camera permission granted" : "Available · permission requested on use") : "Not available on this device",
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        addCapability("Background microphone / wake word", "Unavailable · no background listening", false);
        int battery = ((BatteryManager) getSystemService(BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        addCapability("Battery", battery >= 0 ? battery + "%" : "Unavailable", battery >= 0);
        Button flashlight = button("Toggle flashlight", 0xFFE7F0E8);
        flashlight.setOnClickListener(view -> toggleFlashlight());
        content.addView(flashlight);
        Button wifiSettings = button("Open Wi-Fi settings", 0xFFE7F0E8);
        wifiSettings.setOnClickListener(view -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)); }
            catch (Exception error) { showError("Wi-Fi settings could not be opened."); }
        });
        content.addView(wifiSettings);
        Button timer = button("Create a timer", 0xFFE7F0E8);
        timer.setOnClickListener(view -> showTimerDialog());
        content.addView(timer);
        Button manage = button("Permissions and services", 0xFFE7F0E8);
        manage.setOnClickListener(view -> showCapabilityMenu());
        content.addView(manage);
        Button screen = button("Read visible screen text", 0xFFE7F0E8);
        screen.setOnClickListener(view -> readVisibleScreen());
        content.addView(screen);
        Button click = button("Click a visible control by exact text", 0xFFE7F0E8);
        click.setOnClickListener(view -> requestVisibleTextClick());
        content.addView(click);
        Button apps = button("Open an installed app", 0xFFE7F0E8);
        apps.setOnClickListener(view -> showLaunchableApps());
        content.addView(apps);
        Button back = button("Go back / home", 0xFFE7F0E8);
        back.setOnClickListener(view -> showNavigationActions());
        content.addView(back);
        Button latestNotification = button("Show latest WhatsApp or phone notification", 0xFFE7F0E8);
        latestNotification.setOnClickListener(view -> showLatestNotification());
        content.addView(latestNotification);
        Button directActions = button("Contacts, location, SMS, and calls", 0xFFE7F0E8);
        directActions.setOnClickListener(view -> showPersonalDataActions());
        content.addView(directActions);
        TextView note = text("Screen access and notification access are optional and remain on this device. Android controls these settings; app installation, lock-screen access, and background execution may be restricted.", 10, color(0xFF818B85, 0xFFA0AAA4));
        note.setPadding(0, dp(10), 0, 0);
        content.addView(note);
    }

    private void showCapabilityMenu() {
        String[] options = {"Microphone", "Camera / flashlight", "Location", "Contacts", "Send SMS", "Place phone call", "Notifications", "Accessibility screen actions", "WhatsApp / phone notification access", "Floating orb", "Foreground session", "Battery settings", "Default assistant"};
        new AlertDialog.Builder(this).setTitle("Choose a capability")
                .setItems(options, (dialog, which) -> requestCapability(which)).show();
    }

    private void requestCapability(int capability) {
        switch (capability) {
            case 0:
                explainThenRequest("Microphone", "Used only after you tap Mic to recognize speech. Alya does not listen in the background.", Manifest.permission.RECORD_AUDIO, REQUEST_MICROPHONE, this::requestSpeech);
                break;
            case 1:
                explainThenRequest("Camera", "Needed by Android to operate the flashlight. Alya does not capture photos.", Manifest.permission.CAMERA, REQUEST_CAMERA, this::toggleFlashlight);
                break;
            case 2:
                explainThenRequestLocation();
                break;
            case 3:
                explainThenRequest("Contacts", "Allows you to view contacts on this device only after you choose Show contacts.", Manifest.permission.READ_CONTACTS, REQUEST_CONTACTS, this::showContacts);
                break;
            case 4:
                explainThenRequest("Send SMS", "Allows a text message to be sent only after you review the recipient and message and confirm.", Manifest.permission.SEND_SMS, REQUEST_SMS, this::composeSms);
                break;
            case 5:
                explainThenRequest("Phone call", "Allows Alya to place a call only after you review the number and confirm.", Manifest.permission.CALL_PHONE, REQUEST_CALL, this::composeCall);
                break;
            case 6:
                if (Build.VERSION.SDK_INT >= 33) explainThenRequest("Notifications", "Used to show timer alerts and the visible foreground-session notification.", Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS, () -> showError("Notifications are enabled."));
                else showError("Notifications are available on this Android version.");
                break;
            case 7:
                explainThenOpenSettings("Accessibility screen actions", "Optional. Android will let Alya read visible non-password text or click an exact matching control only when you tap those controls. Screen content is not sent to Gemini.", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case 8:
                explainThenOpenSettings("Notification access", "Optional. Alya can show WhatsApp and phone notification text on this device when notifications expose it. Notification contents are not uploaded or saved.", new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                break;
            case 9:
                explain("Floating orb", "Allows a small overlay on top of other apps. It is shown only when you start it and closes when tapped.", () -> {
                    if (Settings.canDrawOverlays(this)) startService(new Intent(this, AlyaOverlayService.class));
                    else explainThenOpenSettings("Floating orb access", "Android requires you to grant overlay access in system settings.", new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                });
                break;
            case 10:
                explain("Foreground session", "Shows an ongoing notification while Alya's user-started session is active. It does not keep a microphone open and Android may still stop it.", () -> toggleForegroundSession());
                break;
            case 11:
                explainThenOpenSettings("Battery settings", "Android may delay timers or stop background work. Exemption is optional, may not be offered, and is never required for chat.", new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                break;
            case 12:
                requestAssistantRole();
                break;
            default:
                break;
        }
    }

    private void explainThenRequest(String title, String reason, String permission, int requestCode, Runnable afterGrant) {
        explain(title, reason, () -> {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                afterGrant.run();
            } else {
                requestPermissions(new String[]{permission}, requestCode);
            }
        });
    }

    private void explainThenRequestLocation() {
        explain("Location", "Used only when you tap the location action. Alya reads the device's last-known location and does not track in the background.", () -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                showLastKnownLocation();
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            }
        });
    }

    private void explain(String title, String reason, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(reason)
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (dialog, which) -> action.run()).show();
    }

    private void explainThenOpenSettings(String title, String reason, Intent intent) {
        explain(title, reason, () -> {
            try { startActivity(intent); }
            catch (Exception error) { showError("Android settings could not be opened."); }
        });
    }

    private void toggleForegroundSession() {
        if (AlyaForegroundService.isRunning()) {
            stopService(new Intent(this, AlyaForegroundService.class));
        } else {
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, AlyaForegroundService.class));
                else startService(new Intent(this, AlyaForegroundService.class));
            } catch (Exception error) {
                showError("Android did not allow the foreground session to start.");
            }
        }
        content.postDelayed(this::render, 250);
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String component = new ComponentName(this, AlyaAccessibilityService.class).flattenToString();
        for (String item : enabled.split(":")) if (component.equalsIgnoreCase(item)) return true;
        return false;
    }

    private void readVisibleScreen() {
        AlyaAccessibilityService service = AlyaAccessibilityService.getInstance();
        if (service == null) {
            showError("Enable Alya screen actions in Permissions and services first.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Visible screen text")
                .setMessage(service.readVisibleText())
                .setPositiveButton("Close", null).show();
    }

    private void requestVisibleTextClick() {
        EditText input = new EditText(this);
        input.setHint("Exact visible button or control text");
        new AlertDialog.Builder(this).setTitle("Click a visible control")
                .setMessage("Only an exact text match is clicked. Review the label before continuing.")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Review", (dialog, which) -> {
                    String label = input.getText().toString().trim();
                    AlyaAccessibilityService service = AlyaAccessibilityService.getInstance();
                    if (label.isEmpty() || service == null) { showError("Enter a label and enable Alya screen actions."); return; }
                    new AlertDialog.Builder(this).setMessage("Click the visible control named \"" + label + "\"?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Click", (confirm, selection) -> showError(service.clickVisibleText(label) ? "The matching control was clicked." : "No matching clickable control was found."))
                            .show();
                }).show();
    }

    private void showLaunchableApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> activities = getPackageManager().queryIntentActivities(launcher, 0);
        List<String> labels = new ArrayList<>();
        List<Intent> intents = new ArrayList<>();
        for (android.content.pm.ResolveInfo activity : activities) {
            if (activity.activityInfo.packageName.equals(getPackageName())) continue;
            labels.add(activity.loadLabel(getPackageManager()).toString());
            intents.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(activity.activityInfo.packageName, activity.activityInfo.name));
        }
        if (labels.isEmpty()) { showError("No launchable apps were found."); return; }
        new AlertDialog.Builder(this).setTitle("Open installed app")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    try { startActivity(intents.get(which)); }
                    catch (Exception error) { showError("Android could not open that app."); }
                }).show();
    }

    private void showNavigationActions() {
        new AlertDialog.Builder(this).setTitle("Navigate")
                .setItems(new String[]{"Back", "Home"}, (dialog, which) -> {
                    AlyaAccessibilityService service = AlyaAccessibilityService.getInstance();
                    if (service == null) { showError("Enable Alya screen actions first."); return; }
                    boolean success = which == 0 ? service.goBack() : service.goHome();
                    if (!success) showError("Android did not perform that navigation action.");
                }).show();
    }

    private void showLatestNotification() {
        String notification = AlyaNotificationListenerService.getLatestAllowedNotification();
        if (notification.isEmpty()) { showError("No WhatsApp or phone notification has been received while access was enabled."); return; }
        new AlertDialog.Builder(this).setTitle("Latest notification")
                .setMessage(notification).setPositiveButton("Close", null).show();
    }

    private void showPersonalDataActions() {
        String[] actions = {"Show contacts", "Show last-known location", "Compose and send SMS", "Place a call"};
        new AlertDialog.Builder(this).setTitle("Choose an action")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) explainThenRequest("Contacts", "Alya reads names only after you select this action. Contacts remain on this device.", Manifest.permission.READ_CONTACTS, REQUEST_CONTACTS, this::showContacts);
                    else if (which == 1) explainThenRequestLocation();
                    else if (which == 2) explainThenRequest("Send SMS", "Alya sends only after you review the number and full message and confirm.", Manifest.permission.SEND_SMS, REQUEST_SMS, this::composeSms);
                    else explainThenRequest("Phone call", "Alya places a call only after you review the number and confirm.", Manifest.permission.CALL_PHONE, REQUEST_CALL, this::composeCall);
                }).show();
    }

    private void showContacts() {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME}, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC")) {
            if (cursor != null) while (cursor.moveToNext() && names.size() < 100) names.add(cursor.getString(0));
        } catch (Exception error) {
            showError("Contacts could not be read.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Contacts on this device")
                .setMessage(names.isEmpty() ? "No contacts found." : String.join("\n", names))
                .setPositiveButton("Close", null).show();
    }

    private void showLastKnownLocation() {
        try {
            LocationManager locations = getSystemService(LocationManager.class);
            Location latest = null;
            boolean preciseAccess = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            List<String> providers = preciseAccess ? locations.getProviders(true) : java.util.Collections.singletonList(LocationManager.NETWORK_PROVIDER);
            for (String provider : providers) {
                Location candidate = locations.getLastKnownLocation(provider);
                if (candidate != null && (latest == null || candidate.getTime() > latest.getTime())) latest = candidate;
            }
            if (latest == null) { showError("No last-known location is available. Turn on device location and try again."); return; }
            new AlertDialog.Builder(this).setTitle("Last-known location")
                    .setMessage(String.format(Locale.ROOT, "%.5f, %.5f\nFrom %s", latest.getLatitude(), latest.getLongitude(), latest.getProvider()))
                    .setPositiveButton("Close", null).show();
        } catch (SecurityException error) {
            showError("Location permission is not granted.");
        } catch (Exception error) {
            showError("Android could not provide a last-known location.");
        }
    }

    private void composeSms() {
        EditText phone = new EditText(this);
        phone.setHint("Recipient phone number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText body = new EditText(this);
        body.setHint("Message");
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(dp(16), 0, dp(16), 0);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(phone);
        fields.addView(body);
        new AlertDialog.Builder(this).setTitle("Compose SMS").setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review", (dialog, which) -> {
                    String number = phone.getText().toString().trim();
                    String message = body.getText().toString().trim();
                    if (number.isEmpty() || message.isEmpty()) { showError("Enter a recipient and message."); return; }
                    new AlertDialog.Builder(this).setTitle("Send this SMS?")
                            .setMessage("To: " + number + "\n\n" + message)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Send", (confirm, choice) -> {
                                try {
                                    android.telephony.SmsManager manager = getSystemService(android.telephony.SmsManager.class);
                                    if (manager == null) { showError("SMS service is unavailable."); return; }
                                    manager.sendTextMessage(number, null, message, null, null);
                                    showError("SMS submitted to Android. Delivery is not confirmed.");
                                } catch (Exception error) { showError("Android could not submit the SMS."); }
                            }).show();
                }).show();
    }

    private void composeCall() {
        EditText phone = new EditText(this);
        phone.setHint("Phone number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        new AlertDialog.Builder(this).setTitle("Place a call").setView(phone)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review", (dialog, which) -> {
                    pendingCallNumber = phone.getText().toString().trim();
                    if (pendingCallNumber.isEmpty()) { showError("Enter a phone number."); return; }
                    new AlertDialog.Builder(this).setTitle("Call this number?").setMessage(pendingCallNumber)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Call", (confirm, choice) -> {
                                try { startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", pendingCallNumber, null))); }
                                catch (Exception error) { showError("Android could not start the call."); }
                            }).show();
                }).show();
    }

    private void requestAssistantRole() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            showError("This Android version or device does not offer the assistant role.");
            return;
        }
        explain("Default assistant", "Android will ask whether Alya should become the device's assistant. Power-button or lock-screen launch depends on Android version and device settings; Alya cannot unlock the device.", () -> {
            if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) { showError("Alya is already the selected assistant."); return; }
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 29);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && currentPage.equals("Device")) render();
    }

    private void addCapability(String title, String detail, boolean available) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundColor(color(0xFFFFFFFF, 0xFF282F2B));
        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.addView(text(title, 12, color(0xFF25302D, 0xFFE8EEE9)));
        TextView sub = text(detail, 10, color(0xFF818B85, 0xFFA0AAA4));
        sub.setPadding(0, dp(3), 0, 0);
        textGroup.addView(sub);
        row.addView(textGroup, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = text(available ? "●" : "○", 12, available ? 0xFF64846C : 0xFFADB4AD);
        row.addView(mark);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(7));
        content.addView(row, params);
    }

    private void renderSettings() {
        pageHeading("Settings", "Your key is encrypted on this device");
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("Gemini API key");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText("");
        content.addView(keyInput, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView keyHelp = text("Leave blank to keep the saved key. Enter a new key to replace it.", 10, color(0xFF818B85, 0xFFA0AAA4));
        content.addView(keyHelp);

        EditText modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setHint("Gemini model");
        modelInput.setText(model);
        content.addView(modelInput, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText languageInput = new EditText(this);
        languageInput.setSingleLine(true);
        languageInput.setHint("Conversation language");
        languageInput.setText(language);
        content.addView(languageInput, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView memoryHeading = text("Saved memories · sent with Gemini requests", 11, color(0xFF66776B, 0xFFB3C4B8));
        memoryHeading.setPadding(0, dp(13), 0, dp(5));
        content.addView(memoryHeading);
        for (String memory : new ArrayList<>(savedMemories)) {
            Button item = button("−  " + memory, 0x00000000);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setOnClickListener(view -> new AlertDialog.Builder(this).setMessage("Delete this saved memory?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                        savedMemories.remove(memory);
                        persistMemories();
                        render();
                    }).show());
            content.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
        Button addMemory = button("+  Add a memory", 0xFFE7F0E8);
        addMemory.setOnClickListener(view -> {
            EditText memoryInput = new EditText(this);
            memoryInput.setHint("A detail you'd like Alya to remember");
            new AlertDialog.Builder(this).setTitle("Add a saved memory").setView(memoryInput)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                        String memory = memoryInput.getText().toString().trim();
                        if (!memory.isEmpty() && !savedMemories.contains(memory)) savedMemories.add(memory);
                        persistMemories();
                        render();
                    }).show();
        });
        content.addView(addMemory);

        Button voice = button(speakReplies ? "✓ Read replies aloud" : "Read replies aloud", 0xFFE7F0E8);
        voice.setOnClickListener(view -> { speakReplies = !speakReplies; getPreferences(0).edit().putBoolean("speak", speakReplies).apply(); render(); });
        content.addView(voice);

        Button save = button("Save settings", 0xFF315644);
        save.setTextColor(0xFFFFFFFF);
        save.setOnClickListener(view -> {
            String newKey = keyInput.getText().toString().trim();
            if (!newKey.isEmpty()) {
                try { SecureKeyStore.save(this, newKey); }
                catch (Exception error) { showError("Could not encrypt the API key on this device."); return; }
            }
            model = modelInput.getText().toString().trim();
            if (!model.matches("[A-Za-z0-9._-]+")) model = "gemini-2.5-flash";
            language = languageInput.getText().toString().trim();
            if (language.isEmpty()) language = "Automatic";
            getPreferences(0).edit().putString("model", model).putString("language", language).apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            render();
        });
        content.addView(save);

        Button removeKey = button("Remove saved API key", 0x00000000);
        removeKey.setOnClickListener(view -> new AlertDialog.Builder(this).setMessage("Remove the encrypted Gemini API key?")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> {
                    try { SecureKeyStore.save(this, ""); Toast.makeText(this, "API key removed", Toast.LENGTH_SHORT).show(); }
                    catch (Exception error) { showError("Could not remove the API key."); }
                }).show());
        content.addView(removeKey);

        Button darkMode = button(dark ? "Switch to light theme" : "Switch to dark theme", 0xFFE7F0E8);
        darkMode.setOnClickListener(view -> { dark = !dark; getPreferences(0).edit().putBoolean("dark", dark).apply(); recreate(); });
        content.addView(darkMode);

        String version = "Unknown";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView versionInfo = text("Alya Assistant " + version + "\nUpdate checks are unavailable until a release endpoint is configured.\nThe Android app sends Gemini requests directly using your key.", 10, color(0xFF818B85, 0xFFA0AAA4));
        versionInfo.setPadding(0, dp(10), 0, 0);
        content.addView(versionInfo);
    }

    private void toggleFlashlight() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) { showError("No flashlight is available on this device."); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            explainThenRequest("Camera", "Needed by Android to operate the flashlight. Alya does not capture photos.", Manifest.permission.CAMERA, REQUEST_CAMERA, this::toggleFlashlight);
            return;
        }
        try {
            CameraManager cameraManager = getSystemService(CameraManager.class);
            if (cameraManager == null) { showError("Camera service is unavailable."); return; }
            if (cameraId == null) cameraId = cameraManager.getCameraIdList()[0];
            boolean enabled = getPreferences(0).getBoolean("flashlight_on", false);
            cameraManager.setTorchMode(cameraId, !enabled);
            getPreferences(0).edit().putBoolean("flashlight_on", !enabled).apply();
            Toast.makeText(this, "Flashlight " + (!enabled ? "on" : "off"), Toast.LENGTH_SHORT).show();
        } catch (Exception error) { showError("The flashlight could not be changed. Check camera availability."); }
    }

    private void showTimerDialog() {
        EditText minutes = new EditText(this);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("Minutes from now");
        new AlertDialog.Builder(this).setTitle("Create a timer").setMessage("Android may delay reminders due to battery and notification settings.")
                .setView(minutes).setNegativeButton("Cancel", null).setPositiveButton("Start", (dialog, which) -> {
                    int delay;
                    try { delay = Integer.parseInt(minutes.getText().toString()); }
                    catch (NumberFormatException error) { showError("Enter a valid number of minutes."); return; }
                    if (delay < 1 || delay > 1440) { showError("Choose a timer from 1 to 1440 minutes."); return; }
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        explainThenRequest("Notifications", "Needed to show this timer's completion alert. Android may still delay it.", Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS, () -> showError("Notifications enabled. Create the timer again to schedule it."));
                        return;
                    }
                    Intent intent = new Intent(this, ReminderReceiver.class).putExtra("message", "Your " + delay + " minute timer is complete.").putExtra("notification_id", (int) System.currentTimeMillis());
                    PendingIntent pending = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    AlarmManager alarms = getSystemService(AlarmManager.class);
                    if (alarms == null) { showError("Android alarm service is unavailable."); return; }
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay * 60_000L, pending);
                    Toast.makeText(this, "Approximate timer scheduled", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void requestSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { showError("Speech recognition is unavailable on this device."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            explainThenRequest("Microphone", "Used only after you tap Mic to recognize speech. Alya does not listen in the background.", Manifest.permission.RECORD_AUDIO, REQUEST_MICROPHONE, this::requestSpeech);
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Listening…", Toast.LENGTH_SHORT).show(); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> recognized = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (recognized != null && !recognized.isEmpty() && composer != null) composer.setText(recognized.get(0));
                destroySpeechRecognizer();
            }
            @Override public void onError(int error) { showError("Speech recognition failed. Check microphone access and try again."); destroySpeechRecognizer(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.equals("Automatic") ? Locale.getDefault().toLanguageTag() : language);
        speechRecognizer.startListening(intent);
    }

    private void readAloud(String text) {
        if (textToSpeech == null) { showError("Text-to-speech is unavailable."); return; }
        textToSpeech.stop();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alya-reply");
    }

    private void selectNaturalVoice() {
        if (textToSpeech == null || textToSpeech.getVoices() == null) return;
        textToSpeech.getVoices().stream().filter(voice -> !voice.isNetworkConnectionRequired())
                .filter(voice -> voice.getName().toLowerCase(Locale.ROOT).matches(".*(female|woman|samantha|victoria|zira|susan|aria|jenny).*"))
                .findFirst().ifPresent(voice -> textToSpeech.setVoice(voice));
    }

    private void loadSettings() {
        android.content.SharedPreferences preferences = getPreferences(0);
        model = preferences.getString("model", "gemini-2.5-flash");
        language = preferences.getString("language", "Automatic");
        speakReplies = preferences.getBoolean("speak", false);
        try {
            JSONArray memoryArray = new JSONArray(preferences.getString("memories", "[]"));
            savedMemories.clear();
            for (int index = 0; index < memoryArray.length(); index++) savedMemories.add(memoryArray.getString(index));
        } catch (Exception error) { savedMemories.clear(); }
    }

    private void loadConversations() {
        try {
            conversations = new JSONArray(getPreferences(0).getString("conversations", "[]"));
            activeConversationId = getPreferences(0).getString("active_id", "");
            JSONObject active = findConversation(activeConversationId);
            if (active != null) {
                JSONArray saved = active.optJSONArray("messages");
                if (saved != null) for (int index = 0; index < saved.length(); index++) messages.add(saved.getJSONObject(index));
            }
        } catch (Exception error) { conversations = new JSONArray(); messages.clear(); }
    }

    private void persistConversations() {
        getPreferences(0).edit().putString("conversations", conversations.toString()).putString("active_id", activeConversationId).apply();
    }

    private void persistMessages() {
        JSONObject active = findConversation(activeConversationId);
        if (active == null) return;
        try {
            active.put("messages", new JSONArray(messages));
            active.put("updated", System.currentTimeMillis());
            persistConversations();
        } catch (Exception error) { showError("Could not save this conversation locally."); }
    }

    private void persistMemories() {
        getPreferences(0).edit().putString("memories", new JSONArray(savedMemories).toString()).apply();
    }

    private void saveResponse(String conversationId, List<JSONObject> requestMessages, JSONObject response) {
        JSONObject conversation = findConversation(conversationId);
        if (conversation == null) return;
        try {
            JSONArray savedMessages = new JSONArray(requestMessages);
            savedMessages.put(response);
            conversation.put("messages", savedMessages);
            conversation.put("updated", System.currentTimeMillis());
            if (activeConversationId.equals(conversationId)) {
                messages.clear();
                messages.addAll(requestMessages);
                messages.add(response);
            }
            persistConversations();
        } catch (Exception error) { showError("Could not save the Gemini response locally."); }
    }

    private void cancelRequest() {
        requestGeneration++;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        activeConnection = null;
        busy = false;
        Toast.makeText(this, "Response stopped", Toast.LENGTH_SHORT).show();
        render();
    }

    private void startNewConversationIfNeeded() {
        if (activeConversationId.isEmpty() || findConversation(activeConversationId) == null) startNewConversation();
    }

    private void startNewConversation() {
        activeConversationId = UUID.randomUUID().toString();
        messages.clear();
        try { conversations.put(new JSONObject().put("id", activeConversationId).put("title", "New conversation").put("messages", new JSONArray()).put("updated", System.currentTimeMillis())); }
        catch (Exception ignored) { }
        persistConversations();
    }

    private void updateConversationTitle(String prompt) {
        JSONObject active = findConversation(activeConversationId);
        try {
            if (active != null) active.put("title", prompt.length() > 38 ? prompt.substring(0, 38) + "…" : prompt);
        } catch (Exception error) { showError("Could not update the conversation title."); }
    }

    private JSONObject findConversation(String id) {
        for (int index = 0; index < conversations.length(); index++) {
            JSONObject item = conversations.optJSONObject(index);
            if (item != null && item.optString("id").equals(id)) return item;
        }
        return null;
    }

    private void openConversation(String id) {
        activeConversationId = id;
        messages.clear();
        JSONObject active = findConversation(id);
        JSONArray saved = active == null ? null : active.optJSONArray("messages");
        if (saved != null) for (int index = 0; index < saved.length(); index++) messages.add(saved.optJSONObject(index));
        persistConversations();
        currentPage = "Chat";
        render();
    }

    private void deleteConversation(String id) {
        JSONArray kept = new JSONArray();
        for (int index = 0; index < conversations.length(); index++) {
            JSONObject item = conversations.optJSONObject(index);
            if (item != null && !item.optString("id").equals(id)) kept.put(item);
        }
        conversations = kept;
        if (activeConversationId.equals(id)) startNewConversation();
        persistConversations();
        render();
    }

    private void pageHeading(String title, String subtitle) {
        TextView heading = text(title, 19, color(0xFF25302D, 0xFFE8EEE9));
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);
        TextView subheading = text(subtitle, 11, color(0xFF818B85, 0xFFA0AAA4));
        subheading.setPadding(0, dp(4), 0, dp(15));
        content.addView(subheading);
    }

    private TextView text(String value, int size, int tint) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(tint);
        return view;
    }

    private Button button(String label, int background) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background == 0 ? color(0xFFFFFFFF, 0xFF282F2B) : background));
        return button;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private void hideKeyboard() {
        if (composer == null) return;
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(), 0);
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = false;
        for (int result : results) granted |= result == PackageManager.PERMISSION_GRANTED;
        if (!granted) { showError("Permission was not granted. This capability remains unavailable."); render(); return; }
        if (requestCode == REQUEST_MICROPHONE) requestSpeech();
        else if (requestCode == REQUEST_CAMERA) toggleFlashlight();
        else if (requestCode == REQUEST_NOTIFICATIONS) showError("Permission updated. Create the timer again to schedule its notification.");
        else if (requestCode == REQUEST_LOCATION) showLastKnownLocation();
        else if (requestCode == REQUEST_CONTACTS) showContacts();
        else if (requestCode == REQUEST_SMS) composeSms();
        else if (requestCode == REQUEST_CALL) composeCall();
        render();
    }

    @Override protected void onDestroy() {
        requestGeneration++;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        activeConnection = null;
        destroySpeechRecognizer();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        executor.shutdownNow();
        super.onDestroy();
    }
}