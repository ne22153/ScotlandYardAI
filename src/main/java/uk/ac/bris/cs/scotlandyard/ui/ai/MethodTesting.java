package uk.ac.bris.cs.scotlandyard.ui.ai;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;

import static uk.ac.bris.cs.scotlandyard.ui.ai.MyAi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.defaultDetectiveTickets;

public class MethodTesting extends ParameterisedTestBase {

    @Test
    public void testGetMoveDestination() {
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, defaultMrXTickets(), 106),
                new Player(RED, defaultDetectiveTickets(), 42));
        Set<Move> available = gameState.getAvailableMoves();
        MyAi mrX = new MyAi();
        assertThat(mrX.getMoveDestination(available.iterator().next()).equals(105));
    }

    @Test
    public void testDetectivesFar(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, defaultMrXTickets(), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        Map<Integer, Integer> score = new HashMap<>();
        score.put(0, 0);
        assertThat(mrX.stateEvaluation(gameState, 175, score).equals(4));
    }

    @Test
    public void testDetectivesNear(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, defaultMrXTickets(), 175),
                new Player(RED, defaultDetectiveTickets(), 161),
                new Player(GREEN, defaultDetectiveTickets(), 162));
        MyAi mrX = new MyAi();
        Map<Integer, Integer> score = new HashMap<>();
        score.put(0, 0);
        assertThat(mrX.stateEvaluation(gameState, 175, score).equals(2));
    }

    @Test
    public void testTreeInitialiser(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        TreeNode tree = mrX.treeInitialiser(gameState);
        assertThat(tree.children.get(0)
                .children.get(0)
                .children.get(0)
                .children.get(0)
                .children.get(0)
                .LocationAndScore.keySet().iterator().next() == 162);
    }

    @Test
    public void testMinimax(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        TreeNode tree = mrX.treeInitialiser(gameState);
        Map<Integer, Integer> minimax = mrX.minimax(tree, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        assertThat(minimax.get(tree.LocationAndScore.keySet().iterator().next()).equals(3));
    }

    @Test
    public void testTicketMasterTrue(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        assertThat(mrX.ticketmaster(RED, ImmutableSet.of(Transport.BUS), gameState));
    }

    @Test
    public void testTicketMasterFalse(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        assertThat(!mrX.ticketmaster(RED, ImmutableSet.of(Transport.FERRY), gameState));
    }

    @Test
    public void testShortestDistance(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        assertThat(mrX.shortestDistance(175, RED, gameState) == 4);
    }

    @Test
    public void testTicketWeighting(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        TreeNode tree = mrX.treeInitialiser(gameState);
        Map<Integer, Integer> minimax = mrX.minimax(tree, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        Map<Integer, Integer> moves = mrX.treeSearch(tree, minimax.get(tree.LocationAndScore.keySet().iterator().next()));
        List<Move> destMoves = gameState.getAvailableMoves().stream().filter(x -> moves.containsKey(mrX.getMoveDestination(x))).toList();
        List<Move> wantedMoves = new ArrayList<>();
        wantedMoves.add(0, new Move.SingleMove(MRX, 175, Ticket.TAXI, 162));
        wantedMoves.add(1, new Move.SingleMove(MRX, 175, Ticket.SECRET, 162));
        Map<Move, Integer> weighted = mrX.ticketWeighting(gameState, destMoves);
        assertThat(mrX.ticketWeighting(gameState, destMoves).containsKey(new Move.SingleMove(MRX, 175, Ticket.TAXI, 162)));
        assertThat(mrX.ticketWeighting(gameState, destMoves).containsKey(new Move.SingleMove(MRX, 175, Ticket.SECRET, 162)));
        assertThat(mrX.ticketWeighting(gameState, destMoves).containsValue(4));
        assertThat(mrX.ticketWeighting(gameState, destMoves).containsValue(5));
    }

    @Test
    public void testTreeSearch(){
        Board.GameState gameState = gameStateFactory.build(standard24MoveSetup(),
                new Player(MRX, ImmutableMap.of(Ticket.TAXI, 4,
                        Ticket.BUS, 3,
                        Ticket.UNDERGROUND, 3,
                        Ticket.DOUBLE, 0,
                        Ticket.SECRET, 5), 175),
                new Player(RED, defaultDetectiveTickets(), 143),
                new Player(GREEN, defaultDetectiveTickets(), 78));
        MyAi mrX = new MyAi();
        TreeNode tree = mrX.treeInitialiser(gameState);
        Map<Integer, Integer> minimax = mrX.minimax(tree, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        Map<Integer, Integer> moves = mrX.treeSearch(tree, minimax.get(tree.LocationAndScore.keySet().iterator().next()));
        assertThat(moves.containsKey(62));
    }

}