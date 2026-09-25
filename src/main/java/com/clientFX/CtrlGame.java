package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Controlador de la segona vista: el taulell del Sudoku i la llista de
 * jugadors amb la seva puntuació.
 *
 * El taulell (9x9 TextField) es construeix per codi en {@link #initialize}.
 * Cada vegada que arriba un missatge "state" del servidor es redibuixa
 * completament a partir de les dades rebudes ({@link #updateState}).
 */
public class CtrlGame implements Initializable {

    @FXML
    private Label lblPlayer;

    @FXML
    private GridPane gridSudoku;

    @FXML
    private VBox boxPlayers;

    private final TextField[][] cells = new TextField[9][9];
    private final int[][] givens = new int[9][9];

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildGrid();
    }

    /** Crea les 81 caselles del taulell i els seus gestors d'esdeveniments. */
    private void buildGrid() {
        gridSudoku.getChildren().clear();

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                TextField tf = new TextField();
                tf.setPrefSize(42, 42);
                tf.setAlignment(Pos.CENTER);
                tf.setFont(Font.font(18));
                styleAsEmpty(tf);

                final int r = row;
                final int c = col;

                // Només permet un dígit de l'1 al 9
                tf.textProperty().addListener((obs, oldVal, newVal) -> {
                    String filtered = newVal.replaceAll("[^1-9]", "");
                    if (filtered.length() > 1) {
                        filtered = filtered.substring(filtered.length() - 1);
                    }
                    if (!filtered.equals(newVal)) {
                        tf.setText(filtered);
                    }
                });

                // Envia la jugada en prémer Enter o en perdre el focus
                tf.setOnAction(event -> submitCell(r, c));
                tf.focusedProperty().addListener((obs, was, isNow) -> {
                    if (!isNow) submitCell(r, c);
                });

                cells[row][col] = tf;

                Insets margin = new Insets(
                        row % 3 == 0 ? 3 : 1,
                        col % 3 == 2 ? 3 : 1,
                        row % 3 == 2 ? 3 : 1,
                        col % 3 == 0 ? 3 : 1
                );
                GridPane.setMargin(tf, margin);
                gridSudoku.add(tf, col, row);
            }
        }
    }

    /** Envia al servidor el valor escrit a la casella (row, col), si n'hi ha. */
    private void submitCell(int row, int col) {
        TextField tf = cells[row][col];
        if (tf.isDisabled()) return;

        String text = tf.getText();
        if (text == null || text.isBlank()) return;

        int value = Integer.parseInt(text);
        Main.sendMove(row, col, value);
    }

    /** Mostra el nom del jugador actual a la capçalera de la vista. */
    public void setPlayerName(String name) {
        lblPlayer.setText("Juga: " + name);
    }

    /**
     * Redibuixa el taulell i la llista de jugadors a partir d'un missatge
     * "state" complet rebut del servidor.
     */
    public void updateState(JSONObject state) {
        JSONArray givensArr = state.getJSONArray("givens");
        for (int r = 0; r < 9; r++) {
            JSONArray rowArr = givensArr.getJSONArray(r);
            for (int c = 0; c < 9; c++) {
                int value = rowArr.getInt(c);
                givens[r][c] = value;
                if (value != 0) {
                    TextField tf = cells[r][c];
                    tf.setText(String.valueOf(value));
                    tf.setEditable(false);
                    tf.setDisable(true);
                    tf.setTooltip(null);
                    styleAsGiven(tf);
                }
            }
        }

        boolean[][] locked = new boolean[9][9];
        JSONArray filledArr = state.getJSONArray("filled");
        for (int i = 0; i < filledArr.length(); i++) {
            JSONObject cell = filledArr.getJSONObject(i);
            int r = cell.getInt("row");
            int c = cell.getInt("col");
            int value = cell.getInt("value");
            String owner = cell.optString("owner", "");

            TextField tf = cells[r][c];
            tf.setText(String.valueOf(value));
            tf.setEditable(false);
            tf.setDisable(true);
            tf.setTooltip(new Tooltip("Encertat per " + owner));
            styleAsLocked(tf);

            locked[r][c] = true;
        }

        // Les caselles que no són pistes ni han estat encertades queden lliures
        // (rellevant després d'un "restart", quan el taulell canvia sencer)
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (givens[r][c] == 0 && !locked[r][c]) {
                    TextField tf = cells[r][c];
                    if (tf.isDisable()) {
                        tf.setDisable(false);
                        tf.setEditable(true);
                        tf.setTooltip(null);
                        tf.clear();
                        styleAsEmpty(tf);
                    }
                }
            }
        }

        updatePlayers(state.getJSONArray("players"));
    }

    /** Refresca la llista lateral de jugadors i puntuacions. */
    private void updatePlayers(JSONArray players) {
        boxPlayers.getChildren().clear();
        for (int i = 0; i < players.length(); i++) {
            JSONObject p = players.getJSONObject(i);
            String name = p.getString("name");
            int score = p.getInt("score");

            Label lbl = new Label(name + "   " + score);
            lbl.setPadding(new Insets(4, 8, 4, 8));
            boolean isMe = name.equals(Main.playerName);
            lbl.setFont(Font.font("Arial", isMe ? FontWeight.BOLD : FontWeight.NORMAL, 16));

            boxPlayers.getChildren().add(lbl);
        }
    }

    /** Marca breument una casella en vermell quan el servidor indica un error. */
    public void flashWrong(int row, int col) {
        TextField tf = cells[row][col];
        styleAsWrong(tf);

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(600), e -> {
            if (!tf.isDisable()) {
                styleAsEmpty(tf);
                tf.clear();
            }
        }));
        timeline.play();
    }

    private void styleAsGiven(TextField tf) {
        tf.setStyle("-fx-control-inner-background: #e0e0e0; -fx-font-weight: bold;");
    }

    private void styleAsEmpty(TextField tf) {
        tf.setStyle("-fx-control-inner-background: white;");
    }

    private void styleAsLocked(TextField tf) {
        tf.setStyle("-fx-control-inner-background: #b6f2b6; -fx-font-weight: bold;");
    }

    private void styleAsWrong(TextField tf) {
        tf.setStyle("-fx-control-inner-background: #f7b6b6;");
    }
}
