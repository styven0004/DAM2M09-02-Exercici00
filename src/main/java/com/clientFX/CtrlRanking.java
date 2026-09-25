package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Controlador de la tercera vista: classificació final dels jugadors,
 * ordenada per punts de més a menys, amb un botó per tornar a jugar.
 */
public class CtrlRanking implements Initializable {

    @FXML
    private VBox boxRanking;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    @FXML
    private void playAgain() {
        Main.sendRestart();
    }

    /** Mostra la llista de jugadors del missatge "state" ordenada per punts. */
    public void updateRanking(JSONObject state) {
        JSONArray players = state.getJSONArray("players");

        int n = players.length();
        JSONObject[] sorted = new JSONObject[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = players.getJSONObject(i);
        }
        // Ordenació simple per punts descendents (la llista és petita)
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (sorted[j].getInt("score") < sorted[j + 1].getInt("score")) {
                    JSONObject tmp = sorted[j];
                    sorted[j] = sorted[j + 1];
                    sorted[j + 1] = tmp;
                }
            }
        }

        boxRanking.getChildren().clear();
        for (int i = 0; i < n; i++) {
            String name = sorted[i].getString("name");
            int score = sorted[i].getInt("score");

            HBox row = new HBox(16);
            Label lblPos = new Label((i + 1) + ".");
            Label lblName = new Label(name);
            Label lblScore = new Label(String.valueOf(score));

            Font font = (i == 0)
                    ? Font.font("Arial", FontWeight.BOLD, 22)
                    : Font.font("Arial", FontWeight.NORMAL, 18);
            lblPos.setFont(font);
            lblName.setFont(font);
            lblScore.setFont(font);

            lblPos.setMinWidth(30);
            lblName.setMinWidth(200);

            row.getChildren().addAll(lblPos, lblName, lblScore);
            row.setPadding(new Insets(6, 12, 6, 12));
            boxRanking.getChildren().add(row);
        }
    }
}
