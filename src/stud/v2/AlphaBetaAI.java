package stud.v2;

import core.board.PieceColor;
import core.game.Move;
import stud.v1.SmartAI;
import java.util.*;

public class AlphaBetaAI extends SmartAI {
    private static final int MAX_DEPTH = 2;
    private static final int INF = 1000000;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        if (myColor == null) {
            myColor = (opponentMove == null || opponentMove.index1() == -1)
                    ? PieceColor.BLACK : PieceColor.WHITE;
        }

        PieceColor opponent = getOpponent(myColor);

        // 优先检查对手的致命威胁
        Move blockCritical = findCriticalBlock(opponent);
        if (blockCritical != null) {
            this.board.makeMove(blockCritical);
            return blockCritical;
        }

        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            return winMove;
        }

        Move blockMove = findWinningMove(opponent);
        if (blockMove != null) {
            this.board.makeMove(blockMove);
            return blockMove;
        }

        Move bestMove = alphaBetaSearch();
        this.board.makeMove(bestMove);
        return bestMove;
    }

    private Move findCriticalBlock(PieceColor color) {
        List<Integer> critical = new ArrayList<>();

        // 检查所有已有5连或4连
        for (int i = 0; i < 361; i++) {
            if (board.get(i) != color) continue;
            int row = i / 19, col = i % 19;
            for (int[] dir : DIRECTIONS) {
                int count = countConsecutive(row, col, dir[0], dir[1], color);
                if (count >= 4) {
                    List<Integer> ext = findExtensions(row, col, dir[0], dir[1], color);
                    for (int pos : ext) {
                        if (!critical.contains(pos)) critical.add(pos);
                    }
                }
            }
        }

        if (critical.size() >= 2) {
            return new Move(critical.get(0), critical.get(1));
        } else if (critical.size() == 1) {
            List<Integer> empty = getEmptyPositions();
            int pos2 = empty.get(0) == critical.get(0) ? empty.get(1) : empty.get(0);
            return new Move(critical.get(0), pos2);
        }

        return null;
    }

    @Override
    protected Move findWinningMove(PieceColor color) {
        List<Integer> threats = findThreats(color);
        return threats.size() >= 2 ? new Move(threats.get(0), threats.get(1)) : null;
    }

    private int countConsecutive(int row, int col, int dr, int dc, PieceColor color) {
        int count = 1;

        for (int i = 1; i < 6; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19 || board.get(r * 19 + c) != color) break;
            count++;
        }

        for (int i = 1; i < 6; i++) {
            int r = row - dr * i, c = col - dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19 || board.get(r * 19 + c) != color) break;
            count++;
        }

        return count;
    }

    private List<Integer> findExtensions(int row, int col, int dr, int dc, PieceColor color) {
        List<Integer> extensions = new ArrayList<>();

        while (true) {
            int r = row - dr, c = col - dc;
            if (r < 0 || r >= 19 || c < 0 || c >= 19 || board.get(r * 19 + c) != color) break;
            row = r; col = c;
        }

        int r1 = row - dr, c1 = col - dc;
        if (r1 >= 0 && r1 < 19 && c1 >= 0 && c1 < 19 && board.get(r1 * 19 + c1) == PieceColor.EMPTY) {
            extensions.add(r1 * 19 + c1);
        }

        while (true) {
            int r = row + dr, c = col + dc;
            if (r < 0 || r >= 19 || c < 0 || c >= 19 || board.get(r * 19 + c) != color) break;
            row = r; col = c;
        }

        int r2 = row + dr, c2 = col + dc;
        if (r2 >= 0 && r2 < 19 && c2 >= 0 && c2 < 19 && board.get(r2 * 19 + c2) == PieceColor.EMPTY) {
            extensions.add(r2 * 19 + c2);
        }

        return extensions;
    }

    private Move alphaBetaSearch() {
        List<Move> candidates = generateCandidateMoves();
        if (candidates.isEmpty()) return findSmartMove();

        Move bestMove = candidates.get(0);
        int alpha = -INF;

        for (Move move : candidates) {
            board.makeMove(move);
            int score = -negamax(MAX_DEPTH - 1, -INF, -alpha, getOpponent(myColor));
            board.undo(move);

            if (score > alpha) {
                alpha = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int negamax(int depth, int alpha, int beta, PieceColor color) {
        if (depth == 0) return evaluate(color);

        List<Move> moves = generateCandidateMoves();
        if (moves.isEmpty()) return evaluate(color);

        for (Move move : moves) {
            board.makeMove(move);
            int score = -negamax(depth - 1, -beta, -alpha, getOpponent(color));
            board.undo(move);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private int evaluate(PieceColor color) {
        return evaluatePosition(color) - evaluatePosition(getOpponent(color));
    }

    private int evaluatePosition(PieceColor color) {
        int score = 0;

        for (int i = 0; i < 361; i++) {
            if (board.get(i) != color) continue;
            int row = i / 19, col = i % 19;
            for (int[] dir : DIRECTIONS) {
                int count = countConsecutive(row, col, dir[0], dir[1], color);
                if (count >= 6) score += 1000000;
                else if (count == 5) score += 100000;
                else if (count == 4) score += 10000;
                else if (count == 3) score += 1000;
                else if (count == 2) score += 100;
            }
        }
        return score;
    }

    private List<Move> generateCandidateMoves() {
        Map<Integer, Integer> scores = new HashMap<>();
        boolean hasStone = false;

        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) continue;
            hasStone = true;
            int row = i / 19, col = i % 19;

            for (int dr = -2; dr <= 2; dr++) {
                for (int dc = -2; dc <= 2; dc++) {
                    int r = row + dr, c = col + dc;
                    if (r >= 0 && r < 19 && c >= 0 && c < 19) {
                        int pos = r * 19 + c;
                        if (board.get(pos) == PieceColor.EMPTY) {
                            int score = evaluateMove(pos);
                            scores.put(pos, scores.getOrDefault(pos, 0) + score);
                        }
                    }
                }
            }
        }

        if (!hasStone) {
            scores.put(180, 1000);
            scores.put(181, 900);
        }

        List<Integer> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> scores.get(b) - scores.get(a));

        List<Move> moves = new ArrayList<>();
        int limit = Math.min(sorted.size(), 8);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new Move(sorted.get(i), sorted.get(j)));
            }
        }

        return moves;
    }

    private PieceColor getOpponent(PieceColor color) {
        return (color == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
    }

    @Override
    public String name() {
        return "V2-AlphaBeta";
    }
}