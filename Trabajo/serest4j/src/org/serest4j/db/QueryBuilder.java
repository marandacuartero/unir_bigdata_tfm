package org.serest4j.db;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;


 /**
 * Esta clase permite que el programador pueda construir clausulas de tipo select
 * complejas sin necesidad de seguir un orden en la creaci�n del where, o en la
 * asignacion de columnas, o tablas, evitando los problemas en la concatenacion de
 * caracteres (AND que se olvidan, comillas que se pierden, etc.). Ademas automatiza
 * conversiones de fechas, enteros, etc. Todo esto mejora considerablemente el manteniemiento
 * del codigo.
 * Tambien controla la conversion de caracteres conflictivos como pueden ser las comillas.
 * Se puede configurar para que avise al programador cuando se estan utilizando valores
 * nulos que no deberian serlo, o para que los desheche de forma automatica.
 * Tambien facilita la creacion de clausulas del tipo "into", construyendo y visualizando
 * la consulta de forma correctamente visible por el usuario.
 * La impresion generada es un select ejecutable en cualquier programa
 * de tipo consola de acceso a base de datos (como SQLPLUS).
 * Ademas, al ser de tipo Buffer permite concatenar las llamadas a los procedimientos de
 * esta clase.
 */
public class QueryBuilder {

	private static final String AND = "and ";
	private static final String OR = "or ";
	
	private static final String LIKE = " like ";
	private static final String IS_NULL = " IS NULL ";
	private static final String IS_NOT_NULL = " IS NOT NULL ";
	
	private static final String EQUALS = " = ";
	private static final String NOT_EQUALS = " != ";

	private static final char PP_R = '\n';
	private static final char NP_R = ' ';

	private QueryBuilder parent = null;
	private SimpleDateFormat dateFormatInstance = null;
	private String dateFormatMask = null;

	private List<QueryCondition> tables = new ArrayList<QueryCondition>();
	private List<QueryCondition> columns = new ArrayList<QueryCondition>();
	private List<QueryCondition> where = new ArrayList<QueryCondition>();
	private List<String> op = new ArrayList<String>();
	
	private QueryCondition actualCondition;
	private StringBuilder endClausule = new StringBuilder();
	private boolean modeAnd;
	private boolean distinct;
	private boolean verifyValues;
	private boolean emptyAsNull;
	private boolean ceroAsNull;
	private boolean pretty;
	private String tableAliasEnCurso = null;
	private String strJoin;
	
	private transient TMTransactionalLogger logger = null;
	
	public QueryBuilder() {
		this( null );
	}

	private void setLogger(TMTransactionalLogger log) {
		this.logger = log;
	}

	private char getPPC() { return pretty ? PP_R : NP_R; }

	
	/**
	 * Construye un query basico con las columnas y el nombre de la tablas.
	 * @param columnas
	 * @param tablas
	 */
	public QueryBuilder(String columnas, String tablas)
	{
		this(null);
		appendColumn(columnas);
		appendTable(tablas);
	}

	private QueryBuilder(QueryBuilder p)
	{
		parent = p;
		if( p != null ) {
			pretty = p.pretty;
			dateFormatInstance = p.dateFormatInstance;
			dateFormatMask = p.dateFormatMask;
		}
		else {
			dateFormatMask = "yyyy-MM-dd HH:mm:ss";
			dateFormatInstance = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		}
		actualCondition = null;
		modeAnd = true;
		distinct = false;
		verifyValues = false;
		emptyAsNull = true;
		ceroAsNull = false;
		endClausule.setLength(0);
		strJoin="";
	}

	/**
	 * Cambia el modo en que este QueyBuffer presenta las fechas
	 * Por defecto se utiliza el modo americano 'yyyy-MM-dd HH.mm.ss"
	 * @param simpleDateFormat
	 * @return
	 */
	public QueryBuilder setDateFormat(String simpleDateFormatMask) {
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
	 * Configura como SELECT DISTINCT
	 */
	public QueryBuilder setDistinct()
	{
		distinct = true;
		return this;
	}
	
	/**
	 * Inserta el nombre de una tabla en el FROM
	 */
	public QueryBuilder appendTable( String table )
	{
		return appendTable(table, null);
	}

	public QueryBuilder appendTable( String table, String alias )
	{
		if( table != null )
			table = table.trim();
		if( alias != null )
			alias = alias.trim();
		QueryBuilder nullQueryBuffer = null;
		if( check(table) ) {
			if( check(alias) )
				return appendTable(nullQueryBuffer, table + " " + alias);
			else
				return appendTable(nullQueryBuffer, table);
		}
		else if( check(alias) ) {
			return appendTable(nullQueryBuffer, alias);
		}
		return this;
	}

	public QueryBuilder appendTable( QueryBuilder qb, String alias )
	{
		if( check(qb)  ||  check(alias) ) {
			QueryCondition qc = new QueryCondition();
			qc.first = qb;
			qc.last = alias;
			tables.add( qc );
		}
		return this;
	}

	/**
	 * Establece un alias de tabla por defecto, de manera que todas las columnas
	 * que se coloquen en esta query iran precedidas por este alias
	 * Si es nulo, no se establece alias por defecto
	 * @param tableAlias
	 */
	public void setTableAliasEnCurso(String tableAlias) {
		this.tableAliasEnCurso = tableAlias == null ? null : tableAlias.trim();
	}

	/**
	 * Inserta el nombre de una columna en el SELECT
	 * @param columnName
	 * @return
	 */
	public QueryBuilder appendColumn( String columnName )
	{
		return appendColumn(null, columnName, null);
	}

	/**
	 * Inserta el nombre de una columna en el SELECT. Si el alias es nulo, tomara el tableAlias establecido por defecto
	 * @param tableAlias
	 * @param columnName
	 * @return
	 */
	public QueryBuilder appendColumn( String tableAlias, String columnName )
	{
		return appendColumn(tableAlias, columnName, null);
	}

	/**
	 * Inserta el nombre de una columna en el SELECT. Si el alias es nulo, tomara el tableAlias establecido por defecto
	 * @param tableAlias
	 * @param columnName
	 * @param columAlias
	 * @return
	 */
	public QueryBuilder appendColumn( String tableAlias, String columnName, String columAlias ) {
		if( columnName != null )
			columnName = columnName.trim();
		if( check(columnName) ) {
			if( tableAlias != null )
				tableAlias = tableAlias.trim();
			if( check(tableAlias) )
				columnName = tableAlias + "." + columnName;
			else if( check(this.tableAliasEnCurso) )
				columnName = this.tableAliasEnCurso + "." + columnName;
			if( columAlias != null )
				columAlias = columAlias.trim();
			if( check(columAlias) )
				columnName = columnName + " as " + columAlias;
		}
		return _appendColumn(null, columnName);
	}

	public QueryBuilder appendColumn( QueryBuilder qb, String alias ) {
		return _appendColumn(qb, alias);
	}

	private QueryBuilder _appendColumn( QueryBuilder qb, String alias )
	{
		if( check(qb)  ||  check(alias) ) {
			QueryCondition qc = new QueryCondition();
			qc.first = qb;
			qc.last = alias;
			columns.add( qc );
		}
		return this;
	}

	public QueryBuilder appendColumns( String[] columnas ) {
		return appendColumns(null, columnas);
	}

	public QueryBuilder appendColumns( String tableAlias, String[] columnas ) {
		QueryBuilder thisQuery = this;
		if( columnas != null ) {
			for( String columna : columnas ) {
				thisQuery = appendColumn(tableAlias, columna, null);
			}
		}
		return thisQuery;
	}

	public boolean isWhereEmpty() {
		return where == null  ||  where.isEmpty();
	}
	
	////////////////////////////////////////
	//  Para la generacion del WHERE   /////
	////////////////////////////////////////
	
	/**
	 * Obliga a que los valores introducidos no sean nulos
	 */
	public QueryBuilder verifyValue( boolean b )
	{
		verifyValues = b;
		return this;
	}

	/**
	 * Las cadenas vacias equivalen a null.
	 * Por defecto este indicador esta activado
	 * @param b
	 * @return
	 */
	public QueryBuilder setEmptyAsNull( boolean b )
	{
		emptyAsNull = b;
		return this;
	}

	/**
	 * Los numeros menores o iguales a cero se consideran nulos
	 * Por defecto este indicador esta inactivo
	 * @param b
	 * @return
	 */
	public QueryBuilder setCeroAsNull( boolean b )
	{
		ceroAsNull = b;
		return this;
	}

	
	public QueryBuilder dateValue( Date d )
	{
		if( d != null ) {
			Calendar c = Calendar.getInstance();
			c.setTime(d);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			d = new java.sql.Date(c.getTimeInMillis());
		}
		return save(d);
	}
	
	public QueryBuilder timeValue( Date d )
	{
		return save( d == null ? null : new Timestamp(d.getTime()));
	}
	
	public QueryBuilder intValue( int i )
	{
		return save(Integer.valueOf(i));
	}
	
	public QueryBuilder longValue( long l)
	{
		return save(Long.valueOf(l));
	}
	
	public QueryBuilder doubleValue( double d )
	{
		return save(Double.valueOf(d));
	}
	
	public QueryBuilder numberValue( String d ) {
		BigDecimal bd = null;
		try{ bd = new BigDecimal(d); }catch(Exception e){}
		return save(bd);
	}

	public QueryBuilder numberValue( BigDecimal d )
	{
		return save(d);
	}
	
	public QueryBuilder charValue( String str )
	{
		return save(str);
	}

	public QueryBuilder value( Object obj )
	{
		return save(obj);
	}

	public QueryBuilder columnValue( String columnName )
	{
		return columnValue(null, columnName);
	}

	public QueryBuilder columnValue( String tableAlias, String columnName )
	{
		if( columnName != null )
			columnName = columnName.trim();
		if( check(columnName) ) {
			if( check(tableAlias) )
				return save( new ColumnType(tableAlias + "." + columnName) );
			else if( check(this.tableAliasEnCurso) )
				return save( new ColumnType(this.tableAliasEnCurso + "." + columnName) );
			else
				return save( new ColumnType(columnName) );
		}
		return this;
	}

	private QueryBuilder save( Object obj )
	{
		if( check(obj) )
		{
			if( actualCondition == null ) {
				actualCondition = new QueryCondition();
				actualCondition.first = obj;
			}
			else {
				actualCondition.last = obj;
				appendCondition(actualCondition);
				actualCondition = null;
			}
		}
		else
		{
			if( verifyValues )
			{
				StringBuilder msg = new StringBuilder("El valor no puede ser NULL. ");
				if( actualCondition != null ) {
					msg.append('(').append(actualCondition.first).append(actualCondition.op);
					msg.append(actualCondition.last).append(')').append('.');
					if( tables.size() > 0 )
						buildFrom(msg, null);
				}
				actualCondition = null;
				throw new NullPointerException(msg.toString());
			}
			actualCondition = null;
		}
		return this;
	}

	public QueryBuilder in(String columnName, Collection<String> listadoValores) {
		return in(null, columnName, listadoValores);
	}
	
	public QueryBuilder in(String tableAlias, String columnName, Collection<String> listadoValores) {
		if( listadoValores != null  &&  listadoValores.size() == 1 ) {
			Iterator<String> it = listadoValores.iterator();
			if( it.hasNext() ) {
				columnValue(tableAlias, columnName).charValue(it.next());
			}
		}
		else {
			QueryBuilder qbbr = openBrackets().or();
			try {
				for( String value : listadoValores ) {
					qbbr.columnValue(tableAlias, columnName).charValue(value);
				}
			}
			finally {
				qbbr = qbbr.closeBrackets();
			}
		}
		return this;
	}

	public QueryBuilder in(String columName, QueryBuilder qb) {
		return _in(null, columName, qb, true);
	}
	
	public QueryBuilder in(String tableAlias, String columName, QueryBuilder qb) {
		return _in(tableAlias, columName, qb, true);
	}

	public QueryBuilder notIn(String columName, QueryBuilder qb) {
		return _in(null, columName, qb, false);
	}

	public QueryBuilder notIn(String tableAlias, String columName, QueryBuilder qb) {
		return _in(tableAlias, columName, qb, false);
	}

	private QueryBuilder _in(String tableAlias, String columName, QueryBuilder qb, boolean isInto) {
		if( check(qb) ) {
			if( columName != null )
				columName = columName.trim();
			if( check(columName) ) {
				if( check(tableAlias) )
					columName = tableAlias + "." + columName;
				else if( check(this.tableAliasEnCurso) )
					columName = this.tableAliasEnCurso + "." + columName;
				QueryCondition qc = new QueryCondition();
				qc.first = new ColumnType(columName);
				qc.last = qb;
				qc.op = isInto ? " IN " : " NOT IN ";
				return appendCondition(qc);
			}
		}
		return this;
	}

	public QueryBuilder between(Object d0, Object d1, Object d2) {
		QueryCondition qc = new QueryCondition();
		if( d0 instanceof String )
			qc.first = new ColumnType(d0.toString());
		else
			qc.first = d0;
		if( d1 instanceof String )
			qc.last = new ColumnType(d1.toString());
		else
			qc.last = d1;
		qc.op = " between ";
		where.add(qc);
		op.add(" and ");
		qc = new QueryCondition();
		qc.first = new ColumnType("");
		if( d2 instanceof String )
			qc.last = new ColumnType(d2.toString());
		else
			qc.last = d2;
		qc.op = "";
		return appendCondition(qc);
	}
	
	/**
	 * Inserta una condicion completa en el WHERE.
	 */
	public QueryBuilder appendCondition( String str )
	{
		if ( check(str) )
		{
			QueryCondition qc = new QueryCondition();
			qc.first = new ColumnType(str);
			qc.last = new ColumnType("");
			qc.op = "";
			return appendCondition(qc);
		}
		return this;
	}

	private QueryBuilder appendCondition( QueryCondition qc )
	{
		where.add(qc);
		op.add( modeAnd ? getPPC() + AND : getPPC() + OR );
		return this;
	}
	
	/**
	 * Inserta un operador en la condicion que se est�
	 * generando.
	 */
	public QueryBuilder op( String op )
	{
		if( check(op)  &&  actualCondition != null )
			actualCondition.op = op;
		return this;
	}
	
	/**
	 * Inserta el operador LIKE.
	 */
	public QueryBuilder like()
	{
		return op( LIKE );
	}
	
	/**
	 * Inserta el operador =.
	 */
	public QueryBuilder equ()
	{
		return op( EQUALS );
	}
	
	/**
	 * Inserta el operador !=.
	 */
	public QueryBuilder dist()
	{
		return op( NOT_EQUALS );
	}
	
	/**
	 * Inserta el la condicion "columna IS NULL".
	 */
	public QueryBuilder isNull( String columna )
	{
		if( check(columna) )
			appendCondition( columna + IS_NULL );
		return this;
	}

	public QueryBuilder isNull( String tabla, String columna )
	{
		if( !check(tabla) )
			return isNull(columna);
		else if( check(columna) )
			return isNull(tabla + "." + columna);
		return this;
	}

	/**
	 * Inserta el la condicion "columna IS NOT NULL".
	 */
	public QueryBuilder isNotNull( String columna )
	{
		if( check(columna) )
			appendCondition( columna + IS_NOT_NULL );
		return this;
	}

	public QueryBuilder isNotNull( String tabla, String columna )
	{
		if( !check(tabla) )
			return isNotNull(columna);
		else if( check(columna) )
			return isNotNull(tabla + "." + columna);
		return this;
	}

	/**
	 * Para que las condiciones se relacionen con AND.
	 */
	public QueryBuilder and()
	{
		modeAnd = true;
		return this;
	}
	
	/**
	 * Para que las condiciones se relacionen con OR.
	 */
	public QueryBuilder or()
	{
		modeAnd = false;
		return this;
	}
	
	/**
	 * Abre llaves para crear una clausula independiente.
	 * .. columna1 = valor1 AND ( clausula ) AND columna2 = valor2 ...
	 * El QueryBuffer devuelto es otro distinto que el que se estaba utilizando,
	 * y se utiliza para almacenar la clausula de los parentesis.
	 */
	public QueryBuilder openBrackets()
	{
		QueryBuilder qb = new QueryBuilder( this );
		QueryCondition qc = new QueryCondition();
		qc.first = qb;
		qc.last = new ColumnType("");
		qc.op = "";
		synchronized (ar) {
			if( ar.compareAndSet(null, qc) ) {
				return qb;	
			}
		}
		throw new IllegalStateException("openBrackets ya abiertos!!");
	}

	private AtomicReference<QueryCondition> ar = new AtomicReference<QueryCondition>(null);

	/**
	 * Cierra llaves y anexiona la clausula creada a la
	 * sentencia. El QueryBuffer devuelto es aquel desde el
	 * que se invoco el metodo open().
	 */
	public QueryBuilder closeBrackets()
	{
		synchronized (parent.ar) {
			if( isWhereEmpty() ) {
				parent.ar.set(null);
				return parent;
			}
			else {
				return parent.appendCondition(parent.ar.getAndSet(null));	
			}
		}
	}

	/**
	 * Para adjuntar los order by, group by, etc.
	 */
	public QueryBuilder closeWith( String str )
	{
		if( check(str) ) {
			endClausule.setLength(0);
			endClausule.append(str);
		}
		return this;
	}
	
	
	public QueryBuilder addToClose( String str )
	{
		if( check(str) )
			endClausule.append(str);
		return this;
	}
	/**
	 * Para a�adir join entre tablas.
	 */
	public QueryBuilder addJoin( String str )
	{
		if( check(str) ) {
			StringBuilder sb = new StringBuilder();
			if( check(strJoin) )
				sb.append(strJoin).append(pretty ? "\n\t" : "  ");
			sb.append(str);
			strJoin = sb.toString();
		}
		return this;
	}

	
	/**
	 * Devuelve las columnas creadas tal como estarian en el select.
	 * " columna1, columna2, columnaN "
	 */
	private void buildColumns(StringBuilder printed, List<Object> values)
	{
		int i = printed.length();
		int ltrunc = 0;
		for( QueryCondition qc : columns ) {
			if( qc.first != null ) {
				QueryBuilder qbp = (QueryBuilder)qc.first;
				printed.append('(').append(getPPC());
				qbp.buildQuery(printed, values, false);
				printed.append(')');
				if( check(qc.last) )
					printed.append(' ').append(qc.last);
			}
			else {
				printed.append(qc.last);
			}
			ltrunc += (printed.length() - i);
			i = printed.length();
			printed.append(',');
			if( ltrunc > 50 ) {
				printed.append(getPPC());
				ltrunc = 0;
			}
			else
				printed.append(' ');
		}
		if( printed.length() > i )
			printed.setLength(i);
		else
			printed.append("*");
	}

	/**
	 * Devuelve el FROM de la sentencia.
	 *
	 * " from tabla1, tabla2, tablaN "
	 */
	private void buildFrom(StringBuilder printed, List<Object> values)
	{
		int i = printed.length();
		int ltrunc = 0;
		printed.append(getPPC()).append("from " );
		for( QueryCondition qc : tables ) {
			if( qc.first != null ) {
				QueryBuilder qbp = (QueryBuilder)qc.first;
				printed.append('(').append(getPPC());
				qbp.buildQuery(printed, values, false);
				printed.append(')');
				if( check(qc.last) )
					printed.append(' ').append(qc.last);
			}
			else {
				printed.append(qc.last);
			}
			ltrunc += (printed.length() - i);
			i = printed.length();
			printed.append(',');
			if( ltrunc > 50 ) {
				printed.append(getPPC());
				ltrunc = 0;
			}
			else
				printed.append(' ');
		}		
		printed.setLength(i);
	}

	/**
	 * Devuelve la sentencia WHERE que hemos generado.
	 * " where condicion1 AND/OR condicion2 AND/OR condicionN "
	 */
	protected void buildConditions(StringBuilder printed, List<Object> values)
	{
		int i = 0;
		int l = printed.length();
		for( QueryCondition qc : where ) {
			if( qc.first instanceof ColumnType ) {
				printed.append( ((ColumnType)qc.first).column );
			}
			else if( qc.first instanceof QueryBuilder ) { // es un parentesis
				QueryBuilder qbp = (QueryBuilder)qc.first;
				int n = printed.length();
				printed.append('(');
				qbp.buildConditions(printed, values);
				if( printed.length() > (n+1) )
					printed.append(')');
				else
					printed.setLength(n);
			}
			else {
				// es objeto
				putObject( qc.first, printed, values );
			}
			printed.append(' ').append(qc.op.trim()).append(' ');
			if( qc.last instanceof ColumnType ) {
				printed.append( ((ColumnType)qc.last).column );
			}
			else if( qc.last instanceof QueryBuilder ) { // es un select embebido
				QueryBuilder qbp = (QueryBuilder)qc.last;
				printed.append('(');
				qbp.buildQuery(printed, values, false);
				printed.append(')');
			}
			else {
				// es objeto
				putObject( qc.last, printed, values );
			}
			l = printed.length();
			if( i < op.size() )
				printed.append(op.get(i));
			i++;
		}
		printed.setLength(l);
	}

	private void putObject(Object obj, StringBuilder printed, List<Object> values)
	{
		if( values == null ) {
			if( obj instanceof Number ) {
				printed.append(obj);
			}
			else {
				printed.append('\'');
				if( obj instanceof Date ) {
					printed.append(dateFormatInstance.format((Date)obj));
				}
				else {
					printed.append( obj.toString() );
				}
				printed.append('\'');
			}
		}
		else {
			printed.append('?');
			values.add(obj);
		}
	}

	private void buildQuery(StringBuilder printed, List<Object> values, boolean forUpdate)
	{
		printed.append("select ");
		if( distinct )
			printed.append("distinct ");
		buildColumns(printed, values);
		buildFrom(printed, values);
		if( check(strJoin) ) {
			printed.append(getPPC()).append(strJoin);
		}
		if( !where.isEmpty() ) {
			int n1 = printed.length();
			printed.append(getPPC()).append("where ");
			int n2 = printed.length();
			buildConditions(printed, values);
			if( printed.length() <= n2 )
				printed.setLength(n1);
		}
		if( endClausule.length() > 0 ) {
			printed.append(getPPC()).append(endClausule);
		}
		if( forUpdate ) {
			printed.append(" for update");
		}
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
		else { // if( value instanceof String ) {
			ps.setString(i, value.toString());
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getConvertType(int i, Class<T> tipo, ResultSet rs) throws SQLException {
		Object obj = get(i, tipo, rs);
		return (T)obj;
	}
	
	private Object get(int i, Class<?> tipo, ResultSet rs) throws SQLException
	{
		if( tipo.isAssignableFrom(java.sql.Date.class) ) {
			return rs.getDate(i);
		}
		else if( tipo.isAssignableFrom(Date.class) ) {
			return rs.getTimestamp(i);
		}
		else if( tipo.equals(Integer.class) ) {
			i = rs.getInt(i);
			if( rs.wasNull() )
				return null;
			else
				return Integer.valueOf(i);
		}
		else if( tipo == Integer.TYPE ) {
			i = rs.getInt(i);
			if( rs.wasNull() )
				return null;
			else
				return Integer.valueOf(i);
		}
		else if( tipo.equals(Long.class) ) {
			long l = rs.getLong(i);
			if( rs.wasNull() )
				return null;
			else
				return Long.valueOf(l);
		}
		else if( tipo == Integer.TYPE ) {
			long l = rs.getLong(i);
			if( rs.wasNull() )
				return null;
			else
				return Long.valueOf(l);
		}
		else if( tipo.isAssignableFrom(Number.class) ) {
			return rs.getBigDecimal(i);
		}
		else { // if( value instanceof String ) {
			return rs.getString(i);
		}
	}

	/**
	 * Utiliza la query para generar y ejecutar un DELETE. Este solo funcionara
	 * correctamente con una sola tabla.
	 */
	public int executeDelete( TMTransactionalLogger log ) throws SQLException
	{
		setLogger(log);
		PreparedStatement ps = null;
		StringBuilder tostr = new StringBuilder("DELETE from ").append(tables.get(0).last);
		try {
			ArrayList<Object> values = new ArrayList<Object>();
			int n1 = tostr.length();
			tostr.append(getPPC()).append("where ");
			int n2 = tostr.length();
			StringBuilder sb = new StringBuilder(tostr.toString());
			buildConditions(tostr, null);
			buildConditions(sb, values);
			if( tostr.length() <= n2 ) {
				tostr.setLength(n1);
				sb.setLength(n1);
			}
			ps = log.getConexion().prepareStatement(sb.toString());
			int i = 1;
			for( Object value : values ) {
				set(i, value, ps);
				i++;
			}
			if( logger.hashLogger()  &&  tostr.length() > 0 ) {
				logger.getLogger().append(tostr).append(';');
			}
			int eliminados = ps.executeUpdate();
			if( logger.hashLogger() ) {
				logger.mark();
				logger.getLogger().append("\n-- Eliminados " + eliminados + " registros");
			}
			return eliminados;
		}
		catch(Exception e) {
			String strQuery = tostr.toString() + " \nGenera: " + e;
			throw new SQLException(strQuery);
		}
		finally {
			try{ if( ps != null ) ps.close(); }catch(Exception e){}
		}
	}

	// uso del QueryBuffer como extension de los drivers de base de datos
	private PreparedStatement preparedStatement = null;
	private PreparedStatement prepareStatement( java.sql.Connection c, boolean forUpdate ) throws SQLException, IOException
	{
		closeStatement();
		StringBuilder sb = new StringBuilder();
		ArrayList<Object> values = new ArrayList<Object>();
		buildQuery(sb, values, forUpdate);
		if( forUpdate )
			preparedStatement = c.prepareStatement(sb.toString(), ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		else  {
			preparedStatement = c.prepareStatement(sb.toString(), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		}
		int i = 1;
		for( Object value : values ) {
			set(i, value, preparedStatement);
			i++;
		}
		return preparedStatement;
	}

	public int executeCount( TMTransactionalLogger log ) throws SQLException {
		closeStatement();
		List<QueryCondition> _columns = new ArrayList<QueryCondition>();
		_columns.addAll(columns);
		columns.clear();
		appendColumn("count(*)");
		try {
			ResultSet rs = executeQuery(log);
			rs.next();
			int resultado = rs.getInt(1);
			if( logger.hashLogger() ) {  
				logger.getLogger().append("\n-- Encontrados ").append(resultado).append(" registros");
			}
			return resultado;
		}
		finally {
			columns.clear();
			columns.addAll(_columns);
			_columns.clear();
			_columns = null;
			closeStatement();
		}
	}

	public int firstId( String idColumn, TMTransactionalLogger log ) throws SQLException {
		closeStatement();
		List<QueryCondition> _columns = new ArrayList<QueryCondition>();
		_columns.addAll(columns);
		columns.clear();
		appendColumn(idColumn);
		try {
			ResultSet rs = executeQuery(log);
			if( rs.next() ) {
				int retorno = rs.getInt(1);
				if( logger.hashLogger() ) {
				logger.getLogger().append("\n-- Encontrado id=").append(retorno);
				}
				return retorno;
			}
		}
		finally {
			columns.clear();
			columns.addAll(_columns);
			_columns.clear();
			_columns = null;
			closeStatement();
		}
		if( logger.hashLogger() ) {
		logger.getLogger().append("\n-- No se encontro ningun registro");
		}
		return -1;
	}

	public <T> void listColumnValues( String idColumn, Collection<T> datos, Class<T> tipo, TMTransactionalLogger log ) throws SQLException {
		closeStatement();
		List<QueryCondition> _columns = new ArrayList<QueryCondition>();
		_columns.addAll(columns);
		columns.clear();
		appendColumn(idColumn);
		try {
			ResultSet rs = executeQuery(log);
			while( rs.next() ) {
				T obj = getConvertType(1, tipo, rs);
				datos.add(obj);
			}
		}
		finally {
			columns.clear();
			columns.addAll(_columns);
			_columns.clear();
			_columns = null;
			closeStatement();
		}
		if( logger.hashLogger() ) {
		logger.getLogger().append("\n-- Encontrados ").append(datos.size()).append(" registros");
		}
	}

	private ResultSet resultSet = null;

	public ResultSet getResultSet() {
		return resultSet;
	}

	/**
	 * Ejecuta esta query. Solo para SELECTs.
	 */
	public ResultSet executeQuery( TMTransactionalLogger log ) throws SQLException
	{
		setLogger(log);
		return _executeQuery(log, false);
	}

	public ResultSet executeQuery(TMTransactionalLogger log, boolean forUpdate ) throws SQLException
	{
		setLogger(log);
		return _executeQuery(log, forUpdate);
	}

	private int size = 0;
	
	public int size() {
		return this.size;
	}
	
	private ResultSet _executeQuery(TMTransactionalLogger log, boolean forUpdate ) throws SQLException
	{
		Throwable th = null;
		try {
			closeStatement();
			resultSet = prepareStatement(log.getConexion(), forUpdate).executeQuery();
			return resultSet;
		}
		catch(Exception e) {
			th = e;
			closeStatement();
			throw new SQLException(e.getMessage());
		}
		finally {
			if( logger.hashLogger() ) {
			logger.mark();
			buildQuery(logger.getLogger(), null, forUpdate);
			logger.getLogger().append(';');
			if( resultSet != null ) {
				if( resultSet.last() ) {
					int nresultados = resultSet.getRow();
					resultSet.beforeFirst();
					logger.getLogger().append("\n-- Encontrados ").append(nresultados).append(" registros");
					this.size = nresultados;
				}
				else {
					logger.getLogger().append("\n-- La consulta no obtuvo ningun resultado");
				}
			}
			}
			if( th != null ) {
				log.printThrowable(th);
				th = null;
			}
		}
	}

	public <T extends Enum<T>> T getEnum(String columnLabel, Class<T> claseEnumeracion, T valorPorDefecto) throws SQLException {
		if( resultSet != null ) {
			String name = resultSet.getString(columnLabel);
			if( !resultSet.wasNull() ) {
				try {
					T t = Enum.valueOf(claseEnumeracion, name);
					return t;
				}
				catch(Exception e){
					if( logger.hashLogger() ) {
						logger.getLogger().append("\n\nERROR cargando literal=" + name + " en columna=" + columnLabel + ", no existe en la enumeracion " + claseEnumeracion.getSimpleName())
						.append("\nSe devuelve valor por defecto=" + valorPorDefecto).append('\n');
					}
				}
			}
		}
		return valorPorDefecto;
	}

	public <T extends Enum<T>> T getEnum(int columnIndex, Class<T> claseEnumeracion, T valorPorDefecto) throws SQLException {
		if( resultSet != null ) {
			String name = resultSet.getString(columnIndex);
			if( !resultSet.wasNull() ) {
				try {
					T t = Enum.valueOf(claseEnumeracion, name);
					return t;
				}
				catch(Exception e){
					if( logger.hashLogger() ) {  
					logger.getLogger().append("\n\nERROR cargando literal=" + name + " en columna=" + columnIndex + ", no existe en la enumeracion " + claseEnumeracion.getSimpleName())
					.append("\nSe devuelve valor por defecto=" + valorPorDefecto).append('\n');
					}
				}
			}
		}
		return valorPorDefecto;
	}

	public static <T extends Enum<T>> T getEnum(ResultSet rs, String columnLabel, Class<T> claseEnumeracion, T valorPorDefecto) throws SQLException {
		if( rs != null ) {
			String name = rs.getString(columnLabel);
			if( !rs.wasNull() ) {
				try {
					T t = Enum.valueOf(claseEnumeracion, name);
					return t;
				}
				catch(Exception e){}
			}
		}
		return valorPorDefecto;
	}

	public static <T extends Enum<T>> T getEnum(ResultSet rs, int columnIndex, Class<T> claseEnumeracion, T valorPorDefecto) throws SQLException {
		if( rs != null ) {
			String name = rs.getString(columnIndex);
			if( !rs.wasNull() ) {
				try {
					T t = Enum.valueOf(claseEnumeracion, name);
					return t;
				}
				catch(Exception e){}
			}
		}
		return valorPorDefecto;
	}

	public String getString(String columnLabel) throws SQLException {
		if( resultSet != null ) {
			byte[] b = resultSet.getBytes(columnLabel);
			if( !resultSet.wasNull()  &&  b != null  &&  b.length > 0 ) {
				return new String(b);
			}
		}
		return null;
	}

	public String getString(int columnIndex) throws SQLException {
		if( resultSet != null ) {
			return resultSet.getString(columnIndex);
		}
		return null;
	}

	public void close() {
		closeStatement();
		parent = null;
		if( tables != null )
			tables.clear();
		tables = null;
		if( columns != null )
			columns.clear();
		columns = null;
		if( where != null )
			where.clear();
		where = null;
		if( op != null )
			op.clear();
		op = null;
		actualCondition = null;
		endClausule.setLength(0);
		endClausule = null;
		logger = null;
	}

	public void closeStatement() {
		if( resultSet != null ) {
			try{ resultSet.close(); }catch(Exception e){}
		}
		if( preparedStatement != null ) {
			try{ preparedStatement.close(); }catch(Exception e){}
		}
		resultSet = null;
		preparedStatement = null;
	}

	// Invocacion de para cierre de cursores abiertos.
	public void finalize() {
		close();
	}

	/**
	 * Genera la query.
	 */
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		buildQuery(sb, null, false);
		return sb.toString();
	}
	
	/**
	 * Crea una nueva instancia exactamente igual a
	 * esta QueryBuffer, y en el mismo estado de construcci�n.
	 */
	public QueryBuilder clone()
	{
		QueryBuilder nueva = new QueryBuilder(parent);
		nueva.actualCondition = qcClone(nueva, this.actualCondition);
		nueva.tables = vClone(nueva, this.tables);
		nueva.columns = vClone(nueva, this.columns);
		nueva.where = vClone(nueva, this.where);
		nueva.op = new ArrayList<String>();
		nueva.op.addAll(this.op);
		nueva.endClausule.setLength(0);
		nueva.endClausule.append(this.endClausule);
		nueva.modeAnd = this.modeAnd;
		nueva.distinct = this.distinct;
		nueva.verifyValues = this.verifyValues;
		nueva.emptyAsNull = this.emptyAsNull;
		nueva.ceroAsNull = this.ceroAsNull;

		nueva.pretty = pretty;
		nueva.tableAliasEnCurso = tableAliasEnCurso;
		nueva.setLogger(this.logger);
		return nueva;
	}
	
	private QueryCondition qcClone(QueryBuilder p, QueryCondition qc) {
		QueryCondition res = null;
		if( qc != null ) {
			res = new QueryCondition();
			res.first = objClone(p, qc.first);
			res.last = objClone(p, qc.last);
			res.op = qc.op;
		}
		return res;
	}
	
	private Object objClone(QueryBuilder p, Object obj) {
		if( obj == null )
			return null;
		else if( obj instanceof ColumnType ) {
			return new ColumnType( ((ColumnType)obj).column );
		}
		else if( obj instanceof QueryBuilder ) { // es un parentesis
			QueryBuilder qb = ((QueryBuilder)obj).clone();
			if( qb.parent != null )
				qb.parent = p;
			return qb;
		}
		else if( obj instanceof java.sql.Date ) {
			return (java.sql.Date)obj;
		}
		else if( obj instanceof Date ) {
			return new Timestamp(((Date)obj).getTime());
		}
		else if( obj instanceof Integer ) {
			return Integer.valueOf(obj.toString());
		}
		else if( obj instanceof Long ) {
			return Long.valueOf(obj.toString());
		}
		else if( obj instanceof Number ) {
			return new BigDecimal(obj.toString());
		}
		else
			return obj.toString();
	}
	
	private List<QueryCondition> vClone( QueryBuilder qb, List<QueryCondition> v )
	{
		List<QueryCondition> r = new ArrayList<QueryCondition>();
		for( QueryCondition qc : v )
		{
			r.add(qcClone(qb, qc));
		}
		return r;
	}
	
	// Comprueba un objeto nulo para base de datos.
	// La cadena vacia se considera tambien nulo.
	private boolean check( Object o )
	{
		if( o == null ) return false;
		else if( emptyAsNull  &&  o instanceof String  &&  emptyAsNull ) return ((String)o).length() > 0;
		else if( ceroAsNull  &&  o instanceof Float ) return ((Float)o).floatValue() > 0;
		else if( ceroAsNull  &&  o instanceof Double ) return ((Double)o).doubleValue() > 0.0;
		else if( ceroAsNull  &&  o instanceof BigDecimal ) return ((BigDecimal)o).compareTo(new BigDecimal(0.0)) > 0;
		else if( ceroAsNull  &&  o instanceof Number ) return ((Number)o).longValue() > 0;
		return true;
	}
	
	public void setPretty(boolean pretty) {
		this.pretty = pretty;
	}

	private class ColumnType {
		String column;
		ColumnType(String str) { column = str; }
		public String toString() { return column; }
	}
	
	private class QueryCondition {
		public Object first, last;
		public String op = EQUALS;
	}

	void toXMLStream(Element root) {
		Element e = new Element("configuration");
		StringBuilder sb = new StringBuilder();
		sb.append(modeAnd ? "y" : "n");
		sb.append(distinct ? "y" : "n");
		sb.append(verifyValues ? "y" : "n");
		sb.append(emptyAsNull ? "y" : "n");
		sb.append(ceroAsNull ? "y" : "n");
		sb.append(pretty ? "y" : "n");
		e.setText(sb.toString());
		root.addContent(e);

		e = new Element("strJoin");
		e.setText(strJoin);
		root.addContent(e);

		e = new Element("endClausule");
		e.setText(endClausule.toString());
		root.addContent(e);

		e = new Element("dateFormatMask");
		e.setText(dateFormatMask);
		root.addContent(e);

		e = new Element("tables");
		for( QueryCondition qc : tables ) {
			toXMLStream(qc, e);
		}
		root.addContent(e);
		
		e = new Element("columns");
		for( QueryCondition qc : columns ) {
			toXMLStream(qc, e);
		}
		root.addContent(e);

		e = new Element("where");
		for( QueryCondition qc : where ) {
			toXMLStream(qc, e);
		}
		root.addContent(e);

		e = new Element("op");
		sb.setLength(0);
		int l = sb.length();
		for( String str : op) {
			sb.append(str);
			l = sb.length();
			sb.append(',');
		}
		sb.setLength(l);
		e.setText(sb.toString());
		root.addContent(e);
	}

	private void toXMLStream(QueryCondition qc, Element epadre) {
		Element e = new Element("qc");
		e.setAttribute("op", qc.op);
		Element first = new Element("first");
		toQueryConditionFieldXMLStream(qc.first, first);
		e.addContent(first);
		Element last = new Element("last");
		toQueryConditionFieldXMLStream(qc.last, last);
		e.addContent(last);
		epadre.addContent(e);
	}

	private void toQueryConditionFieldXMLStream(Object obj, Element epadre) {
		Element e = null;
		if( obj != null ) {
			if( obj instanceof ColumnType ) {
				e = new Element("col").setAttribute("n", ((ColumnType)obj).column);
			}
			else if( obj instanceof QueryBuilder ) {
				e = new Element("QueryBuilder");
				((QueryBuilder)obj).toXMLStream(e);
			}
			else if( obj instanceof java.sql.Date ) {
				e = new Element("dat").setAttribute("v", Long.toString(((java.sql.Date)obj).getTime()));
			}
			else if( obj instanceof Date ) {
				e = new Element("time").setAttribute("v", Long.toString(((java.util.Date)obj).getTime()));
			}
			else if( obj instanceof Integer ) {
				e = new Element("int").setAttribute("v", String.valueOf(obj));
			}
			else if( obj instanceof Long ) {
				e = new Element("long").setAttribute("v", String.valueOf(obj));
			}
			else if( obj instanceof Number ) {
				e = new Element("bd").setAttribute("v", String.valueOf(obj));
			}
			else {
				e = new Element("str").setText(String.valueOf(obj));
			}
		}
		if( e != null ) {
			epadre.addContent(e);
		}
	}

	void fromXMLStream(Element root) {
		List<?> lista = root.getChildren();
		for( Object obj : lista ) {
			if( obj instanceof Element ) {
				Element e = (Element)obj;
				if( e.getName().equals("configuration") ) {
					String texto = e.getTextTrim();
					this.modeAnd = texto.charAt(0) == 'y';
					this.distinct = texto.charAt(1) == 'y';
					this.verifyValues = texto.charAt(2) == 'y';
					this.emptyAsNull = texto.charAt(3) == 'y';
					this.ceroAsNull = texto.charAt(4) == 'y';
					this.pretty = texto.charAt(5) == 'y';
				}
				else if( e.getName().equals("strJoin") ) {
					this.strJoin = e.getText();
				}
				else if( e.getName().equals("dateFormatMask") ) {
					this.setDateFormat(e.getTextTrim());
				}
				else if( e.getName().equals("endClausule") ) {
					this.endClausule.setLength(0);
					this.endClausule.append(e.getText());
				}
				else if( e.getName().equals("op") ) {
					String[] arrops = e.getText().split(",");
					for( String _op : arrops ) {
						if( _op != null  && !"".equals(_op) ) {
							this.op.add(_op);
						}
					}
				}
				else if( e.getName().equals("tables") ) {
					fromXMLStream(null, this.tables, e.getChildren());
				}
				else if( e.getName().equals("columns") ) {
					fromXMLStream(null, this.columns, e.getChildren());
				}
				else if( e.getName().equals("where") ) {
					fromXMLStream(this, this.where, e.getChildren("qc"));
				}
			}
		}
	}

	private void fromXMLStream(QueryBuilder parent, List<QueryCondition> listaqc, List<?> listaE) {
		for( Object obj : listaE ) {
			if( obj instanceof Element ) {
				Element e = (Element)obj;
				if( e.getName().equals("qc") ) {
					QueryCondition qc = new QueryCondition();
					qc.op = e.getAttributeValue("op");
					List<?> lista2 = e.getChildren();
					for( Object obj2 : lista2 ) {
						if( obj2 instanceof Element ) {
							Element e2 = (Element)obj2;
							if( e2.getName().equals("first") ) {
								qc.first = fromQueryConditionFieldXMLStream(parent, e2);
							}
							else if( e2.getName().equals("last") ) {
								qc.last = fromQueryConditionFieldXMLStream(null, e2);
							}
						}
					}
					listaqc.add(qc);
				}
			}
		}
	}

	private Object fromQueryConditionFieldXMLStream(QueryBuilder parent, Element eraiz) {
		if( eraiz != null ) {
			List<?> lista2 = eraiz.getChildren();
			for( Object obj : lista2 ) {
				if( obj instanceof Element ) {
					Element e = (Element)obj;
					if( e.getName().equals("col") ) {
						ColumnType columnType = new ColumnType(e.getAttributeValue("n"));
						return columnType;
					}
					else if( e.getName().equals("QueryBuilder") ) {
						QueryBuilder qb = new QueryBuilder(parent);
						qb.fromXMLStream(e);
						return qb;
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
				}
			}
		}
		return null;
	}

	public static String toXMLString(QueryBuilder qb) {
		try {
			Element e = new Element("QueryBuilder");
			Document d = new Document(e);
			d.setRootElement(e);
			qb.toXMLStream(e);
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

	public static QueryBuilder fromXMLString(String str) {
		try( StringReader stre = new StringReader(str) ) {
			SAXBuilder sb = new SAXBuilder();
			Document d = sb.build(stre);
			Element root = d.getRootElement();
			QueryBuilder qb = new QueryBuilder();
			qb.fromXMLStream(root);
			return qb;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
