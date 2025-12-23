package stud.g77;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.Random;

/**
 * G77 - 走法2：相邻策略
 * 第一个子随机选择，第二个子下在与第一个子相邻的空位上
 * 相邻空位最多8个，随机选择一个；若相邻位置都有子，则在整个棋盘上随机选择
 *
 * @author 项目组
 * @version 1.0
 */
public class AI extends core.player.AI {
    /** 记录已下步数 */
    private int steps = 0;

    /** 8个方向的行偏移量：左上、上、右上、左、右、左下、下、右下 */
    private static final int[] DX = {-1, -1, -1, 0, 0, 1, 1, 1};
    /** 8个方向的列偏移量：左上、上、右上、左、右、左下、下、右下 */
    private static final int[] DY = {-1, 0, 1, -1, 1, -1, 0, 1};

    /**
     * 寻找下一步走法
     * 策略：第一子随机，第二子优先选择相邻空位，无相邻空位则全局随机
     *
     * @param opponentMove 对手的走法
     * @return 本方的走法
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        // 将对手的走法应用到棋盘上
        this.board.makeMove(opponentMove);
        Random rand = new Random();

        // 第一个子：在整个棋盘随机选择一个空位
        int index1 = findRandomEmpty(rand);
        int row1 = index1 / 19;  // 计算行号
        int col1 = index1 % 19;  // 计算列号

        // 查找第一个子周围的8个相邻空位
        ArrayList<Integer> adjacent = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int newRow = row1 + DX[i];
            int newCol = col1 + DY[i];

            // 检查新位置是否在棋盘范围内
            if (newRow >= 0 && newRow < 19 && newCol >= 0 && newCol < 19) {
                int idx = newRow * 19 + newCol;
                // 如果该位置为空，加入候选列表
                if (this.board.get(idx) == PieceColor.EMPTY) {
                    adjacent.add(idx);
                }
            }
        }

        // 第二个子：优先从相邻空位中随机选择，若无相邻空位则全局随机
        int index2;
        if (!adjacent.isEmpty()) {
            // 从相邻空位中随机选择一个
            index2 = adjacent.get(rand.nextInt(adjacent.size()));
        } else {
            // 相邻位置都被占据，在整个棋盘随机选择
            index2 = findRandomEmpty(rand);
        }

        // 创建走法并应用到棋盘
        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        steps++;
        return move;
    }

    /**
     * 在整个棋盘上随机查找一个空位
     *
     * @param rand 随机数生成器
     * @return 空位的索引（0-360）
     */
    private int findRandomEmpty(Random rand) {
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
        return "G77";
    }

    /**
     * 开始新游戏时的初始化
     * @param game 游戏对象
     */
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new G77Board("G77Board");
        steps = 0;
    }
}
