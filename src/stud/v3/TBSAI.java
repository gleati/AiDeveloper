package stud.v3;

import core.board.PieceColor;
import core.game.Move;
import stud.v2.AlphaBetaAI;
import java.util.List;

/**
 * V3阶段：威胁空间搜索AI
 */
public class TBSAI extends AlphaBetaAI {
    
    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);

        if (myColor == null) {
            if (opponentMove == null || opponentMove.index1() == -1) {
                myColor = PieceColor.BLACK;
            } else {
                myColor = PieceColor.WHITE;
            }
        }

        List<Integer> myThreats = findThreats(myColor);
        if (myThreats.size() >= 2) {
            Move move = new Move(myThreats.get(0), myThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        PieceColor opponentColor = (myColor == PieceColor.BLACK) ?
                                    PieceColor.WHITE : PieceColor.BLACK;
        List<Integer> opponentThreats = findThreats(opponentColor);
        if (opponentThreats.size() >= 2) {
            Move move = new Move(opponentThreats.get(0), opponentThreats.get(1));
            this.board.makeMove(move);
            return move;
        }

        Move smartMove = findSmartMove();
        this.board.makeMove(smartMove);
        return smartMove;
    }
    
    @Override
    public String name() {
        return "V3-TBS";
    }
}