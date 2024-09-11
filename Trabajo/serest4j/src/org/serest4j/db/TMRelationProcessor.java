package org.serest4j.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.serest4j.annotation.db.TMColumnRelation;
import org.serest4j.annotation.db.TMFieldRelation;
import org.serest4j.annotation.db.TMPrimaryKey;
import org.serest4j.annotation.db.TMTableName;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.context.TMContext;

public class TMRelationProcessor<T> {

	public static final String[] CRUD_METHODS = new String[]{"create", "read", "update", "delete", "list"};

	private final Class<T> beanClass;
	private final String tabla;
	private final String[] columnas;
	private final String[] variables;
	private final String[] pkColumns;
	private final AtomicBoolean firstLog = new AtomicBoolean(false);

	private TMContext contexto;

	public void setTMContext(TMContext contexto) { this.contexto = contexto; }

	public Class<T> getBeanClass() { return this.beanClass; }
	
	protected TMTransactionalLogger getTl() { return contexto.getTransaccionLog(); }

	protected BufferDataProvider getOut() { return contexto.getOutput(); }

	protected TMContext getTmContext() { return contexto; }

	public String getTabla() {
		return tabla;
	}

	public String getColumName(int i) {
		return columnas[i];
	}

	public String[] getColumnas() {
		return Arrays.copyOf(columnas, columnas.length);
	}

	public String[] getVariables() {
		return Arrays.copyOf(variables, variables.length);
	}

	public String[] getPkColumns() {
		return Arrays.copyOf(pkColumns, pkColumns.length);
	}

	protected TMRelationProcessor(Class<T> beanClass) {
		super();
		this.beanClass = beanClass;
		TMTableName inyectaTabla = this.getClass().getAnnotation(TMTableName.class);
		TMColumnRelation inyectaColumnas = this.getClass().getAnnotation(TMColumnRelation.class);
		TMFieldRelation inyectaCampos = this.getClass().getAnnotation(TMFieldRelation.class);
		TMPrimaryKey inyectaPK = this.getClass().getAnnotation(TMPrimaryKey.class);
		tabla = inyectaTabla.name().trim();
		columnas = inyectaColumnas.value();
		variables = inyectaCampos.value();
		pkColumns = inyectaPK.value();
		if( beanClass != null  &&  tabla.length() > 0 ) {
			if( columnas.length == variables.length  &&  pkColumns.length > 0  &&  columnas.length >= pkColumns.length ) {
				// ok
			}
			else {
				throw new IllegalArgumentException("configuracion incorrecta en " + this.getClass());
			}
		}
		else {
			throw new IllegalArgumentException("beanClass=" + beanClass + ", tabla=" + tabla);
		}
	}

	public T create(T beanObject) throws SQLException {
		return modifyOrCreate(true, beanObject);
	}

	public T update(T beanObject) throws SQLException {
		return modifyOrCreate(false, beanObject);
	}

	private T modifyOrCreate(boolean createRow, T beanObject) throws SQLException {
		try {
			if( beanObject != null ) {
				if( createRow ) {
					return procesarInsert(beanObject);
				}
				else {
					return procesarUpdate(beanObject);
				}
			}
			throw new IllegalArgumentException("el objeto no puede ser nulo");
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	public boolean verify(Object... parametros) throws SQLException {
		try {
			return procesarVerify(parametros);
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	public T read(Object... parametros) throws SQLException {
		try {
			return procesarGet(parametros);
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	public T readByIndex(int[] tmColumnIndex, Object... parametros) throws SQLException {
		ArrayList<T> al = new ArrayList<T>(1);
		_listByIndex(al, true, tmColumnIndex, parametros);
		if( al.size() > 0 ) {
			return al.get(0);
		}
		return null;
	}

	protected void listByIndex(Collection<T> buffer, int[] tmColumnIndex, Object... parametros) throws SQLException {
		_listByIndex(buffer, false, tmColumnIndex, parametros);
	}

	protected void iteraByIndex(int[] tmColumnIndex, Object... parametros) throws SQLException {
		_listByIndex(null, false, tmColumnIndex, parametros);
	}

	private void _listByIndex(Collection<T> buffer, boolean unElemento, int[] tmColumnIndex, Object... parametros) throws SQLException {
		try {
			QueryBuilder qb = new QueryBuilder();
			qb.appendTable(tabla);
			qb.verifyValue(true);
			StringBuilder sb = new StringBuilder();
			int l = sb.length();
			int[] indices = Arrays.copyOf(tmColumnIndex, tmColumnIndex.length);
			sb.append(columnas[indices[0]]);
			sb.append(parametros[0]);
			sb.setLength(l);
			sb.append("order by ");
			for( int i=0; i<columnas.length; i++ ) {
				for( int j=0; j<indices.length; j++ ) {
					if( i == indices[j] ) {
						qb.columnValue(tabla, columnas[i]).value(parametros[j]);
						sb.append(tabla).append('.');
						sb.append(columnas[i]);
						l = sb.length();
						sb.append(',');
						j += indices.length;
					}
				}
				qb.appendColumn(tabla, columnas[i]);
			}
			for( int i=0; i<pkColumns.length; i++ ) {
				sb.append(tabla).append('.');
				sb.append(pkColumns[i]);
				l = sb.length();
				sb.append(',');
			}
			if( l > 0 ) {
				sb.setLength(l);
				qb.closeWith(sb.toString());
			}
			if( unElemento ) {
				procesarList(qb, buffer, 1);
			}
			else {
				procesarList(qb, buffer, -1);	
			}
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	public T delete(Object... parametros) throws SQLException {
		try {
			procesarDelete(parametros);
			return null;
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	public Iterator<Object> list() throws SQLException {
		try {
			procesarList(null, null, -1);
		} catch (Throwable e) {
			throw new SQLException(e);
		}
		return null;
	}

	protected void iteraFromWhere(QueryBuilder qb) throws SQLException {
		try {
			procesarList(qb, null, -1);
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	protected void listFromWhere(QueryBuilder qb, Collection<T> buffer) throws SQLException {
		try {
			procesarList(qb, buffer, 1000);
		} catch (Throwable e) {
			throw new SQLException(e);
		}
	}

	protected int updateClob(String columnName, Reader r, Object... parametros) throws SQLException, IOException {
		return TMLobUtilities.updateClob(getTl(), tabla, pkColumns, columnName, r, parametros);
	}

	protected int updateBlob(String columnName, InputStream is, Object... parametros) throws SQLException, IOException {
		return TMLobUtilities.updateBlob(getTl(), tabla, pkColumns, columnName, is, parametros);
	}

	protected int loadLob(String columnName, Object... parametros) throws SQLException {
		return TMLobUtilities.loadLob(getTl(), getOut(), getTabla(), getPkColumns(), columnName, parametros);
	}

	private T procesarInsert(T beanObject) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SQLException, NoSuchMethodException, SecurityException {
		int n = columnas.length;
		InsertBuilder ib = new InsertBuilder(tabla);
		ib.omitNullValues(true);
		boolean contieneDatos = false; 
		for( int i=0; i<n; i++ ) {
			ib.append(columnas[i], getValue(beanObject, variables[i]));
			contieneDatos = true;
		}
		if( contieneDatos ) {
			if( ib.executeUpdate(getTl(), pkColumns) == 1 ) {
				Object[] pkValues = ib.getFirstKey();
				return procesarGet(pkValues);
			}
		}
		return null;
	}
	
	private T procesarUpdate(T beanObject) throws SQLException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		QueryBuilder qb = new QueryBuilder().verifyValue(true);
		int n = pkColumns.length;
		Object[] pkValues = new Object[n];
		for( int i=0; i<n ;i++ ) {
			for( int j=0; j<columnas.length; j++ ) {
				if( columnas[j].equalsIgnoreCase(pkColumns[i]) ) {
					pkValues[i] = getValue(beanObject, variables[j]);
				}
			}
		}
		try {
			for( int i=0; i<n; i++ ) {
				qb.columnValue(pkColumns[i]).value(pkValues[i]);
			}
		}
		catch(Exception e) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
		}
		n = columnas.length;
		InsertBuilder ib = new InsertBuilder(tabla, qb);
		boolean contieneDatos = false; 
		for( int i=0; i<n; i++ ) {
			if( !esPK(columnas[i], pkColumns) ) {
				ib.append(columnas[i], getValue(beanObject, variables[i]));
				contieneDatos = true;
			}
		}
		if( contieneDatos ) {
			if( ib.executeUpdate(getTl()) == 1 ) {
				return procesarGet(pkValues);
			}
		}
		return null;
	}

	private Object getValue(T beanObject, String valueName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		try {
			String nombreMetodo = "get" + Character.toUpperCase(valueName.charAt(0)) + valueName.substring(1);
			Method m = beanObject.getClass().getMethod(nombreMetodo);
			if( m != null ) {
				return m.invoke(beanObject);	
			}
		} catch (NoSuchMethodException e) {}
		try {
			String nombreMetodo = "is" + Character.toUpperCase(valueName.charAt(0)) + valueName.substring(1);
			Method m = beanObject.getClass().getMethod(nombreMetodo);
			if( m != null  &&  m.getReturnType().equals(Boolean.TYPE) ) {
				Object retorno = m.invoke(beanObject);
				if( retorno != null  &&  retorno instanceof Boolean ) {
					return Integer.valueOf(((Boolean)retorno).booleanValue() ? 1 : 0);
				}
			}
		} catch (NoSuchMethodException e) {}
		throw new IllegalArgumentException("El metodo Get de " + beanObject.getClass().getName() + " asociado a " + valueName + " no existe");
	}

	private boolean esPK(String nombre, String[] pkColumnas) {
		boolean b = false;
		for( String pk : pkColumnas ) {
			if( !b  &&  pk.equalsIgnoreCase(nombre) ) {
				b = true;
			}
		}
		return b;
	}

	private void procesarList(QueryBuilder qbExt, Collection<T> buffer, int nmax) throws SQLException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException, NoSuchMethodException, SecurityException {
		if( buffer == null  &&  contexto.getOutput() == null ) {
			throw new SQLException("No hay buffer de datos definido donde escribir la informacion");
		}
		int n = variables.length;
		QueryBuilder qb = null;
		if( qbExt != null ) {
			qb = qbExt.clone();
		}
		else {
			qb = new QueryBuilder();
			qb.appendTable(tabla);
			for( int i=0; i<columnas.length; i++ ) {
				qb.appendColumn(tabla, columnas[i]);
			}
			StringBuilder sb = new StringBuilder();
			int l = sb.length();
			sb.append("order by ");
			for( int i=0; i<pkColumns.length; i++ ) {
				sb.append(tabla).append('.');
				sb.append(pkColumns[i]);
				l = sb.length();
				sb.append(',');
			}
			if( l > 0 ) {
				sb.setLength(l);
				qb.closeWith(sb.toString());
			}
		}
		try {
			ResultSet rs = qb.executeQuery(getTl());
			if( buffer == null ) {
				contexto.setOutputSize(qb.size());	
			}
			ResultSetMetaData rsmd = rs.getMetaData();
			printMetaData(rsmd);
			int nleidos = 0;
			while( rs.next()  &&  (nmax < 0  ||  nleidos < nmax) ) {
				T retorno = beanClass.getDeclaredConstructor().newInstance();
				for( int i=0; i<n; i++ ) {
					setValue(rsmd, rs, retorno, i + 1, variables[i]);
				}
				if( buffer != null ) {
					buffer.add(retorno);
				}
				else {
					contexto.sendOutput(retorno);
				}
				nleidos++;
			}
		}
		finally {
			qb.close();
		}
	}

	private boolean procesarVerify(Object[] pkValues) throws SQLException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
			qb.verifyValue(true).setCeroAsNull(true);
			int n = pkColumns.length;
			if( n <= 0 ) {
				throw new IllegalArgumentException("No existe PK asociada a " + tabla);
			}
			try {
				for( int i=0; i<n; i++ ) {
					qb.columnValue(pkColumns[i]).value(pkValues[i]);
				}
			}
			catch(Exception e) {
				throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
			}
			n = columnas.length;
			for( int i=0; i<n; i++ ) {
				qb.appendColumn(tabla, columnas[i]);
			}
			if( n > 0 ) {
				ResultSet rs = qb.executeQuery(getTl());
				return rs.next();
			}
		}
		return false;
	}

	private T procesarGet(Object[] pkValues) throws SQLException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		try( AutoQueryBuilder qb = new AutoQueryBuilder(tabla)) {
			qb.verifyValue(true).setCeroAsNull(true);
			int n = pkColumns.length;
			if( n <= 0 ) {
				throw new IllegalArgumentException("No existe PK asociada a " + tabla);
			}
			try {
				for( int i=0; i<n; i++ ) {
					qb.columnValue(pkColumns[i]).value(pkValues[i]);
				}
			}
			catch(Exception e) {
				throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
			}
			n = columnas.length;
			for( int i=0; i<n; i++ ) {
				qb.appendColumn(tabla, columnas[i]);
			}
			if( n > 0 ) {
				ResultSet rs = qb.executeQuery(getTl());
				if( rs.next() ) {
					ResultSetMetaData rsmd = rs.getMetaData();
					printMetaData(rsmd);
					T retorno = beanClass.getDeclaredConstructor().newInstance();
					for( int i=0; i<n; i++ ) {
						setValue(rsmd, rs, retorno, i + 1, variables[i]);
					}
					return retorno;
				}
			}
		}
		return null;
	}

	private void printMetaData(ResultSetMetaData rsmd) throws SQLException {
		if( firstLog.compareAndSet(false, true) ) {
			StringBuilder sb = new StringBuilder("ResultSetMetaData para ");
			sb.append(getClass()).append(' ');
			sb.append(getBeanClass()).append(' ');
			sb.append(tabla).append('\n');
			for( int i=1; i<=columnas.length; i++ ) {
				sb.append("Columna ").append(columnas[i-1]).append(": SN=");
				sb.append(rsmd.getSchemaName(i)).append("-TN=");
				sb.append(rsmd.getTableName(i)).append("-CatN=");
				sb.append(rsmd.getCatalogName(i)).append("-ColCN=");
				sb.append(rsmd.getColumnClassName(i)).append("-ColL=");
				sb.append(rsmd.getColumnLabel(i)).append("-ColN=");
				sb.append(rsmd.getColumnName(i)).append("-ColT=");
				sb.append(rsmd.getColumnType(i)).append("-ColTN=");
				sb.append(rsmd.getColumnTypeName(i)).append('\n');
			}
			contexto.getLogger().debug(sb.toString());
		}
	}

	private boolean isSupported(int tipo) {
		switch (tipo) {
		case Types.BLOB:
		case Types.JAVA_OBJECT:
		case Types.SQLXML:
		case Types.OTHER:
		case Types.ROWID:
			return false;
		default:
		}
		return true;
	}

	private void setValue(ResultSetMetaData rsmd, ResultSet rs, T beanObj, int columnIndex, String valueName) throws SQLException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		String nombreMetodo = "set" + Character.toUpperCase(valueName.charAt(0)) + valueName.substring(1);
		if( !isSupported(rsmd.getColumnType(columnIndex)) ) {
			return;
		}
		Method[] m = beanClass.getMethods();
		boolean exit = false;
		for( Method _m : m ) {
			if( !exit  &&  _m.getName().equals(nombreMetodo) ) {
				Class<?>[] tipos = _m.getParameterTypes();
				if( tipos != null  &&  tipos.length > 0 ) {
					Class<?> tipo = tipos[0];
					Object value = null;
					if( tipo.equals(Long.TYPE) ) {
						value = rs.getLong(columnIndex);
					}
					else if( tipo.equals(Long.class) ) {
						value = rs.getLong(columnIndex);
						if( rs.wasNull() ) {
							value = null;
						}
					}
					else if( tipo.equals(Integer.TYPE) ) {
						value = rs.getInt(columnIndex);
					}
					else if( tipo.equals(Integer.class) ) {
						value = rs.getInt(columnIndex);
						if( rs.wasNull() ) {
							value = null;
						}
					}
					else if( tipo.equals(Double.TYPE) ) {
						value = rs.getDouble(columnIndex);
					}
					else if( tipo.equals(Double.class) ) {
						value = rs.getDouble(columnIndex);
						if( rs.wasNull() ) {
							value = null;
						}
					}
					else if( tipo.equals(BigDecimal.class) ) {
						value = rs.getBigDecimal(columnIndex);
						if( rs.wasNull() ) {
							value = null;
						}
					}
					else if( tipo.equals(Boolean.TYPE) ) {
						value = Boolean.valueOf(rs.getInt(columnIndex) > 0); 
					}
					else if( tipo.equals(Boolean.class) ) {
						value = Boolean.valueOf(rs.getInt(columnIndex) > 0);
					}
					else if( tipo.isAssignableFrom(Timestamp.class) )  {
						value = rs.getTimestamp(columnIndex);
					}
					else if( tipo.isAssignableFrom(java.sql.Date.class) )  {
						value = rs.getDate(columnIndex);
					}
					else if( tipo.isAssignableFrom(Date.class) )  {
						int tipoColumna = rsmd.getColumnType(columnIndex);
						Date date = null; 
						if( tipoColumna == Types.TIMESTAMP ) {
							date = rs.getTimestamp(columnIndex);	
						}
						else if( tipoColumna == Types.TIME ) {
							date = rs.getTimestamp(columnIndex);
						}
						else if( tipoColumna == Types.DATE ) {
							date = rs.getDate(columnIndex);
						}
						if( !rs.wasNull()  &&  date != null ) {
							long l = date.getTime();
							date = (Date)(tipo.getDeclaredConstructor().newInstance());
							date.setTime(l);
						}
						value = date;
					}
					else if( tipo.equals(String.class) ) {
						value = null;
						switch (rsmd.getColumnType(columnIndex)) {
						case Types.NCLOB:
						case Types.CLOB: { // solo leemos hasta 100Kb
							StringBuilder sb = new StringBuilder();
							try {
								Clob clob = rs.getClob(columnIndex);
								Reader r = null;
								if( clob != null )
									r = clob.getCharacterStream();
								if( r != null ) {
									char[] cbuf = new char[1024];
									int i = r.read(cbuf);
									while( i != -1  &&  sb.length() < (100 *1024) ) {
										sb.append(cbuf, 0, i);
										i = r.read(cbuf);
									}
								}
							} catch (Exception e){ sb.append('\n').append(e); }
							value = sb.toString();
						}
						case Types.CHAR:
						case Types.VARCHAR:
						case Types.NCHAR:
						case Types.NVARCHAR:
							if( value == null ) {
								value = rs.getString(columnIndex);	
							}
						default:
						}
					}
					else {
						throw new IllegalArgumentException("Tipo " + tipo + " asociado a " + nombreMetodo + ") no soportado");
					}
					_m.invoke(beanObj, value);
					exit = true;
				}
			}
		}
	}

	private void procesarDelete(Object[] pkValues) throws SQLException {
		QueryBuilder qb = new QueryBuilder().verifyValue(true);
		qb.appendTable(tabla);
		int n = pkColumns.length;
		if( n <= 0 ) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla);
		}
		try {
			for( int i=0; i<n; i++ ) {
				qb.columnValue(pkColumns[i]).value(pkValues[i]);
			}
		}
		catch(Exception e) {
			throw new IllegalArgumentException("No existe PK asociada a " + tabla, e);
		}
		qb.executeDelete(getTl());
	}

	public static QueryBuilder fullJoin(TMRelationProcessor<?> a, TMRelationProcessor<?> b, QueryBuilder qb, Integer... pares) {
		int n = pares.length / 2;
		if( n > 0 ) {
			for( int i=0; i<n; i++ ) {
				qb.columnValue(a.getTabla(), a.getColumName(pares[i])).columnValue(b.getTabla(), b.getColumName(pares[i + 1]));
			}
		}
		return qb;
	}
}
