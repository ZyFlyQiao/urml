package ca.queensu.cs.mase.interpreter;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtext.EcoreUtil2;

import com.google.inject.Inject;

import ca.queensu.cs.mase.urml.Capsule;
import ca.queensu.cs.mase.urml.StateMachine;
import ca.queensu.cs.mase.urml.State_;
import ca.queensu.cs.mase.urml.Statement;
import ca.queensu.cs.mase.urml.Transition;
import ca.queensu.cs.mase.util.Value;

public class StateMachineTraverser {

	/**
	 * The output stream
	 */
	private PrintStream out;

	// @Inject
	// private Logger logger;

	/**
	 * The input stream
	 */
	private BufferedReader in;

	/**
	 * An enum telling us what to do when we encounter non-deterministic choice
	 * when we encounter multiple enabled transitions at the same time
	 */
	private ExecutionConfig config;

	/**
	 * Constructor
	 * 
	 * @param in
	 *            input stream from the client Eclipse's console view
	 * @param out
	 *            output stream from the client Eclipse's console view
	 * @param config
	 *            execution config for the interpreter
	 */
	public StateMachineTraverser(BufferedReader in, PrintStream out,
			ExecutionConfig config) {
		this.in = in;
		this.out = out;
		this.config = config;
	}

	/**
	 * Attempts to exeute the next state. If the current state is already a
	 * final state, simply returns false. However, if otherwise, the next state
	 * is found and the current state is set as the next state, and returns
	 * true.
	 * 
	 * @param ctx
	 *            the current capsule context, which contains a reference to the
	 *            current state
	 * @return true if the current state is not a final state; otherwise returns
	 *         false.
	 */
	public boolean executeNextState(CapsuleContext ctx) {
		if (ctx.getCurrentState() == null) {
			// can't find the current state; assume we are getting into the
			// first state
			findExecuteFirstState(ctx);
			return true;
		} else {
			// find the next state
			return findExecuteNextState(ctx);
		}
	}

	private void findExecuteFirstState(CapsuleContext ctx) {
		State_ firstState = findFirstState(ctx);
		if (firstState == null) {
			return;
		}
		ctx.setPreviousState(null);
		ctx.setCurrentState(firstState);
		runEntryCodeForState(firstState, ctx);
	}

	@Nullable
	private State_ findFirstState(CapsuleContext ctx) {
		Capsule c = ctx.getCapsule();
		StateMachine sm = c.getStatemachines().get(0);
		Transition init = findInitialTransition(sm);
		if (init == null) {
			return null;
		}
		runActionForTransition(init, ctx);
		return init.getTo();
	}

	/**
	 * Finds and executes the next state for the capsule in the capsule context
	 * {@code ctx} and returns true, only if the current state is not a final
	 * state. If the current state is already a final state, this method simply
	 * returns false.
	 * 
	 * @param ctx
	 *            the current capsule context
	 * @return true if the current state is not a final state; otherwise false
	 */
	private boolean findExecuteNextState(CapsuleContext ctx) {
		State_ currentState = ctx.getCurrentState();
		// if current state is a final state, simply return false
		if (currentState.isFinal()) {
			return false;
		}

		StateMachine subSm = currentState.getSubstatemachine();
		if (subSm != null) {
			// the current state has a sub-state machine. Enter into that
			// sub-state machine.
			Transition init = findInitialTransition(subSm);
			if (init == null) {
				return true;
			}
			runActionForTransition(init, ctx);
			ctx.setPreviousState(ctx.getCurrentState());
			ctx.setCurrentState(init.getTo());
			// logState(ctx.getCurrentState(), ctx);
			runEntryCodeForState(ctx.getCurrentState(), ctx);
		} else {
			// the current state does not have a sub-state machine.
			// just find the next transition from the current state.
			Transition currentTransition = findNextTransition(ctx);
			if (currentTransition == null) {
				return true;
			}
			runExitActionEntryCode(currentTransition, ctx);
			State_ toState = currentTransition.getTo();
			ctx.setCurrentState(toState);

			// logState(ctx.getCurrentState(), ctx);
		}
		return true;
	}

	// /**
	// * Executes the given state {@code StateTarget} by going through its entry
	// * and exit code
	// *
	// * @param state
	// * the state terminal whose entry and exit code is to be
	// * executed. Note that this is done only when the
	// * {@code StateTarget} is of class {@link StateTarget} (i.e. the
	// * terminal has a state in it), even though we are accepting
	// * {@link TransitionTarget} here.
	// * @param ctx
	// * the stored and persistent information relevant to the current
	// * capsule
	// */
	// private void logState(State_ state, CapsuleContext ctx) {
	// out.println(ctx.getRefName() + "   state: " + state.getName());
	// }

	/**
	 * Finds the first appearing initial transition in the state machine
	 * {@code sm}. Note that we assume that there is no guard in the initial
	 * transition and as such guard expressions in an initial transition are not
	 * evaluated.
	 * 
	 * @param sm
	 *            the state machine whose initial transition is to be found
	 * @return the first appearing initial transition in {@code sm}, or
	 *         {@code null} if no initial transition can be found
	 */
	@Nullable
	private Transition findInitialTransition(StateMachine sm) {
		Capsule c = EcoreUtil2.getContainerOfType(sm, Capsule.class);
		if (c != null) {
			for (Transition t : sm.getTransitions()) {
				if (t.isInit()) {
					Transition initial = t;
					// out.println(c.getName() + "   initial transition: "
					// + initial.getName() + " to "
					// + initial.getTo().getName());
					return initial;
				}
			}
		}
		return null;
	}

	/**
	 * Execute the action code for the transition {@code transition}
	 * 
	 * @param transition
	 *            transition whose action code is to be executed
	 * @param ctx
	 *            persistent information relevant to the currently running
	 *            capsule
	 */
	private void runActionCodeForTransition(Transition transition,
			CapsuleContext ctx) {
		if (transition.getAction() != null) {
			// out.println(ctx.getName() + "      running action code for "
			// + transition.getName());
			execute(transition.getAction().getStatements(), ctx);
		}
		if (ctx.getTriggerVars() != null) {
			ctx.setTriggerVars(null);
		}
	}

	/**
	 * Execute the entry code for the state {@code terminal}
	 * 
	 * @param terminal
	 *            state terminal whose entry code is to be executed
	 * @param ctx
	 *            persistent information relevant to the currently running
	 *            capsule
	 */
	private void runEntryCodeForState(State_ state, CapsuleContext ctx) {
		if (state.getEntryCode() != null) {
			// out.println(ctx.getName() + "      running entry code for "
			// + state.getName());
			execute(state.getEntryCode().getStatements(), ctx);
		}
	}

	/**
	 * Execute the exit code for the state {@code terminal}
	 * 
	 * @param terminal
	 *            state terminal whose exit code is to be executed
	 * @param ctx
	 *            persistent information relevant to the currently running
	 *            capsule
	 */
	private void runExitCodeForState(State_ state, CapsuleContext ctx) {
		if (state.getExitCode() != null) {
			// out.println(ctx.getName() + "      running exit code for "
			// + state.getName());
			execute(state.getExitCode().getStatements(), ctx);
		}
	}

	/**
	 * Given a list of statements, execute it as a whole --- with a single scope
	 * (i.e., a call stack is made for this unit of statement list)
	 * 
	 * @param statements
	 *            a list of statements
	 * @param ctx
	 *            persistent information relevant to the currently running
	 *            capsule
	 */
	private void execute(EList<Statement> statements, CapsuleContext ctx) {
		ctx.getCallStack().push(new HashMap<String, Value>());
		for (Statement st : statements) {
			new StatementExecuter().interpret(st, ctx);
		}
		ctx.getCallStack().pop();
	}

	/**
	 * Goes through the current state and all its ancestor states, and then find
	 * the outgoing transitions from such states. After that, filter out the
	 * transitions such that those that pass the guard and are part of the
	 * trigger are selected. After that, choose the enabled transition based on
	 * the execution config. Such a transition will be selected as the next
	 * transition and will be returned.
	 * 
	 * @param ctx
	 *            the current capsule instance
	 * @return the next transition to be executed
	 */
	@Nullable
	private Transition findNextTransition(CapsuleContext ctx) {
		State_ stateToGoThrough = ctx.getCurrentState();
		List<Transition> candidateEnabledTrans = new ArrayList<>();
		do {
			// get the outgoing transitions from stateToGoThrough
			Collection<Transition> outgoingTransitions = ctx
					.getOutgoingTransitions().get(stateToGoThrough);
			// add those outgoing transitions to the candidate enabled
			// transition list
			candidateEnabledTrans.addAll(outgoingTransitions);
			// get current state's ancestors
			stateToGoThrough = EcoreUtil2.getContainerOfType(
					stateToGoThrough.eContainer(), State_.class);
		} while (stateToGoThrough != null);

		TransitionFilterer f = new TransitionFilterer(in, out, config);
		Transition[] withoutCheck = candidateEnabledTrans
				.toArray(new Transition[0]);
		// only the transitions that pass the guard remain
		Transition[] withGuard = f.filterGuard(withoutCheck, ctx);
		// only the transitions that are part of the trigger remain
		Transition[] withGuardTrigger = f.filterTrigger(withGuard, ctx);
		// select the next transition based on the execution config
		return f.chooseNextTransition(withGuardTrigger, ctx);
	}

	private Deque<State_> getStateWithAncestors(State_ state) {
		Deque<State_> stateWithParents = new LinkedList<>();
		while (state != null) {
			stateWithParents.push(state);
			State_ parent = EcoreUtil2.getContainerOfType(state.eContainer(),
					State_.class);
			state = parent;
		}
		return stateWithParents;
	}

	private void removeCommonAncestors(Deque<State_> fromWithParents,
			Deque<State_> toWithParents) {
		State_ from;
		State_ to;
		// pop off the common ancestors of "to" and "from"
		while (true) {
			from = fromWithParents.peek();
			to = toWithParents.peek();
			if (from != to || fromWithParents.isEmpty()
					|| toWithParents.isEmpty()) {
				break;
			}
			fromWithParents.pop();
			toWithParents.pop();
		}
	}

	private void runExitActionEntryCode(Transition t, CapsuleContext ctx) {
		State_ from = t.getFrom();
		State_ to = t.getTo();
		Deque<State_> fromWithAncestors = getStateWithAncestors(from);
		Deque<State_> toWithAncestors = getStateWithAncestors(to);
		removeCommonAncestors(fromWithAncestors, toWithAncestors);

		while (!fromWithAncestors.isEmpty()) {
			State_ toLeave = fromWithAncestors.removeLast();
			runExitCodeForState(toLeave, ctx);
		}

		runActionForTransition(t, ctx);

		while (!toWithAncestors.isEmpty()) {
			State_ toEnter = toWithAncestors.pop();
			runEntryCodeForState(toEnter, ctx);
		}
	}

	/**
	 * Executes the given transition {@code currentTransition} by going through
	 * its action code.
	 * 
	 * @param currentTransition
	 *            the transition to be executed
	 * @param ctx
	 *            persistent information relevant to the current capsule
	 */
	private void runActionForTransition(Transition currentTransition,
			CapsuleContext ctx) {

		// State_ from = currentTransition.getFrom();
		// State_ to = currentTransition.getTo();

		// out.println(ctx.getName() + "   transition: "
		// + currentTransition.getName() + " from "
		// + (from == null ? " null " : from.getName()) + " to "
		// + (to == null ? " null " : to.getName()));
		runActionCodeForTransition(currentTransition, ctx);

	}
}
