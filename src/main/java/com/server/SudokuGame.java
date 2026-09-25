package com.server;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Estat d'una partida de Sudoku compartida per tots els jugadors connectats.
 *
 * Manté la solució (oculta als clients), les caselles inicials (givens) i
 * les caselles que els jugadors han anat encertant, amb el nom de qui les
 * ha resolt.
 */
final class SudokuGame {

    private final int[][] solution = new int[9][9];
    private final int[][] givens = new int[9][9];
    private final int[][] filledValue = new int[9][9];
    private final String[][] filledOwner = new String[9][9];

    private int givenCount = 0;
    private int filledCount = 0;

    SudokuGame() {
        SudokuGenerator.generate(solution, givens);
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (givens[r][c] != 0) {
                    givenCount++;
                }
            }
        }
    }

    /**
     * Indica si una casella es pot omplir (no és una pista inicial i encara
     * no ha estat encertada per cap jugador).
     */
    boolean isEditable(int row, int col) {
        return givens[row][col] == 0 && filledValue[row][col] == 0;
    }

    /**
     * Intenta omplir una casella amb un valor. Si el valor coincideix amb la
     * solució, la casella queda bloquejada i associada al jugador.
     *
     * @return true si el valor era correcte
     */
    synchronized boolean tryFill(int row, int col, int value, String owner) {
        boolean correct = solution[row][col] == value;
        if (correct) {
            filledValue[row][col] = value;
            filledOwner[row][col] = owner;
            filledCount++;
        }
        return correct;
    }

    /** La partida ha acabat quan totes les 81 caselles estan plenes. */
    boolean isFinished() {
        return givenCount + filledCount >= 81;
    }

    JSONArray givensAsJson() {
        JSONArray rows = new JSONArray();
        for (int r = 0; r < 9; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < 9; c++) {
                row.put(givens[r][c]);
            }
            rows.put(row);
        }
        return rows;
    }

    JSONArray filledAsJson() {
        JSONArray arr = new JSONArray();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (filledValue[r][c] != 0) {
                    JSONObject o = new JSONObject();
                    o.put("row", r);
                    o.put("col", c);
                    o.put("value", filledValue[r][c]);
                    o.put("owner", filledOwner[r][c]);
                    arr.put(o);
                }
            }
        }
        return arr;
    }
}
