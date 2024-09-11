package org.serest4j.db;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

public class InsertAction implements Externalizable {

	private InsertBuilder ib;
	private String[] keys;

	public InsertAction() {
		ib = null;
		keys = null;
	}

	public InsertAction(InsertBuilder ib, String... keys) {
		this();
		setIb(ib);
		setKeys(keys);
	}

	public InsertBuilder getIb() {
		return ib;
	}

	public void setIb(InsertBuilder ib) {
		this.ib = ib;
	}

	public String[] getKeys() {
		return keys;
	}

	public void setKeys(String... keys) {
		if( keys != null  &&  keys.length > 0 ) {
			String[] str = Arrays.copyOf(keys, keys.length);
			for( String _str : str ) {
				_str.trim().charAt(0);
			}
			this.keys = str;
		}
		else {
			this.keys = null;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(ib != null);
		if( ib != null ) {
			out.writeInt(keys == null ? 0 : keys.length);
			if( keys != null  &&  keys.length > 0 ) {
				for( String _str : keys ) {
					out.writeUTF(_str);
				}
			}
			String str = InsertBuilder.toXMLString(ib);
			out.writeUTF(str);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		setIb(null);
		setKeys();
		if( in.readBoolean() ) {
			int n = in.readInt();
			if( n > 0 ) {
				String[] str = new String[n];
				for( int i=0; i<n; i++ ) {
					str[i] = in.readUTF();
				}
				setKeys(str);
			}
			InsertBuilder _ib = InsertBuilder.fromXMLString(in.readUTF());
			setIb(_ib);
		}
	}

	@Override
	public String toString() {
		return "keys=" + Arrays.toString(keys) + "\n" + String.valueOf(ib);
	}
}
