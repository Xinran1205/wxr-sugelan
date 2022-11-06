package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Model.Observer;
import uk.ac.bris.cs.scotlandyard.model.Model.Observer.Event;

import java.util.HashSet;
import java.util.Set;

import static uk.ac.bris.cs.scotlandyard.model.Model.Observer.Event.*;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {
	Board.GameState gameState;
	private ImmutableSet<Observer> observers;
	@Nonnull @Override public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		gameState = new MyGameStateFactory().build(setup, mrX, detectives);
		observers = ImmutableSet.of();
		return model;
	}
	//make an inner class and implement the method of Model.
	Model model = new Model() {
		@Nonnull
		@Override
		public Board getCurrentBoard() {
			return gameState;
		}

		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if(observers.contains(observer)) throw new IllegalArgumentException();
			Set<Observer> regisObs = new HashSet<>(observers);
			// check if there is a observer equals to the registerObserver
			// only register when there is no observer equals to the registerObserver.
			int i=1;
			for(Observer o : observers){
				if(o.equals(observer)){
					i=0;
					break;
				}
			}
			if(i==1) {
				regisObs.add(observer);
			}
			observers = ImmutableSet.copyOf(regisObs);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if(observer == null) throw new NullPointerException();
			if(!observers.contains(observer)) throw new IllegalArgumentException();
			HashSet<Observer> unregisterObs = new HashSet<>(observers);
			for(Observer o : observers){
				if(o.equals(observer)){
					unregisterObs.remove(o);
					break;
				}
			}
			observers = ImmutableSet.copyOf(unregisterObs);
		}

		@Nonnull
		@Override
		public ImmutableSet<Observer> getObservers() {
			return observers;
		}

		@Override
		public void chooseMove(@Nonnull Move move) {
			gameState = gameState.advance(move);
			Event i;
			if(gameState.getWinner().isEmpty()){
				i = MOVE_MADE;
			}else{
				i = GAME_OVER;
			}
			for(Observer o : observers){
				//gameState is the board at the time of change
				//i is the event that triggered this call
				o.onModelChanged(gameState,i);
			}
		}
	};
}
