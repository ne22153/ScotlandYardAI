package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;
import static java.lang.Math.*;


public class MyAi implements Ai {

	class TreeNode {
		List<TreeNode> children;
		Map<Integer, Integer> LocationAndScore;
		Board.GameState state;
		TreeNode(List<TreeNode> children, Map<Integer, Integer> LocationAndScore, Board.GameState state){
			this.children = children;
			this.LocationAndScore = LocationAndScore;
			this.state = state;
		}
	}

	// applies the scoring function for the given game state (currently finds the mean distance of MrX from all detectives)
	private Integer stateEvaluation(Board.GameState state, Integer MrXLocation){
		int sumOf = 0;
		for(Piece det : state.getPlayers()){
			if(det.isDetective()){
				sumOf += shortestDistance(MrXLocation, (Piece.Detective) det, state);
			}
		}
		return (sumOf / (state.getPlayers().size()-1));
	}

	private TreeNode treeMaker(TreeNode parentNode, Board.GameState board, int count){
		if(count == 0){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), stateEvaluation(board, parentNode.LocationAndScore.keySet().iterator().next()));
			return parentNode;
		}
		// if it's MrX's turn, then a new child should be made for each move
		if(board.getAvailableMoves().stream().anyMatch((x) -> x.commencedBy().isMrX())){
			// for each move available to the parent node
			for (Move newMove : board.getAvailableMoves()) {
				Map<Integer, Integer> destination = new HashMap<>();
				destination.put(getMoveDestination(newMove), stateEvaluation(board.advance(newMove), getMoveDestination(newMove)));
				parentNode.children.add(new TreeNode(new ArrayList<>(), destination, board.advance(newMove)));
			}
		} else {
			// if it's the detective's turns, then a new child should be made for each combination of moves
			Map<Piece, List<Move>> groupedMoves = board.getAvailableMoves().stream().collect(Collectors.groupingBy(w -> w.commencedBy()));
			// add a new child for each possible move of the first detective
			for(Move move : groupedMoves.get(groupedMoves.keySet().iterator().next())){
				Map<Integer, Integer> destination = new HashMap<>();
				destination.put(0, 0);
				//destination.put(getMoveDestination(move), stateEvaluation(board.advance(move), getMoveDestination(move)));
				parentNode.children.add(new TreeNode(new ArrayList<>(), destination, board));
			}
			// for all detectives, advance the board, and make new children for each move combination (order doesn't matter)

			//for each detective
			for(Piece det : groupedMoves.keySet()){
				// for each of the current nodes
				List<TreeNode> newChildren = new ArrayList<>();
				for(TreeNode node : parentNode.children) {
					// for each possible move of the current detective
					for(Move move : groupedMoves.get(det)){
						Map<Integer, Integer> destination = new HashMap<>();
						//destination.put(getMoveDestination(move), stateEvaluation(node.state.advance(move), getMoveDestination(move)));
						destination.put(getMoveDestination(move), stateEvaluation(node.state.advance(move), getMoveDestination(move)));
						newChildren.add(new TreeNode(node.children, destination, node.state.advance(move)));
					}
				}
				parentNode.children = newChildren;
			}

		}

		for(TreeNode child : parentNode.children){
			System.out.println("Entering child");
			treeMaker(child, child.state, count-1);
		}
		System.out.println("Leaving child");
		return parentNode;
	}

	// sets up the tree for traversal
	private TreeNode treeInitialiser(Board.GameState board){
		Map<Integer, Integer> move = new HashMap<>();
		move.put(board.getAvailableMoves().iterator().next().source(), 0);
		TreeNode root = new TreeNode(new ArrayList<>(), move, board);
		return treeMaker(root, board, 2);
	}

	// based on pseudocode from https://www.youtube.com/watch?v=l-hh51ncgDI
	private Map<Integer, Integer> minimax (TreeNode parentNode, Integer depth, Integer alpha, Integer beta, boolean maximisingPlayer){
		if(depth == 0 || !parentNode.state.getWinner().isEmpty()){return parentNode.LocationAndScore;}
		System.out.println("minimax entered");
		if(maximisingPlayer){
			Map<Integer, Integer> maxEval = new HashMap<>();
			maxEval.put(0, -(Integer.MAX_VALUE));
			if(!parentNode.children.isEmpty()) {
				for (TreeNode child : parentNode.children) {
					// testing print statement
					System.out.println("Max: "+minimax(child, depth - 1, alpha, beta, false).get(child.state.getAvailableMoves().iterator().next().source()));

					int eval = minimax(child, depth - 1, alpha, beta, false).get(child.state.getAvailableMoves().iterator().next().source());
					if (maxEval.get(maxEval.keySet().iterator().next()) < eval) {
						maxEval.replace(child.state.getAvailableMoves().iterator().next().source(), eval);
					}
					alpha = max(alpha, eval);
					if (beta <= alpha) {
						break;
					}
				}
			}
			return maxEval;
		} else {
			Map<Integer, Integer> minEval = new HashMap<>();
			minEval.put(0, Integer.MAX_VALUE);
			if(!parentNode.children.isEmpty()) {
				for (TreeNode child : parentNode.children) {
					// to find the last detective that is being used, by accessing the available moves
					int location = 0;
					int count = 0;
					for(Move move : child.state.getAvailableMoves()){
						count += 1;
						if(count == child.state.getAvailableMoves().size()){
							location = getMoveDestination(move);
						}
					}
					System.out.println(minimax(child, depth - 1, alpha, beta, true).get(location));
					// current problem line:
					// the key is the location of the detective that was processed last in the tree creation stage
					// this should be the last occurring detective after the moves are grouped by 'commencer'
					// current issue is that when I try to find this, it returns null, and the debug will break before I can assess the eval variable
					int eval = minimax(child, depth - 1, alpha, beta, true).get(location);
					if (minEval.get(minEval.keySet().iterator().next()) > eval) {
						minEval.replace(child.state.getAvailableMoves().iterator().next().source(), eval);
					}
					beta = min(beta, eval);
					if (beta <= alpha) {
						break;
					}
				}
			}
			return minEval;
		}
	}

	private boolean ticketmaster(Piece det, ImmutableSet<ScotlandYard.Transport> transport, Board board){
		// check to see if the given piece has a ticket that allows them to move in this direction
		for(ScotlandYard.Transport t : transport){
			Board.TicketBoard tickets;
			if(board.getPlayerTickets(det).isPresent()){
				tickets = board.getPlayerTickets(det).get();
				if(tickets.getCount(t.requiredTicket()) > 0){
					return true;
				}
			}
		}
		return false;
	}

	@Nonnull private Integer getMoveDestination(Move move){
		return move.accept(new Move.Visitor<Integer>() {

			@Override
			public Integer visit(Move.SingleMove move) {
				return move.destination;
			}

			@Override
			public Integer visit(Move.DoubleMove move) {
				return move.destination2;
			}
		});
	}
	@Nonnull private Integer shortestDistance(Integer destination, Piece.Detective detective, Board board){
		//PriorityQueue<Integer> pq = new PriorityQueue<>();
		// get the detective's location
		int source = 0;
		if(board.getDetectiveLocation(detective).isPresent()){
			source = board.getDetectiveLocation(detective).get();
		}
		// adjacent nodes to the detective's current location
		//Set<Integer> nodes = board.getSetup().graph.adjacentNodes(source);

		// set of all nodes in the graph & it's iterator
		Set<Integer> nodes = board.getSetup().graph.nodes();
		Iterator<Integer> nodesIterator = nodes.iterator();

		// list of distances from the source node, and whether they've been visited
		List<Integer> dist = new ArrayList<>();
		List<Boolean> visited = new ArrayList<>();

		// initialise dist and visited
		int i = 0;
		while(nodesIterator.hasNext()){
			if(Objects.equals(nodesIterator.next(), source)){
				dist.add(i, 0);
			} else {dist.add(i, Integer.MAX_VALUE);}
			visited.add(i, Boolean.FALSE);
			i++;
		}
		// while not all nodes have been visited
		Boolean cont = Boolean.TRUE;
		while(cont){
			for (int t = 0; t < size(visited)-1; t++){
				cont = !(cont && visited.get(t));
			}
			nodesIterator = nodes.iterator();
			int minimum = Integer.MAX_VALUE;
			// finds minimum distance which hasn't been looked at yet
			Integer currentNode = size(nodes)-1;
			int index = size(nodes)-1;
			for(int t = 0; t < size(nodes)-1; t++){
				if((dist.get(t) < minimum) && (visited.get(t) == Boolean.FALSE)){
					index = t;
					currentNode = nodesIterator.next();
					minimum = dist.get(t);
				} else {
					nodesIterator.next();
				}
			}
			visited.set(index, Boolean.TRUE);
			Set<Integer> successors = board.getSetup().graph.successors(currentNode);
			// go through successors to assess weights
			for (Integer successor : successors) {
				Iterator<Integer> nodesIterator2 = nodes.iterator();
				int count = 0;
				while (nodesIterator2.hasNext()) {
					if (successor.equals(nodesIterator2.next())) {
						break;
					} else {
						count++;
					}
				}
				// need to change this section for transport types
				if (ticketmaster(detective, board.getSetup().graph.edgeValue(currentNode, successor).get(), board)) {
					if ((dist.get(index) + 1) < dist.get(count)) {
						dist.set(count, (dist.get(index) + 1));
					}
				}
			}
		}
		return dist.get(destination-1);
	}

	@Nonnull @Override public String name() { return "Damien & Patrik's MrX"; }

	@Override
	public void onStart() {
		Ai.super.onStart();
	}

	@Override
	public void onTerminate() {
		Ai.super.onTerminate();
	}

	private Map<Move, Integer> ticketWeighting(Board board, List<Move> destinationMoves){
		Map<Move,Integer> weightedMove = new HashMap<>();
		for(Move move : destinationMoves){
			weightedMove.clear();
			Integer moveInt = (move.accept(new Move.Visitor<Integer>() {
				// using visitor pattern to find how many of the wanted ticket are available
				@Override
				public Integer visit(Move.SingleMove move) {
					return board.getPlayerTickets(move.commencedBy()).get().getCount(move.ticket);
				}

				@Override
				public Integer visit(Move.DoubleMove move) {
					Board.TicketBoard tickets = board.getPlayerTickets(move.commencedBy()).get();
					return max(tickets.getCount(move.ticket1), tickets.getCount(move.ticket2)) - min(tickets.getCount(move.ticket1), tickets.getCount(move.ticket2));
				}
			}));
			weightedMove.put(move,moveInt);
		}

		// if MrX is in a corner area, weight the move which leads towards the centre
		// define a corner by a node which has <= 2 adjacent nodes
		for(Move move : weightedMove.keySet()){
			if (board.getSetup().graph.adjacentNodes(getMoveDestination(move)).size() <= 2) {
				// if the move is in the corner, then it's score should be lowered
				weightedMove.put(move, weightedMove.get(move)-2);
			}
		}

		return weightedMove;
	}

	private void printTree(TreeNode tree){
		System.out.println("Node "+tree.LocationAndScore+" has children: ");
		for(TreeNode child : tree.children){
			printTree(child);
		}
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		TreeNode tree = treeInitialiser((Board.GameState) board);
		System.out.println("Tree made");
		//printTree(tree);
		Map<Integer, Integer> bestMove = minimax(tree, 2, -(Integer.MAX_VALUE), Integer.MAX_VALUE, true);
		List<Move> destinationMoves = new ArrayList<>();
		destinationMoves = board.getAvailableMoves().stream()
				.filter((x) -> getMoveDestination(x).equals(bestMove.keySet().iterator().next()))
				.toList();
		Map<Move, Integer> weightedMove = ticketWeighting(board, destinationMoves);

		// select the best move after all weighting has been applied
		Map<Move,Integer> maxNum = new HashMap<>();
		maxNum.put(destinationMoves.get(0),0);
		Move maxNumMove = destinationMoves.get(0);
		// if the player has more of one kind of ticket, then it should be picked
		for (Move move : weightedMove.keySet()) {
			if (weightedMove.get(move) >= maxNum.get(maxNumMove)) {
				maxNum.clear();
				maxNum.put(move,weightedMove.get(move));
				maxNumMove = move;
			}
		}
		System.out.println("Moves weighted: "+weightedMove);

		// once a maxNum has been found, we should check if a secret card would be a better option
		// if MrX's move was just a reveal, then play a secret card instead
		List<Move> choosableSecrets = new ArrayList<>();
		if(board.getSetup().moves.get(board.getMrXTravelLog().size()-1) && board.getMrXTravelLog().size() != 0) {
			for (Move move : destinationMoves) {
				if (move.accept(new Move.Visitor<ScotlandYard.Ticket>() {
					@Override
					public ScotlandYard.Ticket visit(Move.SingleMove move) {
						return move.ticket;
					}

					@Override
					public ScotlandYard.Ticket visit(Move.DoubleMove move) {
						if (move.ticket1.equals(ScotlandYard.Ticket.SECRET)) {
							return move.ticket1;
						} else {
							return move.ticket2;
						}
					}
				}).equals(ScotlandYard.Ticket.SECRET)) {
					if (getMoveDestination(move).equals(getMoveDestination(maxNumMove))) {
						choosableSecrets.add(move);
					}
				}
			}
			Random randomInt = new Random();
			return choosableSecrets.get(abs(randomInt.nextInt(choosableSecrets.size() - 1)));
		}
		return Objects.requireNonNull((Move)maxNum.keySet().toArray()[0]);
	}
}