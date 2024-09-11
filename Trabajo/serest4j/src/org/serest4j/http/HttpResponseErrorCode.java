package org.serest4j.http;

import java.net.HttpURLConnection;

public class HttpResponseErrorCode {

	public static int OK = HttpURLConnection.HTTP_OK;
	public static int SESION_NO_VALIDA = HttpURLConnection.HTTP_UNAUTHORIZED;
	public static int CREDENCIALES_INSUFICIENTES = HttpURLConnection.HTTP_NOT_AUTHORITATIVE;
	public static int ERROR_EN_LA_PETICION = HttpURLConnection.HTTP_BAD_REQUEST;
}
