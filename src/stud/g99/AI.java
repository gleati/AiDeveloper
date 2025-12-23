package stud.g99;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

/**
 * G99 - 走法3：中心优先策略
 * 两个子的位置均在棋盘中心区域（13×13）随机选择
 * 若连续10次未找到空位，则扩展到整个棋盘（19×19）范围内随机选择
 *
 * @author 项目组
 * @version 1.0
 */
public class AI extends core.player.AI {

    /** 中心区域的起始行/列（3-15，共13行13列） */
    private static final int CENTER_START = 3;
    /** 中心区域的大小 */
    private static final int CENTER_SIZE = 13;
    /** 在中心区域尝试的最大次数 */
    private static final int MAX_CENTER_ATTEMPTS = 10;

    /**
     * 寻找下一步走法
     * 策略：优先在13×13中心区域随机落子，失败10次后扩展到全局
     *
     * @param opponentMove 对手的走法
     * @return 本方的走法
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();

        int index1 = findInCenter(rand);
        int index2 = findInCenter(rand);

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    /**
     * 在中心区域优先查找空位，失败后扩展到全局
     * 中心区域：行列范围都是[3, 15]，共13×13=169个位置
     *
     * @param rand 随机数生成器
     * @return 空位的索引（0-360）
     */
    private int findInCenter(Random rand) {
        // 第一阶段：尝试10次在13×13中心区域找空位
        for (int attempt = 0; attempt < MAX_CENTER_ATTEMPTS; attempt++) {
            // 在中心区域随机选择行和列
            int row = CENTER_START + rand.nextInt(CENTER_SIZE);  // 行：3-15
            int col = CENTER_START + rand.nextInt(CENTER_SIZE);  // 列：3-15
            int index = row * 19 + col;  // 转换为一维索引

            // 如果该位置为空，直接返回
            if (this.board.get(index) == PieceColor.EMPTY) {
                return index;
            }
        }

        // 第二阶段：10次失败后，在全局19×19范围内随机查找
        while (true) {
            int index = rand.nextInt(361);
            if (this.board.get(index) == PieceColor.EMPTY) {
                return index;
            }
        }
    }

    /**
     * 获取AI名称
     * @return AI名称
     */
    public String name() {
        return "G99";
    }

    /**
     * 开始新游戏时的初始化
     * @param game 游戏对象
     */
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
