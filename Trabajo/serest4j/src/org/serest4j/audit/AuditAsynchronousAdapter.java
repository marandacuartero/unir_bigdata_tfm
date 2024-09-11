package org.serest4j.audit;


public class AuditAsynchronousAdapter implements Runnable {

	private AuditInterface auditoriaInterface;
	private Object usuario;
	private String controlador;
	private String servicio;
	private Object retorno;
	private Object[] argumentos;

	protected AuditAsynchronousAdapter(AuditInterface auditoriaInterface, Object usuario, String controlador, String servicio, Object retorno, Object[] argumentos) {
		this.auditoriaInterface = auditoriaInterface;
		this.usuario = usuario;
		this.controlador = controlador;
		this.servicio = servicio;
		this.retorno = retorno;
		this.argumentos = argumentos;
	}
	
	@Override
	public void run() {
		auditoriaInterface.auditar(usuario, controlador, servicio, retorno, argumentos);
	}
}
