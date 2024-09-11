package org.serest4j.common;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class IteratorLoader {

	public static void printToFile(Iterator<?> it, String ficheroOutput, Logger debug) throws FileNotFoundException, IOException {
		long l = System.currentTimeMillis();
		long n = 0l;
		long nb = 0l;
		File f = new File(ficheroOutput);
		try( FileOutputStream fout = new FileOutputStream(f.getAbsoluteFile()) ) {
			if( debug != null ) {
				debug.debug("Abriendo archivo en " + f.getAbsolutePath());
			}
			while( it != null  &&  it.hasNext() ) {
				Object obj = it.next();
				if( obj != null ) {
					if( obj instanceof byte[] ) {
						byte[] b = (byte[])obj;
						n += b.length;
						nb += b.length;
						fout.write(b);
						if( debug != null ) {
							if( nb > 10485760l ) {
								nb = 0l;
								debug.debug("Leidos " + ( (n + 524288l) / 1048576l ) + " Mb");
							}
						}
					}
					else {
						byte[] b = String.valueOf(obj).getBytes();
						n += b.length;
						nb += b.length;
						fout.write(b);
						if( debug != null ) {
							if( nb > 10485760l ) {
								nb = 0l;
								debug.debug("Leidos " + ( (n + 524288l) / 1048576l ) + " Mb");
							}
						}
					}
				}
			}
		}
		if( debug != null ) {
			debug.debug("Leidos " + n + " bytes en " + ((System.currentTimeMillis() - l)/1000l) + " segs");
			debug.debug("Escritos " + n + " bytes en " + f.getAbsolutePath());	
		}
	}

	public static void printToOutput(Iterator<?> it, PrintStream ps) throws FileNotFoundException, IOException {
		while( it != null  &&  it.hasNext() ) {
			Object obj = it.next();
			if( obj != null  &&  ps != null ) {
				if( obj instanceof byte[] ) {
					byte[] b = (byte[])obj;
					ps.write(b);
				}
				else {
					ps.print(obj);
				}
			}
		}
	}
}
