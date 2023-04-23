package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;
import static java.lang.Math.*;


public class MyAi implements Ai {
	static class TreeNode {
		List<TreeNode> children;
		Map<Integer, Integer> LocationAndScore;
		Board.GameState state;

		/**
		 *
		 * @param children stores the children of the current node
		 * @param LocationAndScore maps the player's location to the score of the gameState where they moved
		 * @param state stores the gameState after that player's movement, used for LocationAndScore
		 */
		TreeNode(List<TreeNode> children, Map<Integer, Integer> LocationAndScore, Board.GameState state){
			this.children = children;
			this.LocationAndScore = LocationAndScore;
			this.state = state;
		}
	}

	/**
	 * #
	 * @param state the state to be evaluated
	 * @param MrXLocation MrX's current location
	 * @param currentScore acts as a check to see of the game is over (the node will hold a non-zero value if so)
	 * @return either the distance to the nearest detective, or the mean of the nearby distances, depending on how close they are
	 */
	private Integer stateEvaluation(Board.GameState state, Integer MrXLocation, Map<Integer, Integer> currentScore){

		// if the game is over in this state, then it is the worst possible result, so the minimum value is returned to lower the chance of it being chosen
		if(currentScore.get(currentScore.keySet().iterator().next()) == Integer.MIN_VALUE){
			return Integer.MIN_VALUE;
		}

		int score = 0;
		int minDist = 0;
		int count = 0;

		for(Piece det : state.getPlayers()){

			if(det.isDetective()) {
				int newDist = shortestDistance(MrXLocation, (Piece.Detective) det, state);

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
				if(newDist <= 1){
					return -1;
				}
			}
		}

		if (score == 0){
			score = minDist;
		} else { score = score/count;}

		return score;
	}

	/**
	 *
	 * @param parentNode the current node being worked with
	 * @param board the state the game is in for the parentNode
	 * @param count how deep into the tree we are, the leaves should be held at count = 0
	 * @param MrXTurn holds whose turn it is, as this defines how the children are defined
	 * @return the gameTree
	 */
	private TreeNode treeMaker(TreeNode parentNode, Board.GameState board, int count, boolean MrXTurn){

		// if the bottom of the tree is reached, then no new children should be made, and all leaves should be evaluated
		if(count == -1){
			int key = parentNode.LocationAndScore.keySet().iterator().next();
			int stateEval = stateEvaluation(parentNode.state, parentNode.LocationAndScore.keySet().iterator().next(), parentNode.LocationAndScore);
			parentNode.LocationAndScore.clear();
			parentNode.LocationAndScore.put(key,stateEval);
			return parentNode;
		}

		// if a value has been defined for the node, then that means no more children can be made from it, so the value should be returned
		else if (!parentNode.LocationAndScore.get(parentNode.LocationAndScore.keySet().iterator().next()).equals(0)){
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
			// if it's the detective's turns, then only one child should be made, where each detective makes the optimal move
			Map<Piece, List<Move>> groupedMoves = board.getAvailableMoves().stream().collect(Collectors.groupingBy(Move::commencedBy));

			if(!groupedMoves.isEmpty()) {
				parentNode.children.add(new TreeNode(new ArrayList<>(), parentNode.LocationAndScore, parentNode.state));

				for (Piece det : parentNode.state.getPlayers().stream().filter(Piece::isDetective).toList()) {

					if(parentNode.children.get(0).state.getAvailableMoves().stream().anyMatch((x) -> x.commencedBy().equals(det))) {
						TreeNode node = parentNode.children.get(0);
						int minValue = Integer.MAX_VALUE;
						Board.GameState minState = node.state;
						Move minMove = null;
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

										if (shortestDistance(parentNode.LocationAndScore.keySet().iterator().next(), (Piece.Detective) det, node.state) < minValue) {

											if(parentNode.state.getPlayerTickets(det).get().getCount(ScotlandYard.Ticket.TAXI) > 1) {
												minValue = shortestDistance(parentNode.LocationAndScore.keySet().iterator().next(), (Piece.Detective) det, node.state);
												minMove = move;
												minDestination = destination;
											}

											else {
												int counter = 0;

												// if the detective only has 1 taxi card, then we need to make sure the later moves don't lead to a taxi only location
												for(Integer endMove : parentNode.state.getSetup().graph.adjacentNodes(getMoveDestination(move))){
													ImmutableSet<ScotlandYard.Transport> defaultTrans = ImmutableSet.copyOf(new HashSet<>());

													if(parentNode.state.getSetup().graph.edgeValueOrDefault(getMoveDestination(move), endMove, defaultTrans).stream().allMatch(x -> x.equals(ScotlandYard.Transport.TAXI))){
														counter += 1;
													}
												}

												// if the end point has only taxi moves, but the detective doesn't have any cards, then it shouldn't be played
												if(counter != parentNode.state.getSetup().graph.adjacentNodes(getMoveDestination(move)).size()){
													minValue = shortestDistance(parentNode.LocationAndScore.keySet().iterator().next(), (Piece.Detective) det, node.state);
													minMove = move;
													minDestination = destination;
												}
											}
										}
									}

									// if the move leads to MrX immediately losing, then it should be scored badly
									try {
										if (parentNode.state.advance(move).getWinner().stream().anyMatch(Piece::isDetective)) {
											minMove = move;
											minDestination = new HashMap<>();
											minDestination.put(getMoveDestination(move), Integer.MIN_VALUE + 1);
										}
									} catch (Exception ignored){}
								}

								if (!minDestination.isEmpty()) {
									try{
										parentNode.children.set(0, new TreeNode(node.children, minDestination, parentNode.state.advance(minMove)));
									} catch (Exception ignored){
									}

								}
							}

						// if grouped moves is empty, then it means MrX is stuck, so loses
						} else {
							node.LocationAndScore.replace(node.LocationAndScore.keySet().iterator().next(), Integer.MIN_VALUE);
						}
					}
				}
			}
		}

		// for each child, the tree continues to be made
		for(TreeNode child : parentNode.children){
			treeMaker(child, child.state, count-1, !MrXTurn);
		}

		return parentNode;
	}

	/**
	 *
	 * @param board the gameState of the root node, so the current state of the game
	 * @return a game tree of depth 4
	 */
	private TreeNode treeInitialiser(Board.GameState board){
		Map<Integer, Integer> move = new HashMap<>();
		move.put(board.getAvailableMoves().iterator().next().source(), 0);

		TreeNode root = new TreeNode(new ArrayList<>(), move, board);

		return treeMaker(root, board, 4, true);
	}

	// based on pseudocode from https://www.youtube.com/watch?v=l-hh51ncgDI
	/**
	 *
	 * @param parentNode the node we're currently working with
	 * @param depth how far into the tree we are
	 * @param alpha assuming the best play of the opponent, stores the best value that MrX can hope to achieve
	 * @param beta assuming the best play of the opponent, stores the best value that the detectives can hope to achieve
	 * @param maximisingPlayer defines whether the game seeks to maximise or minimise the score from its children
	 * @return the value to be assigned to the parentNode's LocationAndScore
	 */
	private Map<Integer, Integer> minimax (TreeNode parentNode, Integer depth, Integer alpha, Integer beta, boolean maximisingPlayer){

		// if there's no children, the bottom of the tree has been reached, and we should move upward
		if(parentNode.children.isEmpty()){return parentNode.LocationAndScore;}

		// if MrX has won in this game state, then the score should be set as a maximum
		if(!parentNode.state.getWinner().isEmpty() && parentNode.state.getWinner().stream().anyMatch(Piece::isMrX)){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), Integer.MAX_VALUE);
			return parentNode.LocationAndScore;
		}

		// if the detectives have won in this state, then the score should be set as a minimum
		else if(!parentNode.state.getWinner().isEmpty() && parentNode.state.getWinner().stream().anyMatch(Piece::isDetective)){
			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), Integer.MIN_VALUE);
		}

		// if it is MrX's turn, he wanted to maximise
		if(maximisingPlayer){
			Map<Integer, Integer> maxEval = new HashMap<>();
			maxEval.put(0, Integer.MIN_VALUE);

			if(!parentNode.children.isEmpty()) for (TreeNode child : parentNode.children) {

				if(!child.state.getAvailableMoves().isEmpty()) {
					Integer location = child.LocationAndScore.keySet().iterator().next();
					Map<Integer, Integer> value = minimax(child, depth - 1, alpha, beta, false);
					int eval = value.get(location);

					if (maxEval.get(maxEval.keySet().iterator().next()) <= eval) {
						maxEval.clear();
						maxEval.put(child.state.getAvailableMoves().iterator().next().source(), eval);
					}

					// if the branch you're currently working on gains a value larger than the other branch, then we're done with it, so we can break
					alpha = max(alpha, eval);
					if (beta <= alpha) {
						break;
					}
				}
			}

			parentNode.LocationAndScore.replace(parentNode.LocationAndScore.keySet().iterator().next(), maxEval.get(maxEval.keySet().iterator().next()));
			return parentNode.LocationAndScore;
		}

		// if it's the detective's turn, then they want to minimise
		else {
			Map<Integer, Integer> minEval = new HashMap<>();
			minEval.put(0, Integer.MAX_VALUE);

			if(!parentNode.children.isEmpty()) {

				for (TreeNode child : parentNode.children) {
					Integer location = child.LocationAndScore.keySet().iterator().next();
					Map<Integer,Integer> minimax = minimax(child, depth - 1, alpha, beta, true);
					int eval = minimax.get(location);
					int minEvalVal = minEval.get(minEval.keySet().iterator().next());

					if (minEvalVal >= eval) {
						minEval.clear();
						minEval.put(0, eval);
					}

					// if the branch you're currently working on gains a value larger than the other branch, then we're done with it, so we can break
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

	/**
	 *
	 * @param det the detective whose tickets we're checking
	 * @param transport the kind(s) of transport we're checking the ticket(s) for
	 * @param board the current gameState
	 * @return whether the given detective has the required tickets for a move
	 */
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

	/**
	 *
	 * @param move the move we want to find the destination of
	 * @return the destination of the given move
	 */
	@Nonnull private Integer getMoveDestination(Move move){
		return move.accept(new Move.Visitor<>() {

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

	/**
	 *
	 * @param destination MrX's current location in this state
	 * @param detective the detective we're finding the distance from
	 * @param board the current gameState
	 * @return the shortest distance between the detective and MrX, based on the available tickets of the detective
	 */
	@Nonnull @SuppressWarnings("UnstableApiUsage") private Integer shortestDistance(Integer destination, Piece.Detective detective, Board.GameState board){
		int source = 0;

		// get the detective's location
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
			}

			else {dist.add(i, Integer.MAX_VALUE);}
			visited.add(i, Boolean.FALSE);
			i++;
		}

		// while not all nodes have been visited
		boolean cont = true;
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

				if((dist.get(t) < minimum) && (!visited.get(t))){
					index = t;
					currentNode = nodesIterator.next();
					minimum = dist.get(t);
				}

				else {
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
					}

					else {
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

					else{
						cont = false;
					}
				}
			}
		}
		return dist.get(destination-1);
	}

	/**
	 *
	 * @return the name of the AI system
	 */
	@Nonnull @Override public String name() { return "Damien & Patrik's MrX"; }

	@Override
	public void onStart() {
		Ai.super.onStart();
	}

	@Override
	public void onTerminate() {
		Ai.super.onTerminate();
	}

	/**
	 *
	 * @param board the current gameState
	 * @param destinationMoves the list of available moves that lead MrX to one of the desired locations
	 * @return the same moves, now weighted based on how many of the given tickets are available
	 */
	@SuppressWarnings("UnstableApiUsage")
	private Map<Move, Integer> ticketWeighting(Board board, List<Move> destinationMoves){
		Map<Move,Integer> weightedMove = new HashMap<>();

		for(Move move : destinationMoves){
			Integer moveInt = (move.accept(new Move.Visitor<>() {
				// using visitor pattern to find how many of the wanted ticket are available
				@Override
				public Integer visit(Move.SingleMove move) {
					try {
						Optional<Board.TicketBoard> optTickets = board.getPlayerTickets(move.commencedBy());

						if (optTickets.isPresent()) {
							return optTickets.get().getCount(move.ticket);
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

		for(Move move : weightedMove.keySet()){

			for (Piece det : board.getPlayers()){

				if (det.isDetective()) {
					Optional<Integer> optDetLoc = board.getDetectiveLocation((Piece.Detective) det);

					if (optDetLoc.isPresent()) {
						int detLoc = optDetLoc.get();

						// for each move, if it's destination is adjacent to the detective's location, then it should be avoided
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

	/**
	 *
	 * @param tree the tree to be printed
	 */
	@SuppressWarnings("unused")
	private void printTree(TreeNode tree){
		System.out.println("Node "+tree.LocationAndScore+" has children: ");

		for(TreeNode child : tree.children){
			printTree(child);
		}
	}

	// searches through the tree and finds the moves which result in the given weight

	/**
	 *
	 * @param tree the game tree
	 * @param weight the score we're looking for in the tree
	 * @return the possible locations of MrX's first move that result in the desired score
	 */
	private Map<Integer, Integer> treeSearch(TreeNode tree, Integer weight){
		Map<Integer, Integer> nodeFound = new HashMap<>();

		for(TreeNode node: tree.children){

			if(node.LocationAndScore.get(node.LocationAndScore.keySet().iterator().next()).equals(weight)){
				nodeFound.put(node.LocationAndScore.keySet().iterator().next(), node.LocationAndScore.get(node.LocationAndScore.keySet().iterator().next()));
			}
		}
		return nodeFound;
	}

	/**
	 *
	 * @param board the current gameState
	 * @param timeoutPair how long each player has to make a move
	 * @return the move chosen by MrX
	 */
	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		//  creates the tree and evaluates it
		TreeNode tree = treeInitialiser((Board.GameState) board);
		Map<Integer, Integer> weight = minimax(tree, 4, Integer.MIN_VALUE, Integer.MAX_VALUE, true);

		// finds the best destination(s) in the tree
		Map<Integer, Integer> bestMove = treeSearch(tree, weight.get(weight.keySet().iterator().next()));
		List<Move> destinationMoves = new ArrayList<>();
		Map<Move,Integer> pickSecrets = new HashMap<>();
		Iterator<Move> moveIterator = board.getAvailableMoves().stream().iterator();

		while(moveIterator.hasNext()){
			Move newValue = moveIterator.next();

			for (Integer bestValue : bestMove.keySet()) {

				if (getMoveDestination(newValue).equals(bestValue)) {

					// should only add move which don't use secret tickets, as the weighting for these are separate
					if (newValue.accept(new Move.Visitor<>() {
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
					}

					else {
						pickSecrets.put(newValue, bestMove.get(bestValue));
					}
				}
			}
		}
		Map<Move, Integer> weightedMoves = ticketWeighting(board, destinationMoves);

		// select the best move after all weighting has been applied
		Map<Move,Integer> maxNum = new HashMap<>();
		Move maxNumMove = null;

		try {
			maxNum.put(destinationMoves.get(0), 0);
			maxNumMove = destinationMoves.get(0);
		} catch (Exception ignored){}

		// finds the move(s) with the best weighting
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

				if(!pickSecrets.isEmpty()) {
					Map<Move,Integer> bestSecret = new HashMap<>();
					bestSecret.put(maxNumMove,maxNum.get(maxNumMove));
					Move bestSecretMove = bestSecret.keySet().iterator().next();

					for (Move move : pickSecrets.keySet()) {

						if(move != null) {
							assert maxNumMove != null;

							if (getMoveDestination(move) >= getMoveDestination(maxNumMove)) {
								bestSecret.clear();
								bestSecret.put(move, pickSecrets.get(move));
								bestSecretMove = move;
							}
						}
					}
					return Objects.requireNonNull(bestSecretMove);
				}
			}
		}
		return Objects.requireNonNull(maxNumMove);
	}
}