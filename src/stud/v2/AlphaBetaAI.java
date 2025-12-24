package stud.v2;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import core.player.AI;
import java.util.*;

public class AlphaBetaAI extends AI {
    protected static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};
    protected PieceColor myColor;

    private static final int MAX_DEPTH = 4;
    private static final int INF = 10000000;
    private static final int CANDIDATE_LIMIT = 10;
    private static final int[] SCORES = {0, 10, 100, 1000, 50000, 1000000};

    private Map<Long, Integer> transpositionTable = new HashMap<>();
    private long zobristHash = 0;
    private long[][] zobristTable = new long[361][2];

    public AlphaBetaAI() {
        Random rand = new Random(12345);
        for (int i = 0; i < 361; i++) {
            zobristTable[i][0] = rand.nextLong();
            zobristTable[i][1] = rand.nextLong();
        }
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        updateZobrist(opponentMove);

        if (myColor == null) {
            myColor = (opponentMove == null || opponentMove.index1() == -1)
                    ? PieceColor.BLACK : PieceColor.WHITE;
        }
        System.out.println(myColor);
        PieceColor opponent = getOpponent(myColor);

        Move blockCritical = findCriticalBlock(opponent);
        if (blockCritical != null) {
            this.board.makeMove(blockCritical);
            updateZobrist(blockCritical);
            return blockCritical;
        }

        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            updateZobrist(winMove);
            return winMove;
        }

        Move blockMove = findWinningMove(opponent);
        if (blockMove != null) {
            this.board.makeMove(blockMove);
            updateZobrist(blockMove);
            return blockMove;
        }

        Move bestMove = alphaBetaSearch();
        this.board.makeMove(bestMove);
        updateZobrist(bestMove);
        return bestMove;
    }

    private void updateZobrist(Move move) {
        if (move == null || move.index1() == -1) return;
        PieceColor color = board.get(move.index1());
        int colorIdx = (color == PieceColor.BLACK) ? 0 : 1;
        zobristHash ^= zobristTable[move.index1()][colorIdx];
        if (move.index2() != -1) {
            color = board.get(move.index2());
            colorIdx = (color == PieceColor.BLACK) ? 0 : 1;
            zobristHash ^= zobristTable[move.index2()][colorIdx];
        }
    }

    private Move findCriticalBlock(PieceColor color) {
        List<Integer> critical = new ArrayList<>();

        for (int i = 0; i < 361; i++) {
            int row = i / 19, col = i % 19;
            System.out.println(board.get(i) + "   "+ row + "   " + col);
            if (board.get(i) != color) continue;

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
        System.out.println(" ");
        if (critical.size() >= 2) {
            return new Move(critical.get(0), critical.get(1));
        } else if (critical.size() == 1) {
            List<Integer> empty = getEmptyPositions();
            int pos2 = empty.get(0) == critical.get(0) ? empty.get(1) : empty.get(0);
            return new Move(critical.get(0), pos2);
        }

        return null;
    }

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
            updateZobrist(move);
            int score = -negamax(MAX_DEPTH - 1, -INF, -alpha, getOpponent(myColor));
            board.undo(move);
            updateZobrist(move);

            if (score > alpha) {
                alpha = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int negamax(int depth, int alpha, int beta, PieceColor color) {
        if (transpositionTable.containsKey(zobristHash)) {
            return transpositionTable.get(zobristHash);
        }

        if (depth == 0) {
            int eval = evaluate(color);
            transpositionTable.put(zobristHash, eval);
            return eval;
        }

        List<Move> moves = generateCandidateMoves();
        if (moves.isEmpty()) {
            int eval = evaluate(color);
            transpositionTable.put(zobristHash, eval);
            return eval;
        }

        int maxScore = -INF;
        for (Move move : moves) {
            board.makeMove(move);
            updateZobrist(move);
            int score = -negamax(depth - 1, -beta, -alpha, getOpponent(color));
            board.undo(move);
            updateZobrist(move);

            maxScore = Math.max(maxScore, score);
            if (score >= beta) {
                transpositionTable.put(zobristHash, beta);
                return beta;
            }
            alpha = Math.max(alpha, score);
        }

        transpositionTable.put(zobristHash, maxScore);
        return maxScore;
    }

    private int evaluate(PieceColor color) {
        return evaluatePosition(color) - evaluatePosition(getOpponent(color));
    }

    private int evaluatePosition(PieceColor color) {
        int score = 0;
        Set<Integer> evaluated = new HashSet<>();

        for (int i = 0; i < 361; i++) {
            if (board.get(i) != color || evaluated.contains(i)) continue;

            int row = i / 19, col = i % 19;
            for (int[] dir : DIRECTIONS) {
                int count = countConsecutive(row, col, dir[0], dir[1], color);
                if (count > 1) {
                    score += SCORES[Math.min(count, 5)];
                    markEvaluated(row, col, dir[0], dir[1], color, evaluated);
                }
            }
        }
        return score;
    }

    private void markEvaluated(int row, int col, int dr, int dc, PieceColor color, Set<Integer> evaluated) {
        for (int i = -5; i <= 5; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r >= 0 && r < 19 && c >= 0 && c < 19 && board.get(r * 19 + c) == color) {
                evaluated.add(r * 19 + c);
            }
        }
    }

    private List<Move> generateCandidateMoves() {
        Map<Integer, Integer> scores = new HashMap<>();
        Set<Integer> occupied = new HashSet<>();

        for (int i = 0; i < 361; i++) {
            if (board.get(i) != PieceColor.EMPTY) {
                occupied.add(i);
            }
        }

        if (occupied.isEmpty()) {
            return List.of(new Move(180, 181));
        }

        for (int pos : occupied) {
            int row = pos / 19, col = pos % 19;
            for (int dr = -2; dr <= 2; dr++) {
                for (int dc = -2; dc <= 2; dc++) {
                    int r = row + dr, c = col + dc;
                    if (r >= 0 && r < 19 && c >= 0 && c < 19) {
                        int candidate = r * 19 + c;
                        if (board.get(candidate) == PieceColor.EMPTY) {
                            int score = evaluateMoveQuality(candidate);
                            scores.put(candidate, scores.getOrDefault(candidate, 0) + score);
                        }
                    }
                }
            }
        }

        List<Integer> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> scores.get(b) - scores.get(a));

        List<Move> moves = new ArrayList<>();
        int limit = Math.min(sorted.size(), CANDIDATE_LIMIT);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new Move(sorted.get(i), sorted.get(j)));
            }
        }

        return moves;
    }

    private int evaluateMoveQuality(int pos) {
        int score = 0;
        int row = pos / 19, col = pos % 19;

        for (int[] dir : DIRECTIONS) {
            int myCount = countConsecutive(row, col, dir[0], dir[1], myColor);
            int oppCount = countConsecutive(row, col, dir[0], dir[1], getOpponent(myColor));

            if (myCount >= 4) score += 100000;
            else if (oppCount >= 4) score += 50000;
            else if (myCount == 3) score += 5000;
            else if (oppCount == 3) score += 3000;
            else score += myCount * 100 + oppCount * 50;
        }

        return score;
    }

    private PieceColor getOpponent(PieceColor color) {
        return (color == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
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
        return "V2-AlphaBeta-Optimized";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        myColor = null;
    }
}