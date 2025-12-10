package com.example.androidexample;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.androidexample.blackjack.BlackjackActivity;
import com.example.androidexample.services.Message;
import com.example.androidexample.services.VolleySingleton;
import com.example.androidexample.services.WebSocketListener;
import com.example.androidexample.services.WebSocketManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;

public class LobbyViewActivity extends AppCompatActivity implements WebSocketListener {

    private String gameType, joinCode, username;
    private boolean isHost;
    private TextView gameTypeTxt, joinCodeTxt, deleteBtn, startBtn, chatUnreadLabel;
    private android.widget.Button leaveBtn;
    private LinearLayout userListLayout, chatMessagesLayout;
    private ScrollView chatScroll;
    private BottomSheetDialog chatDialog;
    private int unreadMessages = 0;

    private final List<Message> chat = new ArrayList<>();
    private final ArrayList<String> currentUsers = new ArrayList<>();

    private static final String TAG = "LobbyViewActivity";

    // Polling for kick detection
    private android.os.Handler kickCheckHandler = new android.os.Handler();
    private Runnable kickCheckRunnable;
    private static final int KICK_CHECK_INTERVAL = 2000; // Check every 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobbyview);

        gameTypeTxt = findViewById(R.id.lobbyview_type_txt);
        joinCodeTxt = findViewById(R.id.lobbyview_code_txt);
        userListLayout = findViewById(R.id.lobbyview_user_list);
        deleteBtn = findViewById(R.id.lobbyview_close_btn);
        leaveBtn = findViewById(R.id.lobbyview_leave_btn);
        startBtn = findViewById(R.id.lobbyview_start_btn);
        chatUnreadLabel = findViewById(R.id.chat_unread_label);

        Intent intent = getIntent();
        gameType = intent.getStringExtra("GAMETYPE");
        username = intent.getStringExtra("USERNAME");
        joinCode = intent.getStringExtra("JOINCODE");
        isHost = intent.getBooleanExtra("HOST", false);

        if (!isHost) {
            startBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
        }

        setColoredLobbyTitle();
        joinCodeTxt.setText(joinCode);
        getLobbyRequest();

        deleteBtn.setOnClickListener(v -> deleteLobbyRequest());
        leaveBtn.setOnClickListener(v -> leaveLobbyRequest());
        findViewById(R.id.chat_button).setOnClickListener(v -> openChatSheet());
        startBtn.setOnClickListener(v -> startGame());

        // Start kick detection polling
        startKickDetection();
    }

    // ------------------ Kick Detection ------------------

    /**
     * Starts periodic checking if the user is still in the lobby
     */
    private void startKickDetection() {
        kickCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkIfStillInLobby();
                kickCheckHandler.postDelayed(this, KICK_CHECK_INTERVAL);
            }
        };
        kickCheckHandler.postDelayed(kickCheckRunnable, KICK_CHECK_INTERVAL);
    }

    /**
     * Stops the kick detection polling
     */
    private void stopKickDetection() {
        if (kickCheckHandler != null && kickCheckRunnable != null) {
            kickCheckHandler.removeCallbacks(kickCheckRunnable);
        }
    }

    /**
     * Checks if the current user is still in the lobby
     * If not, shows a dialog and returns to lobby selection
     */
    private void checkIfStillInLobby() {
        String url = "http://coms-3090-006.class.las.iastate.edu:8080/Lobby/joinCode/" + joinCode;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray usersArray = response.getJSONArray("users");
                        boolean stillInLobby = false;

                        for (int i = 0; i < usersArray.length(); i++) {
                            String user = usersArray.getJSONObject(i).getString("username");
                            if (user.equals(username)) {
                                stillInLobby = true;
                                break;
                            }
                        }

                        if (!stillInLobby) {
                            // User has been kicked!
                            runOnUiThread(() -> handleKicked());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking lobby status", e);
                    }
                },
                error -> {
                    // Lobby might have been deleted
                    Log.w(TAG, "Lobby check failed - lobby may have been deleted");
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        runOnUiThread(() -> handleLobbyDeleted());
                    }
                });

        VolleySingleton.getInstance(this).addToRequestQueue(req);
    }

    /**
     * Handles when the user has been kicked from the lobby
     */
    private void handleKicked() {
        stopKickDetection();
        WebSocketManager.getInstance().disconnectWebSocket();

        new AlertDialog.Builder(this)
                .setTitle("Removed from Lobby")
                .setMessage("You have been removed from this lobby by an administrator.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> returnToLobbySelection())
                .show();
    }

    /**
     * Handles when the lobby has been deleted
     */
    private void handleLobbyDeleted() {
        stopKickDetection();
        WebSocketManager.getInstance().disconnectWebSocket();

        new AlertDialog.Builder(this)
                .setTitle("Lobby Closed")
                .setMessage("This lobby has been closed.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> returnToLobbySelection())
                .show();
    }

    /**
     * Returns user to the lobby selection screen
     */
    private void returnToLobbySelection() {
        Intent intent = new Intent(this, LobbyActivity.class);
        intent.putExtra("USERNAME", username);
        intent.putExtra("GAMETYPE", gameType);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ------------------ Accent Color Helpers ------------------
    private int getAccentColor() {
        switch (gameType.toUpperCase()) {
            case "BLACKJACK": return getColor(R.color.my_red);
            case "EUCHRE": return getColor(R.color.my_blue);
            case "CRAZY8": return getColor(R.color.my_orange);
            case "GOFISH": return getColor(R.color.my_green);
            default: return Color.WHITE;
        }
    }

    private void setColoredLobbyTitle() {
        String formatted = gameType.replace("_", " ").toUpperCase();
        String full = formatted + " LOBBY";
        int accent = getAccentColor();

        SpannableString span = new SpannableString(full);
        span.setSpan(new ForegroundColorSpan(accent), 0, formatted.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        gameTypeTxt.setText(span);
    }

    @Override
    protected void onStart() {
        super.onStart();
        String wsUrl = "ws://coms-3090-006.class.las.iastate.edu:8080/ws/lobby/" + joinCode + "/" + username;
        WebSocketManager.getInstance().connectWebSocket(wsUrl);
        WebSocketManager.getInstance().setWebSocketListener(this);
        startKickDetection(); // Resume kick detection
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (chatDialog != null && chatDialog.isShowing()) chatDialog.dismiss();
        stopKickDetection(); // Pause kick detection when activity is not visible
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopKickDetection(); // Clean up when activity is destroyed
    }

    // ------------------ WebSocket ------------------
    @Override
    public void onWebSocketMessage(String message) {
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(message);
                String type = json.getString("type");
                String user = json.optString("username", "");
                String msgText = json.optString("message", "");

                switch (type) {
                    case "JOIN":
                        if (!currentUsers.contains(user)) {
                            currentUsers.add(user);
                            addUserCard(user);
                        }
                        break;
                    case "LEAVE":
                        currentUsers.remove(user);
                        removeUserCard(user);
                        break;
                    case "MESSAGE":
                        Message newMsg = new Message(user, msgText, System.currentTimeMillis());
                        chat.add(newMsg);
                        if (chatDialog == null || !chatDialog.isShowing()) {
                            unreadMessages++;
                            updateUnreadLabel();
                        } else addMessageBubble(user, msgText);
                        break;
                    case "START":
                        Log.d(TAG, "START received, switching activity...");
                        stopKickDetection(); // Stop checking when game starts
                        WebSocketManager.getInstance().disconnectWebSocket();
                        Intent i;
                        switch (gameType) {
                            case "BLACKJACK":
                                i = new Intent(this, BlackjackActivity.class);
                                break;
                            case "GO_FISH":
                                i = new Intent(this, GofishActivity.class);
                                break;
//
                            case "EUCHRE":
                                i = new Intent(this, EuchreActivity.class);
                                break;
                            case "CRAZY8":
                                i = new Intent(this, com.example.androidexample.crazy8.Crazy8Activity.class);
                                break;
                            default:
                                return;
                        }
                        i.putExtra("GAMETYPE", gameType);
                        i.putExtra("USERNAME", username);
                        i.putExtra("JOINCODE", joinCode);
                        i.putExtra("HOST", isHost);
                        i.putStringArrayListExtra("PLAYERS", currentUsers);
                        startActivity(i);
                        finish();
                        break;
                }
            } catch (JSONException e) {
                Log.e(TAG, "Bad JSON: " + message, e);
            }
        });
    }

    @Override public void onWebSocketOpen(ServerHandshake h) { Log.d(TAG, "WS opened"); }
    @Override public void onWebSocketClose(int c, String r, boolean rem) { Log.d(TAG, "WS closed: " + r); }
    @Override public void onWebSocketError(Exception ex) { Log.e(TAG, "WS error", ex); }

    // ------------------ UI ------------------
    private MaterialCardView createUserCard(String username) {
        int accent = getAccentColor();

        MaterialCardView card = new MaterialCardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ) {{ setMargins(0, 12, 0, 12); }});
        card.setRadius(28f);
        card.setCardElevation(10f);
        card.setStrokeWidth(3);
        card.setStrokeColor(accent);
        card.setCardBackgroundColor(Color.parseColor("#141414"));
        card.setUseCompatPadding(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(32, 32, 32, 32);
        inner.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.icon_profile);
        avatar.setColorFilter(accent);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(64, 64) {{ setMarginEnd(24); }});

        TextView userText = new TextView(this);
        userText.setText(username);
        userText.setTextColor(Color.WHITE);
        userText.setTextSize(20);
        userText.setTypeface(ResourcesCompat.getFont(this, R.font.inter_bold));

        inner.addView(avatar);
        inner.addView(userText);
        card.addView(inner);
        card.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        return card;
    }

    private void addUserCard(String username) { userListLayout.addView(createUserCard(username)); }

    private void removeUserCard(String username) {
        for (int i = 0; i < userListLayout.getChildCount(); i++) {
            View v = userListLayout.getChildAt(i);
            if (v instanceof MaterialCardView) {
                LinearLayout inner = (LinearLayout) ((MaterialCardView) v).getChildAt(0);
                TextView t = (TextView) inner.getChildAt(1); // Get the TextView (second child)
                if (t.getText().toString().equals(username)) {
                    userListLayout.removeViewAt(i);
                    break;
                }
            }
        }
    }

    private void addHeader() {
        TextView header = new TextView(this);
        header.setText("PLAYERS");
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setTypeface(ResourcesCompat.getFont(this, R.font.inter_bold));
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 8, 0, 24);
        userListLayout.addView(header);
    }

    private void updateUnreadLabel() {
        if (chatUnreadLabel == null) return;
        chatUnreadLabel.setVisibility(unreadMessages > 0 ? View.VISIBLE : View.GONE);
        if (unreadMessages > 0) chatUnreadLabel.setText(unreadMessages + " unread");
    }

    // ------------------ HTTP ------------------
    private void getLobbyRequest() {
        String url = "http://coms-3090-006.class.las.iastate.edu:8080/Lobby/joinCode/" + joinCode;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray arr = response.getJSONArray("users");
                        currentUsers.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            currentUsers.add(arr.getJSONObject(i).getString("username"));
                        }
                        userListLayout.removeAllViews();
                        addHeader();
                        for (String u : currentUsers) addUserCard(u);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                    }
                },
                error -> {
                    Toast.makeText(this, "Failed to get lobby info", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Volley error", error);
                });
        VolleySingleton.getInstance(this).addToRequestQueue(req);
    }

    private void deleteLobbyRequest() {
        String url = "http://coms-3090-006.class.las.iastate.edu:8080/Lobby/joinCode/" + joinCode;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.DELETE, url, null,
                res -> {
                    stopKickDetection();
                    Intent i = new Intent(this, LobbyActivity.class);
                    i.putExtra("USERNAME", username);
                    i.putExtra("GAMETYPE", gameType);
                    WebSocketManager.getInstance().disconnectWebSocket();
                    startActivity(i);
                    finish();
                },
                err -> {
                    Toast.makeText(this, "Failed to delete lobby", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Volley error", err);
                });
        VolleySingleton.getInstance(this).addToRequestQueue(req);
    }

    private void leaveLobbyRequest() {
        String url = "http://coms-3090-006.class.las.iastate.edu:8080/Lobby/joinCode/" + joinCode + "/" + username;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.PUT, url, null,
                (Response.Listener<JSONObject>) res -> {
                    try {
                        if ("success".equals(res.getString("message"))) {
                            stopKickDetection();
                            Toast.makeText(this, "Lobby left!", Toast.LENGTH_SHORT).show();
                            Intent i = new Intent(this, LobbyActivity.class);
                            i.putExtra("USERNAME", username);
                            i.putExtra("GAMETYPE", gameType);
                            WebSocketManager.getInstance().disconnectWebSocket();
                            startActivity(i);
                            finish();
                        } else Toast.makeText(this, "Can't leave lobby", Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) { throw new RuntimeException(e); }
                },
                err -> {
                    Toast.makeText(this, "Failed to leave lobby", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Volley error", err);
                });
        VolleySingleton.getInstance(this).addToRequestQueue(req);
    }

    // ------------------ Chat ------------------
    private void openChatSheet() {
        chatDialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.chat_bottom_sheet, null);
        chatDialog.setContentView(sheet);

        chatDialog.setOnShowListener(d -> {
            View bs = chatDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                bs.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bs.requestLayout();
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setSkipCollapsed(true);
                behavior.setFitToContents(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        chatDialog.show();
        unreadMessages = 0;
        updateUnreadLabel();

        chatMessagesLayout = sheet.findViewById(R.id.chat_messages_layout);
        chatScroll = sheet.findViewById(R.id.chat_scroll);
        EditText input = sheet.findViewById(R.id.chat_edit_text);
        ImageButton sendBtn = sheet.findViewById(R.id.chat_send_btn);

        for (Message m : chat) addMessageBubble(m.getSender(), m.getText());

        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                WebSocketManager.getInstance().sendMessage("{\"type\":\"MESSAGE\",\"message\":\"" + text + "\"}");
                input.setText("");
            }
        });
    }

    private void addMessageBubble(String sender, String text) {
        if (chatMessagesLayout == null) return;
        TextView msg = new TextView(this);
        msg.setText(sender + ": " + text);
        msg.setTextSize(16f);
        msg.setTypeface(ResourcesCompat.getFont(this, R.font.inter_regular));
        msg.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 8, 0, 8);

        if (sender.equals(username)) {
            p.gravity = Gravity.END;
            msg.setBackgroundResource(R.drawable.chat_message_bubble_user);
        } else {
            p.gravity = Gravity.START;
            msg.setBackgroundResource(R.drawable.chat_message_bubble);
        }

        msg.setLayoutParams(p);
        msg.setPadding(24, 16, 24, 16);
        msg.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        chatMessagesLayout.addView(msg);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void startGame() {
        WebSocketManager.getInstance().sendMessage("{\"type\": \"START\"}");
    }
}