package stud.v3;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

public class AI extends core.player.AI {
    protected static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};
    protected PieceColor myColor;
    private static final int MAX_TBS_DEPTH = 8;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        if (myColor == null) {
            myColor = (opponentMove == null || opponentMove.index1() == -1)
                ? PieceColor.BLACK : PieceColor.WHITE;
        }

        PieceColor opponent = getOpponent(myColor);

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

        Move tbsMove = threatSpaceSearch();
        if (tbsMove != null) {
            this.board.makeMove(tbsMove);
            return tbsMove;
        }

        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }

    private Move threatSpaceSearch() {
        List<Move> attackMoves = generateThreatMoves(myColor);

        for (Move move : attackMoves) {
            board.makeMove(move);
            boolean canWin = tbsSearch(MAX_TBS_DEPTH - 1, myColor, true);
            board.undo(move);

            if (canWin) {
                return move;
            }
        }

        return null;
    }

    private boolean tbsSearch(int depth, PieceColor attacker, boolean isAttacking) {
        if (depth == 0) return false;

        PieceColor defender = getOpponent(attacker);

        if (isAttacking) {
            List<Move> threats = generateThreatMoves(attacker);
            if (threats.isEmpty()) return false;

            for (Move threat : threats) {
                board.makeMove(threat);

                if (findWinningMove(attacker) != null) {
                    board.undo(threat);
                    return true;
                }

                boolean defenderCanDefend = !tbsSearch(depth - 1, attacker, false);
                board.undo(threat);

                if (defenderCanDefend) continue;
                return true;
            }
            return false;
        } else {
            List<Move> defenses = generateDefenseMoves(defender, attacker);
            if (defenses.isEmpty()) return false;

            for (Move defense : defenses) {
                board.makeMove(defense);
                boolean attackerWins = tbsSearch(depth - 1, attacker, true);
                board.undo(defense);

                if (!attackerWins) return true;
            }
            return false;
        }
    }

    private List<Move> generateThreatMoves(PieceColor color) {
        List<Integer> threats = findThreats(color);
        List<Move> moves = new ArrayList<>();

        int limit = Math.min(threats.size(), 5);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new Move(threats.get(i), threats.get(j)));
            }
        }

        return moves;
    }

    private List<Move> generateDefenseMoves(PieceColor defender, PieceColor attacker) {
        List<Integer> attackerThreats = findThreats(attacker);
        List<Move> moves = new ArrayList<>();

        if (attackerThreats.size() >= 2) {
            int limit = Math.min(attackerThreats.size(), 4);
            for (int i = 0; i < limit; i++) {
                for (int j = i + 1; j < limit; j++) {
                    moves.add(new Move(attackerThreats.get(i), attackerThreats.get(j)));
                }
            }
        }

        return moves;
    }

    protected Move findWinningMove(PieceColor color) {
        List<Integer> threats = findThreats(color);
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
        PieceColor opponent = getOpponent(myColor);

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

    private PieceColor getOpponent(PieceColor color) {
        return (color == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
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