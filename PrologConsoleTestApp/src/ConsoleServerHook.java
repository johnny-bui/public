import org.cs3.pl.common.Debug;
import org.cs3.pl.common.Util;
import org.cs3.pl.prolog.LifeCycleHook;
import org.cs3.pl.prolog.PrologSession;
import org.cs3.pl.prolog.SessionException;

/**
 * An Init/ShutdownHook that takes the neccesary steps to open/close
 * an io socket on the server process to which a 
 * Console can be connected.
 */
public class ConsoleServerHook implements LifeCycleHook{
	
	
	public static final String HOOK_ID = "org.cs3.pdt.hooks.ConsoleServerHook";

	 private static int getPort() {
	        
	        int port= Integer.getInteger(PDT.PREF_CONSOLE_PORT,4711).intValue();
	    	if(port==-1){
	    		throw new NullPointerException("Required property \""+PDT.PREF_CONSOLE_PORT+"\" was not specified.");
	    	}
			return port;
		}
	
    public void onInit(PrologSession s) {
        int port = getPort();
		if (Util.probePort(port)) {
			Debug.info("Console server thread seems to be running, so i will not start a new one.");			
		}else{
		    String queryString = "use_module(library(prolog_server)), prolog_server("+port+", [])";
		    Debug.info("starting console server using: "+queryString);
		    try {				
                s.query(queryString);
			} catch (SessionException e) {
				Debug.report(e);
			}
			while(!Util.probePort(port)){
	             try {
	                 Thread.sleep(50);
	             } catch (InterruptedException e1) {
	                 Debug.report(e1);
	             }
	         }
			Debug.debug("Server thread created");
		}
		
	}

	/* (non-Javadoc)
	 * @see org.cs3.pl.prolog.ShutdownHook#beforeShutDown(org.cs3.pl.prolog.PrologSession)
	 */
	public void beforeShutdown(PrologSession session) {
	    /*ld: XXX FIXME TODO ARRRRGH!
	     * things do not work this way in the linux implementation of swi /clib.
	     * i have written a mail on the swi list an i am currently waiting for feedback,
	     * for now, we simply ignore the problem when on non-windows system.
	     */
		if(true){
			return;
		}
	    int port = getPort();
		if (!Util.probePort(port)) {
			Debug
					.info("Console server thread does not seem to be running, so i will not stop it.");
			return;
		}
		String queryString = "thread_signal(prolog_server,throw(FrissStahlExcpeption))";
	    Debug.info("stopping console server using: "+queryString);
		try {
			session.query(queryString);
		} catch (SessionException e) {
			Debug.report(e);
		}
		while(Util.probePort(port)){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e1) {
                Debug.report(e1);
            }
        }		
		Debug.debug("Server thread stopped");
	}

	/**
     * @param session
     */
    private void otherImpl(PrologSession session) {
        int port = getPort();
		if (!Util.probePort(port)) {
			Debug
					.info("Console server thread does not seem to be running, so i will not stop it.");
			return;
		}
		String queryString = "thread_signal(prolog_server,throw(FrissStahlExcpeption)),consoleServer(Socket),tcp_close_socket(Socket)";
	    Debug.info("stopping console server using: "+queryString);
		try {
			session.query(queryString);
		} catch (SessionException e) {
			Debug.report(e);
		}
		while(Util.probePort(port)){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e1) {
                Debug.report(e1);
            }
        }		
		Debug.debug("Server thread stopped");
        
    }

    /* (non-Javadoc)
	 * @see org.cs3.pl.prolog.LifeCycleHook#afterInit()
	 */
	public void afterInit() {

		
	}

	

	

}