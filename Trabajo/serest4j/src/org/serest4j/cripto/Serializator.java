package org.serest4j.cripto;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.log4j.Logger;

public class Serializator {

	private static final Class<?>[] CLASES_PERSISTENCIA_DELEGADA = new Class<?>[]{BigDecimal.class, BigInteger.class, StringBuilder.class, StringBuffer.class};
	private static final PersistenceDelegate MPD_STRING_CONSTRUCTOR = new PersistenceDelegate() {

		@Override
		protected Expression instantiate(Object oldInstance, Encoder out) {
			Class<?> cl = oldInstance.getClass();
			String str = oldInstance.toString();
			if( cl.isAssignableFrom(BigDecimal.class) ) {
				BigDecimal bd = (BigDecimal)oldInstance;
				BigDecimal op = new BigDecimal("10000000000.0");
				bd = bd.multiply(op);
				bd = new BigDecimal(bd.toBigInteger());
				str = bd.divide(op).toString();
			}
			return new Expression(oldInstance, cl, "new", new Object[]{ str });
		}
	};

	public static byte[] serializa(Object[] datos, Logger logger) {
		byte[] b = null;
		try {
			b = serializaObjectOutput(datos);
		} catch (IOException e) {
			if( logger != null ) {
				logger.trace("***** Error serializando>> " + e.getLocalizedMessage());
			}
		}
		if( b == null ) {
			try {
				b = serializaXMLEncoder(datos, logger);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return b;
	}

	private static final int MAX_TAM_BUFFER = 10 * 1024 * 1024;

	public static byte[] serializaObjectOutput(Object[] datos) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream(10 * 1024);
		int n = datos == null ? 0 : datos.length;
		ObjectOutputStream out = new ObjectOutputStream(bout);
		out.writeInt(n);
		for( int i=0; i<n; i++ ) {
			validate(bout.size());
			out.writeObject(datos[i]);
		}
		out.flush();
		validate(bout.size());
		out.close();
		return bout.toByteArray();
	}

	public static byte[] serializaXMLEncoder(Object[] datos, Logger logger) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream(10 * 1024);
		ByteArrayOutputStream bout2 = new ByteArrayOutputStream(10 * 1024);
		int n = datos == null ? 0 : datos.length;
		ObjectOutputStream oos = new ObjectOutputStream(bout);
		oos.writeInt(n);
		for( int i=0; i<n; i++ ) {
			validate(bout.size());
			XMLEncodedObject xmlEncodedObject = new XMLEncodedObject();
			bout2.reset();
			XMLEncoder xmlEncoder = new XMLEncoder(bout2);
			for( Class<?> claseDelegada : CLASES_PERSISTENCIA_DELEGADA ) {
				xmlEncoder.setPersistenceDelegate(claseDelegada, MPD_STRING_CONSTRUCTOR);
			}
			xmlEncoder.writeObject(datos[i]);
			xmlEncoder.flush();
			xmlEncoder.close();
			xmlEncodedObject.setObject(bout2.toString("UTF-8"));
			if( logger != null  &&  logger.isTraceEnabled() ) {
				logger.trace("**********" + datos[i] + "\n" + xmlEncodedObject + "************");
			}
			oos.writeObject(xmlEncodedObject);
			oos.flush();
		}
		oos.flush();
		validate(bout.size());
		oos.close();
		return bout.toByteArray();
	}

	private static void validate(int size) throws IOException {
		if( size > MAX_TAM_BUFFER ) {
			throw new IOException("Limites superados " + size + " > " + MAX_TAM_BUFFER);
		}
	}

	public static Object deserializa(ObjectInputStream in) throws IOException, ClassNotFoundException {
		Object obj = in.readObject();
		if( obj != null  &&  obj instanceof XMLEncodedObject ) {
			XMLEncodedObject xmlEncodedObject = (XMLEncodedObject)obj;
			obj = null;
			if( xmlEncodedObject.getObject() != null ) {
				if( xmlEncodedObject.getObject().length() > 0 ) {
					ByteArrayInputStream bin = new ByteArrayInputStream(xmlEncodedObject.getObject().getBytes("UTF-8"));
					XMLDecoder xmlDecoder = new XMLDecoder(bin);
					obj = xmlDecoder.readObject();
					xmlDecoder.close();
				}
			}
		}
		return obj;
	}
}
