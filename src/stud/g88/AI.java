package stud.g88;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

/**
 * G88 - 走法1：完全随机策略
 * 两个子的位置均通过随机掷骰子的方式确定，在整个棋盘（19×19）范围内掷骰子
 *
 * @author 项目组
 * @version 1.0
 */
public class AI extends core.player.AI {
    /** 记录已下步数 */
    private int steps = 0;

    /**
     * 寻找下一步走法
     * 策略：在整个19×19棋盘（361个位置）中随机选择两个空位落子
     *
     * @param opponentMove 对手的走法
     * @return 本方的走法
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        // 将对手的走法应用到棋盘上
        this.board.makeMove(opponentMove);

        Random rand = new Random();

        // 循环直到找到两个不同的空位
        while (true) {
            // 在361个位置中随机选择第一个位置
            int index1 = rand.nextInt(361);
            // 在361个位置中随机选择第二个位置
            int index2 = rand.nextInt(361);

            // 检查两个位置是否不同且都为空
            if (index1 != index2 &&
                this.board.get(index1) == PieceColor.EMPTY &&
                this.board.get(index2) == PieceColor.EMPTY) {

                // 创建走法并应用到棋盘
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                steps++;
                return move;
            }
        }
    }

    /**
     * 获取AI名称
     * @return AI名称
     */
    @Override
    public String name() {
        return "G88";
    }

    /**
     * 开始新游戏时的初始化
     * @param game 游戏对象
     */
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        steps = 0;
    }
}
