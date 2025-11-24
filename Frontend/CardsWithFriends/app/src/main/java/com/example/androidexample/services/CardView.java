package com.example.androidexample.services;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.androidexample.R;

public class CardView extends View {

    private String rank = "7";
    private String suit = "s";
    private boolean faceUp = true;
    private boolean interactable = true; // NEW: Controls whether card handles touch events

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Typeface interBlack;
    private int redStart, redEnd, greyStart, greyEnd;

    public CardView(Context context) { super(context); init(context); }
    public CardView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(context); }
    public CardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    private void init(Context context) {
        interBlack = ResourcesCompat.getFont(context, R.font.inter_black);
        paint.setTypeface(interBlack);
        paint.setTextAlign(Paint.Align.LEFT);

        redStart = ContextCompat.getColor(context, R.color.my_red);
        redEnd   = ContextCompat.getColor(context, R.color.my_dark_red);
        greyStart = ContextCompat.getColor(context, R.color.my_dark_grey);
        greyEnd   = ContextCompat.getColor(context, R.color.my_light_grey);

        // Enable hardware acceleration for smoother 3D rotation
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setCard(String rank, String suit, boolean faceUp) {
        this.rank = rank.toLowerCase();
        this.suit = suit.toLowerCase();
        this.faceUp = faceUp;
        invalidate();
    }

    // NEW: Method to disable touch interaction (for dialogs)
    public void setInteractable(boolean interactable) {
        this.interactable = interactable;
    }

    public String getCardValue() {
        return rank;
    }

    public String getCardSuit() {
        return suit;
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    private float dX, dY;
    private float startX, startY;
    private float startRotation; // store original rotation

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // NEW: If not interactable, don't handle touch events
        if (!interactable) {
            return false; // Let the parent handle the touch
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Remember original position and rotation
                startX = getX();
                startY = getY();
                startRotation = getRotation();

                // Record offset between touch and card position
                dX = event.getRawX() - getX();
                dY = event.getRawY() - getY();

                // Straighten and "lift" the card
                animate()
                        .rotation(0f)         // straighten to vertical
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(150)
                        .start();
                setElevation(25f);
                return true;

            case MotionEvent.ACTION_MOVE:
                // Move with finger
                float newX = event.getRawX() - dX;
                float newY = event.getRawY() - dY;
                setX(newX);
                setY(newY);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Animate back to original position and rotation
                animate()
                        .x(startX)
                        .y(startY)
                        .rotation(startRotation)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .withEndAction(() -> setElevation(0f))
                        .start();
                return true;
        }

        return super.onTouchEvent(event);
    }

    /** flip animation */
    public void flipCard() {
        ObjectAnimator half1 = ObjectAnimator.ofFloat(this, "rotationY", 0f, 90f);
        half1.setDuration(150);

        ObjectAnimator half2 = ObjectAnimator.ofFloat(this, "rotationY", -90f, 0f);
        half2.setDuration(150);

        half1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                faceUp = !faceUp;
                invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(half1, half2);
        set.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cornerRadius = w * 0.12f;

        rect.set(0, 0, w, h);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);

        if (faceUp) {
            paint.setColor(Color.WHITE);
            canvas.drawPath(path, paint);

            boolean isRed = suit.equals("h") || suit.equals("d");
            int startColor = isRed ? redStart : greyStart;
            int endColor = isRed ? redEnd : greyEnd;

            Shader gradient = new LinearGradient(0, 0, 0, h, startColor, endColor, Shader.TileMode.CLAMP);
            paint.setShader(gradient);

            // Rank
            paint.setTextSize(h * 0.35f);
            canvas.drawText(formatRank(rank), w * 0.1f, h * 0.35f, paint);

            // Suit
            paint.setTextSize(h * 0.55f);
            float suitX = (w - paint.measureText(getSuitSymbol(suit))) / 2f;
            canvas.drawText(getSuitSymbol(suit), suitX, h * 0.90f, paint);
        } else {
            // Back of card: gradient design
            Shader backShader = new LinearGradient(0, 0, w, h,
                    ContextCompat.getColor(getContext(), R.color.my_dark_grey),
                    ContextCompat.getColor(getContext(), R.color.my_light_grey),
                    Shader.TileMode.CLAMP);
            paint.setShader(backShader);
            canvas.drawPath(path, paint);

            // Optional decorative symbol
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setTextSize(h * 0.4f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("★", w / 2f, h / 2f + h * 0.15f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private String formatRank(String rank) {
        try {
            int value = Integer.parseInt(rank);
            switch (value) {
                case 11: return "J";
                case 12: return "Q";
                case 13: return "K";
                case 14: return "A";
                default: return String.valueOf(value);
            }
        } catch (NumberFormatException e) {
            // fallback in case rank isn't numeric
            switch (rank.toLowerCase()) {
                case "a": return "A";
                case "k": return "K";
                case "q": return "Q";
                case "j": return "J";
                default:  return rank.toUpperCase();
            }
        }
    }

    private String getSuitSymbol(String suit) {
        switch (suit) {
            case "h": return "\u2665\uFE0E"; //Ascii codes for suit symbols
            case "d": return "\u2666\uFE0E";
            case "c": return "\u2663\uFE0E";
            case "s": return "\u2660\uFE0E";
            default:  return "?";
        }
    }
}