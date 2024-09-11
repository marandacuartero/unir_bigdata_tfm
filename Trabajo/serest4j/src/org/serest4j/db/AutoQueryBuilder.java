package org.serest4j.db;

public class AutoQueryBuilder extends QueryBuilder implements AutoCloseable {
	
	public AutoQueryBuilder(String table) {
		super();
		super.appendTable(table);
	}
}
