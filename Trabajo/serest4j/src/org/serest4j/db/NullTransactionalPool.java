package org.serest4j.db;

import java.sql.Connection;
import java.sql.SQLException;

public class NullTransactionalPool implements TransactionalPool {

	private final long l;
	private final String dataSource;

	NullTransactionalPool(String dataSource) {
		this.l = System.currentTimeMillis() + 20000l;
		this.dataSource = dataSource;
	}

	public boolean isCaducado() {
		return this.l > System.currentTimeMillis();
	}
	
	@Override
	public Connection next() throws SQLException {
		throw new SQLException("NullTransactionalPool >> Conexion nula para " + dataSource);
	}

	@Override
	public void set(Connection c, boolean error) {
	}

	@Override
	public String getId() {
		return dataSource;
	}
}
