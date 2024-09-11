package org.serest4j.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.serest4j.async.ToroidQueue;

/**
 * Implementa un TransactionalPool basado en el driver de acceso JDBC a base de datos.
 * 
 * @author Maranda
 *
 * @see TransactionalPool
 *
 */
public class DriverTransactionalPool implements TransactionalPool {

	private final ToroidQueue<Connection> conexiones;
	private long time;
	private final String key;
	private final String connectString;
	private final String user;
	private final String password;
	private final String controlQuery;
	private int maximo;

	public DriverTransactionalPool(String key, String driver, String connectString, String user, String password, String controlQuery, int maximo) throws ClassNotFoundException {
		Class.forName(driver);
		this.key = key;
		this.connectString = connectString;
		this.user = user;
		this.password = password;
		this.controlQuery = controlQuery == null ? null : (controlQuery.trim().length() > 0 ? controlQuery.trim() : null);
		this.maximo = Math.max(this.maximo, 3);
		this.maximo = Math.min(this.maximo, 100);
		conexiones = new ToroidQueue<Connection>();
		time = System.currentTimeMillis() + 60000l;
	}

	public String getId() {
		return this.key;
	}
	
	private synchronized Connection _next() throws SQLException {
		Connection c = null;
		if( time < System.currentTimeMillis() ) {
			c = conexiones.saca();
			while( c != null ) {
				try { c.close(); }catch(Throwable th){}
				c = conexiones.saca();
			}
			time = System.currentTimeMillis() + 60000l;
		}
		c = conexiones.saca();
		if( c != null  &&  c.isClosed() ) {
			c = null;
		}
		if( c == null  &&  conexiones.size() <= maximo ) {
			c = generarConexion();
		}
		if( c != null  &&  controlQuery != null ) {
			try( Statement st = c.createStatement() ) {
				try( ResultSet rs = st.executeQuery(controlQuery) ) {
					rs.next();
				}
			}
		}
		return c;
	}

	@Override
	public Connection next() {
		Connection c = null;
		int n = 0;
		Throwable th = null;
		while( c == null  &&  n < 1000 ) {
			n++;
			try {
				c = _next();
			} catch (SQLException e1) {
				th = e1;
				c = null;
			}	
			if( c == null ) {
				try { Thread.sleep(new Random().nextInt(100)); } catch (InterruptedException e) {}
			}
		}
		if( c == null  &&  th != null ) {
			th.printStackTrace();
		}
		return c;
	}

	@Override
	public synchronized void set(Connection c, boolean error) {
		if( c != null ) {
			if( error ) {
				try { c.rollback(); }catch(Throwable th){
					try { c.close(); }catch(Throwable th2){ c = null; }	
				}
			}
			else {
				try { c.commit(); }catch(Throwable th){
					try { c.close(); }catch(Throwable th2){ c = null; }	
				}
			}
			try {
				if( c != null ) {
					if( !c.isClosed() ) {
						conexiones.mete(c);
					}
				}
			} catch(Exception e) {
				try { c.close(); }catch(Throwable th){ c = null; }
			}
		}
	}

	private Connection generarConexion() throws SQLException {
		Connection con = DriverManager.getConnection(connectString, user , password);
		con.setAutoCommit(false);
		return con;
	}

	private static final String DRIVER_SOURCE = "DriverS>>";

	private static final Map<String, TransactionalPool> CONNECTION_POOL = Collections.synchronizedMap(new HashMap<String, TransactionalPool>(5));

	public synchronized static TransactionalPool get(String driver, String connectString, String user, String password, String controlQuery, int maximo) {
		String key = DRIVER_SOURCE + driver + ">>" + connectString + ":" + user;
		TransactionalPool transactionalPool = CONNECTION_POOL.get(key);
		if( transactionalPool == null ) {
			try {
				transactionalPool = new DriverTransactionalPool(key, driver, connectString, user, password, controlQuery, maximo);
				CONNECTION_POOL.put(key, transactionalPool);
			} catch (ClassNotFoundException e) {
				transactionalPool = null;
				e.printStackTrace();
			}
			if( transactionalPool != null ) {
				Connection c = null;
				try {
					c = transactionalPool.next();
				}
				catch(Throwable th) {
					th.printStackTrace();
					c = null;
				}
				if( c == null ) {
					transactionalPool.set(c, true);
					transactionalPool = new NullTransactionalPool(key);
					CONNECTION_POOL.put(key, transactionalPool);
				}
				else {
					transactionalPool.set(c, false);
					CONNECTION_POOL.put(key, transactionalPool);
				}
			}
			else if( transactionalPool instanceof NullTransactionalPool ) {
				if( ((NullTransactionalPool) transactionalPool).isCaducado() ) {
					CONNECTION_POOL.remove(key);
				}
			}
		}
		return transactionalPool;
	}
}
