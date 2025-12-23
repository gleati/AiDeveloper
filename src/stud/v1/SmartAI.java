package stud.v1;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import core.player.AI;

import java.util.ArrayList;
import java.util.List;

public class SmartAI extends AI {
    protected static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};
    protected PieceColor myColor;
    private boolean colorInitialized = false;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        if (!colorInitialized) {
            initColor(opponentMove);
        }

        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            return winMove;
        }

        Move blockMove = findBlockingMove();
        if (blockMove != null) {
            this.board.makeMove(blockMove);
            return blockMove;
        }

        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }

    private void initColor(Move opponentMove) {
        myColor = (opponentMove == null || opponentMove.index1() == -1)
                  ? PieceColor.BLACK : PieceColor.WHITE;
        colorInitialized = true;
    }

    protected Move findWinningMove(PieceColor color) {
        List<Integer> threats = findThreats(color);
        return threats.size() >= 2 ? new Move(threats.get(0), threats.get(1)) : null;
    }

    private Move findBlockingMove() {
        PieceColor opponent = (myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> threats = findThreats(opponent);
        return threats.size() >= 2 ? new Move(threats.get(0), threats.get(1)) : null;
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

        // 正方向计数
        for (int i = 1; i <= 5; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) count++;
            else break;
        }

        // 反方向计数
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

    protected int countLine(int row, int col, int dr, int dc, PieceColor color) {
        return countLineFromEmpty(row, col, dr, dc, color);
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
        return "V1-SmartAI";
    }
    
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        colorInitialized = false;
    }
}