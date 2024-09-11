package org.serest4j.common;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;

public class GSonFormatter {

	private Gson gson = new Gson();
	private Gson gsonHtml = new Gson();

	public GSonFormatter(PropertiesLoader loadGssProperties, boolean esModoDebug ) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(loadGssProperties.getProperty("serest4j.json.dateformat"));
			sdf.format(new Date());
			GsonBuilder gsonBuilder = new GsonBuilder().setDateFormat(loadGssProperties.getProperty("serest4j.json.dateformat")).serializeNulls();
			gsonBuilder.setLongSerializationPolicy(LongSerializationPolicy.STRING);
			if( esModoDebug ) {
				gsonBuilder = gsonBuilder.setPrettyPrinting();
			}
			gsonHtml = gsonBuilder.create();
			gson = gsonBuilder.disableHtmlEscaping().create();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public Gson gsonHtml() {
		return gsonHtml;
	}

	public Gson gson() {
		return gson;
	}

	public String toJson(Object obj) {
		return gson.toJson(obj);	
	}

	public Object fromJson(String obj, Class<?> type) {
		return gson.fromJson(obj, type);
	}
}
