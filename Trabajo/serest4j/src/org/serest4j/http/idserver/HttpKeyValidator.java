package org.serest4j.http.idserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.serest4j.async.BufferDataConsumer;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.buffers.cloud.CloudCacheRepository;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.context.ServerStaticContext;
import org.serest4j.cripto.FlowUtility;
import org.serest4j.cripto.TokenFactory;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.UnidentifiedUserException;
import org.serest4j.http.idserver.policy.CredencialsInterface;
import org.serest4j.http.idserver.policy.CredentialsType;
import org.serest4j.http.idserver.policy.NullCredentials;
import org.serest4j.http.idserver.policy.RolesListUserCredentials;
import org.serest4j.http.idserver.policy.ServerContextUserCredentials;
import org.serest4j.http.idserver.policy.UserDescriptor;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HttpKeyValidator {

	public static boolean validateSession(HttpServletRequest request) throws UnidentifiedUserException {
		return _cc(request, FileLogger.getLogger(request.getContextPath()));
	}

	public static boolean isValidSession(HttpServletRequest request) {
		Logger logger = FileLogger.getLogger(request.getContextPath());
		try {
			return _cc(request, logger);
		} catch (Throwable th) {
			if( logger != null ) {
				logger.debug("[Sesion] " + th);
			}
		}
		return false;
	}

	public static String getIdSesion(HttpServletRequest request, Logger debug) {
		String idSesion = _sacarId(request, debug);
		if( TokenFactory.verify(idSesion) ) {
			return idSesion;
		}
		else {
			return null;
		}
	}

	private static String _sacarId(HttpServletRequest request, Logger debug) {
		Object _idClave = request.getAttribute(RequestAttributes.ID_SESION);
		String idClave = _idClave == null ? null : _idClave.toString();
		if( idClave == null ) {
			Cookie[] cookies = request.getCookies();
			if( cookies != null  &&  cookies.length > 0 ) {
				for( Cookie cookie : cookies ) {
					if( cookie.getName().equals(RequestAttributes.COOKIE_ID) ) {
						idClave = cookie.getValue();
						if( debug != null  &&  debug.isDebugEnabled() ) {
							debug.debug("[Credenciales] Obtenida cookie " + RequestAttributes.COOKIE_ID + "=" + idClave);
						}
					}
				}
			}
		}
		return idClave;
	}

	private static boolean _cc(HttpServletRequest request, Logger debug) throws UnidentifiedUserException {
		String path = request.getContextPath() + request.getServletPath();
		if( debug != null  &&  debug.isDebugEnabled() ) {
			debug.debug("[Credenciales] Comprobando credenciales para " + path);
		}
		String idClave = _sacarId(request, debug);
		KeyContainer contenedorClaves = sacarClave(request.getContextPath(), idClave);
		if( contenedorClaves == null ) {
			throw new UnidentifiedUserException("El usuario no esta logueado. El idSesion de sesion no es valido: " + idClave);
		}
		else if( existeUsuarioLogeado(contenedorClaves) == null ) {
			throw new UnidentifiedUserException("El usuario no esta logueado");
		}
		else {
			CredencialsInterface[] credencialesUsuario = contenedorClaves.getCredencialesUsuario();
			String codigoUsuario = contenedorClaves.getCodigoUsuario();
			Logger userLogger = null;
			request.setAttribute(RequestAttributes.ID_SESION, idClave);
			request.setAttribute(RequestAttributes.USER_CODE, codigoUsuario);
			request.setAttribute(RequestAttributes.USER_SESION, contenedorClaves.getInformacionUsuario());
			request.setAttribute(RequestAttributes.USER_CREDENTIALS, credencialesUsuario);
			userLogger = RequestAttributes.getUserLogger(request);
			if( comprobarCredencialesDominio(userLogger, credencialesUsuario, path) ) {	
				return true;
			}
			else {
				if( debug != null  &&  debug.isDebugEnabled() ) {
					StringBuilder sb = new StringBuilder();
					sb.append('[').append(codigoUsuario).append(']').append(' ');
					sb.append("Credenciales insuficientes: ").append("Acceso a dominio ").append(path).append(" ilegal. ");
					debug.debug(sb);
				}
				return false;
			}
		}
	}

	public static Object existeUsuarioLogeado(KeyContainer contenedorClaves) {
		if( contenedorClaves != null  &&  contenedorClaves.getInformacionUsuario() != null ) {
			String codigo = contenedorClaves.getCodigoUsuario();
			if( codigo != null  &&  codigo.length() > 0 ) {
				return contenedorClaves.getInformacionUsuario();
			}
		}
		return null;
	}

	private static boolean comprobarCredencialesDominio(Logger debug, CredencialsInterface[] credencialesUsuarios, String pathDominio) {
		String dominio = pathDominio.trim();
		if( credencialesUsuarios != null ) {
			for( CredencialsInterface credencialesUsuario : credencialesUsuarios ) {
				if( credencialesUsuario != null  &&  credencialesUsuario.isValid(CredentialsType.DOMINIO)  &&  credencialesUsuario.comprobarCredenciales(debug, dominio) ) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Comprueba si el usuario de esta petici�n contiene o cumple alguno de los roles indicados
	 * @param request
	 * @param losRoles
	 * @return
	 * @throws UnidentifiedUserException
	 */
	public static boolean cumpleAlgunRol(HttpServletRequest request, Object... losRoles) {
		if( losRoles != null  &&  losRoles.length > 0 ) {
			CredencialsInterface[] credencialesUsuarios = (CredencialsInterface[])(request.getAttribute(RequestAttributes.USER_CREDENTIALS));
			if( credencialesUsuarios != null  &&  credencialesUsuarios.length > 0 ) {
				Logger debug = RequestAttributes.getUserLogger(request);
				for( Object rol : losRoles ) {
					if( cumpleRol(debug, credencialesUsuarios, rol) ) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Comprueba si el usuario de esta petici�n cumple con todo los roles indicados
	 * @param request
	 * @param losRoles
	 * @return
	 * @throws UnidentifiedUserException
	 */
	public static boolean cumpleTodosRoles(HttpServletRequest request, Object... losRoles) {
		if( losRoles != null  &&  losRoles.length > 0 ) {
			CredencialsInterface[] credencialesUsuarios = (CredencialsInterface[])(request.getAttribute(RequestAttributes.USER_CREDENTIALS));
			if( credencialesUsuarios != null  &&  credencialesUsuarios.length > 0 ) {
				Logger debug = RequestAttributes.getUserLogger(request);
				for( Object rol : losRoles ) {
					if( !cumpleRol(debug, credencialesUsuarios, rol) ) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	private static boolean cumpleRol(Logger debug, CredencialsInterface[] credencialesUsuarios, Object rol) {
		for( CredencialsInterface credencialesUsuario : credencialesUsuarios ) {
			if( credencialesUsuario != null  &&  credencialesUsuario.isValid(CredentialsType.ROL)  &&  credencialesUsuario.comprobarCredenciales(debug, rol) ) {
				return true;
			}
		}
		return false;
	}

	// Operaciones asociadas a registro de usuario

	public static void setIdSesionInResponse(HttpServletResponse response, Object idSesion, String domain) {
		Cookie cookie = new Cookie(RequestAttributes.COOKIE_ID, idSesion == null ? null : idSesion.toString());
		String pathCookie = "/";
		String dominioCookie = null;
		if( domain != null ) {
			int iopc = domain.indexOf('/');
			if( iopc != -1 ) {
				dominioCookie = domain.substring(0, iopc);
				pathCookie = domain.substring(iopc);
			}
		}
		if( dominioCookie != null  &&  dominioCookie.length() > 0 ) {
			cookie.setDomain(dominioCookie);
		}
		cookie.setPath(pathCookie);	
		cookie.setMaxAge(-1);

		response.addCookie(cookie);
	}

	public static Logger setDatosRequest(HttpServletRequest request, KeyContainer contenedorClaves) {
		if( request != null  &&  contenedorClaves != null ) {
			request.setAttribute(RequestAttributes.ID_SESION, contenedorClaves.getId());
			request.setAttribute(RequestAttributes.USER_CODE, contenedorClaves.getCodigoUsuario());
			request.setAttribute(RequestAttributes.USER_SESION, contenedorClaves.getInformacionUsuario());
			request.setAttribute(RequestAttributes.USER_CREDENTIALS, contenedorClaves.getCredencialesUsuario());
			return RequestAttributes.getUserLogger(request);
		}
		return null;
	}

	public static String getRemoteHost(HttpServletRequest request) {
		String datoCabecera = request.getHeader("X-Real-Ip");
		if( datoCabecera != null  &&  datoCabecera.trim().length() >= 7 ) {
			return datoCabecera;
		}
		else {
			return request.getRemoteHost();
		}
	}

	public static KeyContainer registrarLoginUsuario(Logger debug, HttpServletRequest request, String idSesion, byte[] clave, Object sesionUsuario) throws Throwable {
		if( sesionUsuario != null  &&  sesionUsuario instanceof Throwable ) {
			throw (Throwable)sesionUsuario;
		}
		else if( sesionUsuario != null ) {
			UserDescriptor descriptorSesionUsuario = ServerStaticContext.get(request.getContextPath()).getUserDescriptorInstance().get(sesionUsuario.getClass());
			if( descriptorSesionUsuario != null ) {
				String userCode = descriptorSesionUsuario.getUserCode(sesionUsuario);
				if( userCode != null ) {
					KeyContainer cc = new KeyContainer(idSesion);
					String host = getRemoteHost(request);
					int puerto = request.getRemotePort();
					cc.setDatosConexion(host, puerto);
					cc.setClave(clave);
					cc.setUltimoReenvio(System.currentTimeMillis());
					cc.setCredencialesUsuario(null);
					Object[] objRoles = descriptorSesionUsuario.searchUserRoles(request, sesionUsuario);
					if( objRoles != null  &&  objRoles.length > 0 ) {
						RolesListUserCredentials credencialesUsuario = new RolesListUserCredentials(objRoles);
						ArrayList<CredencialsInterface> permisos = new ArrayList<CredencialsInterface>();
						permisos.add(credencialesUsuario);
						// cargar credenciales desde XML
						try {
							Collection<CredencialsInterface> credencialesNavegacion = loadNavigationAccess(debug, request.getServletContext(), objRoles);
							if( credencialesNavegacion != null  &&  credencialesNavegacion.size() > 0 ) {
								permisos.addAll(credencialesNavegacion);
							}
						}
						catch(Exception e) {
							if( debug != null ) {
								debug.error("registrarLoginUsuario", e);
							}
						}
						CredencialsInterface[] arrci = new CredencialsInterface[permisos.size()];
						arrci = permisos.toArray(arrci);
						cc.setCredencialesUsuario(arrci);
						colocarSesionUsuario(request.getContextPath(), cc, host, puerto, sesionUsuario, userCode);
						Logger userLogger = setDatosRequest(request, cc);
						if( userLogger != null ) {
							userLogger.trace("colocarSesionUsuario >> Genero nueva sesion valida para usuario " + userCode);
						}
						return cc;
					}
				}
			}
		}
		return null;
	}

	public static void procesarLogoutSesion(HttpServletRequest request, String idSesion, String codigoUsuario, Logger debug) {
		if( idSesion != null  &&  codigoUsuario != null ) {
			if( debug != null ) {
				debug.debug("Logout y cierre de conexiones con usuario=" + codigoUsuario + ", id=" + idSesion);
			}
			eliminarConexion(request.getContextPath(), idSesion);
		}
	}

	private static void clearIterator(HttpServletRequest request, Logger trace) {
		Object obj = request.getAttribute(RequestAttributes.RESPUESTA_SERVICIO);
		if( obj instanceof BufferDataProvider ) {
			BufferDataProvider cargaDatosIterator = (BufferDataProvider)obj;
			cargaDatosIterator.close();
			if( trace != null ) {
				trace.trace("cargaDatosIterator.close() en " + cargaDatosIterator);	
			}
		}
		else if( obj instanceof QueuedBufferDataConsumer ) {
			QueuedBufferDataConsumer cargaDatosIterator = (QueuedBufferDataConsumer)obj;
			cargaDatosIterator.remove();
			try { cargaDatosIterator.close(); } catch (IOException e) {}
			if( trace != null ) {
				trace.trace("cargaDatosIterator.close() en " + cargaDatosIterator);	
			}
		}
		else if( obj instanceof BufferDataConsumer ) {
			BufferDataConsumer cargaDatosIterator = (BufferDataConsumer)obj;
			try { cargaDatosIterator.close(); } catch (IOException e) {}
			if( trace != null ) {
				trace.trace("cargaDatosIterator.close() en " + cargaDatosIterator);	
			}
		}
		request.removeAttribute(RequestAttributes.RESPUESTA_SERVICIO);
	}

	public static void clearRequest(HttpServletRequest request, Logger trace) {
		clearIterator(request, trace);
		Enumeration<String> enumeracion = request.getAttributeNames();
		if( enumeracion != null ) {
			ArrayList<String> al = new ArrayList<String>(15);
			while( enumeracion.hasMoreElements() ) {
				al.add(enumeracion.nextElement());
			}
			for( String str : al ) {
				request.removeAttribute(str);
			}
			al.clear();
			al = null;
		}
	}

	public static Collection<CredencialsInterface> loadNavigationAccess(Logger logger, ServletContext sc, Object[] roles) throws IOException, JDOMException {
		byte[] bufferRoles = new byte[0];
		try ( InputStream is = PropertiesLoader.searchInServletContext(sc, "policy.xml", logger) ) {
			ByteArrayOutputStream bout = new ByteArrayOutputStream(is.available());
			FlowUtility.flushStream(is, bout);
			bufferRoles = bout.toByteArray();
			if( logger != null ) {
				logger.error("Credenciales de navegacion cargadas");
			}
		}
		catch(Exception exc) {
			if( logger != null ) {
				logger.error("Cargando credenciales de navegacion ", exc);
			}
		}
		if( bufferRoles.length <= 0 ) {
			return null;
		}
		SAXBuilder sb = new SAXBuilder();
		try( ByteArrayInputStream bin = new ByteArrayInputStream(bufferRoles) ) {
			Document d = sb.build(bin);
			Element root = d.getRootElement();
			List<?> clases = root.getChildren("domain");
			ArrayList<String> al = new ArrayList<String>();
			Element elemento = null;
			Element subelemento = null;
			String nombre = null;
			String pattern = null;
			NullCredentials cinulas = new NullCredentials();
			TreeMap<String, CredencialsInterface> listaCredenciales = new TreeMap<String, CredencialsInterface>();
			listaCredenciales.put("", cinulas);
			if( roles != null ) {
				for(Object obj : roles ) {
					if( obj != null ) {
						listaCredenciales.put(obj.toString().trim().toUpperCase(), cinulas);
					}
				}
			}
			for(Object obj : clases) {
				elemento = (Element)obj;
				nombre = elemento.getAttributeValue("rol");
				if( nombre != null ) {
					nombre = nombre.trim().toUpperCase();
				}
				else {
					nombre = "";
				}
				if( listaCredenciales.containsKey(nombre) ) {
					List<?> sublista = elemento.getChildren("accept");
					al.clear();
					if( sublista != null  &&  sublista.size() > 0 ) {
						for(Object obj2 : sublista ) {
							subelemento = (Element)obj2;
							pattern = subelemento.getTextTrim();
							if( pattern != null  &&  pattern.length() > 0 ) {
								al.add(pattern.trim());
							}
						}
					}
					String[] permisos = al.toArray(new String[al.size()]);
					al.clear();
					sublista = elemento.getChildren("reject");
					if( sublista != null  &&  sublista.size() > 0 ) {
						for(Object obj2 : sublista ) {
							subelemento = (Element)obj2;
							pattern = subelemento.getTextTrim();
							if( pattern != null  &&  pattern.length() > 0 ) {
								al.add(pattern.trim());
							}
						}
					}
					String[] restricciones = al.toArray(new String[al.size()]);
					ServerContextUserCredentials credenciales = new ServerContextUserCredentials(sc, permisos, restricciones);
					if( logger != null ) {
						logger.trace("Para " + nombre + " construidas credenciales " + credenciales);
					}
					CredencialsInterface previas = listaCredenciales.put(nombre, credenciales);
					if( previas != cinulas ) {
						if( logger != null ) {
							logger.debug("Encontrada duplicidad en permiso " + nombre + " !!");	
						}
					}
				}
			}
			ArrayList<CredencialsInterface> alretorno = new ArrayList<CredencialsInterface>();
			for( CredencialsInterface ci : listaCredenciales.values() ) {
				if( ci != null  &&  ci != cinulas ) {
					alretorno.add(ci);
				}
			}
			listaCredenciales.clear();
			return alretorno;
		}
	}

	// Gestion de cache
	private static KeyContainerCloudCache getCache(String contextPath) {
		return CloudCacheRepository.get(contextPath, KeyContainerCloudCache.class);
	}

	private static void eliminarConexion(String contextPath, String id) {
		getCache(contextPath).remove(new KeyContainer(id));
	}
	
	public static KeyContainer sacarClave(String contextPath, String id) {
		if( TokenFactory.verify(id) ) {
			return getCache(contextPath).get(new KeyContainer(id));
		}
		else {
			return null;	
		}
	}

	private static void colocarSesionUsuario(String contextPath, KeyContainer cc, String host, int puerto, Object informacionUsuario, String codigoUsuario) {
		if( cc != null  &&  cc.getId() != null ) {
			cc.setInformacionUsuario(codigoUsuario, informacionUsuario);
			cc.setDatosConexion(host, puerto);
			cc.setContexto(contextPath);
			getCache(contextPath).put(cc);
		}
	}
}
