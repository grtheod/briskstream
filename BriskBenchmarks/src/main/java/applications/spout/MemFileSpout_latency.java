package applications.spout;

import applications.constants.BaseConstants;
import applications.spout.helper.wrapper.StringStatesWrapper;
import applications.util.Configuration;
import applications.util.OsUtils;
import brisk.components.context.TopologyContext;
import brisk.components.operators.api.AbstractSpout;
import brisk.execution.ExecutionGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Scanner;

public class MemFileSpout_latency extends AbstractSpout {
    private static final Logger LOG = LoggerFactory.getLogger(MemFileSpout_latency.class);
    private static final long serialVersionUID = -2394340130331865581L;
    protected ArrayList<char[]> array;
    protected int element = 0;
    protected int counter = 0;
    char[][] array_array;

    private int cnt;
    private int taskId;
    private long last_emit;
    private long msgID_start;
    private long msgID_end;
    private long msgID_counter;


    public MemFileSpout_latency() {
        super(LOG);
//		this.scalable = false;
    }

    @Override
    public Integer default_scale(Configuration conf) {

        int numNodes = conf.getInt("num_socket", 1);
        if (numNodes == 8) {
            return 2;
        } else {
            return 1;
        }
    }

    @Override
    public void initialize(int thread_Id, int thisTaskId, ExecutionGraph graph) {
        LOG.info("Spout initialize is being called");
        long start = System.nanoTime();
        cnt = 0;
        counter = 0;
        taskId = getContext().getThisTaskIndex();//context.getThisTaskId(); start from 0..

        // numTasks = config.getInt(getConfigKey(BaseConstants.BaseConf.SPOUT_THREADS));

        String OS_prefix = null;

        if (OsUtils.isWindows()) {
            OS_prefix = "win.";
        } else {
            OS_prefix = "unix.";
        }
        String path;

        if (OsUtils.isMac()) {
            path = config.getString(getConfigKey(OS_prefix.concat(BaseConstants.BaseConf.SPOUT_TEST_PATH)));
        } else {
            path = config.getString(getConfigKey(OS_prefix.concat(BaseConstants.BaseConf.SPOUT_PATH)));
        }

        String s = System.getProperty("user.home").concat("/Documents/data/app/").concat(path);

        array = new ArrayList<>();
        try {
            openFile(s);
        } catch (FileNotFoundException e) {

            s = "/data/DATA/tony/data/".concat(path);
            try {
                openFile(s);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
        }
        long pid = OsUtils.getPID(TopologyContext.HPCMonotor);
        LOG.info("JVM PID  = " + pid);

        int end_index = array_array.length * config.getInt("count_number", 1);

        LOG.info("spout:" + this.taskId + " elements:" + end_index);
        long end = System.nanoTime();
        LOG.info("spout prepare takes (ms):" + (end - start) / 1E6);

        msgID_start = (long) (1E4 * (taskId));
        msgID_end = (long) (1E4 * (taskId + 1));
        msgID_counter = msgID_start;

    }

    /**
     * relax_reset source messages.
     */
    @Override
    public void cleanup() {

    }

    private void build(Scanner scanner) {
        cnt = 100;
        if (config.getInt("batch") == -1) {
            while (scanner.hasNext()) {
                array.add(scanner.next().toCharArray());//for micro-benchmark only
            }
        } else {

            if (!config.getBoolean("microbenchmark")) {//normal case..
                //&& cnt-- > 0
                if (OsUtils.isWindows()) {
                    while (scanner.hasNextLine() && cnt-- > 0) { //dummy test purpose..
                        array.add(scanner.nextLine().toCharArray());
                    }
                } else {
                    while (scanner.hasNextLine()) {
                        array.add(scanner.nextLine().toCharArray()); //normal..
                    }
                }

            } else {
                int tuple_size = config.getInt("size_tuple");
                LOG.info("Additional tuple size to emit:" + tuple_size);
                StringStatesWrapper wrapper = new StringStatesWrapper(tuple_size);
//                        (StateWrapper<List<StreamValues>>) ClassLoaderUtils.newInstance(parserClass, "wrapper", LOG, tuple_size);
                if (OsUtils.isWindows()) {
                    while (scanner.hasNextLine() && cnt-- > 0) { //dummy test purpose..
                        construction(scanner, wrapper);
                    }
                } else {
                    while (scanner.hasNextLine()) {
                        construction(scanner, wrapper);
                    }
                }
            }
        }
        scanner.close();
    }

    private void construction(Scanner scanner, StringStatesWrapper wrapper) {

        String splitregex = ",";
        String[] words = scanner.nextLine().split(splitregex);

        StringBuilder sb = new StringBuilder();

        for (String word : words) {
            sb.append(word).append(wrapper.getTuple_states()).append(splitregex);
        }

        array.add(sb.toString().toCharArray());


    }

    private void read(String prefix, int i, String postfix) throws FileNotFoundException {
        Scanner scanner = new Scanner(new File((prefix + i) + "." + postfix), "UTF-8");
        build(scanner);
    }

    private void splitRead(String fileName) throws FileNotFoundException {
        int numSpout = this.getContext().getComponent(taskId).getNumTasks();
        int range = 10 / numSpout;//original file is split into 10 sub-files.
        int offset = this.taskId * range + 1;
        String[] split = fileName.split("\\.");
        for (int i = offset; i < offset + range; i++) {
            read(split[0], i, split[1]);
        }

        if (this.taskId == numSpout - 1) {//if this is the last executor of spout
            for (int i = offset + range; i <= 10; i++) {
                read(split[0], i, split[1]);
            }
        }
    }

    private void openFile(String fileName) throws FileNotFoundException {
        boolean split;

        split = !OsUtils.isMac() && config.getBoolean("split", true);

        if (split) {
            splitRead(fileName);
        } else {
            Scanner scanner = new Scanner(new File(fileName), "UTF-8");
            build(scanner);
        }

        array_array = array.toArray(new char[array.size()][]);
        counter = 0;

    }


//	private boolean start_measure = false;

    public void freeze() throws InterruptedException {
        Object obj = new Object();
        synchronized (obj) {
            obj.wait();
        }
    }

    @Override
    public void nextTuple() {

        counter++;
        if (counter == array_array.length) {
            counter = 0;
//			start_measure = true;
        }

//		if (taskId == 0 && timestamp_counter % 1E5 == 0 && start_measure) {//emit marker tuple per 1E4 tuples
//		final long currentTimeNanos = System.nanoTime();
//			last_emit = currentTimeNanos;


        collector.emit_nowait(array_array[counter], msgID_counter++, System.nanoTime());

        if (msgID_counter == msgID_end) {
//			freeze();
            msgID_counter = 0;
        }

//		} else {
//			collector.emit(array_array[timestamp_counter], -1, 0);
//		}
    }


    public void display() {
        LOG.info("timestamp_counter:" + counter);
    }

}

