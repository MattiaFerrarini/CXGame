/*
 *  Copyright (C) 2022 Lamberto Colazzo
 *
 *  This file is part of the ConnectX software developed for the
 *  Intern ship of the course "Information technology", University of Bologna
 *  A.Y. 2021-2022.
 *
 *  ConnectX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This  is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details; see <https://www.gnu.org/licenses/>.
 */

package connectx.IDPlayer;

import connectx.CXPlayer;
import connectx.CXBoard;
import connectx.CXGameState;
import connectx.CXCell;
import connectx.CXCellState;

import java.util.TreeSet;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.LinkedHashMap;

import java.util.Arrays;

import connectx.HashEntry.HashEntry;

/**
 * Software player only a bit smarter than random.
 * <p>
 * It can detect a single-move win or loss. In all the other cases behaves
 * randomly.
 * </p>
 */
public class IDPlayer implements CXPlayer {
    LinkedHashMap<Integer, HashEntry> transTable;
    int transTableCapacity;

    private Random rand;
    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;

    int M; //rows
    int N; //cols
    int K;
    boolean first;

    int missed;

    int[] moveOrder;

    CXBoard board;

    /* Default empty constructor */
    public IDPlayer() {
    }

    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game
        rand = new Random(System.currentTimeMillis());
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;
        TIMEOUT = timeout_in_secs;

        this.M = M;
        this.N = N;
        this.K = K;

        this.first = first;
        missed = 0;

        //move ordering
        moveOrder = new int[N];
        for(int i = 0; i < N; i++){
            if(i % 2 == 0)
                moveOrder[i] = N/2 + i/2;
            else
                moveOrder[i] = N/2 - i/2 - 1;
        }

        //System.err.println("Order " + Arrays.toString(moveOrder));

        int desiredDepthMemory = 5;
        //transTableCapacity = (int)Math.pow(N, desiredDepthMemory);
        transTableCapacity = 20000000;
        transTable = new LinkedHashMap<Integer, HashEntry>(transTableCapacity, 1);
    }

    /**
     * Selects a free colum on game board.
     * <p>
     * Selects a winning column (if any), otherwise selects a column (if any)
     * that prevents the adversary to win with his next move. If both previous
     * cases do not apply, selects a random column.
     * </p>
     */
    public int selectColumn(CXBoard B) {
        START = System.currentTimeMillis(); // Save starting time
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int player = B.currentPlayer();
        this.board = B;
        return ID(player, alpha, beta);
    }

    private int ID(int player, int alpha, int beta) {
        int bestSavedScore = Integer.MIN_VALUE;
        int bestSavedCol = board.getAvailableColumns()[0];
        int freeCells = board.numOfFreeCells();

        //System.err.println("Symmetric " + isSymmetric(board) + " " + (N/2));

        try{
            for (int depth = 1; depth <= freeCells; depth++) {
                int[] eval = alphaBeta(depth, player, alpha, beta, depth);
                if(eval[0] == Integer.MIN_VALUE)
                    break;
                else{
                    bestSavedScore = eval[0];
                    bestSavedCol = eval[1];
                    if(bestSavedScore >= beta)
                        break;
                }
                //System.err.println("Max depth " + depth + " TT Size " + transTable.size() + " Missed " + missed);
            }
        } catch (TimeoutException e) { }

        return bestSavedCol;
    }

    private int[] alphaBeta(int depth, int player, int alpha, int beta, int d) throws TimeoutException {

        if (board.gameState() != CXGameState.OPEN) {
            if (board.gameState() == CXGameState.DRAW)
                return new int[] {0, -1};
            else
                return new int[] {((board.gameState() == myWin) ? Integer.MAX_VALUE : Integer.MIN_VALUE), -1};
        } else if (depth == 0)
            //return new int[]{heuristic(board), -1};
            return new int[]{0, -1};

        /*
        Integer hash = getHash(board.getMarkedCells());
        HashEntry saved = checkTransTable(hash, board.currentPlayer());
        if(saved != null)
            return new int[]{saved.eval, saved.bestCol};
         */

        int bestScore, bestCol = -1, colsToCheck;

        //symmetry check
        boolean isSymmetric = isSymmetric(board);

        // If it's the maximizing player's turn, initialize the best score to the smallest possible value
        if (board.currentPlayer() == player) {
            bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < N; i = isSymmetric ? i+2 : i+1) {
                int col = moveOrder[i];
                if(board.fullColumn(col))
                    continue;
                //System.err.println(col);
                checktime();
                board.markColumn(col);
                int[] eval = alphaBeta(depth - 1, player, alpha, beta, d);
                if (eval[0] > bestScore) {
                    bestScore = eval[0];
                    bestCol = col;
                }
                board.unmarkColumn();
                alpha = Math.max(alpha, bestScore);
                if (beta <= alpha)
                    break; // Beta cutoff
            }
        }
        // If it's the minimizing player's turn, initialize the best score to the largest possible value
        else {
            bestScore = Integer.MAX_VALUE;
            for (int i = 0; i < N; i = isSymmetric ? i+2 : i+1) {
                int col = moveOrder[i];
                if(board.fullColumn(col))
                    continue;
                checktime();
                board.markColumn(col);
                int[] eval = alphaBeta(depth - 1, player, alpha, beta, d);
                if (eval[0] < bestScore) {
                    bestScore = eval[0];
                    bestCol = col;
                }
                board.unmarkColumn();
                beta = Math.min(beta, bestScore);
                if (beta <= alpha)
                    break; // Alpha cutoff
            }
        }

        //if(depth >= 1 && (bestScore == Integer.MIN_VALUE || bestScore == Integer.MAX_VALUE))
        //   updateTransTable(hash, new HashEntry(bestScore, bestCol, board.currentPlayer()));

        return new int[]{bestScore, bestCol};
    }

    boolean isSymmetric(CXBoard board)throws TimeoutException{
        checktime();
        CXCell[] MC = board.getMarkedCells();
        for(CXCell cell : MC){
            if(board.cellState(cell.i, N - cell.j - 1) != cell.state)
                return false;
        }
        return true;
    }

    Integer getHash(CXCell[] MC)  throws TimeoutException{
        checktime();
        Integer a[] = new Integer[MC.length];
        for (int i = 0; i < MC.length; i++) {
            int pos = MC[i].i * M + MC[i].j;
            a[i] = (MC[i].state == CXCellState.P1) ? pos : (-pos);
        }
        //Arrays.sort(a);
        //return a.hashCode();
        return Arrays.hashCode(a);
        //return a;
    }

    HashEntry checkTransTable(Integer hash, int player)throws TimeoutException {
        checktime();
        HashEntry saved = transTable.get(hash);
        if(saved != null && saved.player == player){
            transTable.put(hash, saved);
            return saved;
        }
        else if(saved != null && saved.player != player)
            missed += 1;

        return null;

    }

    void updateTransTable(Integer hash, HashEntry newRes)  throws TimeoutException{
        checktime();
        if (transTable.size() >= transTableCapacity) {
            Integer firstKey = transTable.keySet().iterator().next();
            transTable.remove(firstKey);
        }
        transTable.put(hash, newRes);
    }

    private int heuristic(CXBoard board) {
        return isNotColour(board.getBoard(), board.getLastMove().i, board.getLastMove().j);
    }

    private int isNotColour(CXCellState[][] B, int i, int j) {
        int n, max = 1, free;

        CXCellState me = first ? CXCellState.P1 : CXCellState.P2;
        CXCellState enemy = (me == CXCellState.P1) ? CXCellState.P2 : CXCellState.P1;

        // Useless pedantic check
        if (me == CXCellState.FREE)
            return max;

        // Horizontal check
        free = 1;
        n = 1;
        for (int k = 1; j - k >= 0 && B[i][j - k] != enemy; k++) {
            free++;
            if (B[i][j - k] == me) n++;
        } // backward check
        for (int k = 1; j + k < N && B[i][j + k] != enemy; k++) {
            free++;
            if (B[i][j + k] == me) n++;
        } // forward check
        if (free >= K) max = n;

        // Vertical check
        free = 1;
        n = 1;
        for (int k = 1; i + k < M && B[i + k][j] != enemy; k++) {
            free++;
            if (B[i + k][j] == me) n++;
        }
        if (free >= K) max = Math.max(max, n);

        // Diagonal check
        free = 1;
        n = 1;
        for (int k = 1; i - k >= 0 && j - k >= 0 && B[i - k][j - k] != enemy; k++) {
            free++;
            if (B[i - k][j - k] == me) n++;
        } // backward check
        for (int k = 1; i + k < M && j + k < N && B[i + k][j + k] != enemy; k++) {
            free++;
            if (B[i + k][j + k] == me) n++;
        } // forward check
        if (free >= K) max = Math.max(n, max);

        // Anti-diagonal check
        free = 1;
        n = 1;
        for (int k = 1; i - k >= 0 && j + k < N && B[i - k][j + k] != enemy; k++) {
            free++; // backward check
            if (B[i - k][j + k] == me) n++; // backward check
        }
        for (int k = 1; i + k < M && j - k >= 0 && B[i + k][j - k] != enemy; k++) {
            free++; // forward check
            if (B[i + k][j - k] == me) n++; // forward check
        }
        if (free >= K) max = Math.max(max, n);

        return max * 1000 + (this.N / 2) - Math.abs(j - (this.N / 2));
    }

    private void checktime() throws TimeoutException {
        if ((System.currentTimeMillis() - START) / 1000.0 >= TIMEOUT * (99.0 / 100.0))
            throw new TimeoutException();
    }

    public String playerName() {
        return "IDPlayer";
    }
}
