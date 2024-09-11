package org.serest4j.buffers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Date;

public abstract class FileStrongWeigthCacheManager<E> implements StrongWeigthCacheManager<E> {

	@Override
	public void manageDeleteElement(E key) {
		if( key != null ) {
			File f = getFile(key);
			if( f != null  &&  f.exists() ) {
				f.delete();	
			}
		}
	}

	@Override
	public void manageNuevoElement(E key) {
		if( key != null ) {
			File f = getFile(key);
			if( f != null ) {
				try( FileOutputStream fout = new FileOutputStream(f) ) {
					try( BufferedOutputStream bout = new BufferedOutputStream(fout) ) {
						try( ObjectOutputStream oos = new ObjectOutputStream(bout) ) {
							oos.writeObject(key);
							oos.flush();
						}
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	@Override
	public E manageLoadElement(E key) {
		File f = getFile(key);
		return loadFromFile(f);
	}

	@SuppressWarnings("unchecked")
	private E loadFromFile( File f ) {
		E value = null;
		if( f != null  &&  f.exists() ) {
			try( FileInputStream fin = new FileInputStream(f) ) {
				try( BufferedInputStream bin = new BufferedInputStream(fin) ) {
					try( ObjectInputStream ois = new ObjectInputStream(bin) ) {
						Object obj = ois.readObject();
						value =(E)obj;
					}
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			if( value != null ) {
				f.setLastModified(System.currentTimeMillis());
			}
		}
		return value;
	}

	@Override
	public void manageClearCacheFrom(long timer) {
		final long filterTimer = timer;
		File f = getDirectory();
		if( f != null  &&  f.exists()  &&  f.isDirectory() ) {
			File[] files = f.listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return pathname.exists()  &&  pathname.isFile()  &&  pathname.lastModified() < filterTimer;
				}
			});
			if( files != null ) {
				for( File _f : files ) {
					_f.delete();
				}
			}
		}
	}

	@Override
	public void manageInitCache(long timer) {
		File f = getDirectory();
		if( f != null  &&  f.exists()  &&  f.isDirectory() ) {
			File[] files = f.listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return pathname.exists()  &&  pathname.isFile();
				}
			});
			if( files != null ) {
				for( File _f : files ) {
					if( _f.lastModified() < timer ) {
					}
					else {
						E e = loadFromFile(_f);
						if( e == null ) {
							_f.delete();
						}
					}
				}
			}
		}
	}

	@Override
	public void printAditionalInfo(PrintWriter pw) {
		File f = getDirectory();
		if( f != null  &&  f.exists()  &&  f.isDirectory() ) {
			pw.println("Directorio local: " + f.getAbsolutePath());
			File[] files = f.listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return pathname.exists()  &&  pathname.isFile();
				}
			});
			if( files != null  &&  files.length > 0 ) {
				pw.println("Ficheros en cache: " + files.length);
				for( File _f : files ) {
					pw.print(_f.getAbsolutePath());
					pw.print(' ');
					pw.print(new Date(_f.lastModified()));
					pw.println();
				}
			}
			else {
				pw.println("No hay Ficheros en cache");
			}
		}
	}

	
	protected abstract File getFile(E e);
	
	protected abstract File getDirectory();
}
