package stud.v3;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import core.player.AI;
import java.util.*;

public class TBSAI extends AI {
    protected static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};
    protected PieceColor myColor;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        if (myColor == null) {
            if (opponentMove == null || opponentMove.index1() == -1) {
                myColor = PieceColor.BLACK;
            } else {
                myColor = PieceColor.WHITE;
            }
        }

        List<Integer> myThreats = findThreats(myColor);
        if (myThreats.size() >= 2) {
            Move move = new Move(myThreats.get(0), myThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        PieceColor opponentColor = (myColor == PieceColor.BLACK) ?
                PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> opponentThreats = findThreats(opponentColor);
        if (opponentThreats.size() >= 2) {
            Move move = new Move(opponentThreats.get(0), opponentThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }

    protected List<Integer> findThreats(PieceColor color) {
        List<Integer> threats = new ArrayList<>();

        for (int i = 0; i < 361; i++) {
            if (board.get(i) != PieceColor.EMPTY) continue;

            int row = i / 19, col = i % 19;
            for (int[] dir : DIRECTIONS) {
                int count = countLineFromEmpty(row, col, dir[0], dir[1], color);
                if (count >= 4) {
                    threats.add(i);
                    break;
                }
            }
        }
        return threats;
    }

    protected int countLineFromEmpty(int row, int col, int dr, int dc, PieceColor color) {
        int count = 0;

        for (int i = 1; i <= 5; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) count++;
            else break;
        }

        for (int i = 1; i <= 5; i++) {
            int r = row - dr * i, c = col - dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) count++;
            else break;
        }

        return count;
    }

    protected Move findSmartMove() {
        int bestPos1 = -1, bestPos2 = -1;
        int bestScore = -1;

        List<Integer> empty = getEmptyPositions();
        int limit = Math.min(empty.size(), 30);

        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                int score = evaluateMove(empty.get(i)) + evaluateMove(empty.get(j));
                if (score > bestScore) {
                    bestScore = score;
                    bestPos1 = empty.get(i);
                    bestPos2 = empty.get(j);
                }
            }
        }

        return bestPos1 != -1 ? new Move(bestPos1, bestPos2) : new Move(180, 181);
    }

    protected int evaluateMove(int pos) {
        int row = pos / 19, col = pos % 19;
        int myScore = 0, oppScore = 0;
        PieceColor opponent = (myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;

        for (int[] dir : DIRECTIONS) {
            int myCount = countLineFromEmpty(row, col, dir[0], dir[1], myColor);
            int oppCount = countLineFromEmpty(row, col, dir[0], dir[1], opponent);

            if (myCount >= 4) myScore += 10000;
            else if (myCount == 3) myScore += 500;
            else if (myCount == 2) myScore += 50;

            if (oppCount >= 4) oppScore += 8000;
            else if (oppCount == 3) oppScore += 400;
            else if (oppCount == 2) oppScore += 40;
        }

        int distToCenter = Math.abs(row - 9) + Math.abs(col - 9);
        return myScore + oppScore + (18 - distToCenter);
    }

    protected List<Integer> getEmptyPositions() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) empty.add(i);
        }
        return empty;
    }

    @Override
    public String name() {
        return "V3-TBS";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        myColor = null;
    }
}