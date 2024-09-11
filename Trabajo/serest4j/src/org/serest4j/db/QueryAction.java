package org.serest4j.db;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class QueryAction implements Externalizable {

	public enum Action {
		SELECT, DELETE, COUNT
	}
	
	private QueryBuilder qb;
	private Action action;

	public QueryAction() {
		qb = null;
		action = Action.SELECT;
	}

	public QueryAction(QueryBuilder qb, Action action) {
		this();
		setQb(qb);
		setAction(action);
	}

	public Action getAction() {
		return action == null ? Action.SELECT : action;
	}

	public void setAction(Action action) {
		this.action = action == null ? Action.SELECT : action;
	}

	public QueryBuilder getQb() {
		return qb;
	}

	public void setQb(QueryBuilder qb) {
		this.qb = qb;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(qb != null);
		if( qb != null ) {
			out.writeUTF(getAction().toString());
			String str = QueryBuilder.toXMLString(qb);
			out.writeUTF(str);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		setQb(null);
		if( in.readBoolean() ) {
			setAction(Action.valueOf(in.readUTF()));
			QueryBuilder _qb = QueryBuilder.fromXMLString(in.readUTF());
			setQb(_qb);
		}
	}

	@Override
	public String toString() {
		return "Action=" + action + "\n" + String.valueOf(qb);
	}
}
