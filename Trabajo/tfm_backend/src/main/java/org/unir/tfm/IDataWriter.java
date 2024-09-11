package org.unir.tfm;

import java.sql.SQLException;
import java.util.Date;

import org.unir.tfm.dao.TrazaTorneo;

public interface IDataWriter {
	public int cleanFrom(Date fecha) throws SQLException;
	public void saveTrazas(TrazaTorneo[] trazaTorneos) throws SQLException;
}
