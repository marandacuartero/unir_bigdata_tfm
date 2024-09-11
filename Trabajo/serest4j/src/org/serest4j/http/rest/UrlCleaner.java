package org.serest4j.http.rest;

import java.io.FileNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

public class UrlCleaner {

	private static int partir(StringBuilder sb, int from) {
		int j = 0;
		int insert = -1;
		for( int i= from; i>=0; i-- ) {
			char c = sb.charAt(i);
			if( c == '.' ) { j++; }
			else if( c == '/' ) { insert = i+1; i = 0; }
			else { return i + 1; }
		}
		insert = Math.max(0, insert);
		for( int i = 0; i < j; i++ ) {
			sb.deleteCharAt(insert);
		}
		for( int i = 0; i < j; i++ ) {
			sb.insert(insert, '/');
			sb.insert(insert, '.');
			sb.insert(insert, '.');
		}
		return insert;
	}

	private static void clean(StringBuilder sb) {
		sb.insert(0, '/');
		sb.append('/');
		for( int j=sb.length() - 1; j >= 0; j-- ) {
			if( j > 3  &&  sb.charAt(j) == '/'  &&  sb.charAt(j-1) == '.'  &&  sb.charAt(j-2) == '.'  &&  sb.charAt(j-3) == '.' ) {
				j = partir(sb, j-3);
			}
			if( j > 2  &&  sb.charAt(j) == '/'  &&  sb.charAt(j-1) == '.'  &&  sb.charAt(j-2) == '/' ) {
				sb.setCharAt(j-1, '/');
			}
			if( j > 0  &&  sb.charAt(j) == '/'  &&  sb.charAt(j-1) == '/' ) {
				sb.deleteCharAt(j);
			}
		}
		String url = sb.toString();
		sb.setLength(0);
		int l = url.length();
		int lsb = 0;
		for( int i=0; i<l; i++ ) {
			char c = url.charAt(i);
			if( c == '/'  &&  i < l-3  &&  url.charAt(i+1) == '.'  &&  url.charAt(i+2) == '.'  &&  url.charAt(i+3) == '/' ) {
				i = i + 2;
				lsb = sb.length() - 1;
				if( lsb >= 0 ) {
					for( int j=lsb; j >= 0  &&  j < sb.length()  &&  sb.charAt(j) != '/'; j-- ) {
						sb.deleteCharAt(j);
					}
					lsb = sb.length() - 1;
					if( lsb >= 0 ) {
						sb.deleteCharAt(lsb);
					}
				}
			}
			else {
				sb.append(c);
			}
		}
		lsb = sb.length() - 1;
		for( int j=lsb; j >= 0; j-- ) {
			if( j > 0  &&  sb.charAt(j) == '/'  &&  sb.charAt(j-1) == '/' ) {
				sb.deleteCharAt(j);
			}
		}
		lsb = sb.length() - 1;
		while( lsb >= 0  &&  sb.charAt(lsb) == '/' ) {
			sb.deleteCharAt(lsb);
			lsb = sb.length() - 1;
		}
	}

	static String clean(HttpServletRequest request, String url) throws FileNotFoundException {
		if( url != null ) {
			StringBuilder sb = new StringBuilder(url.trim());
			if( sb.length() > 0 ) {
				clean(sb);
				String cp = request.getContextPath();
				int l = cp.length();
				if( sb.indexOf(cp) == 0 ) {
					sb.delete(0, l);
				}
				if( sb.length() > 0 ) {
					return sb.toString();
				}
//				url = sb.toString();
//				File f = new File(request.getServletContext().getRealPath(url));
//				if( f.exists()  &&  f.isFile() ) {
//					return url;
//				}
//				else {
//					throw new FileNotFoundException("No existe el destino " + url);
//				}
			}
		}
		return null;
	}
}
