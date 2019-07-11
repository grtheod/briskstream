package applications.ml.spout;

import applications.util.datatypes.StreamValues;
import brisk.components.operators.api.AbstractSpout;
import brisk.execution.ExecutionGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static applications.Constants.DEFAULT_STREAM_ID;

public class LableStreamGeneratorSpout extends AbstractSpout {
    private static final Logger LOG = LoggerFactory.getLogger(LableStreamGeneratorSpout.class);
    private static final long serialVersionUID = 7738169734935576086L;
    private RandomTreeGenerator Generator;

    public LableStreamGeneratorSpout() {
        super(LOG);


//        preqSource.setMaxNumInstances(instanceLimitOption.getValue());
//        preqSource.setSourceDelay(sourceDelayOption.getValue());
//        preqSource.setDelayBatchSize(batchDelayOption.getValue());

    }

    @Override
    public void initialize(int thread_Id, int thisTaskId, ExecutionGraph graph) {
        super.initialize(thread_Id, thisTaskId, graph);
        int numContestants = 100;
        Generator = new RandomTreeGenerator(this.getContext().getThisTaskId(), numContestants);
        Generator.prepareForUse();
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void nextTuple() throws InterruptedException {
        collector.emit(DEFAULT_STREAM_ID, new StreamValues(Generator.nextInstance()));
    }
}