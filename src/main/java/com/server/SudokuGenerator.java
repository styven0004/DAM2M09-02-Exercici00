package com.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generador de taulells de Sudoku.
 *
 * Crea una solució completa vàlida mitjançant backtracking amb ordre
 * aleatori de valors, i després n'esborra un nombre fix de cel·les
 * per obtenir la graella inicial (givens) que es mostrarà als jugadors.
 */
final class SudokuGenerator {

    private static final int SIZE = 9;

    /** Nombre de cel·les que s'esborren de la solució per crear el joc. */
    private static final int CELLS_TO_REMOVE = 45;

    private SudokuGenerator() {
    }

    /**
     * Genera una solució completa i el taulell inicial (amb zeros a les
     * caselles buides) a partir d'aquesta.
     *
     * @param solutionOut matriu 9x9 on es deixarà la solució completa
     * @param puzzleOut   matriu 9x9 on es deixarà el taulell inicial (0 = buit)
     */
    static void generate(int[][] solutionOut, int[][] puzzleOut) {
        Random random = new Random();
        int[][] solved = new int[SIZE][SIZE];
        fillGrid(solved, random);

        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(solved[r], 0, solutionOut[r], 0, SIZE);
            System.arraycopy(solved[r], 0, puzzleOut[r], 0, SIZE);
        }

        List<int[]> positions = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                positions.add(new int[]{r, c});
            }
        }
        Collections.shuffle(positions, random);

        int removed = 0;
        for (int[] pos : positions) {
            if (removed >= CELLS_TO_REMOVE) break;
            puzzleOut[pos[0]][pos[1]] = 0;
            removed++;
        }
    }

    /**
     * Omple recursivament la graella amb un sudoku vàlid complet.
     *
     * @param grid   graella a omplir
     * @param random generador aleatori per barrejar l'ordre dels valors provats
     * @return true si s'ha trobat una solució completa
     */
    private static boolean fillGrid(int[][] grid, Random random) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0) {
                    List<Integer> values = new ArrayList<>();
                    for (int v = 1; v <= 9; v++) values.add(v);
                    Collections.shuffle(values, random);

                    for (int value : values) {
                        if (isValid(grid, r, c, value)) {
                            grid[r][c] = value;
                            if (fillGrid(grid, random)) {
                                return true;
                            }
                            grid[r][c] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Comprova si un valor es pot col·locar en una posició sense infringir
     * les regles del Sudoku (fila, columna i regió 3x3).
     */
    private static boolean isValid(int[][] grid, int row, int col, int value) {
        for (int i = 0; i < SIZE; i++) {
            if (grid[row][i] == value || grid[i][col] == value) {
                return false;
            }
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                if (grid[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }
}
