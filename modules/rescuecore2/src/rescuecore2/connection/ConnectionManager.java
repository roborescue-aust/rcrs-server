package rescuecore2.connection;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.InterruptedIOException;
import java.io.IOException;

import java.util.Set;
import java.util.HashSet;

import rescuecore2.misc.WorkerThread;
import rescuecore2.registry.Registry;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
   A class for managing incoming connections.
 */
public class ConnectionManager {
    private static final Log LOG = LogFactory.getLog(ConnectionManager.class);

    private Set<Reader> readers;
    private boolean shutdown;

    private final Object lock = new Object();

    /**
       Construct a new ConnectionManager.
    */
    public ConnectionManager() {
        readers = new HashSet<Reader>();
        shutdown = false;
    }

    /**
       Listen for connections on a particular port.
       @param port The port to listen on.
       @param registry The registry to install in new connections.
       @param listener A ConnectionManagerListener that will be informed of new connections.
       @throws IOException If there is a problem listening on the port.
    */
    public void listen(int port, Registry registry, ConnectionManagerListener listener) throws IOException {
        synchronized (lock) {
            if (shutdown) {
                throw new IOException("Connection manager has been shut down");
            }
            LOG.info("Listening for connections on port " + port);
            ServerSocket socket = new ServerSocket(port);
            socket.setSoTimeout(1000);
            socket.setReuseAddress(true);
            Reader r = new Reader(socket, registry, listener);
            readers.add(r);
            r.start();
        }
    }

    /**
       Shut down this ConnectionManager.
    */
    public void shutdown() {
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            shutdown = true;
        }
        for (Reader next : readers) {
            try {
                next.kill();
            }
            catch (InterruptedException e) {
                LOG.error("ConnectionManager interrupted while shutting down read threads", e);
            }
        }
    }

    /**
       Find out if this ConnectionManager is alive.
       @return True if this manager has not been shut down.
     */
    public boolean isAlive() {
        synchronized (lock) {
            return !shutdown;
        }
    }

    private class Reader extends WorkerThread {
        private ServerSocket socket;
        private Registry registry;
        private ConnectionManagerListener callback;

        public Reader(ServerSocket socket, Registry registry, ConnectionManagerListener callback) {
            this.socket = socket;
            this.registry = registry;
            this.callback = callback;
        }

        @Override
        protected boolean work() {
            try {
                Socket s = socket.accept();
                TCPConnection conn = new TCPConnection(s);
                if (ConnectionManager.this.isAlive()) {
                    conn.setRegistry(registry);
                    callback.newConnection(conn);
                    conn.startup();
                }
            }
            // CHECKSTYLE:OFF:EmptyBlock OK here
            catch (InterruptedIOException e) {
                // Ignore
            }
            // CHECKSTYLE:ON:EmptyBlock
            catch (IOException e) {
                LOG.error("Error listening for connection", e);
            }
            return true;
        }

        @Override
        protected void cleanup() {
            try {
                socket.close();
            }
            catch (IOException e) {
                LOG.error("Error closing server socket", e);
            }
        }
    }
}