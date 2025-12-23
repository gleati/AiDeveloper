package stud.v1;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import core.player.AI;

import java.util.ArrayList;
import java.util.List;

/**
 * V1阶段：基础智能AI
 * 功能：(1)检测己方胜着 (2)防守对方威胁 (3)智能选点
 * 
 * @author 项目组
 * @version 1.0
 */
public class SmartAI extends AI {
    /** 四个主要方向：横、竖、主对角、副对角 */
    private static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};
    
    /** 当前玩家颜色 */
    protected PieceColor myColor;

    /** 是否已初始化颜色 */
    private boolean colorInitialized = false;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        
        // 首次调用时初始化颜色
        if (!colorInitialized) {
            initColor(opponentMove);
        }

        // 1. 检查己方是否有胜着
        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            this.board.makeMove(winMove);
            return winMove;
        }
        
        // 2. 检查对方是否有威胁，需要防守
        Move blockMove = findBlockingMove();
        if (blockMove != null) {
            this.board.makeMove(blockMove);
            return blockMove;
        }
        
        // 3. 智能选点：选择战略位置
        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }
    
    /**
     * 根据对手走法初始化己方颜色
     */
    private void initColor(Move opponentMove) {
        if (opponentMove == null || opponentMove.index1() == -1) {
            // 对手没有走法，说明我方先手，为黑方
            myColor = PieceColor.BLACK;
        } else {
            // 对手已走，我方后手，为白方
            myColor = PieceColor.WHITE;
        }
        colorInitialized = true;
    }

    /**
     * 查找能够获胜的走法
     */
    protected Move findWinningMove(PieceColor color) {
        List<Integer> threats = findThreats(color);
        if (threats.size() >= 2) {
            return new Move(threats.get(0), threats.get(1));
        }
        return null;
    }
    
    /**
     * 查找阻挡对方威胁的走法
     */
    private Move findBlockingMove() {
        PieceColor opponentColor = (myColor == PieceColor.BLACK) ?
                                    PieceColor.WHITE : PieceColor.BLACK;

        List<Integer> threats = findThreats(opponentColor);
        if (threats.size() >= 2) {
            return new Move(threats.get(0), threats.get(1));
        }
        
        return null;
    }
    
    /**
     * 查找威胁点
     */
    protected List<Integer> findThreats(PieceColor color) {
        List<Integer> threats = new ArrayList<>();

        for (int i = 0; i < 361; i++) {
            if (board.get(i) != PieceColor.EMPTY) continue;
            
            int row = i / 19;
            int col = i % 19;

            for (int[] dir : DIRECTIONS) {
                int count = countLine(row, col, dir[0], dir[1], color);
                if (count >= 3) {
                    threats.add(i);
                    break;
                }
            }
        }
        
        return threats;
    }
    
    /**
     * 智能选点
     */
    protected Move findSmartMove() {
        List<Integer> empty = getEmptyPositions();
        List<Integer> center = new ArrayList<>();
        for (int pos : empty) {
            int row = pos / 19;
            int col = pos % 19;
            if (row >= 6 && row <= 12 && col >= 6 && col <= 12) {
                center.add(pos);
            }
        }
        if (center.size() >= 2) {
            return new Move(center.get(0), center.get(1));
        }
        if (empty.size() >= 2) {
            return new Move(empty.get(0), empty.get(1));
        }
        return new Move(empty.get(0), empty.get(0));
    }

    /**
     * 统计某方向上的连子数
     */
    protected int countLine(int row, int col, int dr, int dc, PieceColor color) {
        int count = 0;

        for (int i = 0; i < 6; i++) {
            int r = row + dr * i;
            int c = col + dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) {
                count++;
            } else {
                break;
            }
        }

        for (int i = 1; i < 6; i++) {
            int r = row - dr * i;
            int c = col - dc * i;
            if (r < 0 || r >= 19 || c < 0 || c >= 19) break;
            if (board.get(r * 19 + c) == color) {
                count++;
            } else {
                break;
            }
        }
        
        return count;
    }
    
    /**
     * 获取所有空位
     */
    protected List<Integer> getEmptyPositions() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) {
                empty.add(i);
            }
        }
        return empty;
    }

    @Override
    public String name() {
        return "V1-SmartAI";
    }
    
    /**
     * 开始新游戏时的初始化
     *
     * <p>重置棋盘和颜色标志</p>
     *
     * @param game 游戏对象
     */
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        colorInitialized = false;
    }
}