package org.voltdb;

import java.util.Collection;
import java.util.Collections;

import org.voltdb.catalog.CatalogMap;
import org.voltdb.catalog.PlanFragment;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Statement;
import org.voltdb.messaging.FragmentTaskMessage;
import org.voltdb.types.QueryType;

import edu.brown.BaseTestCase;
import edu.brown.benchmark.mapreduce.procedures.MockMapReduce;
import edu.brown.catalog.CatalogUtil;
import edu.brown.hashing.DefaultHasher;
import edu.brown.utils.PartitionEstimator;
import edu.brown.utils.ProjectType;

public class TestBatchPlannerMapReduce extends BaseTestCase {

    private static final Class<? extends VoltProcedure> MULTISITE_PROCEDURE = MockMapReduce.class;
    private static final String MULTISITE_STATEMENT = "mapInputQuery";
    private static final Object MULTISITE_PROCEDURE_ARGS[] = {
        1
    };
    
    private static final Long TXN_ID = 1000l;
    private static final long CLIENT_HANDLE = 99999l;
    private static final int LOCAL_PARTITION = 0;
    private static final int REMOTE_PARTITION = 0;
    private static final int NUM_PARTITIONS = 10;
    
    private Procedure catalog_proc;
    private Statement catalog_stmt;
    private SQLStmt batch[];
    private ParameterSet args[];
    
    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.MAPREDUCE);
        this.addPartitions(NUM_PARTITIONS);
        p_estimator = new PartitionEstimator(catalog_db, new DefaultHasher(catalog_db, NUM_PARTITIONS));
    }
 
    private void init(Class<? extends VoltProcedure> volt_proc, String stmt_name, Object raw_args[]) {
        this.catalog_proc = this.getProcedure(volt_proc);
        assertNotNull(this.catalog_proc);
        this.catalog_stmt = this.catalog_proc.getStatements().get(stmt_name);
        assertNotNull(this.catalog_stmt);
        
        CatalogMap<PlanFragment> fragments = null;
        if (this.catalog_stmt.getQuerytype() == QueryType.INSERT.getValue()) {
            fragments = this.catalog_stmt.getFragments();
        } else {
            assert(this.catalog_stmt.getHas_multisited());
            fragments = this.catalog_stmt.getMs_fragments();
        }

        // Create a SQLStmt batch
        this.batch = new SQLStmt[] { new SQLStmt(this.catalog_stmt, fragments) };
        this.args = new ParameterSet[] { VoltProcedure.getCleanParams(this.batch[0], raw_args) };
    }
    
    /**
     * testForceSinglePartitionPlan
     */
    public void testForceSinglePartitionPlan() throws Exception {
        this.init(MULTISITE_PROCEDURE, MULTISITE_STATEMENT, MULTISITE_PROCEDURE_ARGS);
        BatchPlanner batchPlan = new BatchPlanner(batch, this.catalog_proc, p_estimator, true);
        BatchPlanner.BatchPlan plan = batchPlan.plan(TXN_ID, CLIENT_HANDLE, REMOTE_PARTITION, Collections.singleton(LOCAL_PARTITION), this.args, true);
        assertNotNull(plan);
        assertFalse(plan.hasMisprediction());
        
        assertNotNull(plan);
        assertFalse(plan.hasMisprediction());
        Collection<FragmentTaskMessage> ftasks = plan.getFragmentTaskMessages(this.args);
        int local_frags = TestBatchPlanner.getLocalFragmentCount(ftasks, LOCAL_PARTITION);
        int remote_frags = TestBatchPlanner.getRemoteFragmentCount(ftasks, LOCAL_PARTITION);
        
        System.err.println(plan);
        System.err.println("Fragments: " + ftasks);
        
        assertTrue(plan.isLocal());
        assertTrue(plan.isSingleSited());
        assertEquals(1, local_frags);
        assertEquals(0, remote_frags);
    }
    
}
