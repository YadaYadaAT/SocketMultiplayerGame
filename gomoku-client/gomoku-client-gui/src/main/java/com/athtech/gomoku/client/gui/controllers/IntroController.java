package com.athtech.gomoku.client.gui.controllers;
//STUDENTS-CODE-NUMBER : CSY-22117
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import javafx.animation.Interpolator;

// Endpoint for game intro scene
// All FXML components are annotated with @FXML
// Not responsible for sending any packets
public class IntroController {

    @FXML
    private VBox introRoot;

    @FXML
    private TextFlow titleFlow;

    private Runnable onFinished;

    // ---------- CONFIGURATION ----------
    private static final double PRE_DELAY = 1.5;   // delay before fade-in starts
    private static final double HOLD_SECONDS = 3.5; // how long the intro stays fully visible
    private static final double FADE_MILLIS = 700;  // fade out duration
    private static final double FADE_IN_MILLIS = 500; // fade in duration after pre-delay
    private static final double LETTER_DELAY = 0.17; // seconds between letters
    private static final double SLAM_SCALE = 1.3;   // zoom scale for dramatic effect
    private static final double SLAM_DURATION = 500; // milliseconds

    @FXML
    private void initialize() {
        // Start fully transparent
        introRoot.setOpacity(0);

        // Pre-delay pause to let the window fully render
        PauseTransition preDelay = new PauseTransition(Duration.seconds(PRE_DELAY));

        // Fade in the whole intro root after pre-delay
        FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_MILLIS), introRoot);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        // Hold before dramatic end
        PauseTransition hold = new PauseTransition(Duration.seconds(HOLD_SECONDS));

        // Fade out at the very end
        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_MILLIS), introRoot);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        // Chain transitions: preDelay -> fadeIn -> typewriter -> hold -> slam/scale -> fadeOut
        preDelay.setOnFinished(e -> fadeIn.play());
        fadeIn.setOnFinished(e -> playTypewriterAnimation(hold, fadeOut));
        hold.setOnFinished(e -> playDramaticSlam(fadeOut));
        fadeOut.setOnFinished(e -> {
            if (onFinished != null) {
                Platform.runLater(onFinished);
            }
        });

        // Start the sequence
        preDelay.play();
    }

    /**
     * Typewriter animation for the title using opacity changes.
     * Plays letters one by one, then triggers the hold transition when done.
     */
    private void playTypewriterAnimation(PauseTransition hold, FadeTransition fadeOut) {
        String title = "       Gomoku By YadaYada       "; // keep your leading/trailing spaces
        titleFlow.getChildren().clear();

        // create Text nodes first, all invisible
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            Text t = new Text(String.valueOf(c));

            // color effect for Gomoku "o"s (the 2nd and 4th letters)
            if (i == 8) t.setFill(Color.GREEN);
            else if (i == 10) t.setFill(Color.RED);
            else t.setFill(Color.WHITE);

            t.setStyle("-fx-font-size: 64px; -fx-font-family: 'Orbitron', 'Arial Black';");
            t.setEffect(new DropShadow(10, Color.WHITE));
            t.setOpacity(0); // start invisible
            titleFlow.getChildren().add(t);
        }

        // Timeline to reveal letters one by one
        Timeline timeline = new Timeline();
        for (int i = 0; i < titleFlow.getChildren().size(); i++) {
            final int idx = i;
            KeyFrame kf = new KeyFrame(Duration.seconds(i * LETTER_DELAY),
                    e -> titleFlow.getChildren().get(idx).setOpacity(1));
            timeline.getKeyFrames().add(kf);
        }

        timeline.setOnFinished(e -> hold.play());
        timeline.play();
    }

    /**
     * Dramatic slam/scale effect before fade-out.
     */
    private void playDramaticSlam(FadeTransition fadeOut) {
        // Scale effect
        ScaleTransition slam = new ScaleTransition(Duration.millis(SLAM_DURATION), introRoot);
        slam.setFromX(1.0);
        slam.setFromY(1.0);
        slam.setToX(SLAM_SCALE);
        slam.setToY(SLAM_SCALE);
        slam.setInterpolator(Interpolator.EASE_OUT);

        // Optional: fade slightly while scaling
        FadeTransition fadeDuringSlam = new FadeTransition(Duration.millis(SLAM_DURATION), introRoot);
        fadeDuringSlam.setFromValue(1.0);
        fadeDuringSlam.setToValue(0.9);

        ParallelTransition dramaticEnd = new ParallelTransition(slam, fadeDuringSlam);
        dramaticEnd.setOnFinished(e -> fadeOut.play());
        dramaticEnd.play();
    }

    /** Called once from app bootstrap to run after intro */
    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }
}
