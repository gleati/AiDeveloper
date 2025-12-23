package stud.v3;

import core.board.PieceColor;
import core.game.Move;
import stud.v2.AlphaBetaAI;
import java.util.List;

/**
 * V3阶段：威胁空间搜索AI (Threat-Based Search AI)
 *
 * <p>核心策略：</p>
 * <ol>
 *   <li>优先检测己方双威胁点（能形成两个或以上威胁的位置）</li>
 *   <li>其次防守对方双威胁点</li>
 *   <li>最后使用V2的评估函数选择最佳走法</li>
 * </ol>
 *
 * <p>威胁定义：某位置落子后，能在某方向形成3个或以上连子</p>
 *
 * @author 项目组
 * @version 3.0
 */
public class TBSAI extends AlphaBetaAI {
    
    /**
     * 寻找下一步最佳走法
     *
     * <p>决策流程：</p>
     * <ol>
     *   <li>应用对手走法到棋盘</li>
     *   <li>初始化己方颜色（首次调用时）</li>
     *   <li>检查己方是否有双威胁点 → 有则立即落子</li>
     *   <li>检查对方是否有双威胁点 → 有则防守</li>
     *   <li>使用父类V2的智能评估选择走法</li>
     * </ol>
     *
     * @param opponentMove 对手的走法，首次调用时为null
     * @return 本方的走法（包含两个落子位置）
     */
    @Override
    public Move findNextMove(Move opponentMove) {
        // 1. 应用对手走法
        this.board.makeMove(opponentMove);

        // 2. 初始化颜色（首次调用）
        if (myColor == null) {
            if (opponentMove == null || opponentMove.index1() == -1) {
                myColor = PieceColor.BLACK;  // 先手为黑方
            } else {
                myColor = PieceColor.WHITE;  // 后手为白方
            }
        }

        // 3. 检查己方双威胁点（进攻优先）
        List<Integer> myThreats = findThreats(myColor);
        if (myThreats.size() >= 2) {
            // 找到双威胁，立即落子形成必胜局面
            Move move = new Move(myThreats.get(0), myThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        // 4. 检查对方双威胁点（防守）
        PieceColor opponentColor = (myColor == PieceColor.BLACK) ?
                                    PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> opponentThreats = findThreats(opponentColor);
        if (opponentThreats.size() >= 2) {
            // 对方有双威胁，必须防守
            Move move = new Move(opponentThreats.get(0), opponentThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        // 5. 无威胁情况，使用V2的智能评估
        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }
    
    /**
     * 获取AI名称
     *
     * @return AI标识名称
     */
    @Override
    public String name() {
        return "V3-TBS";
    }
}