package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class MyAi implements Ai {

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
		//System.out.println(dist.get(destination));
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

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// iterate through MrX's possible moves (will be stored in .availableMoves() when it is his turn)
		ImmutableSet<Move> mrXMoves = board.getAvailableMoves();
		Iterator<Move> mrXIterator = mrXMoves.iterator();
		int newWeight = 0;
		int moveWeights = 0;
		List<Move> currentMove = new ArrayList<>();
		Move newMove;
		while(mrXIterator.hasNext()) {
			int weight = Integer.MAX_VALUE;
			newMove = mrXIterator.next();
			if(!newMove.accept(new Move.Visitor<ScotlandYard.Ticket>() {
				@Override
				public ScotlandYard.Ticket visit(Move.SingleMove move) {
					return move.ticket;
				}

				@Override
				public ScotlandYard.Ticket visit(Move.DoubleMove move) {
					if(move.ticket1.equals(ScotlandYard.Ticket.SECRET)){
						return move.ticket1;
					} else {
						return move.ticket2;
					}
				}
			}).equals(ScotlandYard.Ticket.SECRET)) {
				//System.out.println(newMove);
				// for each move, iterate through the detectives and find the distance between the move's destination and that det
				for (Piece det : board.getPlayers()) {
					if (det.isDetective()) {
						newWeight = shortestDistance(newMove.accept(new Move.Visitor<Integer>() {

							@Override
							public Integer visit(Move.SingleMove move) {
								return move.destination;
							}

							@Override
							public Integer visit(Move.DoubleMove move) {
								return move.destination2;
							}
						}), (Piece.Detective) det, board);
						// for that move, select the detective with the shortest distance
						if (newWeight < weight) {
							weight = newWeight;
						}
					}
				}


				int count = 0;
				for (ScotlandYard.Ticket t : newMove.tickets()){
					count += 1;
				}
				// after iteration, pick the move with the longest 'minimal' distance
				if(weight > moveWeights){
					// removes the double moves from the rotation, as they should be saved for when necessary
					if(count != 3) {
						// this doesn't seem to update sometimes, so the selected moves don't fit 
						moveWeights = weight;
						currentMove.clear(); // Emptying list
						currentMove.add(newMove);
					}
				} else if (weight == moveWeights){
					if (count != 3) {
						currentMove.add(newMove);
					}
				}

				System.out.println("Move: "+newMove + ", weight: " + newWeight);
				//System.out.println("Moves: " +currentMove);
			}

		}
		System.out.println(moveWeights);
		// we now have a list of available moves that maximise the detective distance (currentMove)
		if (currentMove.size() == 1){
			System.out.println("only 1 available move, "+currentMove);
			return currentMove.get(0);
		}
		// assuming the list has multiple elements...
		// now we need to do some checks to find the best one for the situation:
			// we want to weight the moves based on how many of the required ticket(s) we have
			// if MrX is in a corner area (need to define this), then he should prioritise a move which heads to the centre
			// if MrX is within a short distance of 79 or 7, then he should take the move which leads towards or away from it respectively
			// only play double moves if they're the only ones available
		// a list of pairs containing how many of the required ticket MrX has, and the move itself
		List<Pair<Integer, Move>> ticketWeighted = new ArrayList<>();
		for(Move move : currentMove){
			ticketWeighted.add(new Pair<>(move.accept(new Move.Visitor<Integer>() {
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
			}), move));
		}
		Pair<Integer, Move> maxNum = new Pair<>(0, currentMove.get(0));
		for(Pair<Integer, Move> move : ticketWeighted){
			// if the player has more of one kind of ticket, then it should be picked
			if(move.left() >= maxNum.left()){
				maxNum = move;
			}
		}
		System.out.println("Moves weighted: "+ticketWeighted);


		return Objects.requireNonNull(maxNum.right());
	}
}
