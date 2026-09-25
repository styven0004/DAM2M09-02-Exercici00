package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre dels jugadors connectats: nom, socket associat i puntuació.
 *
 * Si dos jugadors demanen el mateix nom, s'afegeix un sufix numèric al
 * segon perquè els noms siguin sempre únics dins la partida.
 *
 * Aquesta classe és segura per a ús concurrent: els mètodes que llegeixen
 * o modifiquen més d'un mapa a la vegada estan sincronitzats.
 */
final class ClientRegistry {

    private static final class PlayerInfo {
        final String name;
        int score;

        PlayerInfo(String name) {
            this.name = name;
            this.score = 0;
        }
    }

    private final Map<WebSocket, PlayerInfo> bySocket = new ConcurrentHashMap<>();
    private final Map<String, WebSocket> byName = new LinkedHashMap<>();

    /**
     * Afegeix un jugador nou, resolent col·lisions de nom afegint un sufix.
     *
     * @param socket        socket del client
     * @param requestedName nom que ha demanat el jugador
     * @return el nom finalment assignat (pot ser diferent del demanat)
     */
    synchronized String add(WebSocket socket, String requestedName) {
        String name = requestedName;
        int suffix = 2;
        while (byName.containsKey(name)) {
            name = requestedName + " (" + suffix + ")";
            suffix++;
        }
        bySocket.put(socket, new PlayerInfo(name));
        byName.put(name, socket);
        return name;
    }

    /**
     * Elimina un client del registre.
     *
     * @return el nom que tenia assignat, o null si no hi era
     */
    synchronized String remove(WebSocket socket) {
        PlayerInfo info = bySocket.remove(socket);
        if (info != null) {
            byName.remove(info.name);
            return info.name;
        }
        return null;
    }

    /** Nom associat a un socket, o null si encara no ha fet "join". */
    String nameBySocket(WebSocket socket) {
        PlayerInfo info = bySocket.get(socket);
        return info == null ? null : info.name;
    }

    /** Suma (o resta, amb delta negatiu) punts a un jugador pel seu nom. */
    synchronized void addScore(String name, int delta) {
        WebSocket socket = byName.get(name);
        if (socket == null) return;
        PlayerInfo info = bySocket.get(socket);
        if (info != null) {
            info.score += delta;
        }
    }

    /** Posa totes les puntuacions a zero (utilitzat en "tornar a jugar"). */
    synchronized void resetScores() {
        for (PlayerInfo info : bySocket.values()) {
            info.score = 0;
        }
    }

    /** Sockets de tots els jugadors actualment connectats. */
    Collection<WebSocket> sockets() {
        return bySocket.keySet();
    }

    /** Llista de jugadors (nom + puntuació) en format JSON. */
    synchronized JSONArray playersAsJson() {
        JSONArray arr = new JSONArray();
        for (PlayerInfo info : bySocket.values()) {
            JSONObject o = new JSONObject();
            o.put("name", info.name);
            o.put("score", info.score);
            arr.put(o);
        }
        return arr;
    }
}
