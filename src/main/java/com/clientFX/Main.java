package com.clientFX;

import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Aplicació JavaFX del client del Sudoku multijugador.
 *
 * Gestiona les tres vistes (configuració, joc i classificació) i tota la
 * comunicació amb el servidor per WebSockets: connexió, unió a la partida
 * amb un nom, enviament de jugades i recepció de l'estat de la partida.
 */
public class Main extends Application {

    public static UtilsWS wsClient;

    /** Nom amb què el jugador ha estat registrat pel servidor. */
    public static String playerName = "";

    public static CtrlConfig ctrlConfig;
    public static CtrlGame ctrlGame;
    public static CtrlRanking ctrlRanking;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 760;
        final int windowHeight = 560;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        UtilsViews.addView(getClass(), "ViewRanking", "/assets/viewRanking.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");
        ctrlRanking = (CtrlRanking) UtilsViews.getController("ViewRanking");

        Scene scene = new Scene(UtilsViews.parentContainer);

        stage.setScene(scene);
        stage.setTitle("Sudoku multijugador");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        if (!System.getProperty("os.name").contains("Mac")) {
            try {
                Image icon = new Image(getClass().getResourceAsStream("/icons/icon.png"));
                stage.getIcons().add(icon);
            } catch (Exception ignored) {
                // Sense icona no és crític per al funcionament del joc
            }
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(0); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    /** Es crida des de la vista de configuració en prémer "Connect". */
    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connectant...");

        pauseDuring(500, () -> {

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            String requestedName = ctrlConfig.txtName.getText();
            playerName = (requestedName == null || requestedName.isBlank())
                    ? "Jugador" : requestedName.trim();

            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

            // Platform.runLater assegura que el codi s'executi
            // al fil de la UI, per evitar problemes de concurrència amb JavaFX
            wsClient.onOpen((response) -> { Platform.runLater(Main::sendJoin); });
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }

    /** Envia el missatge "join" amb el nom del jugador un cop oberta la connexió. */
    private static void sendJoin() {
        JSONObject obj = new JSONObject();
        obj.put("type", "join");
        obj.put("name", playerName);
        wsClient.safeSend(obj.toString());
    }

    /** Envia al servidor la jugada d'una casella. */
    public static void sendMove(int row, int col, int value) {
        JSONObject obj = new JSONObject();
        obj.put("type", "move");
        obj.put("row", row);
        obj.put("col", col);
        obj.put("value", value);
        wsClient.safeSend(obj.toString());
    }

    /** Demana al servidor començar una partida nova. */
    public static void sendRestart() {
        JSONObject obj = new JSONObject();
        obj.put("type", "restart");
        wsClient.safeSend(obj.toString());
    }

    private static void wsMessage(String response) {
        JSONObject msgObj = new JSONObject(response);
        String type = msgObj.optString("type", "");

        switch (type) {
            case "joined" -> {
                playerName = msgObj.getString("name");
                ctrlGame.setPlayerName(playerName);
                UtilsViews.setViewAnimating("ViewGame");
            }
            case "state" -> {
                ctrlGame.updateState(msgObj);
                if (msgObj.optBoolean("finished", false)) {
                    ctrlRanking.updateRanking(msgObj);
                    UtilsViews.setViewAnimating("ViewRanking");
                } else if ("ViewRanking".equals(UtilsViews.getActiveView())) {
                    UtilsViews.setViewAnimating("ViewGame");
                }
            }
            case "wrong" -> ctrlGame.flashWrong(msgObj.getInt("row"), msgObj.getInt("col"));
            case "error" -> System.out.println("Error del servidor: " + msgObj.optString("message", ""));
            default -> { /* Tipus no gestionat */ }
        }
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.contains(connectionRefused)) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
