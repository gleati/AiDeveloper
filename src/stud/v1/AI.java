package stud.v1;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * 六子棋智能AI - 版本1 (V1-SmartAI)
 *
 * 本AI实现了基本的威胁检测和智能走法选择算法，主要特点包括：
 * 1. 威胁检测：能够识别自己和对手的威胁（四连及以上）
 * 2. 优先策略：优先选择获胜走法，其次阻挡对手威胁
 * 3. 智能评估：对空位进行评分，选择最佳的两个位置落子
 * 4. 搜索优化：限制搜索范围以提高效率
 *
 * 算法核心思想：
 * - 使用方向数组检测直线上的棋子连接情况
 * - 通过威胁列表识别关键位置
 * - 结合位置评分和中心优先策略选择落子
 *
 * @author AI开发者
 * @version 1.0
 */
public class AI extends core.player.AI {
    /** 四个主要方向：水平、垂直、两个对角线 */
    protected static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};

    /** 当前AI的棋子颜色 */
    protected PieceColor myColor;

    /** 颜色是否已初始化标志 */
    private boolean colorInitialized = false;

    /**
     * 寻找下一步走法
     *
     * 算法流程：
     * 1. 处理对手的上一步走法
     * 2. 检查是否有获胜走法（形成五连或六连）
     * 3. 检查是否需要阻挡对手的威胁
     * 4. 使用智能评估选择最佳走法
     *
     * @param opponentMove 对手的上一步走法
     * @return 选择的下一步走法
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        // 处理对手走法
        this.board.makeMove(opponentMove);

        // 1. 优先检查是否有获胜走法
        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            return winMove;
        }

        // 2. 检查是否需要阻挡对手的威胁
        Move blockMove = findBlockingMove();
        if (blockMove != null) {
            this.board.makeMove(blockMove);
            return blockMove;
        }

        // 3. 使用智能评估选择最佳走法
        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }

    /**
     * 验证走法是否有效
     *
     * @param move 待验证的走法
     * @return 如果走法不为null且索引有效则返回true
     */
    protected boolean isValidMove(Move move) {
        return move != null && move.index1() != -1;
    }

    /**
     * 初始化AI颜色
     *
     * 根据对手的第一步走法确定AI的颜色：
     * - 如果对手没有走法或走法无效，AI执黑先行
     * - 否则AI执白后行
     *
     * @param opponentMove 对手的第一步走法
     */
    private void initColor(Move opponentMove) {
        myColor = (opponentMove == null || opponentMove.index1() == -1)
                ? PieceColor.BLACK : PieceColor.WHITE;
        colorInitialized = true;
    }

    /**
     * 寻找获胜走法
     *
     * 检查当前颜色是否有形成威胁的位置，如果有两个或以上的威胁位置，
     * 则选择前两个位置作为走法（形成双威胁）
     *
     * @param color 要检查的棋子颜色
     * @return 获胜走法，如果没有则返回null
     */
    protected Move findWinningMove(PieceColor color) {
        List<Integer> threats = findThreats(color);
        return threats.size() >= 2 ? new Move(threats.get(0), threats.get(1)) : null;
    }

    /**
     * 寻找阻挡走法
     *
     * 检查对手是否有威胁，如果有则选择前两个威胁位置进行阻挡
     *
     * @return 阻挡走法，如果没有则返回null
     */
    private Move findBlockingMove() {
        PieceColor opponent = (myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> threats = findThreats(opponent);
        return threats.size() >= 2 ? new Move(threats.get(0), threats.get(1)) : null;
    }

    /**
     * 寻找威胁位置
     *
     * 遍历棋盘上的所有空位，检查每个空位在四个方向上是否能够形成威胁
     * 威胁定义：在某个方向上，从该空位出发，同色棋子连续数量达到4个或以上
     *
     * @param color 要检查的棋子颜色
     * @return 威胁位置列表
     */
    protected List<Integer> findThreats(PieceColor color) {
        List<Integer> threats = new ArrayList<>();

        // 遍历棋盘所有位置（19x19=361个位置）
        for (int i = 0; i < 361; i++) {
            if (board.get(i) != PieceColor.EMPTY) continue;

            int row = i / 19, col = i % 19;

            // 检查四个方向
            for (int[] dir : DIRECTIONS) {
                int count = countLineFromEmpty(row, col, dir[0], dir[1], color);
                // 如果某个方向上有4个或以上同色棋子，则该位置是威胁
                if (count >= 4) {
                    threats.add(i);
                    break; // 一个位置只要在一个方向上有威胁就足够
                }
            }
        }
        return threats;
    }

    /**
     * 从空位开始统计直线上的同色棋子数量
     *
     * 算法：从给定位置向两个相反方向延伸，统计连续的同色棋子数量
     *
     * @param row 起始行
     * @param col 起始列
     * @param dr 行方向增量
     * @param dc 列方向增量
     * @param color 要统计的棋子颜色
     * @return 直线上的同色棋子总数
     */
    protected int countLineFromEmpty(int row, int col, int dr, int dc, PieceColor color) {
        int count = 0;

        // 正向延伸（最多5步，因为六子棋最多需要检查6个位置）
        for (int i = 1; i <= 5; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) count++;
            else break; // 遇到不同颜色或空位则停止
        }

        // 反向延伸
        for (int i = 1; i <= 5; i++) {
            int r = row - dr * i, c = col - dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) count++;
            else break;
        }

        return count;
    }

    /**
     * 寻找智能走法
     *
     * 使用评估函数对空位进行评分，选择评分最高的两个位置作为走法
     * 搜索优化：只考虑前30个空位，减少计算量
     *
     * @return 选择的智能走法
     */
    protected Move findSmartMove() {
        int bestPos1 = -1, bestPos2 = -1;
        int bestScore = -1;

        List<Integer> empty = getEmptyPositions();
        int limit = Math.min(empty.size(), 30); // 限制搜索范围，提高效率

        // 遍历所有空位组合（限制在30个以内）
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                // 计算两个位置的评分总和
                int score = evaluateMove(empty.get(i)) + evaluateMove(empty.get(j));
                if (score > bestScore) {
                    bestScore = score;
                    bestPos1 = empty.get(i);
                    bestPos2 = empty.get(j);
                }
            }
        }

        // 如果没有找到合适位置，返回默认位置（棋盘中心附近）
        return bestPos1 != -1 ? new Move(bestPos1, bestPos2) : new Move(180, 181);
    }

    /**
     * 评估单个位置的得分
     *
     * 评分标准：
     * 1. 进攻价值：自己棋子的连接情况
     *    - 4连及以上：+10000（极高威胁）
     *    - 3连：+500
     *    - 2连：+50
     * 2. 防守价值：对手棋子的连接情况
     *    - 对手4连及以上：+8000（需要阻挡）
     *    - 对手3连：+400
     *    - 对手2连：+40
     * 3. 位置价值：距离中心越近得分越高
     *    - 中心位置（9,9）得分最高
     *
     * @param pos 要评估的位置索引
     * @return 该位置的综合评分
     */
    protected int evaluateMove(int pos) {
        int row = pos / 19, col = pos % 19;
        int myScore = 0, oppScore = 0;
        PieceColor opponent = (myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;

        // 检查四个方向
        for (int[] dir : DIRECTIONS) {
            int myCount = countLineFromEmpty(row, col, dir[0], dir[1], myColor);
            int oppCount = countLineFromEmpty(row, col, dir[0], dir[1], opponent);

            // 计算自己棋子的得分
            if (myCount >= 4) myScore += 10000;
            else if (myCount == 3) myScore += 500;
            else if (myCount == 2) myScore += 50;

            // 计算对手棋子的得分（防守价值）
            if (oppCount >= 4) oppScore += 8000;
            else if (oppCount == 3) oppScore += 400;
            else if (oppCount == 2) oppScore += 40;
        }

        // 计算距离中心的曼哈顿距离，距离越近得分越高
        int distToCenter = Math.abs(row - 9) + Math.abs(col - 9);
        return myScore + oppScore + (18 - distToCenter);
    }

    /**
     * 统计直线上同色棋子数量（countLineFromEmpty的别名）
     *
     * @param row 起始行
     * @param col 起始列
     * @param dr 行方向增量
     * @param dc 列方向增量
     * @param color 要统计的棋子颜色
     * @return 直线上的同色棋子总数
     */
    protected int countLine(int row, int col, int dr, int dc, PieceColor color) {
        return countLineFromEmpty(row, col, dr, dc, color);
    }

    /**
     * 获取所有空位置列表
     *
     * @return 棋盘上所有空位置的索引列表
     */
    protected List<Integer> getEmptyPositions() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) empty.add(i);
        }
        return empty;
    }

    /**
     * 返回AI的名称
     *
     * @return AI名称字符串
     */
    @Override
    public String name() {
        return "V1";
    }

    /**
     * 开始新游戏时的初始化
     *
     * 重置棋盘状态和颜色信息
     *
     * @param game 游戏实例
     */
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        colorInitialized = false;
        myColor = null;
    }
}