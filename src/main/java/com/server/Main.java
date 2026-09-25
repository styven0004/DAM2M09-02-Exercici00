package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

/**
 * Servidor WebSocket del joc de Sudoku multijugador.
 *
 * Manté una única partida compartida per a tots els clients connectats.
 * Quan un jugador encerta una casella la bloqueja i suma 2 punts; si
 * s'equivoca resta 1 punt. Quan el taulell es completa, envia l'estat
 * final perquè els clients mostrin la classificació.
 *
 * Missatges suportats (camp "type"):
 *  - join:    el client s'uneix a la partida amb un nom
 *  - move:    el client proposa un valor per a una casella
 *  - restart: el client demana començar una partida nova
 *
 * Missatges enviats pel servidor:
 *  - joined:  confirmació del nom assignat al client
 *  - state:   estat complet de la partida (taulell, jugadors, si ha acabat)
 *  - wrong:   avís individual que l'última jugada era incorrecta
 *  - error:   missatge d'error
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_NAME = "name";
    private static final String K_ROW = "row";
    private static final String K_COL = "col";
    private static final String K_VALUE = "value";
    private static final String K_GIVENS = "givens";
    private static final String K_FILLED = "filled";
    private static final String K_PLAYERS = "players";
    private static final String K_FINISHED = "finished";
    private static final String K_MESSAGE = "message";

    // Tipus de missatge
    private static final String T_JOIN = "join";
    private static final String T_JOINED = "joined";
    private static final String T_MOVE = "move";
    private static final String T_STATE = "state";
    private static final String T_WRONG = "wrong";
    private static final String T_RESTART = "restart";
    private static final String T_ERROR = "error";

    /** Registre de jugadors connectats i les seves puntuacions. */
    private final ClientRegistry clients = new ClientRegistry();

    /** Partida de Sudoku actual, compartida per tots els jugadors. */
    private SudokuGame game = new SudokuGame();

    public Main(InetSocketAddress address) {
        super(address);
    }

    // ----------------- Helpers JSON -----------------

    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            clients.remove(to);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String payload) {
        for (WebSocket conn : clients.sockets()) {
            sendSafe(conn, payload);
        }
    }

    /** Construeix el missatge d'estat complet de la partida. */
    private JSONObject buildStateMessage() {
        JSONObject o = msg(T_STATE);
        o.put(K_GIVENS, game.givensAsJson());
        o.put(K_FILLED, game.filledAsJson());
        o.put(K_PLAYERS, clients.playersAsJson());
        o.put(K_FINISHED, game.isFinished());
        return o;
    }

    private void broadcastState() {
        broadcast(buildStateMessage().toString());
    }

    // ----------------- WebSocketServer overrides -----------------

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Client connectat (pendent de 'join')");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        if (name != null) {
            System.out.println("Client desconnectat: " + name);
            broadcastState();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "JSON invàlid").toString());
            return;
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_JOIN -> handleJoin(conn, obj);
            case T_MOVE -> handleMove(conn, obj);
            case T_RESTART -> handleRestart();
            default -> sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Tipus desconegut: " + type).toString());
        }
    }

    /** Registra el jugador amb el nom demanat (o una variant única) i li confirma el nom. */
    private synchronized void handleJoin(WebSocket conn, JSONObject obj) {
        String requested = obj.optString(K_NAME, "Jugador").trim();
        if (requested.isEmpty()) requested = "Jugador";

        String finalName = clients.add(conn, requested);
        System.out.println("Client connectat: " + finalName);

        sendSafe(conn, msg(T_JOINED).put(K_NAME, finalName).toString());
        broadcastState();
    }

    /** Valida i aplica la jugada d'un client, actualitzant puntuació i estat. */
    private synchronized void handleMove(WebSocket conn, JSONObject obj) {
        String player = clients.nameBySocket(conn);
        if (player == null) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Cal fer 'join' abans de jugar").toString());
            return;
        }
        if (game.isFinished()) {
            return;
        }

        int row = obj.optInt(K_ROW, -1);
        int col = obj.optInt(K_COL, -1);
        int value = obj.optInt(K_VALUE, -1);

        if (row < 0 || row > 8 || col < 0 || col > 8 || value < 1 || value > 9) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Moviment invàlid").toString());
            return;
        }
        if (!game.isEditable(row, col)) {
            return; // Casella ja bloquejada: s'ignora la jugada
        }

        boolean correct = game.tryFill(row, col, value, player);
        if (correct) {
            clients.addScore(player, 2);
        } else {
            clients.addScore(player, -1);
            sendSafe(conn, msg(T_WRONG).put(K_ROW, row).put(K_COL, col).toString());
        }
        broadcastState();
    }

    /** Comença una partida nova amb un taulell nou i puntuacions a zero. */
    private synchronized void handleRestart() {
        game = new SudokuGame();
        clients.resetScores();
        broadcastState();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket engegat al port: " + getPort());
        setConnectionLostTimeout(100);
    }

    // ----------------- Lifecycle util -----------------

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Punt d'entrada: arrenca el servidor al port per defecte i espera senyals.
     */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en execució al port " + DEFAULT_PORT + ". Prem Ctrl+C per aturar-lo.");
        awaitForever();
    }
}
