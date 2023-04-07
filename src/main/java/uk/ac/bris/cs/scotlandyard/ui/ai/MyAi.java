package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;
import static java.lang.Math.*;


public class MyAi implements Ai {

	static class TreeNode {
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
	private Integer stateEvaluation(Board.GameState state, Integer MrXLocation, Map<Integer, Integer> currentScore){
		if(currentScore.get(currentScore.keySet().iterator().next()) == Integer.MIN_VALUE){
			System.out.println("Game over move");
			return Integer.MIN_VALUE;
		}
		int score = 0;
		int minDist = 0;
		int count = 0;
		for(Piece det : state.getPlayers()){
			if(det.isDetective()) {
				int newDist = shortestDistance(MrXLocation, (Piece.Detective) det, state);
				if(newDist <= 3){
					score += newDist;
					count += 1;
				}
				if (newDist >= minDist){
					minDist = newDist;
				}
			}
		}
		if (score == 0){
			score = minDist;
		}
		else { score = score/count;}
		return score;
	}

	private TreeNode treeMaker(TreeNode parentNode, Board.GameState board, int count){
		if(count == -1){
			System.out.println("MrX location: "+parentNode.LocationAndScore.keySet().iterator().next());
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), stateEvaluation(board, (Integer) parentNode.LocationAndScore.keySet().iterator().next(), parentNode.LocationAndScore));
			return parentNode;
		}
		// if it's MrX's turn, then a new child should be made for each move
		if(board.getAvailableMoves().stream().anyMatch((x) -> x.commencedBy().isMrX())){
			// for each move available to the parent node
			for (Move newMove : board.getAvailableMoves()) {
				Map<Integer, Integer> destination = new HashMap<>();
				//destination.put(getMoveDestination(newMove), stateEvaluation(board.advance(newMove), getMoveDestination(newMove)));
				destination.put(getMoveDestination(newMove), 0);
				int counter = 0;
				for(TreeNode child : parentNode.children){
					if(child.LocationAndScore.keySet().equals(destination.keySet())){
						counter += 1;
					}
				}
				if(counter == 0) {
					parentNode.children.add(new TreeNode(new ArrayList<>(), destination, board.advance(newMove)));
				}
			}
		} else {
			// if it's the detective's turns, then a new child should be made for each combination of moves
			Map<Piece, List<Move>> groupedMoves = board.getAvailableMoves().stream().collect(Collectors.groupingBy(Move::commencedBy));
			// add a new child for each possible move of the first detective
			if(!groupedMoves.isEmpty()) {
				for (Move move : groupedMoves.get(groupedMoves.keySet().iterator().next())) {
					Map<Integer, Integer> destination = new HashMap<>();
					destination.put(0, 0);
					//destination.put(getMoveDestination(move), stateEvaluation(board.advance(move), getMoveDestination(move)));
					parentNode.children.add(new TreeNode(new ArrayList<>(), destination, board));
				}

				// for all detectives, advance the board, and make new children for each move combination (order doesn't matter)
				// changed, so it doesn't rely on the groupedMoves variable
				for (Piece det : parentNode.state.getPlayers().stream().filter(Piece::isDetective).toList()) {
					// for each of the current nodes
					List<TreeNode> newChildren = new ArrayList<>();
					for (TreeNode node : parentNode.children) {
						Board.GameState nodeCheck = node.state;
						groupedMoves = nodeCheck.getAvailableMoves().stream().collect(Collectors.groupingBy(Move::commencedBy));
						// if groupedMoves is empty, that means the game is over, so no new children should be made from this node
						if (!groupedMoves.isEmpty()) {
							// for each possible move of the current detective
							for (Move move : groupedMoves.get(det)) {
								int locationCheck = 0;
								// runs a check to ensure the space isn't occupied
								for (Piece detective : nodeCheck.getPlayers()) {
									if (detective.isDetective()) {
										Optional<Integer> detLocation = node.state.getDetectiveLocation((Piece.Detective) detective);
										if (detLocation.isPresent()) {
											if ((detLocation.get()).equals(getMoveDestination(move))) {
												locationCheck += 1;
											}
										}
									}
								}
								if (locationCheck == 0) {
									Map<Integer, Integer> destination = new HashMap<>();
									destination.put(getMoveDestination(move), 0);
									int counter = 0;
									for (TreeNode child : newChildren) {
										if (child.LocationAndScore.keySet().equals(destination.keySet())) {
											counter += 1;
										}
									}
									if (counter == 0) {
										nodeCheck = node.state.advance(move);
										newChildren.add(new TreeNode(node.children, destination, node.state.advance(move)));
									}
								}
								//newChildren.add(new TreeNode(node.children, destination, node.state.advance(move)));

							}
						} else {
							System.out.println("State over");
							node.LocationAndScore.replace(node.LocationAndScore.keySet().iterator().next(), Integer.MIN_VALUE);
							//destination.replace(destination.keySet().iterator().next(), Integer.MIN_VALUE);
							//newChildren.add(new TreeNode(node.children, destination, node.state.advance(move)));
						}
					}
					parentNode.children = newChildren;
				}
			}

		}

		for(TreeNode child : parentNode.children){
			treeMaker(child, child.state, count-1);
		}
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
	private Map<Integer, Integer> minimax (TreeNode parentNode, Integer depth, Integer alpha, Integer beta, boolean maximisingPlayer, int count){
		if(parentNode.children.isEmpty() || !parentNode.state.getWinner().isEmpty()){return parentNode.LocationAndScore;}
		if(maximisingPlayer){
			Map<Integer, Integer> maxEval = new HashMap<>();
			maxEval.put(0, -(Integer.MAX_VALUE));
			if(!parentNode.children.isEmpty()) for (TreeNode child : parentNode.children) {
				Integer loc = child.LocationAndScore.keySet().iterator().next();
				Map<Integer, Integer> blah = minimax(child, depth - 1, alpha, beta, false, count);
				int eval = blah.get(loc);
				if (maxEval.get(maxEval.keySet().iterator().next()) < eval) {
					maxEval.clear();
					maxEval.put(child.state.getAvailableMoves().iterator().next().source(), eval);
				}
				alpha = max(alpha, eval);
				if (beta <= alpha) {
					break;
				}
			}
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), maxEval.get(maxEval.keySet().iterator().next()));
			return parentNode.LocationAndScore;
		} else {
			Map<Integer, Integer> minEval = new HashMap<>();
			minEval.put(0, Integer.MAX_VALUE);
			if(!parentNode.children.isEmpty()) {
				for (TreeNode child : parentNode.children) {
					Iterator<Move> iterator = child.state.getAvailableMoves().iterator();
					for(int i = 0; i < count; i++){
						iterator.next();
					}
					//int location = getMoveDestination(iterator.next());
					Integer loc = child.LocationAndScore.keySet().iterator().next();
					Map<Integer,Integer> minimax = minimax(child, depth - 1, alpha, beta, true, count+1);
					int eval = minimax.get(loc);
					int minEvalVal = minEval.get(minEval.keySet().iterator().next());
					if (minEvalVal > eval) {
						if(!child.state.getAvailableMoves().isEmpty()) {
							int source = child.state.getAvailableMoves().iterator().next().source();
							minEval.clear();
							minEval.put(source, eval);
						}
					}
					beta = min(beta, eval);
					if (beta <= alpha) {
						break;
					}
				}
			}
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), minEval.get(minEval.keySet().iterator().next()));
			return parentNode.LocationAndScore;
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

	@Nonnull @SuppressWarnings("UnstableApiUsage") private Integer shortestDistance(Integer destination, Piece.Detective detective, Board board){
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
				if (board.getSetup().graph.edgeValue(currentNode, successor).isPresent()) {
					if(ticketmaster(detective, board.getSetup().graph.edgeValue(currentNode, successor).get(), board)) {
						if ((dist.get(index) + 1) < dist.get(count)) {
							dist.set(count, (dist.get(index) + 1));
						}
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
	@SuppressWarnings("UnstableApiUsage")
	private Map<Move, Integer> ticketWeighting(Board board, List<Move> destinationMoves){
		Map<Move,Integer> weightedMove = new HashMap<>();
		for(Move move : destinationMoves){
			weightedMove.clear();
			Integer moveInt = (move.accept(new Move.Visitor<Integer>() {
				// using visitor pattern to find how many of the wanted ticket are available
				@Override
				public Integer visit(Move.SingleMove move) {
					try {
						Optional<Board.TicketBoard> optTickets = board.getPlayerTickets(move.commencedBy());
						if (optTickets.isPresent()) {
							Board.TicketBoard tickets = optTickets.get();
							return tickets.getCount(move.ticket);
						}
					} catch (NullPointerException exception){
						System.out.println("Ticket for move was not found!");
					}
					return null;
				}

				@Override
				public Integer visit(Move.DoubleMove move) {
					try {
						Optional<Board.TicketBoard> optTickets = board.getPlayerTickets(move.commencedBy());
						if (optTickets.isPresent()) {
							Board.TicketBoard tickets = optTickets.get();
							return max(tickets.getCount(move.ticket1), tickets.getCount(move.ticket2)) - min(tickets.getCount(move.ticket1), tickets.getCount(move.ticket2));
						}
					} catch (NullPointerException exception){
						System.out.println("Tickets for moves were not found!");
					}
					return null;
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

	@SuppressWarnings("unused")
	private void printTree(TreeNode tree){
		System.out.println("Node "+tree.LocationAndScore+" has children: ");
		for(TreeNode child : tree.children){
			printTree(child);
		}
	}

	private Map<Integer, Integer> treeSearch(TreeNode tree, Integer weight, int count){
		if(count == 1) {
			if (tree.LocationAndScore.get(tree.LocationAndScore.keySet().iterator().next()).equals(weight)) {
				return tree.LocationAndScore;
			}
		}
		Map<Integer, Integer> nodeFound = new HashMap<>();
		for(TreeNode node : tree.children){
			nodeFound = treeSearch(node, weight, count+1);
			if(!nodeFound.isEmpty()){
				return  nodeFound;
			}
		}
		return nodeFound;
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		TreeNode tree = treeInitialiser((Board.GameState) board);
		System.out.println("Tree made");
		//printTree(tree);
		Map<Integer, Integer> weight = minimax(tree, 2, -(Integer.MAX_VALUE), Integer.MAX_VALUE, true, 0);
		System.out.println(weight);
		Map<Integer, Integer> bestMove = treeSearch(tree, weight.get(weight.keySet().iterator().next()), 0);
		System.out.println(bestMove);
		List<Move> destinationMoves = new ArrayList<>();
		destinationMoves = board.getAvailableMoves().stream()
				.filter((x) -> getMoveDestination(x).equals(bestMove.keySet().iterator().next()))
				.toList();
		Map<Move, Integer> weightedMove = ticketWeighting(board, destinationMoves);
		// select the best move after all weighting has been applied
		Map<Move,Integer> maxNum = new HashMap<>();
		Move maxNumMove = null;
		try {
			maxNum.put(destinationMoves.get(0), 1);
			maxNumMove = destinationMoves.get(0);
		} catch (Exception e){
			System.out.println("It's empty again");

		}
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
		if(board.getMrXTravelLog().size() != 0) {
			if (board.getSetup().moves.get(board.getMrXTravelLog().size() - 1)) {
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
						if (getMoveDestination(move).equals(getMoveDestination(Objects.requireNonNull(maxNumMove)))) {
							choosableSecrets.add(move);
						}
					}
				}
				Random randomInt = new Random();
				if(choosableSecrets.size() <= 1){
					return Objects.requireNonNull(maxNum.keySet().iterator().next());
				}
				return choosableSecrets.get(abs(randomInt.nextInt(choosableSecrets.size() - 1)));
			}
		}
		return Objects.requireNonNull(maxNum.keySet().iterator().next());
	}
}