package org.serest4j.db;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 *
 * <p>Title: InsertBuffer </p>
 * <p>Description: Este peazo de clase facilita la labor del programador
 * generando las sentencias de insercion o modificacion de datos en bases de
 * datos.
 * Se gestiona la generaci�n y correcta asignaci�n de los valores, sin
 * necesidad de que el usuario deba de controlar el numero de interrogantes y
 * el indice correcto del valor asignado, labor tediosa que suele generar errores
 * y que dificulta considerablemente el mantenimiento en sentencias grandes.
 * Ademas realiza comprobaciones en el momento de convertir los datos, teniendo en
 * cuenta los valores nulos, convirtiendo numeros, fechas y secuencias de caracteres.
 * El que la clase sea de tipo Buffer, indica que se pueden concatenar las llamadas
 * a los distintos metodos, no implica necesariamente una mejora en el rendimiento.
 * </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: De las americas</p>
 * @author SOY YO, MARIO, EL UNICO !!!
 * @version v.teasa.ber
 */

public class InsertBuilder
{
	private static final int BREAK = 10;
	
	private static final char PP_R = '\n';
	private static final char NP_R = ' ';

	private SimpleDateFormat dateFormatInstance;
	private String dateFormatMask;

	private QueryBuilder where;
	private List<String> columnas;
	private Object[] batch = null;
	private ArrayList<Object> valores;
	private boolean omitirNulos;
	private String tabla;
	private boolean pretty;
	private boolean soloUno;

	private TMTransactionalLogger logger = null;

	public InsertBuilder(String t) { this( t, null); }
	
	/**
	 * Constructor genera de la clase.
	 * @param tabla El nombre de la tabla sobre la que se actua.
	 * @param w El where Si es nulo o esta vacio, se considera que la clausula es de
	 * tipo "INSERT INTO", en caso contrario se construye un "UPDATE ... " + where.
	 */
	public InsertBuilder(String tabla, QueryBuilder w)
	{
		tabla.charAt(0);
		this.omitirNulos = false;
		this.tabla = tabla;
		this.where = w;
		setSoloUnUpdate(w != null);
		this.columnas = new ArrayList<String>();
		this.valores = new ArrayList<Object>();
		this.dateFormatInstance = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		this.dateFormatMask = "yyyy-MM-dd HH:mm:ss";
	}

	private char getPPC() { return pretty ? PP_R : NP_R; }

	/**
	 * Para indicar si al hacer un update debe de verificar los valores
	 * nulos. Si se verifican los valores, y alguno es nulo, no se colocan
	 * en la sentencia, si los valores no se verifican, se colocan como NULL
	 * aquellos que sean nulos.
	 */
	public InsertBuilder omitNullValues( boolean b )
	{
		omitirNulos = b;
		return this;
	}

	/**
	 * Cambia el modo en que este QueyBuffer presenta las fechas
	 * Por defecto se utiliza el modo americano 'yyyy-MM-dd HH.mm.ss"
	 * @param simpleDateFormat
	 * @return
	 */
	public InsertBuilder setDateFormat(String simpleDateFormatMask) {
		if( simpleDateFormatMask != null ) {
			try {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat(simpleDateFormatMask);
				simpleDateFormat.parse(simpleDateFormat.format(new Date()));
				this.dateFormatMask = simpleDateFormatMask;
				this.dateFormatInstance = simpleDateFormat;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return this;
	}

	/**
	 * Realiza una asignacion generica, procesando el dato que se le pasa.
	 * @param name Nombre de la columna.
	 * @param value Cualquier dato, String, Integer, BigDecimal, Long, java.sql.Date
	 */
	public InsertBuilder append( String name, Object value )
	{
		name.charAt(0);
		if( batch == null ) {
			if( value != null  ||  !omitirNulos )
			{
				columnas.add(name);
				valores.add(value);
				batchUpdated = true;
			}
		}
		else {
			int io = columnas.indexOf(name);
			valores.set(io, value);
			batchUpdated = true;
		}
		return this;
	}
	
	/**
	 * Realiza una asignacion sin procesar la condicion. Este valor se asigna tal cual.
	 * Se utiliza cuando la asignacion a la columna es una secuencia, o el resultado de
	 * una funcion, o una valor conocido y ya procesado.
	 * @param name Nombre de la columna.
	 * @param cond
	 * @return this
	 */
	public InsertBuilder appendCond( String name, String cond )
	{
		name.charAt(0);
		cond.charAt(0);
		if( batch == null ) {
			columnas.add(name);
			ConditionType conditionType = new ConditionType();
			conditionType.value = cond;
			valores.add(conditionType);
		}
		else {
			int io = columnas.indexOf(name);
			ConditionType conditionType = new ConditionType();
			conditionType.value = cond;
			valores.set(io, cond);
		}
		return this;
	}

	private class ConditionType {
		String value;
		public String toString() { return value; }
	}

	/**
	 * Asigna la subcadena de caracteres. Se utiliza cuando la columna de destino
	 * es de tipo caracter y tiene una limitacion de tama�o, y no queremos correr
	 * el riesgo de pasarle un dato demasiado largo. Si es un valor nulo, asigna
	 * el nulo, si la cadena supera el tama�o indicado la recorta a ese tama�o,
	 * y si no la asigna tal cual.
	 * @param name La columna
	 * @param value La cadena
	 * @param size El tama�o maximo permitido
	 * @return this
	 */
	public InsertBuilder appendSubstr( String name, String value, int size )
	{
		if( value != null )
		{
			if( value.length() <= size )
				return append(name, value);
			else
				return append(name, value.substring(0,  size));
		}
		else
			return append(name, null);
	}

	public InsertBuilder appendTime( String name, Date date )
	{
		return append(name, date == null ? null : new Timestamp(date.getTime()));
	}

	public InsertBuilder appendDate( String name, Date date )
	{
		if( date != null ) {
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			date = new java.sql.Date(c.getTimeInMillis());
		}
		return append(name, date);
	}
	
	public InsertBuilder appendDouble( String name, double value )
	{
		return append(name, new BigDecimal(value) );
	}
	
	public InsertBuilder appendInt( String name, int value )
	{
		return append(name, Integer.valueOf(value));
	}
	
	public InsertBuilder appendLong( String name, long value )
	{
		return append(name, Long.valueOf(value));
	}

	private void setLogger(TMTransactionalLogger log) {
		logger = log;
	}

	private void constructQuery(StringBuilder sb, List<Object> valoresStatement) {
		if( where != null ) {
			constructUpdateQuery(sb, valoresStatement);
		}
		else {
			constructIntroQuery(sb, valoresStatement);
		}
	}

	private void constructUpdateQuery(StringBuilder sbV, List<Object> valoresStatement)
	{
		sbV.append("UPDATE ").append(tabla).append(" SET ");
		int n = columnas.size();
		int l = sbV.length();
		for( int i=0; i<n; i++ ) {
			sbV.append(columnas.get(i)).append('=');
			Object value = valores.get(i);
			if( value == null ) {
				sbV.append("NULL");
			}
			else if( value instanceof ConditionType ) {
				sbV.append(value);
			}
			else if( valoresStatement == null ) {
				if( value instanceof String ) {
					sbV.append('\'').append(value).append('\'');
				}
				else if( value instanceof Date ) {
					sbV.append('\'').append(dateFormatInstance.format((Date)value)).append('\'');
				}
				else if( value instanceof File ) {
					sbV.append('[').append(((File)value).getAbsolutePath()).append(']');
				}
				else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Byte.TYPE) ) {
					sbV.append('[').append("BLOB size=").append(Array.getLength(value)).append(']');
				}
				else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Character.TYPE) ) {
					char[] c = (char[])value;
					if( c.length > 128 ) {
						c = Arrays.copyOf(c, Math.min(c.length, 64));
						sbV.append('[').append(c).append("... CLOB size=").append(Array.getLength(value)).append(']');
					}
					else {
						sbV.append('\'').append(c).append('\'');
					}
				}
				else
					sbV.append(value);
			}
			else {
				sbV.append('?');
				valoresStatement.add(value);
			}
			l = sbV.length();
			sbV.append(',');
			if( i > 0  &&  i % BREAK == 0 )
				sbV.append(getPPC());
		}
		sbV.setLength(l);
		sbV.append(getPPC()).append("where ");
		int l2 = sbV.length();
		where.setPretty(pretty);
		where.buildConditions(sbV, valoresStatement);
		if( sbV.length() <= l2 )
			sbV.setLength(l);
	}

	private int getBatchSize() {
		return batch == null ? 0 : batch.length;
	}

	private boolean hasSomeValue() {
		if( batch != null  &&  batch.length > 0 ) {
			Object[] obj = (Object[])batch[0];
			return obj != null  &&  obj.length > 0;
		}
		else {
			return valores.size() > 0;
		}
	}
	
	private void constructIntroQuery(StringBuilder sbC, List<Object> valoresStatement)
	{
		sbC.append("INSERT INTO ").append(tabla).append(getPPC()).append('(');
		int l = sbC.length();
		int i = 0;
		for( String ncol : columnas ) {
			sbC.append(ncol);
			l = sbC.length();
			sbC.append(',');
			if( i > 0  &&  i % BREAK == 0 ) {
				sbC.append(getPPC());
			}
			i++;
		}
		sbC.setLength(l);
		sbC.append(')').append(getPPC()).append("VALUES").append(getPPC());
		StringBuilder sbV = new StringBuilder();
		if( batch != null  &&  batch.length > 0 ) {
			for( Object array_values : batch ) {
				sbV.append('(');
				l = sbV.length();
				i = 0;
				for( Object value : (Object[])array_values ) {
					constructIntroValuesQuery(value, sbV, valoresStatement);
					l = sbV.length();
					sbV.append(',');
					if( i > 0  &&  i % BREAK == 0 ) {
						sbV.append(getPPC());
					}
					i++;
				}
				sbV.setLength(l);
				sbV.append(')');
				l = sbV.length();
				sbV.append(',').append(getPPC());
			}
			sbV.setLength(l);
		}
		else {
			sbV.append('(');
			l = sbV.length();
			i = 0;
			for( Object value : valores ) {
				constructIntroValuesQuery(value, sbV, valoresStatement);
				l = sbV.length();
				sbV.append(',');
				if( i > 0  &&  i % BREAK == 0 ) {
					sbV.append(getPPC());
				}
				i++;
			}
			sbV.setLength(l);
			sbV.append(')');
		}
		sbC.append(sbV);
	}

	private void constructIntroValuesQuery(Object value, StringBuilder sbV, List<Object> valoresStatement)
	{
		if( value == null ) {
			sbV.append("NULL");
		}
		else if( value instanceof ConditionType ) {
			sbV.append(value);
		}
		else if( valoresStatement == null ) {
			if( value instanceof Number ) {
				sbV.append(value);
			}
			else if( value instanceof Date ) {
				sbV.append('\'').append(dateFormatInstance.format((Date)value)).append('\'');
			}
			else if( value instanceof File ) {
				sbV.append('[').append(((File)value).getAbsolutePath()).append(']');
			}
			else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Byte.TYPE) ) {
				sbV.append('[').append("BLOB size=").append(Array.getLength(value)).append(']');
			}
			else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Character.TYPE) ) {
				char[] c = (char[])value;
				if( c.length > 128 ) {
					c = Arrays.copyOf(c, Math.min(c.length, 64));
					sbV.append('[').append(c).append("... CLOB size=").append(Array.getLength(value)).append(']');
				}
				else {
					sbV.append('\'').append(c).append('\'');
				}
			}
			else {
				sbV.append('\'').append(String.valueOf(value)).append('\'');
			}
		}
		else {
			sbV.append('?');
			valoresStatement.add(value);
		}
	}

	private boolean batchUpdated = false;
	private boolean unmodifiableListActive = false;

	public void addBatch() {
		if( where != null ) {
			throw new UnsupportedOperationException("Solo se permite batch en clausulas INSERT");
		}
		if( batchUpdated ) {
			batchUpdated = false;
			if( unmodifiableListActive ) { // las columnas ya estan bloqueadas
			}
			else {
				columnas = Collections.unmodifiableList(columnas); // bloqueo las columnas
				unmodifiableListActive = true;
			}
			Object[] a = new Object[columnas.size()];
			a = valores.toArray(a);
			if( batch == null ) {
				batch = new Object[]{a};
			}
			else {
				int n = batch.length;
				Object[] aux = new Object[n + 1];
				for( int i=0; i<n; i++ ) {
					aux[i] = batch[i];
					batch[i] = null;
				}
				aux[batch.length] = a;
				batch = aux;
			}
		}
	}

	/**
	 * Ejecuta la clausula
	 * @param c Conexion a base de datos.
	 * @param claves Columnas que conforman la clave primaria
	 * @throws java.sql.SQLException
	 */
	public int executeUpdate(TMTransactionalLogger log, String... claves) throws java.sql.SQLException
	{
		setLogger(log);
		return _executeUpdate(logger.getConexion(), claves);
	}

	/**
	 * Ejecuta la clausula asegurandose de que es un batch y contiene datos
	 * @param log
	 * @param claves
	 * @return
	 * @throws java.sql.SQLException
	 */
	public int executeBatch(TMTransactionalLogger log, String... claves) throws java.sql.SQLException
	{
		setLogger(log);
		if( getBatchSize() > 0 )
			return _executeUpdate(logger.getConexion(), claves);
		else {
			close();
			return -1;
		}
	}

	private ArrayList<Object[]> keyValues = null;

	/**
	 * @return la PK del primer registro guardado, en el caso de que sea un
	 * autoincremental
	 * Si no es el caso lanzara una excepcion
	 */
	public int getFirstAutoincrementKey() {
		if( keyValues != null ) {
			try {
				for( Object[] obj : keyValues ) {
					return Integer.parseInt(obj[0].toString());
				}
			}finally {
				keyValues.clear();
				keyValues = null;
			}
		}
		return -1;
	}

	/**
	 * @return la PK del primer registro guardado, en el caso de que sea un
	 * autoincremental
	 * Si no es el caso lanzara una excepcion
	 */
	public long getFirstAutoincrementKeyLong() {
		if( keyValues != null ) {
			try {
				for( Object[] obj : keyValues ) {
					return Long.parseLong(obj[0].toString());
				}
			}finally {
				keyValues.clear();
				keyValues = null;
			}
		}
		return -1;
	}
	
	public Object[] getFirstKey() {
		if( keyValues != null ) {
			try {
				for( Object[] obj : keyValues ) {
					return obj;
				}
			}finally {
				keyValues.clear();
				keyValues = null;
			}
		}
		return null;
	}

	public Object[] getAllKeys() {
		if( keyValues != null ) {
			try {
				Object[] claves = new Object[keyValues.size()];
				int i = 0;
				for( Object[] obj : keyValues ) {
					claves[i] = obj;
				}
				return claves;
			}finally {
				keyValues.clear();
				keyValues = null;
			}
		}
		return null;
	}

	private int _executeUpdate(java.sql.Connection c, String... primaryKey) throws java.sql.SQLException
	{
		Throwable th = null;
		PreparedStatement ps = null;
		int retorno = -1;
		try
		{
			if( hasSomeValue() ) {
				ArrayList<Object> valoresStatement = new ArrayList<Object>();
				StringBuilder sb = new StringBuilder();
				constructQuery(sb, valoresStatement);
				ps = primaryKey == null  ||  primaryKey.length <= 0 ? c.prepareStatement(sb.toString()) : c.prepareStatement(sb.toString(), primaryKey);
				int i = 1;
				for( Object obj : valoresStatement ) {
					set(i, obj, ps);
					i++;
				}
				retorno = ps.executeUpdate();
				if( retorno > 1  &&  soloUno ) {
					throw new SQLException("ERROR!!: La consulta ha modificado " + retorno + " registros, y solo estaba previsto que modificara uno!!");
				}
				if( retorno > 0  &&  primaryKey != null  &&  primaryKey.length > 0 ) {
					ResultSet rs = ps.getGeneratedKeys();
					if( rs != null ) {
						keyValues = new ArrayList<Object[]>();
						while( rs.next() ) {
							Object[] claves = new Object[primaryKey.length];
							for( i=1; i<=primaryKey.length; i++ ) {
								claves[i - 1] = rs.getObject(i);
							}
							keyValues.add(claves);
						}
						try{ rs.close(); }catch(Exception e){}
					}
				}
			}
		}
		catch(Exception e) {
			th = e;
			throw new SQLException(e.getMessage());
		}
		finally {
			try{ if( ps != null ) ps.close(); }catch(Exception e){}
			if( logger.hashLogger() ) {
				logger.mark();
				constructQuery(logger.getLogger(), null);
				logger.getLogger().append(';');
				if( retorno > 0 ) {
					if( where != null )
						logger.getLogger().append("\n-- Actualizados ").append(retorno).append(" registros.");
					else {
						logger.getLogger().append("\n-- Insertados ").append(retorno).append(" registros.");
						if( keyValues != null  &&  keyValues.size() > 0 ) {
							logger.getLogger().append("\n-- Generadas claves >> ");
							int nclave = 1;
							for( Object[] objClave : keyValues ) {
								logger.getLogger().append("\n--    ").append(nclave).append(": ").append(Arrays.toString(objClave));
							}
						}
					}
				}
				else {
					logger.getLogger().append("\n-- La operacion no ha realizado ningun cambio sobre la base de datos");
				}
				if( th != null ) {
					logger.printThrowable(th);
					th = null;
				}
			}
			close();
		}
		return retorno;
	}

	private void set(int i, Object value, PreparedStatement ps )
	throws SQLException, IOException
	{
		if( value instanceof java.sql.Date ) {
			ps.setDate(i, (java.sql.Date)value);
		}
		else if( value instanceof Date ) {
			ps.setTimestamp(i, new Timestamp(((Date)value).getTime()));
		}
		else if( value instanceof Enum ) {
			ps.setString(i, value.toString());
		}
		else if( value instanceof Integer ) {
			ps.setInt(i, (Integer)value);
		}
		else if( value instanceof Long ) {
			ps.setLong(i, (Long)value);
		}
		else if( value instanceof BigDecimal ) {
			ps.setBigDecimal(i, (BigDecimal)value);
		}
		else if( value instanceof Number ) {
			ps.setBigDecimal(i, new BigDecimal(value.toString()));
		}
		else if( value instanceof File ) {
			FileInputStream fin = new FileInputStream((File)value);
			ps.setBinaryStream(i, fin, fin.available());
		}
		else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Byte.TYPE) ) {
			ByteArrayInputStream bin = new ByteArrayInputStream((byte[])value);
			ps.setBinaryStream(i, bin, ((byte[])value).length);
		}
		else if( value.getClass().isArray()  &&  value.getClass().getComponentType().isAssignableFrom(Character.TYPE) ) {
			CharArrayReader car = new CharArrayReader((char[])value);
			ps.setCharacterStream(i, car, ((char[])value).length);
		}
		else { // if( value instanceof String ) {
			ps.setString(i, value.toString());
		}
	}

	public void close() {
		if( where != null )
			where.close();
		if( valores != null )
			valores.clear();
		if( batch != null  &&  batch.length > 0 ) {
			Arrays.fill(batch, null);
		}
		batch = null;
		where = null;
		columnas = null;
		valores = null;
		tabla = null;
		logger = null;
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		constructQuery(sb, null);
		return sb.toString();
	}

	/**
	 * Copia el InsertBuffer con la opcion de cambiar la funcionalidad.
	 * @param qb si es nulo, opera como un INSERT, si no lo es, opera como un UPDATE
	 */
	public InsertBuilder clone(QueryBuilder qb) {
		return clone(this.tabla, qb);
	}

	/**
	 * Copia el InsertBuffer con la opcion de cambiar la funcionalidad, y de tabla,
	 * manteniendo los parametros.<br />Util para aquellas tablas que comparten el nombre
	 * de muchas de sus columnas. El batch efectuado no se clona.
	 * @param tabla El nombre de la nueva tabla, si es nulo se mantiene la original
	 * @param qb  si es nulo, opera como un INSERT, si no lo es, opera como un UPDATE
	 */
	public InsertBuilder clone(String tabla, QueryBuilder qb) {
		InsertBuilder ib = new InsertBuilder(tabla != null &&  tabla.length() > 0 ? tabla : this.tabla, qb);
		ib.pretty = this.pretty;
		ib.columnas.addAll(this.columnas);
		ib.valores.addAll(this.valores);
		ib.logger = this.logger;
		return ib;
	}

	/**
	 * Realiza una copia identica del InserBuffer
	 */
	public InsertBuilder clone()
	{
		InsertBuilder ib = clone(this.tabla, where == null ? null : where.clone());
		ib.omitirNulos = this.omitirNulos;
		if( this.batch != null ) {
			int n = this.batch.length;
			ib.batch = new Object[n];
			System.arraycopy(this.batch, 0, ib.batch, 0, n);
		}
		return ib;
	}

	public void setPretty(boolean pretty) {
		this.pretty = pretty;
	}

	public void setSoloUnUpdate(boolean soloUno) {
		this.soloUno = soloUno;
	}

	@Override
	public void finalize() {
		close();
	}

	private void toXMLStream(Element root) {
		Element e = new Element("configuration");
		e.setAttribute("tabla", tabla);
		StringBuilder sb = new StringBuilder();
		sb.append(batchUpdated ? "y" : "n");
		sb.append(unmodifiableListActive ? "y" : "n");
		sb.append(omitirNulos ? "y" : "n");
		sb.append(pretty ? "y" : "n");
		sb.append(soloUno ? "y" : "n");
		sb.append(pretty ? "y" : "n");
		e.setText(sb.toString());
		root.addContent(e);

		e = new Element("dateFormatMask");
		e.setText(dateFormatMask);
		root.addContent(e);

		e = new Element("where");
		if( where != null ) {
			Element ewhere = new Element("QueryBuilder");
			where.toXMLStream(ewhere);
			e.addContent(ewhere);
		}
		root.addContent(e);

		e = new Element("columns");
		for( String col : columnas ) {
			ArrayList<Element> ale = new ArrayList<Element>();
			if( col != null  &&  col.length() > 0 ) {
				ale.add(new Element("name").setText(col));
			}
			e.addContent(ale);
		}
		root.addContent(e);

		if( batch != null  &&  batch.length > 0 ) {
			e = new Element("batch");
			e.setAttribute("ncolumns", Integer.toString(columnas.size()));
			for( Object object : batch ) {
				Object[] values = (Object[])object;
				if( values != null  &&  values.length == columnas.size() ) {
					for( Object value : values ) {
						toXMLStream(value, e);
					}
				}
			}
			root.addContent(e);
		}
		else if( valores != null  &&  valores.size() > 0 ) {
			e = new Element("values");
			for( Object value : valores ) {
				toXMLStream(value, e);
			}
			root.addContent(e);
		}
	}

	private void toXMLStream(Object value, Element epadre) {
		if( value == null ) {
			epadre.addContent(new Element("null"));
		}
		else if( value instanceof ConditionType ) {
			epadre.addContent(new Element("ct").setText(value.toString()));
		}
		else if( value instanceof java.sql.Date ) {
			epadre.addContent(new Element("dat").setAttribute("v", Long.toString(((java.sql.Date)value).getTime())));
		}
		else if( value instanceof Date ) {
			epadre.addContent(new Element("time").setAttribute("v", Long.toString(((java.util.Date)value).getTime())));
		}
		else if( value instanceof Integer ) {
			epadre.addContent(new Element("int").setAttribute("v", String.valueOf(value)));
		}
		else if( value instanceof Long ) {
			epadre.addContent(new Element("long").setAttribute("v", String.valueOf(value)));
		}
		else if( value instanceof Number ) {
			epadre.addContent(new Element("bd").setAttribute("v", String.valueOf(value)));
		}
		else {
			epadre.addContent(new Element("str").setText(String.valueOf(value)));
		}
	}

	private void fromXMLStream(Element root) {
		List<?> lista = root.getChildren();
		for( Object obj : lista ) {
			if( obj instanceof Element ) {
				Element e = (Element)obj;
				if( e.getName().equals("configuration") ) {
					tabla = e.getAttributeValue("tabla");
					String texto = e.getTextTrim();
					batchUpdated = texto.charAt(0) == 'y';
					unmodifiableListActive = texto.charAt(1) == 'y';
					omitirNulos = texto.charAt(2) == 'y';
					pretty = texto.charAt(3) == 'y';
					soloUno = texto.charAt(4) == 'y';
					pretty = texto.charAt(5) == 'y';
				}
				else if( e.getName().equals("where") ) {
					Element _where = e.getChild("QueryBuilder");
					if( _where != null ) {
						QueryBuilder qb = new QueryBuilder();
						qb.fromXMLStream(_where);
						where = qb;
					}
				}
				else if( e.getName().equals("dateFormatMask") ) {
					setDateFormat(e.getTextTrim());
				}
				else if( e.getName().equals("columns") ) {
					fromXMLStreamColumns(e);
				}
				else if( e.getName().equals("batch") ) {
					if( e.getAttribute("ncolumns") != null ) {
						int ncols = Integer.parseInt(e.getAttributeValue("ncolumns"));
						if( ncols > 0 ) {
							ArrayList<Object> alvalues = new ArrayList<Object>();
							for( Object obj2 : e.getChildren() ) {
								if( obj2 instanceof Element ) {
									Element eval = (Element)obj2;
									alvalues.add(fromXMLStreamValue(eval));
								}
							}
							int nrows = alvalues.size() / ncols;
							if( nrows > 0 ) {
								batch = new Object[nrows];
								int icol = 0;
								int irow = 0;
								for( ; irow < nrows; irow++ ) {
									batch[irow] = new Object[ncols];
								}
								irow = 0;
								for( Object obj2 : alvalues ) {
									((Object[])batch[irow])[icol] = obj2;
									icol++;
									if( icol == ncols ) {
										icol = 0;
										irow++;
									}
								}
							}
							alvalues.clear();
							alvalues = null;
						}
					}
				}
			}
		}
		if( batch == null ) {
			for( Object obj : lista ) {
				if( obj instanceof Element ) {
					Element e = (Element)obj;
					if( e.getName().equals("values") ) {
						for( Object obj2 : e.getChildren() ) {
							if( obj2 instanceof Element ) {
								Element eval = (Element)obj2;
								valores.add(fromXMLStreamValue(eval));
							}
						}
					}
				}
			}
		}
		if( unmodifiableListActive ) {
			columnas = Collections.unmodifiableList(columnas); // bloqueo las columnas	
		}
	}

	private void fromXMLStreamColumns(Element columns) {
		List<?> lista = columns.getChildren();
		for( Object obj : lista ) {
			if( obj instanceof Element ) {
				Element e = (Element)obj;
				if( e.getName().equals("name") ) {
					columnas.add(e.getTextTrim());
				}
			}
		}
	}

	private Object fromXMLStreamValue(Element e) {
		if( e.getName().equals("ct") ) {
			ConditionType conditionType = new ConditionType();
			conditionType.value = e.getText();
			return conditionType;
		}
		else if( e.getName().equals("dat") ) {
			long date = Long.valueOf(e.getAttributeValue("v"));
			return new java.sql.Date(date);
		}
		else if( e.getName().equals("time") ) {
			long date = Long.valueOf(e.getAttributeValue("v"));
			return new java.sql.Timestamp(date);
		}
		else if( e.getName().equals("int") ) {
			return Integer.valueOf(e.getAttributeValue("v"));
		}
		else if( e.getName().equals("long") ) {
			return Long.valueOf(e.getAttributeValue("v"));
		}
		else if( e.getName().equals("bd") ) {
			return new BigDecimal(e.getAttributeValue("v"));
		}
		else if( e.getName().equals("str") ) {
			return e.getText();
		}
		return null;
	}

	public static String toXMLString(InsertBuilder ib) {
		try {
			Element e = new Element("InsertBuilder");
			Document d = new Document(e);
			d.setRootElement(e);
			ib.toXMLStream(e);
			XMLOutputter xmlOutput = new XMLOutputter();
			xmlOutput.setFormat(Format.getCompactFormat());
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			xmlOutput.output(d, bout);
			return bout.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static InsertBuilder fromXMLString(String str) {
		try( StringReader stre = new StringReader(str) ) {
			SAXBuilder sb = new SAXBuilder();
			Document d = sb.build(stre);
			Element root = d.getRootElement();
			InsertBuilder ib = new InsertBuilder("__");
			ib.fromXMLStream(root);
			return ib;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
