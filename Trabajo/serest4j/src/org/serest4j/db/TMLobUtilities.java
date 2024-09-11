package org.serest4j.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import org.serest4j.async.BufferDataProvider;

public class TMLobUtilities {

	public static int updateClob(TMTransactionalLogger tl, String tabla, String[] pkColumns, String columnName, Reader r, Object... parametros) throws SQLException, IOException {
		char[] cbuf = new char[1024];
		int ncbuf = r.read(cbuf);
		while( ncbuf == 0 ) {
			ncbuf = r.read(cbuf);
		}
		if( ncbuf == -1 ) {
			throw new IOException("No data available from Reader");
		}
		QueryBuilder qbUpdate = new QueryBuilder().verifyValue(true).setCeroAsNull(true);
		int n = pkColumns.length;
		if( n <= 0 ) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla);
		}
		try {
			for( int i=0; i<n; i++ ) {
				qbUpdate.columnValue(pkColumns[i]).value(parametros[i]);
			}
		}
		catch(Exception e) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
		}
		InsertBuilder ib = new InsertBuilder(tabla, qbUpdate.isNull(columnName));
		ib.append(columnName, Arrays.copyOf(cbuf, ncbuf));
		ib.executeUpdate(tl);
		try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
			qb.verifyValue(true).setCeroAsNull(true);
			qb.appendColumn(tabla, columnName);
			for( int i=0; i<n; i++ ) {
				qb.appendColumn(tabla, pkColumns[i]);
				qb.columnValue(pkColumns[i]).value(parametros[i]);
			}
			ResultSet rs = qb.executeQuery(tl, true);
			if( rs.next() ) {
				int tipo = rs.getMetaData().getColumnType(1);
				Clob clob = null;
				if( tipo == Types.CLOB ) {
					clob = rs.getClob(1);
				}
				else if( tipo == Types.NCLOB ) {
					clob = rs.getNClob(1);
				}
				if( clob != null ) {
					clob.truncate(0l);
					n = ncbuf;
					Writer w = clob.setCharacterStream(0l);
					w.write(cbuf, 0, ncbuf);
					ncbuf = r.read(cbuf);
					while( ncbuf != -1 ) {
						n += ncbuf;
						w.write(cbuf, 0, ncbuf);
						ncbuf = r.read(cbuf);
					}
					w.close();
					tl.println("Escritos " + n + " caracteres");
					return n;
				}
			}
		}
		return -1;
	}

	public static int updateBlob(TMTransactionalLogger tl, String tabla, String[] pkColumns, String columnName, InputStream is, Object... parametros) throws SQLException, IOException {
		byte[] b = new byte[1024];
		int nb = is.read(b);
		while( nb == 0 ) {
			nb = is.read(b);
		}
		if( nb == -1 ) {
			throw new IOException("No data available from InputStream");
		}
		QueryBuilder qbUpdate = new QueryBuilder().verifyValue(true).setCeroAsNull(true);
		int n = pkColumns.length;
		if( n <= 0 ) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla);
		}
		try {
			for( int i=0; i<n; i++ ) {
				qbUpdate.columnValue(pkColumns[i]).value(parametros[i]);
			}
		}
		catch(Exception e) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
		}
		InsertBuilder ib = new InsertBuilder(tabla, qbUpdate.isNull(columnName));
		ib.append(columnName, Arrays.copyOf(b, nb));
		ib.executeUpdate(tl);
		try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
			qb.verifyValue(true).setCeroAsNull(true);
			qb.appendColumn(tabla, columnName);
			for( int i=0; i<n; i++ ) {
				qb.appendColumn(tabla, pkColumns[i]);
				qb.columnValue(pkColumns[i]).value(parametros[i]);
			}
			ResultSet rs = qb.executeQuery(tl, true);
			if( rs.next() ) {
				int tipo = rs.getMetaData().getColumnType(1);
				Blob blob = null;
				if( tipo == Types.BLOB ) {
					blob = rs.getBlob(1);
				}
				if( blob != null ) {
					blob.truncate(0l);
					n = nb;
					OutputStream out = blob.setBinaryStream(0l);
					out.write(b, 0, nb);
					nb = is.read(b);
					while( nb != -1 ) {
						n += nb;
						out.write(b, 0, nb);
						nb = is.read(b);
					}
					out.close();
					tl.println("Escritos " + n + " bytes");
					return n;
				}
			}
		}
		return -1;
	}

	public static int loadLob(TMTransactionalLogger tl, BufferDataProvider out, String tabla, String[] pkColumns, String columnName, Object... parametros) throws SQLException {
		try {
			try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
				qb.verifyValue(true).setCeroAsNull(true);
				int n = pkColumns.length;
				if( n <= 0 ) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla);
				}
				try {
					for( int i=0; i<n; i++ ) {
						qb.columnValue(pkColumns[i]).value(parametros[i]);
					}
				}
				catch(Exception e) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
				}
				qb.appendColumn(tabla, columnName);
				ResultSet rs = qb.executeQuery(tl);
				if( rs.next() ) {
					loadLobResultSet(tl, rs, out, 1);
				}
			}
		} catch (SQLException e) {
			throw e;
		} catch (Throwable th) {
			throw new SQLException(th);
		}
		return -1;
	}
	
	public static int loadLobResultSet(TMTransactionalLogger tl, ResultSet rs, BufferDataProvider out, int columnIndex) throws SQLException, IOException {
		int tipo = rs.getMetaData().getColumnType(columnIndex);
		if( tipo == Types.VARCHAR  ||  tipo == Types.CHAR ) {
			String str = rs.getString(columnIndex);
			if( str != null ) {
				out.send(str);
				return str.length();
			}
		}
		else if( tipo == Types.CLOB  ||  tipo == Types.NCLOB
				||  tipo == Types.NVARCHAR  ||  tipo == Types.VARCHAR
				||  tipo == Types.NCHAR  ||  tipo == Types.CHAR
				||  tipo == Types.LONGNVARCHAR  ||  tipo == Types.LONGVARCHAR ) {
			Clob clob = null;
			Reader r = null;
			if( tipo == Types.CLOB ) {
				clob = rs.getClob(columnIndex);
			}
			else if( tipo == Types.NCLOB ) {
				clob = rs.getNClob(columnIndex);
			}
			else if( tipo == Types.NVARCHAR  ||  tipo == Types.NCHAR  ||  tipo == Types.LONGNVARCHAR ) {
				r = rs.getNCharacterStream(columnIndex);
			}
			else {
				r = rs.getCharacterStream(columnIndex);
			}
			if( clob != null ) {
				r = clob.getCharacterStream();
			}
			return sendReader(r, out, tl);
		}
		else if( tipo == Types.BLOB  ||  tipo == Types.LONGVARBINARY  ||  tipo == Types.BINARY ) {
			InputStream is = null;
			if( tipo == Types.BLOB ) {
				Blob blob = rs.getBlob(columnIndex);
				if( blob != null ) {
					is = blob.getBinaryStream();
				}
			}
			else {
				is = rs.getBinaryStream(columnIndex);
			}
			return sendInputStream(is, out, tl);
		}
		else {
			Object obj = rs.getObject(columnIndex);
			if( !rs.wasNull() ) {
				out.send(String.valueOf(obj));
			}
		}
		return -1;
	}

	public static int loadCLob(TMTransactionalLogger tl, Writer out, String tabla, String[] pkColumns, String columnName, Object... parametros) throws SQLException {
		try {
			try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
				qb.verifyValue(true).setCeroAsNull(true);
				int n = pkColumns.length;
				if( n <= 0 ) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla);
				}
				try {
					for( int i=0; i<n; i++ ) {
						qb.columnValue(pkColumns[i]).value(parametros[i]);
					}
				}
				catch(Exception e) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
				}
				qb.appendColumn(tabla, columnName);
				ResultSet rs = qb.executeQuery(tl);
				if( rs.next() ) {
					int tipo = rs.getMetaData().getColumnType(1);
					if( tipo == Types.VARCHAR  ||  tipo == Types.CHAR ) {
						String str = rs.getString(1);
						if( str != null ) {
							out.write(str);
							return str.length();
						}
					}
					else if( tipo == Types.CLOB  ||  tipo == Types.NCLOB
							||  tipo == Types.NVARCHAR  ||  tipo == Types.VARCHAR
							||  tipo == Types.NCHAR  ||  tipo == Types.CHAR
							||  tipo == Types.LONGNVARCHAR  ||  tipo == Types.LONGVARCHAR ) {
						Clob clob = null;
						Reader r = null;
						if( tipo == Types.CLOB ) {
							clob = rs.getClob(1);
						}
						else if( tipo == Types.NCLOB ) {
							clob = rs.getNClob(1);
						}
						else if( tipo == Types.NVARCHAR  ||  tipo == Types.NCHAR  ||  tipo == Types.LONGNVARCHAR ) {
							r = rs.getNCharacterStream(1);
						}
						else {
							r = rs.getCharacterStream(1);
						}
						if( clob != null ) {
							r = clob.getCharacterStream();
						}
						return sendReader(r, out, tl);
					}
				}
			}
		} catch (SQLException e) {
			throw e;
		} catch (Throwable th) {
			throw new SQLException(th);
		}
		return -1;
	}

	public static int loadBLob(TMTransactionalLogger tl, OutputStream out, String tabla, String[] pkColumns, String columnName, Object... parametros) throws SQLException {
		try {
			try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
				qb.verifyValue(true).setCeroAsNull(true);
				int n = pkColumns.length;
				if( n <= 0 ) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla);
				}
				try {
					for( int i=0; i<n; i++ ) {
						qb.columnValue(pkColumns[i]).value(parametros[i]);
					}
				}
				catch(Exception e) {
					throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
				}
				qb.appendColumn(tabla, columnName);
				ResultSet rs = qb.executeQuery(tl);
				if( rs.next() ) {
					int tipo = rs.getMetaData().getColumnType(1);
					if( tipo == Types.BLOB  ||  tipo == Types.LONGVARBINARY  ||  tipo == Types.BINARY ) {
						InputStream is = null;
						if( tipo == Types.BLOB ) {
							Blob blob = rs.getBlob(1);
							if( blob != null ) {
								is = blob.getBinaryStream();
							}
						}
						else {
							is = rs.getBinaryStream(1);
						}
						return sendInputStream(is, out, tl);
					}
				}
			}
		} catch (SQLException e) {
			throw e;
		} catch (Throwable th) {
			throw new SQLException(th);
		}
		return -1;
	}

	private static int sendReader(Reader r, BufferDataProvider out, TMTransactionalLogger tl) throws IOException {
		if( r != null ) {
			int n = 0;
			try {
				StringBuilder sb = new StringBuilder();
				char[] cbuf = new char[1024];
				int i = r.read(cbuf);
				while( i != -1 ) {
					if( i > 0 ) {
						n += i;
						sb.append(cbuf, 0, i);
						if( sb.length() > 10000 ) {
							out.send(sb);
							sb.setLength(0);
						}
					}
					i = r.read(cbuf);
				}
				if( sb.length() > 0 ) {
					out.send(sb);
				}
			}
			finally {
				r.close();
			}
			return n;
		}
		return -1;
	}

	private static int sendReader(Reader r, Writer out, TMTransactionalLogger tl) throws IOException {
		if( r != null ) {
			int n = 0;
			try {
				char[] cbuf = new char[1024];
				int i = r.read(cbuf);
				while( i != -1 ) {
					n += i;
					out.write(cbuf, 0, i);
					i = r.read(cbuf);
				}
			}
			finally {
				r.close();
			}
			return n;
		}
		return -1;
	}

	private static int sendInputStream(InputStream is, BufferDataProvider out, TMTransactionalLogger tl) throws IOException {
		if( is != null ) {
			int n = 0;
			try {
				byte[] b = new byte[8 * 1024];
				int i = is.read(b);
				while( i != -1 ) {
					if( i > 0 ) {
						n += i;
						out.send(Arrays.copyOf(b, i));
					}
					i = is.read(b);
				}
			}
			finally {
				is.close();
			}
			return n;
		}
		return -1;
	}

	private static int sendInputStream(InputStream is, OutputStream out, TMTransactionalLogger tl) throws IOException {
		if( is != null ) {
			int n = 0;
			try {
				byte[] b = new byte[1024];
				int i = is.read(b);
				while( i != -1 ) {
					n += i;
					out.write(b, 0, i);
					i = is.read(b);
				}
			}
			finally {
				is.close();
			}
			return n;
		}
		return -1;
	}
}
