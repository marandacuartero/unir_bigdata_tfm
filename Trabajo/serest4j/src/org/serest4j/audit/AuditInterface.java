package org.serest4j.audit;

public interface AuditInterface {

	public void auditar(Object usuario, String controlador, String servicio, Object retorno, Object[] argumentos);
}
