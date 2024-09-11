package org.serest4j.jmx;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

public class ControllerRegister {

	public static boolean registrar(String name, Object mbean, Logger logger, String namePrevio) {
		unregistrar(namePrevio);
		if( name != null ) {
			unregistrar(name);
			try {
				MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
				ObjectName objectName = new ObjectName(name);
				mbs.registerMBean(mbean, objectName);
				if( logger != null ) {
					logger.info("Registrando " + name);
				}
				return true;
			} catch (Exception e) {
				if( logger != null ) {
					logger.error("Registrando " + name, e);
				}
			}
		}
		return false;
	}

	private static boolean unregistrar(String name) {
		if( name != null ) {
			try {
				MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
				ObjectName objectName = new ObjectName(name);
				mbs.unregisterMBean(objectName);
				return true;
			} catch (Exception e) {}
		}
		return false;
	}
}
