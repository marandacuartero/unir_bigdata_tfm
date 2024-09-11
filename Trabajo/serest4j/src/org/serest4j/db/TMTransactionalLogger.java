package org.serest4j.db;

import java.sql.Connection;

public interface TMTransactionalLogger {

	public Connection getConexion();

	public boolean hashLogger();

	public StringBuilder getLogger();

	public void mark();

	public void printThrowable(Throwable th);

	public String getName();

	public void println(String string);

	public boolean isClosed();

	public void close();

	public long duracion();

	public void flush();
}
