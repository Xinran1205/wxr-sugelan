package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

import static uk.ac.bris.cs.scotlandyard.model.LogEntry.*;
/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives){
		if(setup.rounds.isEmpty()) throw new IllegalArgumentException("Rounds is empty!");
		if(setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("graph is empty!");
		if(mrX==null) throw new NullPointerException("No mrX!");
		if(detectives.isEmpty()) throw new NullPointerException("detectives are empty!");
		if(detectives.contains(null)) throw new NullPointerException("detectives contains null!");
		if(mrX.isDetective()) throw new IllegalArgumentException("no mrX!");
		for(Player a : detectives){
			if ((a.hasAtLeast(Ticket.DOUBLE,1))
					|| (a.hasAtLeast(Ticket.SECRET,1 ))
					|| (a.isMrX()))throw new IllegalArgumentException();
		}
		for(Player a:detectives){
			List<Player> detective = new ArrayList<>(detectives);
			detective.remove(a);
			for(Player b : detective){
				if(a.piece().equals(b.piece())){
					throw new IllegalArgumentException("name problem");
				}
				if(a.location()== b.location()){
					throw new IllegalArgumentException("location problem");
				}
			}
		}
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}

	private static final class MyGameState implements GameState {
		private final GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private final ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		private MyGameState(
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives) {
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.winner = ImmutableSet.of();
			this.moves =  getAvailableMoves();
			this.winner = getWinner();
		}

		@Nonnull
		@Override
		public GameSetup getSetup(){
			return setup;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers(){
			Set<Piece> a = new HashSet<>();
			a.add(mrX.piece());
			for(Player b : detectives){
				a.add(b.piece());
			}
			return ImmutableSet.copyOf(a);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Detective detective){
			for (Player a : detectives) {
				if (a.piece().webColour().equals( detective.webColour())){
					return Optional.of(a.location());
				}
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece){
			List<Player> a = new ArrayList<>(detectives);
			a.add(0,mrX);
			for (int n=0;n<a.size();n++) {
				if (a.get(n).piece().webColour().equals(piece.webColour())){
					int index = n;
					class ABC implements TicketBoard{
						@Override
						public int getCount(@Nonnull Ticket ticket){
							int c;
							c = a.get(index).tickets().getOrDefault(ticket, 0);
							return c;
						}
					}
					TicketBoard TTicket = new ABC();
					return Optional.of(TTicket);
				}
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog(){
			return log;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner(){
			//if detectives do not have tickets or remaining is empty, mrX win.
			int count=0;
			for (Player a : detectives){
				if (a.has(Ticket.TAXI) || a.has(Ticket.BUS) || a.has(Ticket.UNDERGROUND)) {
					count=1;
				}
			}
			if(remaining.isEmpty()||count==0){
				return ImmutableSet.of(mrX.piece());
			}
			//when detectives location equals to mrx location, detective win
			for (Player b:detectives){
				if(b.location() == mrX.location()){
					return detectiveWin();
				}
			}
			//mrX can not move anymore
			if (remaining.contains(mrX.piece()) && moves.isEmpty()){
				return  detectiveWin();
			}
			return ImmutableSet.of();
		}

		// a method which return the set if detectiveWin
		private ImmutableSet<Piece> detectiveWin(){
			ImmutableSet.Builder<Piece> detectiveWinner = ImmutableSet.builder();
			for (Player a: detectives) {
				detectiveWinner.add(a.piece());
			}
			return detectiveWinner.build();
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves(){
			ImmutableSet.Builder<Move> moveBuilder = ImmutableSet.builder();
			if(!winner.isEmpty()) return ImmutableSet.of();
			if (remaining.contains(mrX.piece())) {
				moveBuilder.addAll(makeSingleMoves(setup, detectives, mrX, mrX.location()));
				moveBuilder.addAll(makeDoubleMoves(setup, detectives, mrX, mrX.location()));
			} else {
				for (int n = 0; n < detectives.size(); n++) {
					if (remaining.contains(detectives.get(n).piece())) {
						moveBuilder.addAll(makeSingleMoves(setup, detectives, detectives.get(n),
								detectives.get(n).location()));
					}
				}
			}
			return moveBuilder.build();
		}

		@Override
		public GameState advance(Move move){
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);
			Visitor<Boolean> isDoubleMove = new Visitor<>() {
				@Override
				public Boolean visit(SingleMove move) {
					return false;
				}

				@Override
				public Boolean visit(DoubleMove move) {
					for (Ticket t : move.tickets()) {
						if (t.equals(Ticket.DOUBLE)) return true;
					}
					return false;
				}
			};
			newRemainingPiece(move);
			newTicket(move);
			newLocation(move,move.visit(isDoubleMove));
			return new MyGameState(setup, remaining, log, mrX, detectives);
		}
		private void newLocation(Move move, Boolean isDouble){
			// use the visitor to distinguish the first destination whether is singleMove or doubleMove
			Visitor<Integer> visitFirstDestination = new Visitor<>() {
				@Override
				public Integer visit(Move.SingleMove singleMove) { return singleMove.destination; }
				@Override
				public Integer visit(Move.DoubleMove doubleMove) { return doubleMove.destination1; }
			};
			// use the visitor to distinguish the second destination whether is singleMove or doubleMove
			// if it is singleMove, it still return destination
			Visitor<Integer> visitSecondDestination = new Visitor<>() {
				@Override
				public Integer visit(Move.SingleMove singleMove) { return singleMove.destination; }
				@Override
				public Integer visit(Move.DoubleMove doubleMove) { return doubleMove.destination2; }
			};
			// use the visitor to distinguish the first ticket whether is singleMove or doubleMove
			Visitor<Ticket> visitFirstTicket = new Visitor<>() {
				@Override
				public Ticket visit(Move.SingleMove singleMove) { return singleMove.ticket; }
				@Override
				public Ticket visit(Move.DoubleMove doubleMove) { return doubleMove.ticket1; }
			};
			//use the visitor to distinguish the second ticket whether is singleMove or doubleMove
			Visitor<Ticket> visitSecondTicket = new Visitor<>() {
				@Override
				public Ticket visit(Move.SingleMove singleMove) { return null; }
				@Override
				public Ticket visit(Move.DoubleMove doubleMove) { return doubleMove.ticket2; }
			};

			//to check whether this is detective move
			if(move.commencedBy().isDetective()){
				ArrayList<Player> newDetectives = new ArrayList<>();
				for (final Player i : detectives){
					if(i.piece().equals(move.commencedBy())) {
						Player movedDetective = i.at(move.visit(visitFirstDestination));
						newDetectives.add(movedDetective);
					}else{
						newDetectives.add(i);
					}
					detectives = List.copyOf(newDetectives);
				}
			}


			//to check whether this is mrX move
			if(move.commencedBy().isMrX()) {
				// this below is to update mrx log.
				ArrayList<LogEntry> updateLog = new ArrayList<>(log);
				boolean thisRoundShow = false, nextRoundShow = false;
				int round = 0;
				// checks if the current and next rounds are reveal or not, using the log as round count
				for (Boolean i : setup.rounds){
					round ++;
					if (round == log.size() + 1) thisRoundShow = i;
					else if (round == log.size() + 2) nextRoundShow = i;
				}
				//six cases:  1. this is a double move and this round and next round are both shows
				//            2. this is a double move and this round is show but next round is hidden
				//            3. this is a double move and this round is hidden but next round is show
				//            4. this is a double move and this round and next round are both hidden
				//            5. this is not a double move and this round is show
				//            6. this is not a double move and this round is hidden
				if(isDouble){
					if(thisRoundShow){
						updateLog.add(reveal(move.visit(visitFirstTicket), move.visit(visitFirstDestination)));
						if(nextRoundShow){
							updateLog.add(reveal(move.visit(visitSecondTicket), move.visit(visitSecondDestination)));
						}else{
							updateLog.add(hidden(move.visit(visitSecondTicket)));
						}
					}else{
						if(nextRoundShow){
							updateLog.add(hidden(move.visit(visitFirstTicket)));
							updateLog.add(reveal(move.visit(visitSecondTicket), move.visit(visitSecondDestination)));
						}else{
							updateLog.add(hidden(move.visit(visitFirstTicket)));
							updateLog.add(hidden(move.visit(visitSecondTicket)));
						}
					}
				}else{
					if(thisRoundShow){
						updateLog.add(reveal(move.visit(visitFirstTicket), move.visit(visitSecondDestination)));
					}else{
						updateLog.add(hidden(move.visit(visitFirstTicket)));
					}
				}
				if(isDouble) {
					mrX = mrX.at(move.visit(visitSecondDestination));
				}else{
					mrX = mrX.at(move.visit(visitFirstDestination));
				}
				log = ImmutableList.copyOf(updateLog);
			}
		}

		//a method which is used in advance method to update the ticket after moving
		private void newTicket(Move move){
			List<Player> currentDetective = new ArrayList<>(detectives);
			if(move.commencedBy().equals(mrX.piece())){
				mrX = mrX.use(move.tickets());
			}else{
				for(Player d : detectives) {
					if (move.commencedBy().equals(d.piece())) {
						currentDetective.remove(d);
						currentDetective.add(d.use(move.tickets()));
						mrX = mrX.give(move.tickets());
						detectives = List.copyOf(currentDetective);
						break;
					}
				}
			}
		}


		//a method which is used in advance method to update the remaining piece after moving
		private void newRemainingPiece(Move move) {
			HashSet<Piece> remainingPiece = new HashSet<>();
			//if this move is mrx it means the remaining pieces are detectives
			if (move.commencedBy().equals(mrX.piece())) {
				for (Player d : detectives) {
					remainingPiece.add(d.piece());
				}
			} else {
				for (Player d : detectives) {
					if (remaining.contains(d.piece()) &&
							(!move.commencedBy().equals(d.piece())) &&
							(d.has(Ticket.TAXI) || d.has(Ticket.BUS) || d.has(Ticket.UNDERGROUND))) {
						remainingPiece.add(d.piece());
					}
				}
			}
			if(!remainingPiece.isEmpty()) remaining = ImmutableSet.copyOf(remainingPiece);
			else if(log.size() == setup.rounds.size()) remaining = ImmutableSet.of(); // game over
			else remaining = ImmutableSet.of(mrX.piece()); //go next round;
		}
	}

	private static ImmutableSet<SingleMove> makeSingleMoves(
			GameSetup setup,
			List<Player> detectives,
			Player player,
			int source){
		final var singleMoves = new ArrayList<SingleMove>();
		for(int destination : setup.graph.adjacentNodes(source)) {
			// TODO find out if destination is occupied by a detective
			//  if the location is occupied, don't add to the list of moves to return
			int i=0;
			for(Player a:detectives){
				if(a.location()==destination){
					i=1;
					break;
				}
			}
			if(i==1){
				continue;
			}
			for(Transport t : setup.graph.edgeValueOrDefault(source,destination,ImmutableSet.of())) {
				// TODO find out if the player has the required tickets
				//  if it does, construct SingleMove and add it the list of moves to return
				if(player.hasAtLeast(t.requiredTicket(),1)){
					SingleMove single = new SingleMove(player.piece(),source,t.requiredTicket(),destination);
					singleMoves.add(single);
				}
			}
			// TODO consider the rules of secret moves here
			//  add moves to the destination via a secret ticket if there are any left with the player
			if(player.hasAtLeast(Ticket.SECRET,1)){
				SingleMove singleMo = new SingleMove(player.piece(),source,Ticket.SECRET,destination);
				singleMoves.add(singleMo);
			}
		}
		return ImmutableSet.copyOf(singleMoves);
	}

	private static ImmutableSet<DoubleMove> makeDoubleMoves(
			GameSetup setup,
			List<Player> detectives,
			Player player,
			int source){
		final var doubleMoves = new ArrayList<DoubleMove>();
		if(player.hasAtLeast(Ticket.DOUBLE,1)&&setup.rounds.size()>1) {
			// check if destination is occupied by a detective
			for (int destination : setup.graph.adjacentNodes(source)) {
				int i = 0;
				for (Player a : detectives) {
					if (a.location() == destination) {
						i = 1;
						break;
					}
				}
				if (i == 1) {
					continue;
				}
				//keep the initial source and the initial player
				//because I change the player in below by using ticket so I need to set the initial player back before the next loop
				int initialSource = source;
				Player initialPlayer = player;
				//four cases : 1. the first time and the second time both do not use secret ticket
				//             2. the first time do not use secret ticket but the second time use secret ticket
				//             3. the first time use the secret ticket but the second time do not use secret ticket
				//             4. the first time and the second time bot use the secret ticket
				for(Transport t : setup.graph.edgeValueOrDefault(source,destination,ImmutableSet.of())) {
					if(player.hasAtLeast(t.requiredTicket(),1)) {
						//update the player and use that ticket(minus 1)
						player = new Player(player.piece(), player.tickets(), destination);
						player = player.use(t.requiredTicket());
						source = destination;
						//use the second ticket
						//check the second destination is free
						for (int destination2 : setup.graph.adjacentNodes(source)) {
							int signal2 = 0;
							for (Player a : detectives) {
								if (a.location() == destination2) {
									signal2 = 1;
									break;
								}
							}
							if (signal2 == 1) {
								continue;
							}
							for (Transport d : setup.graph.edgeValueOrDefault(source, destination2, ImmutableSet.of())) {
								if (player.hasAtLeast(d.requiredTicket(), 1)) {
									DoubleMove doubleMo = new DoubleMove(player.piece(), initialSource,
											t.requiredTicket(), source,
											d.requiredTicket(), destination2);
									doubleMoves.add(doubleMo);
								}
							}
							if(player.hasAtLeast(Ticket.SECRET,1)){
								DoubleMove doubleMo = new DoubleMove(player.piece(), initialSource,
										t.requiredTicket(), source,
										Ticket.SECRET, destination2);
								doubleMoves.add(doubleMo);
							}
						}
						player = initialPlayer;
						source = initialSource;
					}
				}
				if(player.hasAtLeast(Ticket.SECRET,1)){
					player = new Player(player.piece(), player.tickets(), destination);
					player = player.use(Ticket.SECRET);
					source = destination;
					for (int destination2 : setup.graph.adjacentNodes(source)) {
						int signal2 = 0;
						for (Player a : detectives) {
							if (a.location() == destination2) {
								signal2 = 1;
								break;
							}
						}
						if (signal2 == 1) {
							continue;
						}
						for (Transport d : setup.graph.edgeValueOrDefault(source, destination2, ImmutableSet.of())) {
							if (player.hasAtLeast(d.requiredTicket(), 1)) {
								DoubleMove doubleMo = new DoubleMove(player.piece(), initialSource,
										Ticket.SECRET, source,
										d.requiredTicket(), destination2);
								doubleMoves.add(doubleMo);
							}
						}
						if(player.hasAtLeast(Ticket.SECRET,1)){
							DoubleMove doubleMo = new DoubleMove(player.piece(), initialSource,
									Ticket.SECRET, source,
									Ticket.SECRET, destination2);
							doubleMoves.add(doubleMo);
						}
					}
					player = initialPlayer;
					source = initialSource;
				}
			}
		}
		return ImmutableSet.copyOf(doubleMoves);
	}
}
