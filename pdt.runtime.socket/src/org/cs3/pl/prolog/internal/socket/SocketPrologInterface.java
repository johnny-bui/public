/*****************************************************************************
 * This file is part of the Prolog Development Tool (PDT)
 * 
 * Author: Lukas Degener (among others) 
 * E-mail: degenerl@cs.uni-bonn.de
 * WWW: http://roots.iai.uni-bonn.de/research/pdt 
 * Copyright (C): 2004-2006, CS Dept. III, University of Bonn
 * 
 * All rights reserved. This program is  made available under the terms 
 * of the Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * In addition, you may at your option use, modify and redistribute any
 * part of this program under the terms of the GNU Lesser General Public
 * License (LGPL), version 2.1 or, at your option, any later version of the
 * same license, as long as
 * 
 * 1) The program part in question does not depend, either directly or
 *   indirectly, on parts of the Eclipse framework and
 *   
 * 2) the program part in question does not include files that contain or
 *   are derived from third-party work and are therefor covered by special
 *   license agreements.
 *   
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *   
 * ad 1: A program part is said to "depend, either directly or indirectly,
 *   on parts of the Eclipse framework", if it cannot be compiled or cannot
 *   be run without the help or presence of some part of the Eclipse
 *   framework. All java classes in packages containing the "pdt" package
 *   fragment in their name fall into this category.
 *   
 * ad 2: "Third-party code" means any code that was originaly written as
 *   part of a project other than the PDT. Files that contain or are based on
 *   such code contain a notice telling you so, and telling you the
 *   particular conditions under which they may be used, modified and/or
 *   distributed.
 ****************************************************************************/

package org.cs3.pl.prolog.internal.socket;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.cs3.pdt.runtime.PrologRuntimePlugin;
import org.cs3.pl.common.Debug;
import org.cs3.pl.common.ResourceFileLocator;
import org.cs3.pl.prolog.AsyncPrologSession;
import org.cs3.pl.prolog.PrologInterface;
import org.cs3.pl.prolog.PrologInterfaceException;
import org.cs3.pl.prolog.PrologInterfaceFactory;
import org.cs3.pl.prolog.PrologSession;
import org.cs3.pl.prolog.ServerStartAndStopStrategy;
import org.cs3.pl.prolog.internal.AbstractPrologInterface;
import org.cs3.pl.prolog.internal.ReusablePool;
import org.eclipse.jface.preference.IPreferenceStore;

public class SocketPrologInterface extends AbstractPrologInterface {

	

	
	
	private class InitSession extends SocketSession {
		public InitSession(SocketClient client, AbstractPrologInterface pif,int flags)
				throws IOException {
			super(client, pif,flags);
		}

		public void dispose() {
			Debug.warning("Ignoring attempt to dispose an initial session!");
			Debug.warning("called from here:");
			Thread.dumpStack();
		}

		public void doDispose() {
			super.dispose();
		}
	}

	private class ShutdownSession extends SocketSession {
		public ShutdownSession(SocketClient client, AbstractPrologInterface pif, int flags)
				throws IOException {
			super(client, pif,flags);
		}

		public void dispose() {
			Debug.warning("Ignoring attempt to dispose a shutdown session!");
			Debug.warning("called from here:");
			Thread.dumpStack();
		}

		public void doDispose() {
			super.dispose();
		}
	}


	/************************************************/
	/**** Options [Start] *****/
	/************************************************/
	

	public static final String PREF_KILLCOMMAND = "pif.killcommand";
	public static final String PREF_PORT = "pif.port";
	public static final String PREF_HIDE_PLWIN = "pif.hide_plwin";
	//public static final String PREF_ENGINE_FILE = "pif.engine_file";
	public static final String PREF_MAIN_FILE = "pif.main_file";
	public final static String PREF_USE_POOL = "pif.use_pool";
	public static final String PREF_CREATE_LOGS = "pif.create_logs";
	
	private boolean useSessionPooling = true;
	private int port = -1;
	private boolean hidePlwin;
	private String killcommand;
	private boolean createLogs;
		

	
	private void setKillcommand(String killcommand) {
		this.killcommand = killcommand;
	}
	public String getKillcommand() {
		return killcommand;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public void setPort(String port) {
		this.port = Integer.parseInt(port);
	}
	public void setUseSessionPooling(String useSessionPooling) {
		setUseSessionPooling(Boolean.parseBoolean(useSessionPooling));
	}
	public void setUseSessionPooling(boolean useSessionPooling) {
		this.useSessionPooling = useSessionPooling;
		pool = useSessionPooling ? new ReusablePool() : null;
	}
	public void setCreateLogs(boolean createLogs) {
		this.createLogs = createLogs;
	}
	public void setCreateLogs(String createLogs) {
		this.createLogs = Boolean.parseBoolean(createLogs);
	}
	public boolean isCreateLogs() {
		return createLogs;
	}
	public int getPort() {
		return port;
	}

	
	public boolean isHidePlwin() {
		return hidePlwin;
	}

	public void setHidePlwin(boolean hidePlwin) {
		this.hidePlwin = hidePlwin;
	}
	public void setHidePlwin(String hidePlwin) {
		this.hidePlwin = Boolean.parseBoolean(hidePlwin);
	}
	
	public void initOptions(){
		
		super.initOptions(preference_store);
	
		
		setPort(overridePreferenceBySytemProperty(PREF_PORT));
		this.setHidePlwin(overridePreferenceBySytemProperty(PREF_HIDE_PLWIN));		
		this.setKillcommand(overridePreferenceBySytemProperty(PREF_KILLCOMMAND));
		this.setCreateLogs(overridePreferenceBySytemProperty(PREF_CREATE_LOGS));
		setUseSessionPooling(overridePreferenceBySytemProperty(PREF_USE_POOL));
	}
	
	
	
//	public void setOption(String opt, String value) {
//		if (FILE_SEARCH_PATH.equals(opt)){
//			this.fileSearchPath=value;
//		}
//		else if (PREF_EXECUTABLE.equals(opt)) {
//			this.executable = value;
//		} else if (PREF_ENVIRONMENT.equals(opt)) {
//			this.environment = value;
//		} else if (KILLCOMMAND.equals(opt)) {
//			this.killcommand = value;
//		} else if (PREF_STANDALONE.equals(opt)) {
//			setStandAloneServer(Boolean.valueOf(value).booleanValue());
//		} else if (PREF_TIMEOUT.equals(opt)) {
//			this.timeout = Integer.parseInt(value);
//		} else if (PREF_HIDE_PLWIN.equals(opt)) {
//			this.hidePlwin = Boolean.valueOf(value).booleanValue();
//		} else if (PREF_CREATE_LOGS.equals(opt)) {
//			this.createLogs = Boolean.valueOf(value).booleanValue();
//		} else if (PREF_USE_POOL.equals(opt)) {
//			setUseSessionPooling(Boolean.valueOf(value).booleanValue());
//		} else if (PREF_HOST.equals(opt)) {
//			setHost(value);
//		} else if (PREF_PORT.equals(opt)) {
//			setPort(Integer.parseInt(value));
//		} else {
//			throw new IllegalArgumentException("option not supported: " + opt);
//		}
//	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.IPrologInterface#getOption(java.lang.String)
	 */
	// Please use getters & setters instead
	//@Deprecated
//	public String getOption(String opt) {
//		// ld: changed semantic:: System properties override any settings
//		String s = System.getProperty(opt);
//		if (s != null) {
//			Debug.warning("option " + opt
//					+ " is overridden by System Property: " + s);
//			return s;
//		}
//		if(PREF_FILE_SEARCH_PATH.equals(opt)){
//			return getFileSearchPath();
//		} else if (PREF_EXECUTABLE.equals(opt)) {
//			return getExecutable();
//		} else if (PREF_ENVIRONMENT.equals(opt)) {
//			return getEnvironment();
//		} else if (PREF_KILLCOMMAND.equals(opt)) {
//			return "" + getKillcommand();
//		} else if (PREF_STANDALONE.equals(opt)) {
//			return "" + isStandAloneServer();
//		} else if (PREF_USE_POOL.equals(opt)) {
//			return "" + useSessionPooling;
//		} else if (PREF_HIDE_PLWIN.equals(opt)) {
//			return "" + isHidePlwin();
//		} else if (PREF_CREATE_LOGS.equals(opt)) {
//			return "" + createLogs;
//		} else if (PREF_TIMEOUT.equals(opt)) {
//			return "" + getTimeout();
//		} else if (PREF_HOST.equals(opt)) {
//			return getHost();
//		} else if (PREF_PORT.equals(opt)) {
//			return "" + port;
//		} else {
//			throw new IllegalArgumentException("option not supported: " + opt);
//		}
//	}
	
	
	/************************************************/
	/**** Options [End] *****/
	/************************************************/	


	private ReusablePool pool = useSessionPooling ? new ReusablePool() : null;
	private PrologInterfaceFactory factory;

	private ResourceFileLocator locator;

	private HashMap consultServices = new HashMap();

	private File lockFile;

	
	private ServerStartAndStopStrategy startAndStopStrategy;

	protected IPreferenceStore preference_store;

	public SocketPrologInterface(PrologInterfaceFactory factory, String name) {
		super(name);
		this.factory = factory;
		preference_store = PrologRuntimePlugin.getDefault().getPreferenceStore();

	}

	public PrologSession getSession_impl(int flags) throws Throwable {
		ReusableSocket socket = null;
		try {
			if (useSessionPooling) {
				socket = (ReusableSocket) pool
						.findInstance(ReusableSocket.class);
			}
			if (socket == null) {
				Debug.info("creating new ReusableSocket");
				socket = new ReusableSocket(getHost(), port);
			} else {
				Debug.info("reusing old ReusableSocket");
			}

			SocketClient client = new SocketClient(socket);
			client.setPool(pool);
			SocketSession s = new SocketSession(client, this,flags);

			return s;
		} catch (Throwable e) {
			throw error(e);
			
		}
	}

	public AsyncPrologSession getAsyncSession_impl(int flags) throws Throwable {
		ReusableSocket socket = null;
		try {
			if (useSessionPooling) {
				socket = (ReusableSocket) pool
						.findInstance(ReusableSocket.class);
			}
			if (socket == null) {
				Debug.info("creating new ReusableSocket");
				socket = new ReusableSocket(getHost(), port);
			} else {
				Debug.info("reusing old ReusableSocket");
			}
			SocketClient client = new SocketClient(socket);
			client.setParanoiaEnabled(false);
			client.setPool(pool);
			
			AsyncPrologSession s = new AsyncSocketSession(client, this,flags);

			return s;
		} catch (Throwable e) {
			throw error(e);
			
		}
	}

	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#disposeInitialSession(org.cs3.pl.prolog.PrologSession)
	 */
	protected void disposeInitialSession(PrologSession initSession) {
		InitSession s = (InitSession) initSession;
		s.doDispose();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#disposeShutdownSession(org.cs3.pl.prolog.PrologSession)
	 */
	protected void disposeShutdownSession(PrologSession s) {
		ShutdownSession ss = (ShutdownSession) s;
		ss.doDispose();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#getInitialSession()
	 */
	protected PrologSession getInitialSession() throws PrologInterfaceException {
		try {
			//FIXME: LEGACY for now, should be specified by client somehow.
			return new InitSession(new SocketClient(getHost(), port), this,LEGACY);
		} catch (Throwable e) {
			throw error(e);
			
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#getShutdownSession()
	 */
	protected PrologSession getShutdownSession()
			throws PrologInterfaceException {
		try {
			//FIXME: LEGACY for now, should be specified by client somehow.
			return new ShutdownSession(new SocketClient(getHost(), port), this,LEGACY);
		} catch (Throwable e) {
			throw error(e);
			
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.IPrologInterface#getFactory()
	 */
	public PrologInterfaceFactory getFactory() {
		return factory;
	}

	/**
	 * @return Returns the locator.
	 */
	public ResourceFileLocator getLocator() {
		return locator;
	}

	/**
	 * @param locator
	 *            The locator to set.
	 */
	public void setLocator(ResourceFileLocator locator) {
		this.locator = locator;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#stop()
	 */
	public  void stop() throws PrologInterfaceException {
		try {
			super.stop();
		} finally {
			if (pool != null) {
				pool.clear();
			}
		}
	}

	public  PrologInterfaceException error(Throwable e) {
		try {
			super.error(e);
		} finally {
			if (pool != null) {
				pool.clear();
			}
		}
		return getError();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.cs3.pl.prolog.internal.AbstractPrologInterface#stop()
	 */
	public void start() throws PrologInterfaceException {
		System.out.println("Start Socket ");
		if (pool != null) {
			pool.clear();
		}
		super.start();
	}

	public void setLockFile(File string) {
		this.lockFile = string;

	}

	public File getLockFile() {
		return lockFile;
	}

	
	
	public ServerStartAndStopStrategy getStartAndStopStrategy() {	
		return this.startAndStopStrategy;
	}

	/**
	 * @param startAndStopStrategy
	 *            The startAndStopStrategy to set.
	 */
	public void setStartAndStopStrategy(ServerStartAndStopStrategy startAndStopStrategy) {
		this.startAndStopStrategy = startAndStopStrategy;
	}

	
	public void debug_wakeupPoledSessions() {
		int S =pool.getMaxTotalSize();
		PrologSession[] sessions = new PrologSession[S];
		for(int i=0;i<sessions.length;i++){
			try {
				sessions[i]=getSession(PrologInterface.NONE);
			} catch (PrologInterfaceException e) {
				;
			}
		}
		for (PrologSession session : sessions) {
			if(session!=null){
				session.dispose();
			}
		}
	}



	
}