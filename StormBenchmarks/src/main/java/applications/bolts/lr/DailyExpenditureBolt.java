/*
 * #!
 * %
 * Copyright (C) 2014 - 2015 Humboldt-Universität zu Berlin
 * %
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #_
 */

package applications.general.bolts.lr;

import applications.general.bolts.AbstractBolt;
import applications.datatypes.AbstractLRBTuple;
import applications.datatypes.DailyExpenditureRequest;
import applications.datatypes.toll.MemoryTollDataStore;
import applications.datatypes.toll.TollDataStore;
import applications.datatypes.util.Constants;
import applications.datatypes.util.TopologyControl;
import applications.util.Configuration;
import applications.util.OsUtils;
import applications.util.events.HistoryEvent;
import applications.util.lr.Helper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Map;


/**
 * Stub for daily expenditure queries. Responds to {@link DailyExpenditureRequest}s with tuple in the form of (Type = 3,
 * Time (specifying the time that d was emitted), Emit (specifying the time the query response is emitted), QID
 * (identifying the query that issued the request), Bal (the sum of all tolls from expressway x on day n that were
 * charged to the vehi- cle’s account). Reads from {@link TopologyControl#DAILY_EXPEDITURE_REQUESTS_STREAM_ID} and emits
 * tuple on {@link }.
 *
 * @TODO either use external distributed database to keep historic data or load it into memory
 */
public class DailyExpenditureBolt extends AbstractBolt {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(DailyExpenditureBolt.class);
	private LinkedList<HistoryEvent> historyEvtList;
	private transient TollDataStore dataStore;


	public TollDataStore getDataStore() {
		return this.dataStore;
	}

	/**
	 * initializes the used {@link TollDataStore} using the string specified as value to the
	 * {@link Helper#TOLL_DATA_STORE_CONF_KEY} map key.
	 *
	 * @param conf
	 * @param context
	 * @param collector
	 */
	/*
	 * internal implementation notes: - due to the fact that compatibility is incapable of serializing Class property, a String
	 * has to be passed in conf
	 */
	@Override
	public void prepare(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, OutputCollector collector) {
		super.prepare(conf, context, collector);
		@SuppressWarnings("unchecked")

		String tollDataStoreClass = MemoryTollDataStore.class.getName();//(String) conf.get(Helper.TOLL_DATA_STORE_CONF_KEY);
		try {
			this.dataStore = (TollDataStore) Class.forName(tollDataStoreClass).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
			throw new RuntimeException(String.format("The data store instance '%s' could not be initialized (see "
					+ "nested exception for details)", this.dataStore), ex);
		}


		//historyEvtList = new LinkedList<HistoryEvent>();
		String OS_prefix = null;
		if (OsUtils.isWindows()) {
			OS_prefix = "win.";
		} else {
			OS_prefix = "unix.";
		}
		String historyFile;
		if (OsUtils.isMac()) {
			historyFile = System.getProperty("user.home").concat("/Documents/data/app/").concat((String) conf.get(OS_prefix.concat("test.linear-history-file")));
		} else {
			historyFile = System.getProperty("user.home").concat("/Documents/data/app/").concat((String) conf.get(OS_prefix.concat("linear-history-file")));
		}
		loadHistoricalInfo(historyFile);
		Configuration config = Configuration.fromMap(conf);

	}

	public void loadHistoricalInfo(String inputFileHistory) {
		BufferedReader in;
		try {
			in = new BufferedReader(new FileReader(inputFileHistory));

			String line;
			int counter = 0;
			int batchCounter = 0;
			int BATCH_LEN = 10000;//A batch fieldSize of 1000 to 10000 is usually OK
			Statement stmt;
			StringBuilder builder = new StringBuilder();

			//log.info(Utilities.getTimeStamp() + " : Loading history data");
			while ((line = in.readLine()) != null) {


				String[] fields = line.split(" ");
				fields[0] = fields[0].substring(2);
				fields[3] = fields[3].substring(0, fields[3].length() - 1);

				//historyEvtList.add(new HistoryEvent(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
				this.dataStore.storeToll(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
			}
		} catch (FileNotFoundException | NumberFormatException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		//log.info(Utilities.getTimeStamp() + " : Done Loading history data");
		//Just notfy this to the input event injector so that it can start the data emission process
//        try {
//            PrintWriter writer = new PrintWriter("done.txt", "UTF-8");
//            writer.println("\n");
//            writer.flush();
//            writer.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }

	}

	@Override
	public void execute(Tuple tuple) {
//        if (stat != null) stat.start_measure();
//        Fields fields = tuple.getFields();

		//if (fields.contains(TopologyControl.DAILY_EXPEDITURE_REQUEST_FIELD_NAME)) {

		DailyExpenditureRequest exp = new DailyExpenditureRequest();
		exp.addAll(tuple.getValues());

		//.getValueByField(TopologyControl.DAILY_EXPEDITURE_REQUEST_FIELD_NAME);
		int vehicleIdentifier = exp.getVid();
		Values values;
		Integer toll = this.dataStore.retrieveToll(exp.getXWay(), exp.getDay(), vehicleIdentifier);
		if (toll != null) {
			//LOG.DEBUG("ExpenditureRequest: found vehicle identifier %d", vehicleIdentifier);

			// //LOG.DEBUG("3, %d, %d, %d, %d", exp.getTime(), exp.getTimer().getOffset(), exp.getQueryIdentifier(),
			// toll);

			values = new Values(AbstractLRBTuple.DAILY_EXPENDITURE_REQUEST, exp.getTime(), exp.getQid(), toll);
		} else {
			values = new Values(AbstractLRBTuple.DAILY_EXPENDITURE_REQUEST, exp.getTime(), exp.getQid(),
					Constants.INITIAL_TOLL);

		}
		this.collector.emit(TopologyControl.DAILY_EXPEDITURE_OUTPUT_STREAM_ID, values);
//        if (stat != null) stat.end_measure();
	}


	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(TopologyControl.DAILY_EXPEDITURE_OUTPUT_STREAM_ID,
				new Fields(TopologyControl.DAILY_EXPEDITURE_REQUEST_FIELD_NAME,
						TopologyControl.TIME_FIELD_NAME, TopologyControl.QUERY_ID_FIELD_NAME, TopologyControl.TOLL_FIELD_NAME));
	}

}
