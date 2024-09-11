package org.serest4j.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

/**
 * Implementa un TransactionalPool basado en el pool de conexiones del servidor de aplicaciones, que sirve sus conexiones
 * a traves de el datasource indicado
 * 
 * @author Maranda
 *
 * @see TransactionalPool
 *
 */

public class DSTransactionalPool implements TransactionalPool {

	private static final String DATA_SOURCE = "DataS>>";
	
	private String dataSource;
	private String key;
	private Logger trace;

	public DSTransactionalPool(String dataSource) {
		this.dataSource = dataSource;
		this.key = DATA_SOURCE + dataSource.substring(0, 2) + dataSource.substring(2);
		this.trace = Logger.getLogger(TransactionalBaseContainer.class);
		if( !this.trace.isTraceEnabled() ) {
			this.trace = null;
		}
	}

	public String getId() {
		return this.key;
	}

	@Override
	public synchronized Connection next() throws SQLException {
		try {
			Connection c = makeConnection(dataSource);
			if( c != null ) {
				c.setAutoCommit(false);
			}
			return c;
		} catch (NamingException e) {
			throw new SQLException("Obteniendo conexion con " + dataSource, e);
		}
	}

	@Override
	public synchronized void set(Connection c, boolean error) {
		if( c != null ) {
			if( error ) {
				try { c.rollback(); }catch(Throwable th){}
			}
			else {
				try { c.commit(); }catch(Throwable th){}
			}
			try { c.close(); }catch(Throwable th){}
		}
	}

	private static final Map<String, String> jndiNames = Collections.synchronizedMap(new HashMap<String, String>());

	private DataSource tryWith(Context ctx, String jndi, String dataSource) {
		DataSource ds = null;
		try{ ds = (DataSource)ctx.lookup(jndi); }catch(Exception e){ if( trace != null ) { trace.error(e); } }
		if( ds != null ) {
			if( trace != null ) {
				trace.error("Encontrado para " + dataSource + " > " + jndi + ", " + ds);	
			}
			jndiNames.put(dataSource, jndi);
		}
		return ds;
	}

	private Connection makeConnection(String name) throws NamingException, SQLException {
		InitialContext ctx = new InitialContext();
		String jndiDataSource = jndiNames.get(name);
		DataSource ds = null;
		if( jndiDataSource != null ) {
			try{ ds = (DataSource)ctx.lookup(jndiDataSource); }catch(Exception e){ e.printStackTrace(); }
		}
		if( ds == null ) {
			ds = tryWith(ctx, name, name);
			if( ds == null ) {
				ds = tryWith(ctx, "java:/comp/env/" + name, name);
			}
			if( ds == null ) {
				ds = tryWith(ctx, "java:jdbc/" + name, name);
			}
		}
		return ds == null ? null : ds.getConnection();
	}
}
