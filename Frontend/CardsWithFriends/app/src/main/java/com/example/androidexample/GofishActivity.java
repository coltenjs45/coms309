package com.example.androidexample;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.example.androidexample.services.WebSocketListener;
import com.example.androidexample.services.WebSocketManager;

import org.java_websocket.handshake.ServerHandshake;

public class GofishActivity extends AppCompatActivity implements WebSocketListener {

    private static final String TAG = "GofishActivity";

    private static final float CENTER_X_BIAS = 0.5f;
    private static final float CENTER_Y_BIAS = 0.75f;
    private static final float RADIUS_BIAS = 1.4f;
    private static final float TOTAL_SPREAD = 30f;

    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 200;

    private String joinCode;
    private String username;
    private boolean isHost;
    private String gameType;
    private ArrayList<String> playerList;

    private com.example.androidexample.services.CardView selectedCard = null;
    private ConstraintLayout rootLayout;
    private List<com.example.androidexample.services.CardView> cards;
    private GameState gameState = new GameState();
    private boolean gameStarted = false;

    // UI Elements
    private LinearLayout playerListLayout;
    private Button askButton;
    private Button startGameButton;
    private TextView gameStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gofish);

        Intent intent = getIntent();
        gameType = intent.getStringExtra("GAMETYPE");
        username = intent.getStringExtra("USERNAME");
        joinCode = intent.getStringExtra("JOINCODE");
        isHost = intent.getBooleanExtra("HOST", false);
        playerList = intent.getStringArrayListExtra("PLAYERS");

        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.rootLayout);
        playerListLayout = findViewById(R.id.playerList);
        askButton = findViewById(R.id.gofish_ask_button);
        cards = new ArrayList<>();

        // Create start game button dynamically
        startGameButton = new Button(this);
        startGameButton.setId(View.generateViewId());
        startGameButton.setText("Start Game");
        startGameButton.setVisibility(isHost ? View.VISIBLE : View.GONE);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        startGameButton.setLayoutParams(params);
        rootLayout.addView(startGameButton);

        // Position start button in center
        ConstraintSet set = new ConstraintSet();
        set.clone(rootLayout);
        set.connect(startGameButton.getId(), ConstraintSet.START, rootLayout.getId(), ConstraintSet.START, 0);
        set.connect(startGameButton.getId(), ConstraintSet.END, rootLayout.getId(), ConstraintSet.END, 0);
        set.connect(startGameButton.getId(), ConstraintSet.TOP, rootLayout.getId(), ConstraintSet.TOP, 0);
        set.connect(startGameButton.getId(), ConstraintSet.BOTTOM, rootLayout.getId(), ConstraintSet.BOTTOM, 0);
        set.applyTo(rootLayout);

        // Create game status text
        gameStatusText = new TextView(this);
        gameStatusText.setId(View.generateViewId());
        gameStatusText.setTextSize(18);
        gameStatusText.setTextColor(0xFFFFFFFF);
        gameStatusText.setText("Waiting for host to start game...");
        gameStatusText.setVisibility(View.VISIBLE);

        ConstraintLayout.LayoutParams textParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        gameStatusText.setLayoutParams(textParams);
        rootLayout.addView(gameStatusText);

        // Position status text at top center
        set.clone(rootLayout);
        set.connect(gameStatusText.getId(), ConstraintSet.START, rootLayout.getId(), ConstraintSet.START, 0);
        set.connect(gameStatusText.getId(), ConstraintSet.END, rootLayout.getId(), ConstraintSet.END, 0);
        set.connect(gameStatusText.getId(), ConstraintSet.TOP, rootLayout.getId(), ConstraintSet.TOP, 50);
        set.applyTo(rootLayout);

        // Hide ask button initially
        askButton.setVisibility(View.GONE);
        askButton.setText("Ask");
    }

    private void setupListeners() {
        askButton.setOnClickListener(v -> handleAskButtonClick());
        startGameButton.setOnClickListener(v -> startGame());
    }

    @Override
    protected void onStart() {
        super.onStart();
        String wsUrl = "ws://coms-3090-006.class.las.iastate.edu:8080/gofish/" + joinCode;
        Log.d(TAG, "Connecting to WebSocket: " + wsUrl);
        WebSocketManager.getInstance().connectWebSocket(wsUrl);
        WebSocketManager.getInstance().setWebSocketListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        WebSocketManager.getInstance().disconnectWebSocket();
    }

    @Override
    public void onWebSocketOpen(ServerHandshake handshakedata) {
        runOnUiThread(() -> {
            Log.d(TAG, "WebSocket connected");
            Toast.makeText(this, "Connected to game server", Toast.LENGTH_SHORT).show();

            // Only request state if game hasn't started yet
            if (!gameStarted) {
                requestGameState();
            }
        });
    }

    @Override
    public void onWebSocketMessage(String message) {
        runOnUiThread(() -> {
            Log.d(TAG, "Received message: " + message);

            // Check if it's a plain text message (not JSON)
            if (!message.trim().startsWith("{")) {
                Log.d(TAG, "Received plain text message: " + message);

                // Filter out messages that shouldn't be shown as toasts
                boolean shouldShowToast = true;

                // Don't show "no game running" errors
                if (message.toLowerCase().contains("no game running")) {
                    shouldShowToast = false;
                }

                // Don't show "went fishing and drew" messages (these reveal cards)
                if (message.contains("went fishing and drew")) {
                    shouldShowToast = false;
                }

                // Don't show "received a" messages to everyone (only log them)
                if (message.contains("received a") && message.contains(" from ")) {
                    shouldShowToast = false;
                }

                if (shouldShowToast) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }

                // If we get a "game started" type message, request the state
                if (message.toLowerCase().contains("started") || message.toLowerCase().contains("new game")) {
                    Log.d(TAG, "Game started confirmation received, requesting state...");
                    requestGameState();
                }
                return;
            }

            try {
                JSONObject json = new JSONObject(message);
                Log.d(TAG, "Parsing JSON game state...");
                gameState = parseGameState(json);
                gameStarted = true;

                // Hide start button once game starts
                startGameButton.setVisibility(View.GONE);
                gameStatusText.setVisibility(View.GONE);

                Log.d(TAG, "Game state parsed successfully. Players: " + gameState.players.size());
                updateUIFromState();

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing message: " + message, e);
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onWebSocketClose(int code, String reason, boolean remote) {
        Log.e(TAG, "Closed → code=" + code +
                ", reason=" + (reason == null ? "null" : reason) +
                ", remote=" + remote);
    }

    @Override
    public void onWebSocketError(Exception ex) {
        Log.e(TAG, "Error → " + ex.getMessage(), ex);
    }

    // -------------------------------------------------------------------
    // WebSocket Message Sending
    // -------------------------------------------------------------------

    private void startGame() {
        Log.d(TAG, "Start button clicked!");

        try {
            JSONObject message = new JSONObject();
            message.put("type", "start");

            // Use the player list passed from LobbyViewActivity
            JSONArray playerArray = new JSONArray();
            if (playerList != null && !playerList.isEmpty()) {
                for (String player : playerList) {
                    playerArray.put(player);
                }
                Log.d(TAG, "Player list size: " + playerList.size());
                Log.d(TAG, "Players: " + playerList.toString());
            } else {
                // Fallback: just add current user
                playerArray.put(username);
                Log.w(TAG, "Player list was null/empty, using only current user");
            }

            message.put("players", playerArray);

            String msg = message.toString();
            Log.d(TAG, "Sending start message: " + msg);
            WebSocketManager.getInstance().sendMessage(msg);

            Toast.makeText(this, "Starting game...", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating start message", e);
            Toast.makeText(this, "Error starting game", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestGameState() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "state");

            String msg = message.toString();
            Log.d(TAG, "Requesting state: " + msg);
            WebSocketManager.getInstance().sendMessage(msg);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating state request", e);
        }
    }

    private void handleAskButtonClick() {
        // First, show player selection dialog
        showModernPlayerSelectionDialog();
    }

    private void showModernPlayerSelectionDialog() {
        // Get list of other players (not current user)
        List<String> otherPlayers = new ArrayList<>();
        for (PlayerState p : gameState.players) {
            if (!p.username.equals(username)) {
                otherPlayers.add(p.username);
            }
        }

        if (otherPlayers.isEmpty()) {
            Toast.makeText(this, "No other players available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create modern dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_player_selection);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView titleText = dialog.findViewById(R.id.dialog_title);
        LinearLayout playerContainer = dialog.findViewById(R.id.player_container);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);

        titleText.setText("Select a Player");

        // Add player buttons with animation
        for (int i = 0; i < otherPlayers.size(); i++) {
            final String playerName = otherPlayers.get(i);

            CardView playerCard = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 24);
            playerCard.setLayoutParams(cardParams);
            playerCard.setCardElevation(8f);
            playerCard.setRadius(16f);
            playerCard.setCardBackgroundColor(Color.parseColor("#2196F3"));
            playerCard.setClickable(true);
            playerCard.setFocusable(true);

            TextView playerText = new TextView(this);
            playerText.setText(playerName);
            playerText.setTextSize(18);
            playerText.setTextColor(Color.WHITE);
            playerText.setPadding(48, 40, 48, 40);
            playerText.setGravity(Gravity.CENTER);

            playerCard.addView(playerText);

            // Add click listener
            playerCard.setOnClickListener(v -> {
                dialog.dismiss();
                showModernRankSelectionDialog(playerName);
            });

            playerContainer.addView(playerCard);

            // Animate entry
            final int delay = i * 80;
            playerCard.setAlpha(0f);
            playerCard.setTranslationY(30f);
            playerCard.postDelayed(() -> {
                playerCard.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .start();
            }, delay);
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showModernRankSelectionDialog(String targetPlayer) {
        // Get unique card values from current player's hand
        PlayerState currentPlayer = null;
        for (PlayerState p : gameState.players) {
            if (p.username.equals(username)) {
                currentPlayer = p;
                break;
            }
        }

        if (currentPlayer == null || currentPlayer.hand.isEmpty()) {
            Toast.makeText(this, "You have no cards to ask for", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get unique values from hand
        List<Integer> uniqueValues = new ArrayList<>();
        List<String> displayNames = new ArrayList<>();
        List<String> suits = new ArrayList<>();

        for (CardState card : currentPlayer.hand) {
            int value = convertStringToValue(card.value);
            if (!uniqueValues.contains(value)) {
                uniqueValues.add(value);
                displayNames.add(card.value);
                suits.add(card.suit); // Store one suit for display
            }
        }

        // Sort the values
        for (int i = 0; i < uniqueValues.size() - 1; i++) {
            for (int j = i + 1; j < uniqueValues.size(); j++) {
                if (uniqueValues.get(i) > uniqueValues.get(j)) {
                    // Swap values
                    int tempVal = uniqueValues.get(i);
                    uniqueValues.set(i, uniqueValues.get(j));
                    uniqueValues.set(j, tempVal);
                    // Swap display names
                    String tempStr = displayNames.get(i);
                    displayNames.set(i, displayNames.get(j));
                    displayNames.set(j, tempStr);
                    // Swap suits
                    String tempSuit = suits.get(i);
                    suits.set(i, suits.get(j));
                    suits.set(j, tempSuit);
                }
            }
        }

        // Create modern dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_card_selection);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView titleText = dialog.findViewById(R.id.dialog_title);
        TextView subtitleText = dialog.findViewById(R.id.dialog_subtitle);
        LinearLayout cardContainer = dialog.findViewById(R.id.card_container);
        Button confirmButton = dialog.findViewById(R.id.confirm_button);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);

        titleText.setText("Select a Card Rank");
        subtitleText.setText("Ask " + targetPlayer + " for:");

        // Track selected card
        final int[] selectedValue = {-1};
        final View[] selectedCardWrapper = {null};

        // Calculate grid layout
        int totalCards = displayNames.size();
        int cardsPerRow = Math.min(5, totalCards);
        int numRows = (int) Math.ceil((double) totalCards / cardsPerRow);

        // Clear container and set it to vertical orientation for rows
        cardContainer.removeAllViews();
        cardContainer.setOrientation(LinearLayout.VERTICAL);

        // Disable confirm button initially
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.5f);

        // Add cards in grid layout
        int cardIndex = 0;
        for (int row = 0; row < numRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowLayout.setLayoutParams(rowParams);

            // Add cards to this row
            int cardsInThisRow = Math.min(cardsPerRow, totalCards - cardIndex);
            for (int col = 0; col < cardsInThisRow; col++) {
                final String rank = displayNames.get(cardIndex);
                final String suit = suits.get(cardIndex);
                final int value = uniqueValues.get(cardIndex);

                // Create clickable wrapper for the card
                LinearLayout cardWrapper = new LinearLayout(this);
                cardWrapper.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                wrapperParams.setMargins(12, 12, 12, 12);
                cardWrapper.setLayoutParams(wrapperParams);
                cardWrapper.setClickable(true);
                cardWrapper.setFocusable(true);
                cardWrapper.setTag(value); // Store value in tag

                // Create mini card view
                com.example.androidexample.services.CardView miniCard =
                        new com.example.androidexample.services.CardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(120, 160);
                miniCard.setLayoutParams(cardParams);
                miniCard.setCard(rank, suit, true);
                miniCard.setInteractable(false); // Disable card's touch handling for dialogs
                miniCard.setElevation(8f);

                cardWrapper.addView(miniCard);

                // Add click listener to wrapper for selection
                cardWrapper.setOnClickListener(v -> {
                    // Deselect previous card
                    if (selectedCardWrapper[0] != null) {
                        selectedCardWrapper[0].setScaleX(1f);
                        selectedCardWrapper[0].setScaleY(1f);
                        selectedCardWrapper[0].setAlpha(1f);
                    }

                    // Select this card
                    selectedValue[0] = value;
                    selectedCardWrapper[0] = v;

                    // Highlight selected card
                    v.setScaleX(1.15f);
                    v.setScaleY(1.15f);
                    v.setAlpha(1f);

                    // Enable confirm button
                    confirmButton.setEnabled(true);
                    confirmButton.setAlpha(1f);

                    // Add bounce animation
                    v.animate()
                            .scaleX(1.15f)
                            .scaleY(1.15f)
                            .setDuration(200)
                            .start();
                });

                rowLayout.addView(cardWrapper);

                // Animate entry
                final int delay = cardIndex * 80;
                cardWrapper.setAlpha(0f);
                cardWrapper.setScaleX(0.7f);
                cardWrapper.setScaleY(0.7f);
                cardWrapper.postDelayed(() -> {
                    cardWrapper.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .start();
                }, delay);

                cardIndex++;
            }

            cardContainer.addView(rowLayout);
        }

        // Confirm button action
        confirmButton.setOnClickListener(v -> {
            if (selectedValue[0] != -1) {
                dialog.dismiss();
                sendTurn(username, targetPlayer, selectedValue[0]);
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private int convertStringToValue(String valueStr) {
        switch (valueStr) {
            case "A": return 14;
            case "K": return 13;
            case "Q": return 12;
            case "J": return 11;
            default:
                try {
                    return Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
        }
    }

    private void sendTurn(String askingPlayer, String targetPlayer, int value) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "turn");
            message.put("askingPlayer", askingPlayer);
            message.put("targetPlayer", targetPlayer);
            message.put("value", value);

            String msg = message.toString();
            Log.d(TAG, "Sending turn: " + msg);
            WebSocketManager.getInstance().sendMessage(msg);

            // Clear any card selection
            if (selectedCard != null) {
                selectedCard.setSelected(false);
                selectedCard = null;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating turn message", e);
            Toast.makeText(this, "Error sending turn", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------
    // JSON → GameState Parsing
    // -------------------------------------------------------------------

    private GameState parseGameState(JSONObject json) throws JSONException {
        GameState state = new GameState();

        // Check if the response has a "game" wrapper (backend sends this sometimes)
        JSONObject gameData = json;
        if (json.has("game")) {
            gameData = json.getJSONObject("game");
        }

        // Get current turn - try multiple field names
        if (json.has("currentTurn")) {
            state.currentTurn = json.optString("currentTurn");
        } else if (gameData.has("currentPlayerUsername")) {
            state.currentTurn = gameData.optString("currentPlayerUsername");
        }

        Log.d(TAG, "Current turn: " + state.currentTurn);

        // Parse players
        JSONArray playersArray = gameData.optJSONArray("players");
        if (playersArray != null) {
            Log.d(TAG, "Parsing " + playersArray.length() + " players");
            for (int i = 0; i < playersArray.length(); i++) {
                JSONObject p = playersArray.getJSONObject(i);
                PlayerState ps = new PlayerState();
                ps.username = p.optString("username");

                Log.d(TAG, "Parsing player: " + ps.username);

                // Parse hand
                JSONArray hand = p.optJSONArray("hand");
                if (hand != null) {
                    Log.d(TAG, "  Hand size: " + hand.length());
                    for (int j = 0; j < hand.length(); j++) {
                        JSONObject c = hand.getJSONObject(j);
                        CardState card = new CardState();
                        card.value = convertValueToString(c.optInt("value"));
                        card.suit = c.optString("suit");
                        ps.hand.add(card);
                    }
                }
                ps.handSize = ps.hand.size();

                // Parse completed books
                JSONArray books = p.optJSONArray("completedBooks");
                if (books != null) {
                    ps.books = books.length();
                    Log.d(TAG, "  Books: " + ps.books);
                }

                state.players.add(ps);
            }
        }

        Log.d(TAG, "Finished parsing. Total players: " + state.players.size());

        return state;
    }

    private String convertValueToString(int value) {
        switch (value) {
            case 14: return "A";
            case 13: return "K";
            case 12: return "Q";
            case 11: return "J";
            default: return String.valueOf(value);
        }
    }

    // -------------------------------------------------------------------
    // UI Update Methods
    // -------------------------------------------------------------------

    private void updateUIFromState() {
        updatePlayerList();
        updateCardsUI();
        updateAskButton();
    }

    private void updatePlayerList() {
        playerListLayout.removeAllViews();

        for (PlayerState player : gameState.players) {
            TextView playerView = new TextView(this);

            String displayText = player.username + " - Books: " + player.books +
                    " | Cards: " + player.handSize;

            // Add debug info for card count issue
            Log.d(TAG, "Player " + player.username + " has " + player.handSize + " cards, " + player.books + " books");

            // Highlight current turn
            if (player.username.equals(gameState.currentTurn)) {
                displayText = "▶ " + displayText;
                playerView.setTextColor(0xFFFFFF00); // Yellow
            } else {
                playerView.setTextColor(0xFFFFFFFF); // White
            }

            // Highlight current user
            if (player.username.equals(username)) {
                playerView.setTextSize(16);
                playerView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                playerView.setTextSize(14);
            }

            playerView.setText(displayText);
            playerView.setPadding(16, 8, 16, 8);
            playerListLayout.addView(playerView);
        }
    }

    private void updateCardsUI() {
        Log.d(TAG, "updateCardsUI called");

        // Clear existing cards
        for (com.example.androidexample.services.CardView card : cards) {
            rootLayout.removeView(card);
        }
        cards.clear();

        // Find current player's hand
        PlayerState currentPlayer = null;
        for (PlayerState p : gameState.players) {
            if (p.username.equals(username)) {
                currentPlayer = p;
                Log.d(TAG, "Found current player: " + username + " with " + p.hand.size() + " cards");
                break;
            }
        }

        if (currentPlayer == null) {
            Log.w(TAG, "Current player not found in game state!");
            return;
        }

        if (currentPlayer.hand.isEmpty()) {
            Log.w(TAG, "Current player has no cards!");
            return;
        }

        // Create card views from hand data
        for (CardState cardData : currentPlayer.hand) {
            Log.d(TAG, "Creating card: " + cardData.value + cardData.suit);
            com.example.androidexample.services.CardView card = createCardFromData(cardData.value, cardData.suit);
            rootLayout.addView(card);
            cards.add(card);
        }

        Log.d(TAG, "Added " + cards.size() + " cards to layout");

        // Layout cards in arc
        setupCardLayout();
        animateCardsIntroduction();
    }

    private com.example.androidexample.services.CardView createCardFromData(String value, String suit) {
        com.example.androidexample.services.CardView card = new com.example.androidexample.services.CardView(this);
        card.setId(View.generateViewId());
        card.setLayoutParams(new ConstraintLayout.LayoutParams(CARD_WIDTH, CARD_HEIGHT));
        card.setClickable(false); // Cards don't need to be selectable anymore
        card.setFocusable(false);
        card.setCard(value, suit, true);

        return card;
    }

    private void updateAskButton() {
        // Enable ask button only if it's the player's turn
        boolean isMyTurn = username.equals(gameState.currentTurn);
        askButton.setEnabled(isMyTurn);
        askButton.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------
    // Card Layout Methods
    // -------------------------------------------------------------------

    private void setupCardLayout() {
        ConstraintSet set = new ConstraintSet();
        set.clone(rootLayout);
        applyArcConstraints(set, cards);
        set.applyTo(rootLayout);
    }

    private void applyArcConstraints(ConstraintSet set, List<com.example.androidexample.services.CardView> cardList) {
        int n = cardList.size();
        if (n == 0) return;

        float startAngle = -TOTAL_SPREAD / 2f;
        float angleStep = (n == 1) ? 0f : TOTAL_SPREAD / (n - 1);

        for (int i = 0; i < n; i++) {
            com.example.androidexample.services.CardView card = cardList.get(i);
            int id = card.getId();

            set.connect(id, ConstraintSet.LEFT, rootLayout.getId(), ConstraintSet.LEFT, 0);
            set.connect(id, ConstraintSet.RIGHT, rootLayout.getId(), ConstraintSet.RIGHT, 0);
            set.connect(id, ConstraintSet.TOP, rootLayout.getId(), ConstraintSet.TOP, 0);
            set.connect(id, ConstraintSet.BOTTOM, rootLayout.getId(), ConstraintSet.BOTTOM, 0);

            float angle = startAngle + i * angleStep;
            double rad = Math.toRadians(angle);
            float xBias = (float) (CENTER_X_BIAS + RADIUS_BIAS * Math.sin(rad));
            float yBias = (float) (CENTER_Y_BIAS + RADIUS_BIAS * (1 - Math.cos(rad)));

            xBias = Math.max(0f, Math.min(1f, xBias));
            yBias = Math.max(0f, Math.min(1f, yBias));

            set.setHorizontalBias(id, xBias);
            set.setVerticalBias(id, yBias);
        }
    }

    private void animateCardsIntroduction() {
        int n = cards.size();
        float startAngle = -TOTAL_SPREAD / 2f;
        float angleStep = (n == 1) ? 0f : TOTAL_SPREAD / (n - 1);

        for (int i = 0; i < n; i++) {
            final com.example.androidexample.services.CardView card = cards.get(i);
            final float targetAngle = startAngle + i * angleStep;
            final int index = i;

            card.post(() -> {
                card.setPivotX(card.getWidth() / 2f);
                card.setPivotY(card.getHeight());
                card.setRotation(0f);

                card.animate()
                        .rotation(targetAngle)
                        .setStartDelay(index * 80L)
                        .setDuration(350)
                        .start();
            });
        }
    }

    // -------------------------------------------------------------------
    // Data Models
    // -------------------------------------------------------------------

    private static class GameState {
        String currentTurn;
        List<PlayerState> players = new ArrayList<>();
    }

    private static class PlayerState {
        String username;
        int handSize;
        int books;
        List<CardState> hand = new ArrayList<>();
    }

    private static class CardState {
        String value;
        String suit;
    }
}