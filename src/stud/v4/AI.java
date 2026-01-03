package stud.v4;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * V4: Hybrid MCTS + Alpha-Beta
 * 核心策略：
 * 1. 开局(前5回合)使用Alpha-Beta剪枝快速搜索
 * 2. 中后期使用MCTS进行深度探索
 * 3. 基于"路"的状态评估，考虑连子数和两端开放性
 * 4. 增量更新棋盘状态，提高效率
 */
public class AI extends core.player.AI {

    // 棋盘尺寸
    private static final int LENGTH = 19;
    private static final int TOTAL = LENGTH * LENGTH;

    // MCTS参数
    private static final int DEPTH = 10;
    private static final double BASE_C = 1.5;
    private static final double A = 0.3;
    private static final double K = 0.2;
    private static final long TIME_LIMIT_MS = 2800; // 时间限制

    // 评估权重表 - 自己的棋型
    private static final long[][] VIGILANCE_SELF = {
            {1, 1, 1}, {1, 1, 1}, {1, 1, 3}, {1, 3, 12}, {1, 100, 10030}, {1, 10080, 10080}
    };
    // 评估权重表 - 对手的棋型
    private static final long[][] VIGILANCE_OPP = {
            {1, 1, 1}, {1, 1, 1}, {1, 1, 2}, {1, 4, 10}, {1, 110, 10100}, {1, 10050, 10100}
    };
    // 跳连权重 - 自己
    private static final long[][] HOPED_SELF = {
            {1, 1, 1}, {1, 1, 1}, {1, 1, 1}, {1, 3, 5}, {1, 115, 120}, {900, 960, 1050}
    };
    // 跳连权重 - 对手
    private static final long[][] HOPED_OPP = {
            {1, 1, 1}, {1, 1, 1}, {1, 1, 1}, {1, 2, 4}, {1, 110, 120}, {900, 940, 1050}
    };

    private static final long WIN_SCORE = 314159265357L;
    private static final double VIGILANCE_LIMIT = 1000.0;
    private static final int[] BREADTH = {2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 12, 12};

    // 四个方向: 横、竖、撇、捺
    private static final int[][] DIR = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    // 玩家类型
    private static final int SELF = 0;
    private static final int OPP = 1;
    private static final int BLANK = 2;
    private static final int OUTSIDE = 3;

    private PieceColor myColor;
    private int turnCount = 0;
    private long startTime;

    // 棋盘状态
    private int[][] boardState;          // 每个位置的玩家
    private State[][][][] allStates;     // 每个位置在4个方向上的状态[x][y][dir][player]
    private long[][][] evaluations;       // 每个位置的评估值[x][y][player]
    private TreeSet<MCTSMove> moveSet;   // 候选移动集合

    public AI() {
        this.board = new Board();
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
        this.myColor = null;
        this.turnCount = 0;
        initializeBoard();
    }

    @Override
    public String name() {
        return "V4-MCTS";
    }

    private void initializeBoard() {
        boardState = new int[LENGTH][LENGTH];
        allStates = new State[LENGTH][LENGTH][4][2];
        evaluations = new long[LENGTH][LENGTH][2];
        moveSet = new TreeSet<>();

        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < LENGTH; y++) {
                boardState[x][y] = BLANK;
                for (int dir = 0; dir < 4; dir++) {
                    allStates[x][y][dir][SELF] = new State(SELF);
                    allStates[x][y][dir][OPP] = new State(OPP);

                    // 初始化边界状态
                    initBorderState(x, y, dir, SELF);
                    initBorderState(x, y, dir, OPP);
                }
                evaluateState(x, y, SELF);
                evaluateState(x, y, OPP);
                moveSet.add(new MCTSMove(x, y, Math.max(evaluations[x][y][SELF], evaluations[x][y][OPP])));
            }
        }
    }

    private void initBorderState(int x, int y, int dir, int player) {
        State state = allStates[x][y][dir][player];
        int dx = DIR[dir][0], dy = DIR[dir][1];

        // 左侧
        if (inBoard(x - dx, y - dy)) {
            state.isLenNextBlank[0] = true;
            state.hopedConnectedLen[0] = 1;
            if (inBoard(x - 2 * dx, y - 2 * dy)) {
                state.isHopedLenNextBlank[0] = true;
            }
        }
        // 右侧
        if (inBoard(x + dx, y + dy)) {
            state.isLenNextBlank[1] = true;
            state.hopedConnectedLen[1] = 1;
            if (inBoard(x + 2 * dx, y + 2 * dy)) {
                state.isHopedLenNextBlank[1] = true;
            }
        }
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.draw();
        try {
            startTime = System.currentTimeMillis();

            // 同步对手落子
            if (isValidMove(opponentMove)) {
                this.board.makeMove(opponentMove);
                updateBoard(opponentMove.index1() / LENGTH, opponentMove.index1() % LENGTH, OPP);
                if (opponentMove.index2() != -1) {
                    updateBoard(opponentMove.index2() / LENGTH, opponentMove.index2() % LENGTH, OPP);
                }
            }

            // 确定颜色
            if (myColor == null) {
                myColor = (opponentMove == null || opponentMove.index1() == -1)
                        ? PieceColor.BLACK : PieceColor.WHITE;
                if (boardState == null) initializeBoard();
            }

            turnCount++;

            // 用于修复的代码
            // 解决“失忆症”：每一回合前，遍历棋盘，确保内部 boardState 与实际 board 完全一致
            // 这能修复因异常或初始化顺序导致的状态丢失
            for (int i = 0; i < TOTAL; i++) {
                int x = i / LENGTH;
                int y = i % LENGTH;
                PieceColor p = this.board.get(i); // 获取实际棋盘状态

                // 将实际颜色转换为 AI 内部的 SELF/OPP/BLANK
                int realState = BLANK;
                if (p == myColor) {
                    realState = SELF;
                } else if (p != PieceColor.EMPTY) {
                    realState = OPP;
                }

                // 如果发现记忆偏差（AI以为是空的，实际上有子），强制更新
                if (boardState[x][y] != realState) {
                    updateBoard(x, y, realState);
                }
            }

            // 开局天元
            if (myColor == PieceColor.BLACK && getBoardStoneCount() == 0) {
                Move start = new Move(LENGTH / 2 * LENGTH + LENGTH / 2, -1);
                applyMove(start);
                return start;
            }

            Move bestMove;
            if (turnCount <= 4) {
                // 前几回合使用Alpha-Beta
                bestMove = alphaBetaSearch();
            } else {
                // 后续使用MCTS
                bestMove = mctsSearch();
            }

            // ==================== [可视化日志 - 修正版] ====================
            // 修正：根据你的反馈，Board存储格式为 board[Col][Row] (即 board[x][y])
            // index / LENGTH = x (列/Col)
            // index % LENGTH = y (行/Row)

            int c1 = bestMove.index1() / LENGTH; // Column (X)
            int r1 = bestMove.index1() % LENGTH; // Row (Y)

            int c2 = bestMove.index2() / LENGTH;
            int r2 = bestMove.index2() % LENGTH;

            // 这里的参数顺序调整为 (row, col) 传给转换函数
            String visualP1 = (bestMove.index1() >= 0) ? toVisualCoords(r1, c1) : "PASS";
            String visualP2 = (bestMove.index2() >= 0) ? toVisualCoords(r2, c2) : "PASS";

            System.err.println(String.format("=== [V4 Turn %d] Decision Log ===", turnCount));
            System.err.println(String.format("Strategy: %s", (turnCount <= 4 ? "Alpha-Beta" : "MCTS")));
            // 打印格式: 形象坐标 [Raw: (列x, 行y)]
            System.err.println(String.format("Move 1  : %-7s [Raw: (%d, %d)]", visualP1, c1, r1));
            System.err.println(String.format("Move 2  : %-7s [Raw: (%d, %d)]", visualP2, c2, r2));
            System.err.println("========================================");
            // ==========================================================

            return safeReturn(bestMove);

        } catch (Throwable e) {
            e.printStackTrace();
            return safeReturn(getFallbackMove());
        }
    }

    /**
     * 将坐标转换为围棋/六子棋标准记法
     * @param row (Y) 0-18, 其中0是棋盘最上方
     * @param col (X) 0-18, 其中0是棋盘最左方
     * @return 格式如 (12, J)
     */
    private String toVisualCoords(int row, int col) {
        // 1. 计算行号: 0 -> 19, 18 -> 1
        int visualRow = 19 - row;

        // 2. 计算列字母: A-T, 跳过 'I'
        char visualCol;
        if (col < 8) {
            // 0-7 对应 A-H
            visualCol = (char) ('A' + col);
        } else {
            // 8-18 对应 J-T (跳过I, 所以+1)
            visualCol = (char) ('A' + col + 1);
        }

        // 返回格式: (12, J)
        return String.format("(%d, %c)", visualRow, visualCol);
    }

    // ==================== MCTS 实现 ====================

    private Move mctsSearch() {
        Node root = new Node(SELF, null, null, null);

        while (!isTimeout()) {
            mcts(root, DEPTH);
        }

        if (root.children.isEmpty()) {
            return getFallbackMove();
        }

        // 选择访问次数最多的节点
        Node best = Collections.max(root.children, Comparator.comparingInt(n -> n.visitedTimes));
        int p1 = best.move1.x * LENGTH + best.move1.y;
        int p2 = best.move2.x * LENGTH + best.move2.y;
        return new Move(p1, p2);
    }

    private int mcts(Node node, int depth) {
        double dynamicC = A * Math.exp(-K * depth);
        int nodePlayer = node.player;

        if (node.visitedTimes == 0) {
            // 扩展节点
            if (node.end == BLANK && depth > 0) {
                expandNode(node, nodePlayer, depth, dynamicC);
            }
        }

        if (node.children.isEmpty()) {
            node.updateNode(node.end);
            return node.end;
        }

        // UCB选择
        Node decision = selectChild(node, dynamicC);

        // 模拟落子
        updateBoard(decision.move1.x, decision.move1.y, nodePlayer);
        updateBoard(decision.move2.x, decision.move2.y, nodePlayer);

        int loser = mcts(decision, depth - 1);

        // 撤销落子
        updateBoard(decision.move2.x, decision.move2.y, BLANK);
        updateBoard(decision.move1.x, decision.move1.y, BLANK);

        node.updateNode(loser);
        return loser;
    }

    private void expandNode(Node node, int nodePlayer, int depth, double dynamicC) {
        List<MCTSMove> moves1 = getTopMoves(depth);
        Set<Long> visited = new HashSet<>();

        for (int i = 0; i < moves1.size(); i++) {
            MCTSMove move1 = moves1.get(i);

            if (boardState[move1.x][move1.y] != BLANK) continue;

            // 检查是否直接获胜
// 修复 2: expandNode 中确保 move2 有效
            if (evaluations[move1.x][move1.y][nodePlayer] >= WIN_SCORE) {
                MCTSMove move2 = null;
                // 找一个有效的第二步
                for (int k = 0; k < moves1.size(); k++) {
                    if (k != i && boardState[moves1.get(k).x][moves1.get(k).y] == BLANK) {
                        move2 = moves1.get(k);
                        break;
                    }
                }
                if (move2 == null) move2 = move1; // 极端情况

                Node winChild = new Node(nodePlayer ^ 1, move1, move2, node);
                winChild.end = nodePlayer;
                node.children.add(winChild);
                break;
            }

            updateBoard(move1.x, move1.y, nodePlayer);

            int count = 0;
            boolean win = false;
            int breadth = depth < BREADTH.length ? BREADTH[depth] : BREADTH[BREADTH.length - 1];

            for (MCTSMove move2 : moveSet) {
                long key = Math.min(move1.x * LENGTH + move1.y, move2.x * LENGTH + move2.y) * TOTAL +
                        Math.max(move1.x * LENGTH + move1.y, move2.x * LENGTH + move2.y);

                if (visited.add(key)) {
                    count++;
                    if (count > (breadth - i) / 2 + 1) break;

                    if (evaluations[move2.x][move2.y][nodePlayer] >= WIN_SCORE) {
                        win = true;
                        Node winChild = new Node(nodePlayer ^ 1, move1, move2, node);
                        winChild.end = nodePlayer;
                        node.children.add(winChild);
                        break;
                    } else {
                        node.children.add(new Node(nodePlayer ^ 1, move1, move2, node));
                    }
                }
            }

            updateBoard(move1.x, move1.y, BLANK);
            if (win) break;
        }
    }

    private Node selectChild(Node node, double dynamicC) {
        Node decision = null;
        double maxUCB = -1;

        for (Node child : node.children) {
            if (child.visitedTimes == 0) {
                return child;
            }
            double ucb = (double) child.winTimes / child.visitedTimes +
                    dynamicC * Math.sqrt(Math.log(node.visitedTimes) / child.visitedTimes);
            if (ucb > maxUCB) {
                maxUCB = ucb;
                decision = child;
            }
        }
        return decision;
    }

    private List<MCTSMove> getTopMoves(int depth) {
        List<MCTSMove> moves = new ArrayList<>();
        double minWeight = Math.min(VIGILANCE_LIMIT, Math.sqrt(moveSet.isEmpty() ? 1 : moveSet.first().weight));
        int breadth = depth < BREADTH.length ? BREADTH[depth] : BREADTH[BREADTH.length - 1];

        for (MCTSMove move : moveSet) {
            if (moves.size() >= Math.max(2, breadth / 2) &&
                    (move.weight < minWeight || moves.size() >= breadth)) {
                break;
            }
            if (boardState[move.x][move.y] == BLANK) {
                moves.add(move);
            }
        }
        return moves;
    }

    // ==================== Alpha-Beta 实现 ====================

    private Move alphaBetaSearch() {
        List<int[]> candidates = getAlphaBetaCandidates();
        if (candidates.size() < 2) {
            return getFallbackMove();
        }

        int[] bestMove = new int[4];
        int bestScore = Integer.MIN_VALUE;

        int limit = Math.min(candidates.size(), 15);
        for (int i = 0; i < limit; i++) {
            int[] p1 = candidates.get(i);
            if (boardState[p1[0]][p1[1]] != BLANK) continue;

            updateBoard(p1[0], p1[1], SELF);

            for (int j = i + 1; j < limit; j++) {
                int[] p2 = candidates.get(j);
                if (boardState[p2[0]][p2[1]] != BLANK) continue;

                updateBoard(p2[0], p2[1], SELF);

                int score = -alphaBeta(Integer.MIN_VALUE + 1, Integer.MAX_VALUE, OPP, 2);

                updateBoard(p2[0], p2[1], BLANK);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove[0] = p1[0];
                    bestMove[1] = p1[1];
                    bestMove[2] = p2[0];
                    bestMove[3] = p2[1];
                }

                if (isTimeout()) break;
            }

            updateBoard(p1[0], p1[1], BLANK);
            if (isTimeout()) break;
        }

        return new Move(bestMove[0] * LENGTH + bestMove[1], bestMove[2] * LENGTH + bestMove[3]);
    }

    private int alphaBeta(int alpha, int beta, int player, int depth) {
        if (isTimeout() || depth == 0) {
            return evaluateBoard(player);
        }

        List<int[]> candidates = getAlphaBetaCandidates();
        int limit = Math.min(candidates.size(), 11);

        for (int i = 0; i < limit; i++) {
            int[] p1 = candidates.get(i);
            if (boardState[p1[0]][p1[1]] != BLANK) continue;

            updateBoard(p1[0], p1[1], player);

            for (int j = i; j < limit; j++) {
                int[] p2 = candidates.get(j);
                if (boardState[p2[0]][p2[1]] != BLANK) continue;

                updateBoard(p2[0], p2[1], player);

                int value = -alphaBeta(-beta, -alpha, player ^ 1, depth - 1);

                updateBoard(p2[0], p2[1], BLANK);

                if (value >= beta) {
                    updateBoard(p1[0], p1[1], BLANK);
                    return beta;
                }
                if (value > alpha) {
                    alpha = value;
                }
            }

            updateBoard(p1[0], p1[1], BLANK);
        }

        return alpha;
    }

    private List<int[]> getAlphaBetaCandidates() {
        List<int[]> candidates = new ArrayList<>();
        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < LENGTH; y++) {
                if (boardState[x][y] == BLANK && hasNeighbor(x, y)) {
                    long score = evaluations[x][y][SELF] + evaluations[x][y][OPP];
                    candidates.add(new int[]{x, y, (int) Math.min(score, Integer.MAX_VALUE)});
                }
            }
        }
        candidates.sort((a, b) -> b[2] - a[2]);
        return candidates;
    }

    private boolean hasNeighbor(int x, int y) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBoard(nx, ny) && boardState[nx][ny] != BLANK) {
                    return true;
                }
            }
        }
        return false;
    }

    private int evaluateBoard(int player) {
        int score = 0;
        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < LENGTH; y++) {
                if (boardState[x][y] == player) {
                    score += evaluatePointForAB(x, y, player);
                }
            }
        }
        return score;
    }

    private int evaluatePointForAB(int x, int y, int player) {
        int score = 0;
        for (int dir = 0; dir < 4; dir++) {
            State state = allStates[x][y][dir][player];
            int totalLen = state.connectedLen[0] + state.connectedLen[1] + 1;
            int openEnds = (state.isLenNextBlank[0] ? 1 : 0) + (state.isLenNextBlank[1] ? 1 : 0);

            if (totalLen >= 6) return 10000000;
            if (totalLen == 5) score += 1000000;
            else if (totalLen == 4) score += (openEnds == 2 ? 100000 : 10000);
            else if (totalLen == 3) score += (openEnds == 2 ? 5000 : 500);
            else if (totalLen == 2) score += (openEnds == 2 ? 100 : 10);
        }
        return score;
    }

    // ==================== 棋盘更新 ====================

    // 修复 1: 使用正确的数据结构存储位置
    private void updateBoard(int x, int y, int player) {
        if (x < 0 || x >= LENGTH || y < 0 || y >= LENGTH) return;

        int currentPlayer = boardState[x][y];
        if (currentPlayer == player) return;

        // 从moveSet中移除旧的
        if (currentPlayer == BLANK) {
            moveSet.remove(new MCTSMove(x, y, Math.max(evaluations[x][y][SELF], evaluations[x][y][OPP])));
        }

        boardState[x][y] = player;

        // 修复：使用 Set<Long> 或 Set<Integer> 存储位置编码
        Set<Integer> changedPositions = new HashSet<>();

        for (int dir = 0; dir < 4; dir++) {
            for (int p = 0; p < 2; p++) {
                State currentState = allStates[x][y][dir][p];
                for (int lr = 0; lr < 2; lr++) {
                    int dx = (lr == 1) ? DIR[dir][0] : -DIR[dir][0];
                    int dy = (lr == 1) ? DIR[dir][1] : -DIR[dir][1];

                    int maxDist = currentState.hopedConnectedLen[lr] +
                            (currentState.isHopedLenNextBlank[lr] ? 1 : 0) + 1;

                    int nx = x + dx, ny = y + dy;
                    for (int k = 1; k < maxDist && inBoard(nx, ny); k++) {
                        if (boardState[nx][ny] == BLANK) {
                            changedPositions.add(nx * LENGTH + ny); // 修复：使用整数编码
                        }
                        int prevX = nx - dx, prevY = ny - dy;
                        if (inBoard(prevX, prevY)) {
                            allStates[nx][ny][dir][p].updateState(
                                    allStates[prevX][prevY][dir][p],
                                    boardState[prevX][prevY],
                                    1 - lr
                            );
                        }
                        nx += dx;
                        ny += dy;
                    }
                }
            }
        }

        // 修复：正确遍历位置
        for (int pos : changedPositions) {
            int px = pos / LENGTH;
            int py = pos % LENGTH;
            moveSet.removeIf(m -> m.x == px && m.y == py);
            evaluateState(px, py, SELF);
            evaluateState(px, py, OPP);
            if (boardState[px][py] == BLANK) {
                moveSet.add(new MCTSMove(px, py,
                        Math.max(evaluations[px][py][SELF], evaluations[px][py][OPP])));
            }
        }

        if (player == BLANK) {
            evaluateState(x, y, SELF);
            evaluateState(x, y, OPP);
            moveSet.add(new MCTSMove(x, y, Math.max(evaluations[x][y][SELF], evaluations[x][y][OPP])));
        }
    }

    private void evaluateState(int x, int y, int player) {
        long value = 1;
        for (int dir = 0; dir < 4; dir++) {
            long weight = allStates[x][y][dir][player].evaluateWeight(player);
            if (weight >= WIN_SCORE) {
                value = WIN_SCORE;
                break;
            }
            value *= weight;
        }
        evaluations[x][y][player] = value;
    }

    // ==================== 辅助类和方法 ====================

    private static class State {
        int player;
        int[] connectedLen = new int[2];      // 左右两侧连续同色棋子数
        int[] hopedConnectedLen = new int[2]; // 包含一个空位的连续长度
        boolean[] isLenNextBlank = new boolean[2];      // 连续棋子后是否为空
        boolean[] isHopedLenNextBlank = new boolean[2]; // 跳连后是否为空

        State(int player) {
            this.player = player;
        }

        void updateState(State neighbour, int neighbourPlayer, int lr) {
            if (neighbourPlayer == BLANK) {
                connectedLen[lr] = 0;
                hopedConnectedLen[lr] = neighbour.connectedLen[lr] + 1;
                isLenNextBlank[lr] = true;
                isHopedLenNextBlank[lr] = neighbour.isLenNextBlank[lr];
            } else if (neighbourPlayer == player) {
                connectedLen[lr] = neighbour.connectedLen[lr] + 1;
                hopedConnectedLen[lr] = neighbour.hopedConnectedLen[lr] + 1;
                isLenNextBlank[lr] = neighbour.isLenNextBlank[lr];
                isHopedLenNextBlank[lr] = neighbour.isHopedLenNextBlank[lr];
            } else {
                connectedLen[lr] = 0;
                hopedConnectedLen[lr] = 0;
                isLenNextBlank[lr] = false;
                isHopedLenNextBlank[lr] = false;
            }
        }

        long evaluateWeight(int evalPlayer) {
            int totalConnectLen = connectedLen[0] + connectedLen[1] + 1;
            if (totalConnectLen >= 6) return WIN_SCORE;

            int leftHopedLen = Math.min(5, hopedConnectedLen[0] + connectedLen[1]);
            int rightHopedLen = Math.min(5, hopedConnectedLen[1] + connectedLen[0]);
            int openEnds = (isLenNextBlank[0] ? 1 : 0) + (isLenNextBlank[1] ? 1 : 0);
            int leftOpenEnds = (isLenNextBlank[1] ? 1 : 0) + (isHopedLenNextBlank[0] ? 1 : 0);
            int rightOpenEnds = (isLenNextBlank[0] ? 1 : 0) + (isHopedLenNextBlank[1] ? 1 : 0);

            long[][] vigilance = (evalPlayer == SELF) ? VIGILANCE_SELF : VIGILANCE_OPP;
            long[][] hoped = (evalPlayer == SELF) ? HOPED_SELF : HOPED_OPP;

            return Math.max(Math.max(
                            vigilance[Math.min(5, totalConnectLen)][openEnds],
                            hoped[leftHopedLen][Math.min(2, leftOpenEnds)]),
                    hoped[rightHopedLen][Math.min(2, rightOpenEnds)]);
        }
    }

    private static class MCTSMove implements Comparable<MCTSMove> {
        int x, y;
        long weight;
        int distanceFromCenter;

        MCTSMove(int x, int y, long weight) {
            this.x = x;
            this.y = y;
            this.weight = weight;
            int center = LENGTH / 2;
            this.distanceFromCenter = (Math.abs(x - center) + 1) * (Math.abs(y - center) + 1);
        }

        @Override
        public int compareTo(MCTSMove o) {
            if (weight != o.weight) return Long.compare(o.weight, weight);
            if (distanceFromCenter != o.distanceFromCenter)
                return Integer.compare(distanceFromCenter, o.distanceFromCenter);
            return (x == o.x) ? Integer.compare(y, o.y) : Integer.compare(x, o.x);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MCTSMove)) return false;
            MCTSMove o = (MCTSMove) obj;
            return x == o.x && y == o.y;
        }

        @Override
        public int hashCode() {
            return x * LENGTH + y;
        }
    }

    private static class Node {
        int player;
        int end = BLANK;
        int visitedTimes = 0;
        int winTimes = 0;
        MCTSMove move1, move2;
        Node parent;
        List<Node> children = new ArrayList<>();

        Node(int player, MCTSMove move1, MCTSMove move2, Node parent) {
            this.player = player;
            this.move1 = move1;
            this.move2 = move2;
            this.parent = parent;
        }

        void updateNode(int loser) {
            visitedTimes += 2;
            if (loser == BLANK) {
                winTimes++;
            } else if (loser == (player ^ 1)) {
                winTimes += 2;
            }
        }
    }

    private Move safeReturn(Move move) {
        try {
            if (move == null) move = getFallbackMove();
            applyMove(move);
            return move;
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

    private void applyMove(Move move) {
        this.board.makeMove(move);
        if (move.index1() >= 0) {
            updateBoard(move.index1() / LENGTH, move.index1() % LENGTH, SELF);
        }
        if (move.index2() >= 0) {
            updateBoard(move.index2() / LENGTH, move.index2() % LENGTH, SELF);
        }
    }

    private Move getFallbackMove() {
        int p1 = getAnyEmpty(-1);
        int p2 = getAnyEmpty(p1);
        return new Move(p1, p2);
    }

    private int getAnyEmpty(int exclude) {
        for (int i = 0; i < TOTAL; i++) {
            if (this.board.get(i) == PieceColor.EMPTY && i != exclude) return i;
        }
        return -1;
    }

    private boolean inBoard(int x, int y) {
        return x >= 0 && x < LENGTH && y >= 0 && y < LENGTH;
    }

    private boolean isValidMove(Move m) {
        return m != null && m.index1() >= 0;
    }

    private boolean isTimeout() {
        return System.currentTimeMillis() - startTime > TIME_LIMIT_MS;
    }

    private int getBoardStoneCount() {
        int c = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) c++;
        }
        return c;
    }
}
