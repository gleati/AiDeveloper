package stud.v2;

import core.board.PieceColor;
import core.game.Move;
import stud.v1.SmartAI;

import java.util.ArrayList;
import java.util.List;

/**
 * V2阶段：博弈树搜索AI
 */
public class AlphaBetaAI extends SmartAI {
    
    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        
        // 初始化颜色
        if (myColor == null) {
            if (opponentMove == null || opponentMove.index1() == -1) {
                myColor = PieceColor.BLACK;
            } else {
                myColor = PieceColor.WHITE;
            }
        }

        // 检查必胜和必防
        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            return winMove;
        }

        PieceColor opponentColor = (myColor == PieceColor.BLACK) ?
                                    PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> threats = findThreats(opponentColor);
        if (threats.size() >= 2) {
            Move blockMove = new Move(threats.get(0), threats.get(1));
            this.board.makeMove(blockMove);
            return blockMove;
        }

        // 使用评估函数选择最佳走法
        Move bestMove = findBestMove();
        this.board.makeMove(bestMove);
        return bestMove;
    }
    
    private Move findBestMove() {
        List<Move> candidates = generateMoves();
        if (candidates.isEmpty()) {
            return findSmartMove();
        }
        
        Move bestMove = candidates.get(0);
        int bestScore = evaluateMove(bestMove);
        
        for (int i = 1; i < candidates.size(); i++) {
            Move move = candidates.get(i);
            int score = evaluateMove(move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }
    
    private int evaluateMove(Move move) {
        return evaluatePosition(move.index1()) + evaluatePosition(move.index2());
    }
    
    private int evaluatePosition(int pos) {
        int row = pos / 19;
        int col = pos % 19;
        int score = (18 - Math.abs(row - 9) - Math.abs(col - 9)) * 10;

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr;
                int c = col + dc;
                if (r >= 0 && r < 19 && c >= 0 && c < 19) {
                    if (board.get(r * 19 + c) == myColor) score += 50;
                }
            }
        }
        return score;
    }

    private List<Move> generateMoves() {
        List<Integer> empty = getEmptyPositions();
        List<Move> moves = new ArrayList<>();
        int limit = Math.min(empty.size(), 15);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new Move(empty.get(i), empty.get(j)));
            }
        }
        return moves;
    }
    
    @Override
    public String name() {
        return "V2-AlphaBeta";
    }
}