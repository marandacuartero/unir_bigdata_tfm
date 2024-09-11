package org.unir.tfm;

import java.sql.SQLException;
import java.util.Date;

import org.apache.log4j.Logger;
import org.serest4j.annotation.db.TMDataSource;
import org.serest4j.annotation.rest.TMRest;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.annotation.service.TMNoWaitResponse;
import org.serest4j.db.TMTransactionalLogger;
import org.unir.tfm.dao.TrazaTorneo;

@TMInjectableContext
@TMDataSource("jdbc/IanseoDest2")
public class DataWriter2 extends DataWriter {

	TMTransactionalLogger tl;
	Logger debug;

	public DataWriter2() {
		super();
	}
	
	@Override
	TMTransactionalLogger getTmTransactionalLogger() {
		return tl;
	}

	@Override
	Logger getLogger() {
		return debug;
	}

	@Override
	@TMRest
	public int cleanFrom(Date fecha) throws SQLException {
		return super._cleanFrom(fecha);
	}

	@Override
	@TMRest
	@TMNoWaitResponse
	public void saveTrazas(TrazaTorneo[] trazaTorneos) throws SQLException {
		super._saveTrazas(trazaTorneos);
	}
}
