import core.game.Game;
import core.game.GameResult;
import core.game.timer.StopwatchCPU;
import core.game.ui.Configuration;
import core.match.GameEvent;
import core.match.Match;
import core.player.Player;
import stud.v1.AI;

import java.util.ArrayList;

/**
 * 六子棋AI评测程序
 */
public class AITester {
    public static void main(String[] args) {
        StopwatchCPU timer = new StopwatchCPU();
        testAllVersions();
//        zeroCarnival();
//        oneMatch();
        double elapsedTime = timer.elapsedTime();
        System.out.printf("总耗时: %.4f秒\n", elapsedTime);
    }

    // 测试所有版本AI
    private static void testAllVersions() {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
//        players.add(new stud.g77.AI());      // V0-走法2：相邻策略
//        players.add(new stud.g88.AI());      // V0-走法1：完全随机策略
//        players.add(new stud.g99.AI());      // V0-走法3：中心优先策略
        players.add(new stud.v1.AI());
        players.add(new stud.v2.AI()); // V2-博弈树
//        players.add(new stud.v3.TBSAI());    // V3-威胁搜索

        GameEvent event = new GameEvent("AI Evolution Test", players);
        event.carnivalRun(100);
        event.showResults();
    }

    // 走法2 vs 走法3，各500场
    private static void testStrategy2vs3() {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new stud.g77.AI()); // 走法2
        players.add(new stud.g99.AI()); // 走法3

        GameEvent event = new GameEvent("Strategy2 vs Strategy3", players);
        event.carnivalRun(500);
        event.showResults();
    }

    /**
     * 这个用来完成项目二的第二部分内容，随机棋手的测试。
     *
     */
    private static void zeroCarnival(){
        //Zero大狂欢:)
        Configuration.GUI = false; //不是使用GUI

//        //默认生成配置文件中配置的AI棋手列表(根据id生成)
//        GameEvent event = new GameEvent("Carnival of Zeros");

        //使用自己生成的AI棋手列表
        GameEvent event = new GameEvent("Carnival of Zeros", createPlayers());

        //每对棋手下500局棋，先后手各250局
        //n个棋手，共下C(n,2)*500局棋，每个棋手下500*(n-1)局棋
        event.carnivalRun(500);
        event.showResults();
    }

    //生成自己的棋手
    private static ArrayList<Player> createPlayers(){
        ArrayList<Player> players = new ArrayList<>();
        players.add(new stud.g88.AI());
        players.add(new stud.g77.AI());
        players.add(new stud.g99.AI());
        return players;
    }
    //海之子联赛
    private static void oucLeague() throws CloneNotSupportedException {
        Configuration.GUI = true; //使用GUI
        Configuration.STEP_INTER = 300;
        GameEvent event = new GameEvent("海之子排名赛");

        //主场先手与其他棋手对局
        event.hostGames(Configuration.HOST_ID);

        event.showHostResults(Configuration.HOST_ID);
    }
    //自组织一场比赛
    private static void oneMatch(){
        Configuration.GUI = true;
        Configuration.STEP_INTER = 300;
        Player one = new stud.v2.AI();
        Player two = new stud.v1.AI();
        Match match = new Match(10, one, two);
        for (Game game : match.getGames()){
            game.run();
        }

        for (GameResult result : one.gameResults()){
            System.out.println(result);
        }
    }
}

