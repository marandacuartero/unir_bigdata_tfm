package org.serest4j.context;


/**
 * Exception de apoyo que permite devolver datos y controlar las transacciones realizadas
 * Para poder lanzar una excepcion y ademas devolver los datos al cliente
 */
@SuppressWarnings("serial")
public class TMReturnContextException extends RuntimeException {

	private final Object retorno;

	public TMReturnContextException() {
		this(null, null, null);
	}
	
	public TMReturnContextException(String mensaje) {
		this(mensaje, null, null);
	}
 
	public TMReturnContextException(String mensaje, Object retorno) {
		this(mensaje, null, retorno);
	}

	/**
	 * 
	 * @param causa En el caso de que exista una excepcion asociada al proceso. En este caso se realizara un rollback de la transaccion.
	 * @param retorno El objeto que deberia devolver el metodo, se puede colocar aqui en caso de querer propagarlo hasta el cliente,
	 *               independientemente de que exista excepcion asociada
	 */
	public TMReturnContextException(String mensaje, Throwable causa, Object retorno) {
		super(mensaje, causa);
		this.retorno = retorno;
	}

	public Object getRetorno() {
		return retorno;
	}

	public String getMinimaTraza(int i) {
		Throwable th = getCause();
		if( th == null )
			th = this;
		StackTraceElement[] st = th.getStackTrace();
		StringBuilder sb = new StringBuilder(toString());
		if( st != null ) {
			int n = Math.min(i, st.length);
			for( int j=0; j<n; j++ ) {
				sb.append('\n').append('\t').append(st[j]);	
			}
		}
		return sb.toString();
	}
}
