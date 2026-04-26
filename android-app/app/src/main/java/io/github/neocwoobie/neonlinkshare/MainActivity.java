package io.github.neocwoobie.neonlinkshare;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

public class MainActivity extends Activity {
    private static final String PREFS = "neonlink";
    private static final String PREF_SERVER_URL = "server_url";
    private static final String PREF_COOKIE = "cookie";
    private static final String DEFAULT_GROUP_COLOR = "#06b6d4";
    private static final int ACCENT = Color.rgb(8, 145, 178);
    private static final long CLOSE_AFTER_SUCCESS_MS = 900;
    private static final String[] GROUP_COLOR_VALUES = {
            "#06b6d4",
            "#0ea5e9",
            "#3b82f6",
            "#8b5cf6",
            "#d946ef",
            "#f43f5e",
            "#f97316",
            "#eab308",
            "#22c55e",
            "#14b8a6",
            "#64748b",
            "#111827"
    };
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Category> categories = new ArrayList<>();
    private final List<TextView> groupColorSwatches = new ArrayList<>();

    private Handler mainHandler;
    private SharedPreferences prefs;
    private String cookie = "";
    private String sharedText = "";
    private boolean authenticationEnabled = true;
    private boolean authenticated = false;

    private EditText serverUrlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText urlInput;
    private EditText titleInput;
    private EditText descriptionInput;
    private EditText newGroupInput;
    private EditText tagsInput;
    private Spinner groupSpinner;
    private Button connectButton;
    private Button loginButton;
    private Button saveButton;
    private TextView statusText;
    private TextView selectedGroupColorText;
    private ArrayAdapter<String> categoryAdapter;
    private String selectedGroupColor = DEFAULT_GROUP_COLOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        cookie = prefs.getString(PREF_COOKIE, "");

        buildUi();
        serverUrlInput.setText(prefs.getString(PREF_SERVER_URL, ""));
        applyShareIntent(getIntent());
        refreshServerState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyShareIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("NeonLink");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android share helper");
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setPadding(0, dp(2), 0, dp(16));
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setText("Enter your NeonLink URL, then tap Connect.");
        statusText.setTextColor(Color.rgb(71, 85, 105));
        statusText.setPadding(0, 0, 0, dp(12));
        root.addView(statusText);

        serverUrlInput = makeInput("NeonLink URL, e.g. https://tomato.tailnet.ts.net", false);
        addLabeled(root, "Server", serverUrlInput);

        connectButton = makeButton("Connect");
        root.addView(connectButton);

        usernameInput = makeInput("Username", false);
        addLabeled(root, "Username", usernameInput);

        passwordInput = makeInput("Password", false);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addLabeled(root, "Password", passwordInput);

        loginButton = makeButton("Login");
        root.addView(loginButton);

        urlInput = makeInput("https://example.com", false);
        addLabeled(root, "Shared URL", urlInput);

        titleInput = makeInput("Title", false);
        addLabeled(root, "Title", titleInput);

        descriptionInput = makeInput("Description", true);
        addLabeled(root, "Description", descriptionInput);

        groupSpinner = new Spinner(this);
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(categoryAdapter);
        addLabeled(root, "Group", groupSpinner);
        setCategoryNames(new ArrayList<Category>());

        newGroupInput = makeInput("Optional new group name", false);
        addLabeled(root, "New group", newGroupInput);

        addLabeled(root, "New group color", createGroupColorPicker());
        updateSelectedGroupColor(DEFAULT_GROUP_COLOR);

        tagsInput = makeInput("comma, separated, tags", false);
        addLabeled(root, "Tags", tagsInput);

        saveButton = makeButton("Save to NeonLink");
        root.addView(saveButton);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshServerState();
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                login();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveShare();
            }
        });

        setContentView(scrollView);
    }

    private EditText makeInput(String hint, boolean multiline) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(!multiline);
        editText.setMinHeight(multiline ? dp(88) : dp(48));
        editText.setTextSize(16);
        editText.setPadding(dp(10), 0, dp(10), 0);
        if (multiline) {
            editText.setGravity(android.view.Gravity.TOP);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return editText;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout createGroupColorPicker() {
        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = null;
        for (int i = 0; i < GROUP_COLOR_VALUES.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                picker.addView(row);
            }

            final String color = GROUP_COLOR_VALUES[i];
            TextView swatch = new TextView(this);
            swatch.setTag(color);
            swatch.setGravity(android.view.Gravity.CENTER);
            swatch.setTextSize(18);
            swatch.setContentDescription("Group color " + color);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(44));
            params.setMargins(0, dp(4), dp(8), dp(4));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    updateSelectedGroupColor(color);
                }
            });

            groupColorSwatches.add(swatch);
            row.addView(swatch);
        }

        selectedGroupColorText = new TextView(this);
        selectedGroupColorText.setTextColor(Color.rgb(71, 85, 105));
        selectedGroupColorText.setPadding(0, dp(4), 0, 0);
        picker.addView(selectedGroupColorText);

        return picker;
    }

    private void updateSelectedGroupColor(String color) {
        selectedGroupColor = normalizeColor(color);
        for (TextView swatch : groupColorSwatches) {
            String swatchColor = String.valueOf(swatch.getTag());
            boolean selected = selectedGroupColor.equalsIgnoreCase(swatchColor);
            swatch.setBackground(makeColorSwatchBackground(swatchColor, selected));
            swatch.setText(selected ? "✓" : "");
            swatch.setTextColor(getReadableTextColor(swatchColor));
        }
        if (selectedGroupColorText != null) {
            selectedGroupColorText.setText("Selected " + selectedGroupColor.toUpperCase(Locale.US));
        }
    }

    private GradientDrawable makeColorSwatchBackground(String color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(8));
        drawable.setColor(Color.parseColor(normalizeColor(color)));
        drawable.setStroke(
                selected ? dp(3) : dp(1),
                selected ? Color.rgb(17, 24, 39) : Color.rgb(203, 213, 225)
        );
        return drawable;
    }

    private int getReadableTextColor(String color) {
        int parsedColor = Color.parseColor(normalizeColor(color));
        int red = Color.red(parsedColor);
        int green = Color.green(parsedColor);
        int blue = Color.blue(parsedColor);
        double luminance = 0.299 * red + 0.587 * green + 0.114 * blue;
        return luminance < 150 ? Color.WHITE : Color.rgb(17, 24, 39);
    }

    private void addLabeled(LinearLayout root, String label, View child) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(Color.rgb(51, 65, 85));
        textView.setPadding(0, dp(10), 0, dp(4));
        root.addView(textView);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        child.setLayoutParams(params);
        root.addView(child);
    }

    private void applyShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;

        CharSequence textValue = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence titleValue = intent.getCharSequenceExtra(Intent.EXTRA_TITLE);
        CharSequence subjectValue = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);

        sharedText = textValue == null ? "" : textValue.toString();
        String sharedUrl = extractUrl(sharedText);
        String sharedTitle = firstNonEmpty(
                titleValue == null ? "" : titleValue.toString(),
                subjectValue == null ? "" : subjectValue.toString()
        );

        if (!sharedUrl.isEmpty()) {
            urlInput.setText(sharedUrl);
            descriptionInput.setText(sharedText.replace(sharedUrl, "").trim());
        } else if (looksLikeUrl(sharedText)) {
            urlInput.setText(sharedText.trim());
            descriptionInput.setText("");
        }

        if (!sharedTitle.isEmpty()) {
            titleInput.setText(sharedTitle);
        }

        showStatus("Shared content loaded.", false);
    }

    private void refreshServerState() {
        final String baseUrl;
        try {
            baseUrl = normalizeServerUrl();
        } catch (Exception error) {
            showStatus(error.getMessage(), true);
            return;
        }

        prefs.edit().putString(PREF_SERVER_URL, baseUrl).apply();
        runNetwork("Connecting...", new NetworkTask() {
            @Override
            public void run() throws Exception {
                JSONObject settings = getJson("/api/settings/application");
                authenticationEnabled = settings.optBoolean("authenticationEnabled", true);

                JSONObject me = getJson("/api/users/me");
                authenticated = me.optBoolean("authenticated", false);

                JSONArray categoryArray = null;
                if (!authenticationEnabled || authenticated) {
                    categoryArray = getArray("/api/categories");
                }

                final JSONArray categoriesResponse = categoryArray;
                final String userName = me.optString("username", "");
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (categoriesResponse != null) {
                            setCategoryNames(parseCategories(categoriesResponse));
                        }

                        if (!authenticationEnabled) {
                            showStatus("Connected. Authentication is disabled on this NeonLink server.", false);
                        } else if (authenticated) {
                            showStatus("Connected as " + userName + ".", false);
                        } else {
                            showStatus("Connected. Please log in before saving.", false);
                        }
                    }
                });
            }
        });
    }

    private void login() {
        try {
            normalizeServerUrl();
        } catch (Exception error) {
            showStatus(error.getMessage(), true);
            return;
        }

        final String username = usernameInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Enter username and password.", true);
            return;
        }

        runNetwork("Logging in...", new NetworkTask() {
            @Override
            public void run() throws Exception {
                clearCookie();
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                postJson("/api/users/login", body);
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        passwordInput.setText("");
                        refreshServerState();
                    }
                });
            }
        });
    }

    private void saveShare() {
        if (authenticationEnabled && !authenticated) {
            showStatus("Please log in before saving.", true);
            return;
        }

        final JSONObject body;
        try {
            body = buildSharePayload();
        } catch (Exception error) {
            showStatus(error.getMessage(), true);
            return;
        }

        runNetwork("Saving...", new NetworkTask() {
            @Override
            public void run() throws Exception {
                final JSONObject response = postJson("/api/share", body);
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (response.optBoolean("duplicate", false)) {
                            completeShare("This link is already in NeonLink.");
                        } else {
                            completeShare("Saved to NeonLink.");
                        }
                    }
                });
            }
        });
    }

    private JSONObject buildSharePayload() throws Exception {
        String sharedUrl = urlInput.getText().toString().trim();
        if (!looksLikeUrl(sharedUrl)) {
            throw new IllegalArgumentException("A valid http or https URL is required.");
        }

        JSONObject body = new JSONObject();
        body.put("url", sharedUrl);
        body.put("title", titleInput.getText().toString().trim());
        body.put("text", sharedText);
        body.put("desc", descriptionInput.getText().toString().trim());

        String newGroup = newGroupInput.getText().toString().trim();
        if (!newGroup.isEmpty()) {
            body.put("newCategoryName", newGroup);
            body.put("newCategoryColor", selectedGroupColor);
        } else {
            int selectedIndex = groupSpinner.getSelectedItemPosition();
            if (selectedIndex > 0 && selectedIndex - 1 < categories.size()) {
                body.put("categoryId", categories.get(selectedIndex - 1).id);
            }
        }

        JSONArray tags = new JSONArray();
        String[] tagParts = tagsInput.getText().toString().split(",");
        for (String tagPart : tagParts) {
            String tag = tagPart.trim();
            if (!tag.isEmpty() && tags.length() < 10) {
                tags.put(tag);
            }
        }
        body.put("tags", tags);
        return body;
    }

    private JSONObject getJson(String path) throws Exception {
        return new JSONObject(request("GET", path, null));
    }

    private JSONArray getArray(String path) throws Exception {
        return new JSONArray(request("GET", path, null));
    }

    private JSONObject postJson(String path, JSONObject body) throws Exception {
        String response = request("POST", path, body);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(normalizeServerUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (!cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }

        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(bytes);
            } finally {
                outputStream.close();
            }
        }

        int status = connection.getResponseCode();
        captureCookie(connection);
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readStream(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IllegalStateException(readErrorMessage(status, response));
        }
        return response;
    }

    private void captureCookie(HttpURLConnection connection) {
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            String key = entry.getKey();
            if (key == null || !"set-cookie".equals(key.toLowerCase(Locale.US))) continue;

            for (String value : entry.getValue()) {
                if (value == null || !value.startsWith("SSID=")) continue;
                String cookieValue = value.split(";", 2)[0];
                if ("SSID=".equals(cookieValue)) {
                    clearCookie();
                } else {
                    cookie = cookieValue;
                    prefs.edit().putString(PREF_COOKIE, cookie).apply();
                }
            }
        }
    }

    private void clearCookie() {
        cookie = "";
        prefs.edit().remove(PREF_COOKIE).apply();
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private String readErrorMessage(int status, String response) {
        try {
            JSONObject json = new JSONObject(response);
            String message = json.optString("message", "");
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {
            // Keep the HTTP fallback below when the response is not JSON.
        }
        return "Request failed with HTTP " + status + ".";
    }

    private String normalizeServerUrl() throws Exception {
        String value = serverUrlInput.getText().toString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter your NeonLink server URL.");
        }
        if (!value.toLowerCase(Locale.US).startsWith("http://")
                && !value.toLowerCase(Locale.US).startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        new URL(value);
        return value;
    }

    private String normalizeColor(String color) {
        String value = color == null ? "" : color.trim();
        if (value.matches("^#[0-9a-fA-F]{6}$")) return value;
        return "#06b6d4";
    }

    private String extractUrl(String value) {
        Matcher matcher = URL_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group() : "";
    }

    private boolean looksLikeUrl(String value) {
        return value != null && URL_PATTERN.matcher(value.trim()).matches();
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return "";
    }

    private List<Category> parseCategories(JSONArray array) {
        List<Category> parsed = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            parsed.add(new Category(item.optInt("id"), item.optString("name", "Group " + item.optInt("id"))));
        }
        return parsed;
    }

    private void setCategoryNames(List<Category> nextCategories) {
        categories.clear();
        categories.addAll(nextCategories);

        List<String> names = new ArrayList<>();
        names.add("No group");
        for (Category category : categories) {
            names.add(category.name);
        }
        categoryAdapter.clear();
        categoryAdapter.addAll(names);
        categoryAdapter.notifyDataSetChanged();
    }

    private void runNetwork(final String message, final NetworkTask task) {
        setBusy(true);
        showStatus(message, false);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (final Exception error) {
                    postUi(new Runnable() {
                        @Override
                        public void run() {
                            showStatus(friendlyError(error), true);
                        }
                    });
                } finally {
                    postUi(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                        }
                    });
                }
            }
        });
    }

    private void postUi(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private void setBusy(boolean busy) {
        connectButton.setEnabled(!busy);
        loginButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
    }

    private void showStatus(String message, boolean error) {
        statusText.setText(message == null ? "" : message);
        statusText.setTextColor(error ? Color.rgb(185, 28, 28) : Color.rgb(71, 85, 105));
        if (error && message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void completeShare(String message) {
        showStatus(message, false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, CLOSE_AFTER_SUCCESS_MS);
    }

    private String friendlyError(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return "Cannot resolve this host. If you use a Tailscale MagicDNS name, make sure the Android Tailscale app is connected and DNS is enabled. You can also try the Tailscale IP directly, for example http://100.x.x.x:3333.";
            }
            if (current instanceof SSLHandshakeException) {
                return "HTTPS certificate check failed. If this is a private NAS URL, try the Tailscale HTTPS name or use http://100.x.x.x:3333.";
            }
            current = current.getCause();
        }

        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Request failed. Check the server URL and network connection.";
        }
        return message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface NetworkTask {
        void run() throws Exception;
    }

    private static class Category {
        final int id;
        final String name;

        Category(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
