package org.unir.tfm;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import org.serest4j.annotation.endpoint.TMLinkController;
import org.unir.tfm.dao.Tournament;

@TMLinkController(controller=DataLoader.class)
public interface IDataLoader {

	public Iterator<Tournament> loadTournamentsFrom(Date fecha) throws IOException;

}
