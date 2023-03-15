package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.collect.Iterables.size;

public class MyAi implements Ai {

	private boolean ticketmaster(Piece det, ImmutableSet<ScotlandYard.Transport> transport, Board board){
		// check to see if the given piece has a ticket that allows them to move in this direction
		for(ScotlandYard.Transport t : transport){
			// need to use t.requiredTicket()
			Board.TicketBoard tickets = null;
			if(board.getPlayerTickets(det).isPresent()){
				tickets = board.getPlayerTickets(det).get();
				if(tickets.getCount(t.requiredTicket()) > 0){
					return true;
				}
			}
		}
		return false;
	}
	@Nonnull private Integer shortestDistance(Integer destination, Piece.Detective detective, Board board, Move move){
		PriorityQueue<Integer> pq = new PriorityQueue<>();
		// get the detective's location
		int source = 0;
		if(board.getDetectiveLocation(detective).isPresent()){
			source = board.getDetectiveLocation((Piece.Detective) detective).get();
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
			Iterator<Integer> successIterator = successors.iterator();
			// go through successors to assess weights
			while(successIterator.hasNext()){
				AtomicInteger weight = new AtomicInteger();
				Integer successor = successIterator.next();
				Iterator<Integer>nodesIterator2 = nodes.iterator();
				int count = 0;
				while(nodesIterator2.hasNext()){
					if(successor.equals(nodesIterator2.next())){
						break;
					} else {
						count++;
					}
				}
				// need to change this section for transport types
				//board.getSetup().graph.edgeValue(currentNode, successor).ifPresent(weight::set);
				if(ticketmaster(detective, board.getSetup().graph.edgeValue(currentNode, successor).get(), board)) {
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

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// iterate through MrX's possible moves (will be stored in .availableMoves() when it is his turn)
		ImmutableSet<Move> mrXMoves = board.getAvailableMoves();
		Iterator<Move> mrXIterator = mrXMoves.iterator();
		int weight = Integer.MAX_VALUE;
		int newWeight = 0;
		Move currentMove = null;
		int moveWeights = 0;
		Move newMove = null;
		while(mrXIterator.hasNext()) {
			newMove = mrXIterator.next();

			// for each move, iterate through the detectives and find the distance between the move's destination and that det
			for (Piece det : board.getPlayers()) {
				if(det.isDetective()) {
					newWeight = shortestDistance(newMove.accept(new Move.Visitor<Integer>() {

						@Override
						public Integer visit(Move.SingleMove move) {
							return move.destination;
						}

						@Override
						public Integer visit(Move.DoubleMove move) {
							return move.destination2;
						}
					}), (Piece.Detective) det, board, newMove);
					// for that move, select the detective with the shortest distance
					if (newWeight < weight) {
						weight = newWeight;
					}
				}
			}
			// after iteration, pick the move with the longest 'minimal' distance
			if(weight > moveWeights){
				moveWeights = weight;
				currentMove = newMove;
			}

		}


		return Objects.requireNonNull(currentMove);
	}
}
