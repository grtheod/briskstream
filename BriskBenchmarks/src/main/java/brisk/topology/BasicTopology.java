package brisk.topology;

import applications.constants.BaseConstants;
import applications.general.sink.BaseSink;
import applications.general.spout.helper.parser.Parser;
import applications.util.ClassLoaderUtils;
import applications.util.Configuration;
import brisk.components.operators.api.AbstractSpout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The basic topology has only one spout and one sink, configured by the default
 * configuration keys.
 */
public abstract class BasicTopology extends AbstractTopology {
    private static final Logger LOG = LoggerFactory.getLogger(BasicTopology.class);
    protected final int spoutThreads;
    protected final int sinkThreads;
    protected AbstractSpout spout;
    protected BaseSink sink;
    protected Parser parser;

    protected BasicTopology(String topologyName, Configuration config) {
        super(topologyName, config);

        boolean profile = config.getBoolean("profile");
        boolean benchmark = config.getBoolean("benchmark");
        int forwardThreads;
        if (!benchmark) {
            //spoutThreads = config.getInt(getConfigKey(BaseConstants.BaseConf.SPOUT_THREADS), 1);
            spoutThreads = config.getInt(BaseConstants.BaseConf.SPOUT_THREADS, 1);//now read from parameters.
            forwardThreads = config.getInt("SPOUT_THREADS", 1);//now read from parameters.
            sinkThreads = config.getInt(BaseConstants.BaseConf.SINK_THREADS, 1);
        } else {
            forwardThreads = 1;
            spoutThreads = 1;
            sinkThreads = 1;
        }
    }


    protected void initilize_parser() {
        String parserClass = config.getString(getConfigKey(), null);
        if (parserClass != null) {

            parser = (Parser) ClassLoaderUtils.newInstance(parserClass, "parser", LOG);

            parser.initialize(config);
        } else LOG.info("No parser is initialized");

    }

    @Override
    public void initialize() {
        config.setConfigPrefix(getConfigPrefix());
        spout = loadSpout();
        initilize_parser();
    }
}
