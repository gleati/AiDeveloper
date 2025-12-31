package stud.g05;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * V14: Tactician (战术大师 - 纯 TBS 评分版)
 * 策略核心：放弃 MCTS 随机模拟，改用高精度的棋型评分。
 * 1. 【双重威胁】: 极高权重寻找"一子双杀" (如双活三、四三杀)。V1 无法防御这种进攻。
 * 2. 【绝对防守】: 模拟对手落子，如果对手能成 5 或 6，强制最高优先级防守。
 * 3. 【进攻优先】: 在安全的情况下，优先选择能延伸出最多"气"的进攻点。
 */
public class AI extends core.player.AI {

    private PieceColor myColor;
    private final int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    // 棋型分数常量 (精心调优，针对 V1)
    private static final int SCORE_WIN = 100000000; // 连6
    private static final int SCORE_BLOCK_WIN = 50000000;  // 阻挡对手连6
    private static final int SCORE_MAKE_5 = 10000000;  // 制造连5 (必胜前兆)
    private static final int SCORE_BLOCK_5 = 5000000;   // 阻挡对手连5
    private static final int SCORE_LIVE_4 = 1000000;   // 活4 (必杀)
    private static final int SCORE_RUSH_4 = 50000;     // 冲4 (死4)
    private static final int SCORE_LIVE_3 = 5000;      // 活3 (双活3的基础)
    private static final int SCORE_SLEEP_3 = 500;       // 眠3
    private static final int SCORE_LIVE_2 = 100;       // 活2

    public AI() {
        this.board = new Board();
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
        this.myColor = null;
    }

    @Override
    public String name() {
        return "G05";
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        try {
            // 1. 同步
            if (isValidMove(opponentMove)) this.board.makeMove(opponentMove);

            // 2. 颜色
            if (myColor == null) {
                myColor = (opponentMove == null || opponentMove.index1() == -1)
                        ? PieceColor.BLACK : PieceColor.WHITE;
            }

            // 3. 开局天元
            if (myColor == PieceColor.BLACK && getBoardStoneCount() == 0) {
                Move start = new Move(180, -1);
                this.board.makeMove(start);
                return start;
            }

            // --- 阶段 A: 必胜扫瞄 (VCF) ---
            // 如果我能赢，直接赢，不需要思考
            Move winMove = findWinningMove(myColor);
            if (winMove != null) return safeReturn(winMove);

            // --- 阶段 B: 必死防御 (必须堵) ---
            // 扫描对手的必杀点 (连6 或 连5)
            Move blockMove = findForcedBlock(getOpponent(myColor));
            if (blockMove != null) return safeReturn(blockMove);

            // --- 阶段 C: 战术评分搜索 (Tactical Search) ---
            // 既然没有直接死活，就找分最高的两步棋
            // 这里的核心是找"双活三"或"四三杀"
            Move bestMove = findBestTacticalMove();
            return safeReturn(bestMove);

        } catch (Throwable e) {
            e.printStackTrace();
            return safeReturn(getFallbackMove());
        }
    }

    /**
     * 寻找必须堵的点 (对手下这就赢了，或者连5了)
     */
    private Move findForcedBlock(PieceColor opp) {
        List<Integer> empties = getInterestingPoints();
        Set<Integer> killPoints = new HashSet<>();
        Set<Integer> fatalPoints = new HashSet<>();

        for (int p : empties) {
            int threat = simulateThreatLevel(p, opp);
            if (threat == 2) killPoints.add(p);      // 对手下这就赢
            else if (threat == 1) fatalPoints.add(p); // 对手下这就连5
        }

        // 1. 必败点 (Kill): 必须堵
        if (!killPoints.isEmpty()) {
            List<Integer> list = new ArrayList<>(killPoints);
            if (list.size() >= 2) return new Move(list.get(0), list.get(1)); // 双杀，必输无疑，挣扎一下

            // 堵住必败点，第二手尝试堵必死点或进攻
            int p1 = list.get(0);
            int p2 = -1;
            if (!fatalPoints.isEmpty()) p2 = fatalPoints.iterator().next();
            else p2 = findBestSingleAttack(myColor, p1);
            return new Move(p1, p2);
        }

        // 2. 必死点 (Fatal/连5): 必须堵
        if (!fatalPoints.isEmpty()) {
            List<Integer> list = new ArrayList<>(fatalPoints);
            // 关键：如果有两个以上的点能成5，大概率是活四的两头，必须全堵
            if (list.size() >= 2) return new Move(list.get(0), list.get(1));

            // 只有一个点，堵住它
            return new Move(list.get(0), findBestSingleAttack(myColor, list.get(0)));
        }
        return null;
    }

    /**
     * 战术搜索：寻找得分最高的两步棋组合
     * 复杂度控制：O(N^2) 太慢，我们采用贪心优化。
     * 先找得分最高的 Top 10 个单点，然后在这些点里组合。
     */
    private Move findBestTacticalMove() {
        List<Integer> cands = getInterestingPoints();
        // 1. 给每个空位打分 (进攻分 + 防守分)
        Map<Integer, Integer> pointScores = new HashMap<>();
        for (int p : cands) {
            int attack = evaluatePoint(p, myColor);
            int defense = evaluatePoint(p, getOpponent(myColor));
            // 进攻权重 1.2，鼓励进攻，但也别完全不顾防守
            pointScores.put(p, (int) (attack * 1.2 + defense));
        }

        // 2. 选出 Top 15 候选点
        cands.sort((a, b) -> pointScores.get(b) - pointScores.get(a));
        int limit = Math.min(cands.size(), 15);
        List<Integer> topCands = cands.subList(0, limit);

        // 3. 在 Top 候选点中穷举最佳组合
        Move bestMove = null;
        long maxScore = Long.MIN_VALUE;

        // 这里我们模拟"下两子"，然后评估局面总分
        for (int i = 0; i < topCands.size(); i++) {
            for (int j = i + 1; j < topCands.size(); j++) {
                int p1 = topCands.get(i);
                int p2 = topCands.get(j);

                // 组合分 = 单点分之和 + 协作加成
                long currentScore = pointScores.get(p1) + pointScores.get(p2);

                // 协作加成：如果两子靠得近，或者形成了连珠，加分
                if (isConnected(p1, p2)) {
                    // 简单模拟一下连线
                    currentScore += calculateLinkBonus(p1, p2, myColor);
                }

                if (currentScore > maxScore) {
                    maxScore = currentScore;
                    bestMove = new Move(p1, p2);
                }
            }
        }

        if (bestMove == null) return getFallbackMove();
        return bestMove;
    }

    // 计算两子配合的加成 (这是产生双杀的关键)
    private int calculateLinkBonus(int p1, int p2, PieceColor color) {
        // 简单粗暴：如果这两个子在同一条线上，并且中间没有敌子，加分
        int bonus = 0;
        int r1 = p1 / 19, c1 = p1 % 19;
        int r2 = p2 / 19, c2 = p2 % 19;

        int dr = r2 - r1;
        int dc = c2 - c1;

        // 归一化方向
        if (Math.abs(dr) == Math.abs(dc) || dr == 0 || dc == 0) {
            // 在一直线上，且距离小于 5 (isConnected 保证了距离)
            // 这意味着它们在构筑同一个杀招
            bonus += 5000;
        }
        return bonus;
    }

    /**
     * 单点评估函数：评估在此处落子能形成的棋型
     */
    private int evaluatePoint(int p, PieceColor color) {
        int r = p / 19, c = p % 19;
        int totalScore = 0;

        for (int[] d : directions) {
            // 扫描该方向的棋型
            totalScore += evaluateDirection(r, c, d[0], d[1], color);
        }
        return totalScore;
    }

    private int evaluateDirection(int r, int c, int dr, int dc, PieceColor color) {
        // 这里的逻辑必须非常细腻
        // 我们统计：连子数、跳连数、两端是否被堵

        int count = 1;
        int openEnds = 0;

        // 向前向后扫描，直到碰到边界、敌子或第二个空位
        // 这里简化为：连续扫描

        // 正向
        int k = 1;
        while (isValid(r + dr * k, c + dc * k) && this.board.get((r + dr * k) * 19 + (c + dc * k)) == color) {
            count++;
            k++;
        }
        if (isValid(r + dr * k, c + dc * k) && this.board.get((r + dr * k) * 19 + (c + dc * k)) == PieceColor.EMPTY)
            openEnds++;

        // 反向
        k = 1;
        while (isValid(r - dr * k, c - dc * k) && this.board.get((r - dr * k) * 19 + (c - dc * k)) == color) {
            count++;
            k++;
        }
        if (isValid(r - dr * k, c - dc * k) && this.board.get((r - dr * k) * 19 + (c - dc * k)) == PieceColor.EMPTY)
            openEnds++;

        // 评分 (V14 的核心调整)
        if (count >= 6) return SCORE_WIN;
        if (count == 5) return SCORE_MAKE_5;
        if (count == 4) {
            if (openEnds == 2) return SCORE_LIVE_4; // 活四 (极大威胁)
            if (openEnds == 1) return SCORE_RUSH_4; // 冲四
            return 0; // 死四
        }
        if (count == 3) {
            if (openEnds == 2) return SCORE_LIVE_3; // 活三 (重要)
            if (openEnds == 1) return SCORE_SLEEP_3;
            return 0;
        }
        if (count == 2) {
            if (openEnds == 2) return SCORE_LIVE_2;
        }
        return 1; // 聊胜于无
    }

    // --- 威胁模拟 ---
    private int simulateThreatLevel(int pos, PieceColor opp) {
        int r = pos / 19, c = pos % 19;
        for (int[] d : directions) {
            int count = 1;
            int k = 1;
            while (isValid(r + d[0] * k, c + d[1] * k) && this.board.get((r + d[0] * k) * 19 + c + d[1] * k) == opp) {
                count++;
                k++;
            }
            k = 1;
            while (isValid(r - d[0] * k, c - d[1] * k) && this.board.get((r - d[0] * k) * 19 + c - d[1] * k) == opp) {
                count++;
                k++;
            }
            if (count >= 6) return 2; // 必杀
            if (count == 5) return 1; // 必死
        }
        return 0;
    }

    // --- 辅助方法 ---

    private Move safeReturn(Move move) {
        try {
            if (move == null) move = getFallbackMove();
            boolean isStart = (myColor == PieceColor.BLACK && getBoardStoneCount() == 0);

            if (!isStart && move.index2() == -1) {
                int p2 = findBestSingleAttack(myColor, move.index1());
                move = new Move(move.index1(), p2);
            }
            int p1 = move.index1();
            int p2 = move.index2();
            if (!isSpotEmpty(p1)) p1 = getAnyEmpty(-1);
            if (!isStart && (!isSpotEmpty(p2) || p2 == p1)) p2 = getAnyEmpty(p1);
            Move finalMove = isStart ? new Move(p1, -1) : new Move(p1, p2);
            this.board.makeMove(finalMove);
            return finalMove;
        } catch (Exception e) {
            int r1 = getAnyEmpty(-1);
            Move panic = new Move(r1, getAnyEmpty(r1));
            try {
                this.board.makeMove(panic);
            } catch (Exception ignore) {
            }
            return panic;
        }
    }

    private Move findWinningMove(PieceColor color) {
        List<Integer> cands = getInterestingPoints();
        int limit = Math.min(cands.size(), 15);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                int p1 = cands.get(i);
                int p2 = cands.get(j);
                this.board.makeMove(new Move(p1, p2));
                boolean win = checkWin(color);
                this.board.undo();
                if (win) return new Move(p1, p2);
            }
        }
        return null;
    }

    private int findBestSingleAttack(PieceColor me, int exclude) {
        List<Integer> cands = getInterestingPoints();
        int best = -1;
        int max = -1;
        for (int p : cands) {
            if (p == exclude) continue;
            int s = evaluatePoint(p, me);
            if (s > max) {
                max = s;
                best = p;
            }
        }
        return best != -1 ? best : getAnyEmpty(exclude);
    }

    private List<Integer> getInterestingPoints() {
        Set<Integer> points = new HashSet<>();
        boolean hasStone = false;
        for (int i = 0; i < 361; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) {
                hasStone = true;
                int r = i / 19, c = i % 19;
                for (int dr = -2; dr <= 2; dr++)
                    for (int dc = -2; dc <= 2; dc++)
                        if (isValid(r + dr, c + dc) && this.board.get((r + dr) * 19 + c + dc) == PieceColor.EMPTY)
                            points.add((r + dr) * 19 + c + dc);
            }
        }
        if (!hasStone) {
            points.add(180);
            points.add(181);
        }
        return new ArrayList<>(points);
    }

    private Move getFallbackMove() {
        return new Move(getAnyEmpty(-1), getAnyEmpty(getAnyEmpty(-1)));
    }

    private boolean isSpotEmpty(int idx) {
        return idx >= 0 && idx < 361 && this.board.get(idx) == PieceColor.EMPTY;
    }

    private int getAnyEmpty(int ex) {
        for (int i = 0; i < 361; i++) if (this.board.get(i) == PieceColor.EMPTY && i != ex) return i;
        return -1;
    }

    private boolean checkWin(PieceColor c) {
        for (int i = 0; i < 361; i++)
            if (this.board.get(i) == c) {
                int r = i / 19, col = i % 19;
                for (int[] d : directions) {
                    int cnt = 1;
                    for (int k = 1; k < 6; k++)
                        if (isValid(r + d[0] * k, col + d[1] * k) && this.board.get((r + d[0] * k) * 19 + col + d[1] * k) == c)
                            cnt++;
                        else break;
                    if (cnt >= 6) return true;
                }
            }
        return false;
    }

    private PieceColor getOpponent(PieceColor c) {
        return c == PieceColor.BLACK ? PieceColor.WHITE : PieceColor.BLACK;
    }

    private boolean isValidMove(Move m) {
        return m != null && m.index1() != -1;
    }

    private boolean isValid(int r, int c) {
        return r >= 0 && r < 19 && c >= 0 && c < 19;
    }

    private boolean isConnected(int p1, int p2) {
        return Math.abs(p1 / 19 - p2 / 19) <= 2 && Math.abs(p1 % 19 - p2 % 19) <= 2;
    }

    private int getBoardStoneCount() {
        int c = 0;
        for (int i = 0; i < 361; i++) if (this.board.get(i) != PieceColor.EMPTY) c++;
        return c;
    }
}