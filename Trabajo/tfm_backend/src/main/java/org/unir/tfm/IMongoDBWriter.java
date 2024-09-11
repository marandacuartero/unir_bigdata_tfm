package org.unir.tfm;

import java.util.Date;

import org.serest4j.annotation.endpoint.TMLinkController;
import org.unir.tfm.dao.TrazaTorneo;

@TMLinkController(controller=MongoDBWriter.class)
public interface IMongoDBWriter {

	public long cleanFrom(Date fecha);
	public void saveTrazas(TrazaTorneo[] trazaTorneos);
}
