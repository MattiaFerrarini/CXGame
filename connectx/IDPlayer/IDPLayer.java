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
import java.util.Arrays;
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
    private int  TIMEOUT;
    private long START;

    int M; //rows
    int N; //cols
    int K;
    boolean first;

    /* Default empty constructor */
    public IDPlayer() {
    }

    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game
        rand    = new Random(System.currentTimeMillis());
        myWin   = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;
        TIMEOUT = timeout_in_secs;

        this.M = M;
        this.N = N;
        this.K = K;

        this.first = first;

        transTableCapacity = 500;
        transTable = new LinkedHashMap<>(transTableCapacity);
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

        int alpha = -1;
        int beta = 1;
        int player = B.currentPlayer();

        return ID(B, player, alpha, beta);
    }

    private int ID(CXBoard B, int player, int alpha, int beta){

        Integer[] L = B.getAvailableColumns();
        int bestValue = -1;
        int bestCol = L[rand.nextInt(L.length)]; // Save a random column
        int freeCels = B.numOfFreeCells();

        int depth = 0;
        boolean pruning = false;

        while(depth < freeCels && !pruning){
            try{
                for(int d = 1; d <= depth && !pruning; d++){

                    int hash = getHash(B.getMarkedCells());
                    HashEntry saved = checkTransTable(hash, d);
                    if(saved != null){
                        bestValue = saved.eval;
                        bestCol = saved.bestCol;
                        //System.err.println("Depth " + d + " Saved val: " + bestValue + " col: " + bestCol);
                        continue;
                    }

                    //System.err.println("Calculating depth " + d);

                    for (int col : L) { // for each column

                        //System.err.println("First it col " + col + " Depth: " + depth);

                        B.markColumn(col);
                        int value = alphaBeta(B, B.getAvailableColumns(), d-1, player, -1, 1);
                        B.unmarkColumn();

                        //System.err.println("Col " + col + " val " + value + "\n");

                        //System.err.println("Depth: " + d + " Col: " + col + " Val: " + value);
                        if (value > bestValue) {
                            bestValue = value;
                            bestCol = col;
                        }
                        //TODO: heuristic for best choice among equal evals
                        else if(value == bestValue && Math.abs(col - N/2) < Math.abs(bestCol - N/2))
                            bestCol = col;

                        //System.err.println("Best Col: " + bestCol + " Best Val: " + bestValue);
                        alpha = Math.max(alpha, bestValue);
                        if (beta <= alpha) {
                            //System.err.println("Pruning!");
                            pruning = true;
                            break;
                        }
                    }
                    updateTransTable(hash, new HashEntry(bestValue, d, bestCol));
                }
                depth += 1;
            }
            catch (TimeoutException e) {
                //System.err.println("Timeout! Best column selected");
                break;
            }
        }

        //transTable.forEach((key, value) -> System.out.println("Rank : " + key + "\t\t Name : " + value.depth + " " + value.eval));

        //System.err.println("Max depth: " + depth);
        //System.err.println(transTable.elements());

        //System.err.println("End ID");
        //System.err.println("Best col: " + bestCol + " Best val: " + bestValue);
        return bestCol;
    }

    private int alphaBeta(CXBoard board, Integer[] L, int depth, int player, int alpha, int beta) throws TimeoutException{

        int bestScore, bestCol = L[rand.nextInt(L.length)], hash;
        HashEntry saved = null;

        if(board.gameState() != CXGameState.OPEN){
            if(board.gameState() == CXGameState.DRAW)
                return 0;
            else
                return ((board.gameState() == myWin) ? 1 : -1);
        }
        else if(depth == 0)
            return heuristic(board);

        else{
            checktime();
            hash = getHash(board.getMarkedCells());
            saved = checkTransTable(hash, depth);
        }

        //System.err.println("Start AB");

        if(saved != null)
            return saved.eval;


        // If it's the maximizing player's turn, initialize the best score to the smallest possible value
        else if (board.currentPlayer() == player) {
            bestScore = -1;
            // Iterate over all possible moves and recursively evaluate each one
            for (int i : L) {
                checktime();
                board.markColumn(i);
                int score = alphaBeta(board, board.getAvailableColumns(), depth - 1, player, alpha, beta);
                board.unmarkColumn();
                if(score >= bestScore){
                    bestScore = score;
                    bestCol = i;
                }
                alpha = Math.max(alpha, bestScore);
                if (beta <= alpha)
                    break; // Beta cutoff

                //System.err.println("\tCol" + i + " val " + bestScore + "\n");
                //System.err.println("Max col " + i + " Depth: " + depth);
            }
            updateTransTable(hash, new HashEntry(bestScore, depth, bestCol));
        }
        // If it's the minimizing player's turn, initialize the best score to the largest possible value
        else {
            bestScore = 1;
            // Iterate over all possible moves and recursively evaluate each one
            for (int i : L) {
                checktime();
                board.markColumn(i);
                int score = alphaBeta(board, board.getAvailableColumns(),depth - 1, player, alpha, beta);
                board.unmarkColumn();
                if(score <= bestScore){
                    bestScore = score;
                    bestCol = i;
                }
                beta = Math.min(beta, bestScore);
                if (beta <= alpha)
                    break; // Alpha cutoff

                //System.err.println("\tCol" + i + " val " + bestScore + "\n");
                //System.err.println("Min col " + i + " Depth: " + depth);
            }
            updateTransTable(hash, new HashEntry(bestScore, depth, bestCol));
        }
        //System.err.println("End AB");
        return bestScore;
    }

    int getHash(CXCell[] MC){
        int a[] = new int[MC.length];
        for(int i = 0; i < MC.length; i++){
            int pos = MC[i].i * M + MC[i].j + 1;
            a[i] = (MC[i].state == CXCellState.P1) ? pos : (-pos);
        }
        Arrays.sort(a);
        return Arrays.hashCode(a);
    }

    HashEntry checkTransTable(int hash, int depth){
        HashEntry saved = transTable.get(hash);
        if(saved != null && saved.depth >= depth)
            return saved;
        else
            return null;
    }

    void updateTransTable(int hash, HashEntry newRes){
        HashEntry saved = transTable.get(hash);
        if(saved == null || saved.depth <= newRes.depth){
            if (transTable.size() == transTableCapacity) {
                int firstKey = transTable.keySet().iterator().next();
                transTable.remove(firstKey);
            }
            transTable.put(hash, newRes);
        }
    }

    private int heuristic(CXBoard board){
        return 0;
    }

    // TODO
    /*
    private double heuristic(CXBoard board){
        double plus = 0;
        double minus = 0;
        double v = 0;

        CXCell[] usedCells = board.getMarkedCells();
        CXCellState S = first ? CXCellState.P1 : CXCellState.P2;

        for(CXCell c : usedCells){
            v = M/2 - Math.abs(M/2 - c.i) + N/2 - Math.abs(N/2 - c.j);
            if(c.state == S)
                plus += v;
            else
                minus += v;
        }
        return (plus-minus) / plus;
    }
    */

    private void checktime() throws TimeoutException {
        if ((System.currentTimeMillis() - START) / 1000.0 >= TIMEOUT * (99.0 / 100.0))
            throw new TimeoutException();
    }

    public String playerName() {
        return "IDPlayer";
    }
}
