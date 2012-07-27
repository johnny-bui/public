/*****************************************************************************
 * This file is part of the Prolog Development Tool (PDT)
 * 
 * WWW: http://sewiki.iai.uni-bonn.de/research/pdt/start
 * Mail: pdt@lists.iai.uni-bonn.de
 * Copyright (C): 2004-2012, CS Dept. III, University of Bonn
 * 
 * All rights reserved. This program is  made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 ****************************************************************************/

package org.cs3.prolog.internal.lifecycle;

import org.cs3.prolog.pif.PrologInterfaceException;

public class ErrorState extends AbstractState {

	private PrologInterfaceException error;
	private boolean shouldReset=false;

	public ErrorState(LifeCycle context, PrologInterfaceException e) {
		super(context);
		this.error = e;
	}

	
	@Override
	public PrologInterfaceException getError() {
		return error;
	}

	
	
	@Override
	public void enter() {		
		for (LifeCycleHookWrapper w : context.getHooks().values()) {
			w.onError();
		}
		context.enqueueWork(new NamedWorkRunnable("shutdown") {
			
			@Override
			public void run() throws PrologInterfaceException {
				try {
					context.disposeSessions();
					context.stopServer();
					context.workDone();
				} catch (Throwable e) {
					throw new PrologInterfaceException(e);
					
				}

			}
		});
	}

	
	@Override
	public State reset() {
		return new DownState(context);
//		shouldReset=true;
//		return this;
	}

	
	@Override
	public State workDone() {
		if(shouldReset){
			return new DownState(context);
		}
		return new Error2State(context,error);
	}

	
	@Override
	public State error(Throwable e) {
		return this; // ignore further errors.
	}

}


