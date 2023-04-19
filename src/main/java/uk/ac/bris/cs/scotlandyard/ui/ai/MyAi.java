package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;
import static java.lang.Math.*;


public class MyAi implements Ai {

	// inner class which defines the tree structure
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

	// applies the scoring function for the given game state
	private Integer stateEvaluation(Board.GameState state, Integer MrXLocation, Map<Integer, Integer> currentScore){
		// if the game is over in this state, then it is the worst possible result, so the minimum value is returned to lower the chance of it being chosen
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
				//System.out.println("With MrX location: "+MrXLocation+", distance to detective "+det+" is: "+newDist);
				// for all detectives within 3 moves of MrX, then the score is the average of them all
				if(newDist <= 3){
					score += newDist;
					count += 1;
				}
				// if no detective is within 3 moves of MrX, then the score is taken to be the nearest detective
				if (newDist <= minDist){
					minDist = newDist;
				}
				// if the move leads to the detective being able to reach MrX, it should be rated badly
				if(newDist == 1){
					return -1;
				}
			}
		}
		if (score == 0){
			score = minDist;
		}
		else { score = score/count;}
		return score;
	}

	// constructs the tree for the given depth
	private TreeNode treeMaker(TreeNode parentNode, Board.GameState board, int count, boolean MrXTurn){
		// if the bottom of the tree is reached, then no new children should be made, and all leaves should be evaluated
		if(count == -1){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), stateEvaluation(parentNode.state, parentNode.LocationAndScore.keySet().iterator().next(), parentNode.LocationAndScore));
			return parentNode;
		}
		// if it's MrX's turn, then a new child should be made for each move
		if(MrXTurn){
			// for each move available to the parent node, it should check if the destination has already been defined.
			// If not, then the move's destination is added to the tree
			for (Move newMove : board.getAvailableMoves()) {
				Map<Integer, Integer> destination = new HashMap<>();
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
			if(!groupedMoves.isEmpty()) {
				// add a new child for each possible move of the first detective
				/*for (Move move : groupedMoves.get(groupedMoves.keySet().iterator().next())) {
					Map<Integer, Integer> destination = new HashMap<>();
					destination.put(0, 0);
					parentNode.children.add(new TreeNode(new ArrayList<>(), destination, board));
				}*/
				parentNode.children.add(new TreeNode(new ArrayList<>(), parentNode.LocationAndScore, parentNode.state));
				// for all detectives, advance the board, and make new children for each move combination (order doesn't matter)
				//for (TreeNode node : parentNode.children) {
					//System.out.println("Entering new detective: "+det);
					//System.out.println("Current children: "+parentNode.children);
					// for each of the current nodes
					List<TreeNode> newChildren = new ArrayList<>();
					for (Piece det : parentNode.state.getPlayers().stream().filter(Piece::isDetective).toList()) {
						if(parentNode.children.get(0).state.getAvailableMoves().stream().anyMatch((x) -> x.commencedBy().equals(det))) {
							TreeNode node = parentNode.children.get(0);
							//System.out.println("Current detective: " + det);
							int minValue = Integer.MAX_VALUE;
							Board.GameState minState = node.state;
							Move minMove = minState.getAvailableMoves().iterator().next();
							Map<Integer, Integer> minDestination = new HashMap<>();
							groupedMoves = minState.getAvailableMoves().stream().collect(Collectors.groupingBy(Move::commencedBy));
							// if groupedMoves is empty, that means the game is over, so no new children should be made from this node
							if (!groupedMoves.isEmpty()) {
								// for each possible move of the current detective
								if (!groupedMoves.keySet().iterator().next().isMrX()) {
									for (Move move : groupedMoves.get(det)) {
										int locationCheck = 0;
										// runs a check to ensure the space isn't occupied
										for (Piece detective : minState.getPlayers()) {
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
												//newChildren.add(new TreeNode(node.children, destination, node.state.advance(move)));
												if (shortestDistance(parentNode.LocationAndScore.keySet().iterator().next(), (Piece.Detective) det, node.state) < minValue) {
													minValue = shortestDistance(parentNode.LocationAndScore.keySet().iterator().next(), (Piece.Detective) det, node.state);
													minMove = move;
													minDestination = destination;
												}
											}
										}
									}
									if (!minDestination.isEmpty()) {
										//newChildren.add(new TreeNode(node.children, minDestination, node.state.advance(minMove)));
										parentNode.children.set(0, new TreeNode(node.children, minDestination, parentNode.state.advance(minMove)));
									}
								}
							} else {
								node.LocationAndScore.replace(node.LocationAndScore.keySet().iterator().next(), Integer.MIN_VALUE);
							}
						}
					}
					//parentNode.children = newChildren;

			}
		}
		// for each child, the tree continues to be made
		for(TreeNode child : parentNode.children){
			treeMaker(child, child.state, count-1, !MrXTurn);
		}
		return parentNode;
	}

	// sets up the tree for traversal and creation
	private TreeNode treeInitialiser(Board.GameState board){
		Map<Integer, Integer> move = new HashMap<>();
		move.put(board.getAvailableMoves().iterator().next().source(), 0);
		TreeNode root = new TreeNode(new ArrayList<>(), move, board);
		return treeMaker(root, board, 4, true);
	}

	// based on pseudocode from https://www.youtube.com/watch?v=l-hh51ncgDI
	// minimax should traverse the tree, reaching the bottom and moving upwards
	// for each level, it should look through the children's scores, and choose either the lowest or highest depending
	// on whether they're minimising or maximising respectively
	private Map<Integer, Integer> minimax (TreeNode parentNode, Integer depth, Integer alpha, Integer beta, boolean maximisingPlayer, int count){
		if(parentNode.children.isEmpty()){return parentNode.LocationAndScore;}
		if(!parentNode.state.getWinner().isEmpty() && parentNode.state.getWinner().stream().anyMatch(Piece::isMrX)){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), Integer.MAX_VALUE);
			return parentNode.LocationAndScore;
		} else if(!parentNode.state.getWinner().isEmpty() && parentNode.state.getWinner().stream().anyMatch(Piece::isDetective)){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), Integer.MIN_VALUE);
		}
		// the maximising player should always be MrX
		if(maximisingPlayer){
			Map<Integer, Integer> maxEval = new HashMap<>();
			maxEval.put(0, Integer.MIN_VALUE);
			if(!parentNode.children.isEmpty()) for (TreeNode child : parentNode.children) {
				Integer loc = child.LocationAndScore.keySet().iterator().next();
				Map<Integer, Integer> blah = minimax(child, depth - 1, alpha, beta, false, count);
				int eval = blah.get(loc);
				if (maxEval.get(maxEval.keySet().iterator().next()) < eval) {
					maxEval.clear();
					maxEval.put(child.state.getAvailableMoves().iterator().next().source(), eval);
				}
				// if the branch you're currently working on gains a value larger than the other branch, then we're done with it, so we can break
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
					System.out.println("loc" +child.LocationAndScore);
					/*Iterator<Move> iterator = child.state.getAvailableMoves().iterator();
					for(int i = 0; i < count; i++){
						iterator.next();
					}*/
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

	// ensures that the given detective has the needed tickets for a move
	private boolean ticketmaster(Piece det, ImmutableSet<ScotlandYard.Transport> transport, Board board){
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

	// returns the final destination of a given move
	// uses visitor pattern as the destinations are private
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

	// finds the shortest distance between a detective and the given point on the board. This should depend on the tickets the detective has
	@Nonnull @SuppressWarnings("UnstableApiUsage") private Integer shortestDistance(Integer destination, Piece.Detective detective, Board.GameState board){
		// get the detective's location
		int source = 0;
		if(board.getDetectiveLocation(detective).isPresent()){
			source = board.getDetectiveLocation(detective).get();
		}

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
			//Set<Integer> successors = board.getSetup().graph.edges().stream().toList();
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

				Optional<ImmutableSet<ScotlandYard.Transport>> transports = board.getSetup().graph.edgeValue(currentNode, successor);
				if (transports.isPresent()) {
					if(ticketmaster(detective, transports.get(), board)) {
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

	// weights the given moves (ones which end up in the optimal destination) based on the defined strategy
	@SuppressWarnings("UnstableApiUsage")
	private Map<Move, Integer> ticketWeighting(Board board, List<Move> destinationMoves){
		Map<Move,Integer> weightedMove = new HashMap<>();
		for(Move move : destinationMoves){
			//weightedMove.clear();
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
			/*if (board.getSetup().graph.adjacentNodes(getMoveDestination(move)).size() <= 2) {
				// if the move is in the corner, then it's score should be lowered
				weightedMove.put(move, weightedMove.get(move)-2);
			}*/
			for (Piece det : board.getPlayers()){
				if (det.isDetective()) {
					Optional<Integer> optDetLoc = board.getDetectiveLocation((Piece.Detective) det);
					if (optDetLoc.isPresent()) {
						int detLoc = optDetLoc.get();
						for (int node : board.getSetup().graph.adjacentNodes(getMoveDestination(move))){
							if (detLoc == node){
								weightedMove.put(move, weightedMove.get(move)-5);
							}
						}
					}
				}
			}
		}

		return weightedMove;
	}

	// prints the tree, used for testing
	@SuppressWarnings("unused")
	private void printTree(TreeNode tree){
		System.out.println("Node "+tree.LocationAndScore+" has children: ");
		for(TreeNode child : tree.children){
			printTree(child);
		}
	}

	// searches through the tree and finds the moves which result in the given weight
	private Map<Integer, Integer> treeSearch(TreeNode tree, Integer weight, int count){
		/*if(count == 1) {
			if (tree.LocationAndScore.get(tree.LocationAndScore.keySet().iterator().next()).equals(weight)) {
				return tree.LocationAndScore;
			}
		}
		Map<Integer, Integer> nodeFound = new HashMap<>();
		for(TreeNode node : tree.children){
			if(!treeSearch(node, weight, count + 1).isEmpty()) {
				nodeFound.put(node.LocationAndScore.keySet().iterator().next(), treeSearch(node, weight, count + 1).get(node.LocationAndScore.keySet().iterator().next()));
			}
		}*/
		Map<Integer, Integer> nodeFound = new HashMap<>();
		for(TreeNode node: tree.children){
			if(node.LocationAndScore.get(node.LocationAndScore.keySet().iterator().next()).equals(weight)){
				nodeFound.put(node.LocationAndScore.keySet().iterator().next(), node.LocationAndScore.get(node.LocationAndScore.keySet().iterator().next()));
			}
		}
		return nodeFound;
	}

	// manages the overall move picking
	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		//  creates the tree and evaluates it
		TreeNode tree = treeInitialiser((Board.GameState) board);
		Map<Integer, Integer> weight = minimax(tree, 4, -(Integer.MAX_VALUE), Integer.MAX_VALUE, true, 0);
		// finds the best destination in the tree
		Map<Integer, Integer> bestMove = treeSearch(tree, weight.get(weight.keySet().iterator().next()), 0);
		System.out.println("Possible destinations: "+bestMove);
		List<Move> destinationMoves = new ArrayList<>();
		List<Move> choosableSecrets = new ArrayList<>();
		Iterator<Move> moveIterator = board.getAvailableMoves().stream().iterator();
		//Iterator<Integer> bestIterator = bestMove.keySet().iterator();
		while(moveIterator.hasNext()){
			Move newValue = moveIterator.next();
			Iterator<Integer> bestIterator = bestMove.keySet().iterator();
			while(bestIterator.hasNext()) {
				Integer bestValue = bestIterator.next();
				if (getMoveDestination(newValue).equals(bestValue)) {
					// should only add move which don't use secret tickets, as the weighting for these are separate
					if(newValue.accept(new Move.Visitor<Boolean>() {
						@Override
						public Boolean visit(Move.SingleMove move) {
							return !move.ticket.equals(ScotlandYard.Ticket.SECRET);
						}

						@Override
						public Boolean visit(Move.DoubleMove move) {
							return !(move.ticket1.equals(ScotlandYard.Ticket.SECRET) || move.ticket2.equals(ScotlandYard.Ticket.SECRET));
						}
					})) {
						destinationMoves.add(newValue);
					} else {
						choosableSecrets.add(newValue);
					}
				}
			}
		}
		Map<Move, Integer> weightedMoves = ticketWeighting(board, destinationMoves);
		//System.out.println("Pre Moves weighted: "+weightedMoves);
		// select the best move after all weighting has been applied
		Map<Move,Integer> maxNum = new HashMap<>();
		Move maxNumMove = null;
		try {
			maxNum.put(destinationMoves.get(0), 0);
			maxNumMove = destinationMoves.get(0);
		} catch (Exception e){
			System.out.println("It's empty again");

		}
		// if the player has more of one kind of ticket, then it should be picked
		for (Move move : weightedMoves.keySet()) {
			if (weightedMoves.get(move) >= maxNum.get(maxNumMove)) {
				maxNum.clear();
				maxNum.put(move,weightedMoves.get(move));
				maxNumMove = move;
			}
		}

		// once a maxNum has been found, we should check if a secret card would be a better option
		if(board.getMrXTravelLog().size() > 1) {
			System.out.println(ScotlandYard.REVEAL_MOVES);
			System.out.println(board.getMrXTravelLog().size());
			// secret cards should be played after a reveal move to hide where he went
			if (ScotlandYard.REVEAL_MOVES.contains(board.getMrXTravelLog().size())) {
				if(!choosableSecrets.isEmpty()) {
					System.out.println("Entering secret weighting");
					/*for (Move move : choosableSecrets) {
						if (!getMoveDestination(move).equals(getMoveDestination(Objects.requireNonNull(maxNumMove)))) {
							choosableSecrets.remove(move);
						}
					}*/

					System.out.println("Choosable secrets: " + choosableSecrets);
					Map<Move, Integer> weightedSecrets = ticketWeighting(board, choosableSecrets);

					for (Move move : weightedSecrets.keySet()) {
						if (weightedSecrets.get(move) >= maxNum.get(maxNumMove)) {
							maxNum.clear();
							maxNum.put(move, weightedSecrets.get(move));
							maxNumMove = move;
						}
					}
					return Objects.requireNonNull(maxNumMove);
				}
			}
		}

		return Objects.requireNonNull(maxNumMove);
	}
}