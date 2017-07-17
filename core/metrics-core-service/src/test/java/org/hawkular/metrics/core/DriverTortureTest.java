/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.metrics.core;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * @author michael
 */
public class DriverTortureTest {
    static final private AtomicInteger batches = new AtomicInteger(40_000);
    static final private AtomicInteger done = new AtomicInteger(2_000_000);
    static final private AtomicInteger errors = new AtomicInteger(0);

    private Session session;

    @BeforeClass
    public void setup() {
        String nodeAddresses = System.getProperty("nodes", "127.0.0.1");
        Cluster cluster = new Cluster.Builder()
                .addContactPoints(nodeAddresses.split(","))
                .withQueryOptions(new QueryOptions().setRefreshSchemaIntervalMillis(0))
                .build();
        session = cluster.connect();
        session.execute("USE hawkulartest");
        session.execute("TRUNCATE data_temp_2017071410");
    }

    static class MetricSubmitCall {
        public int i;
        public int j;
        public long start;

        public MetricSubmitCall(int i, int j, long start) {
            this.i = i;
            this.j = j;
            this.start = start + j;
        }
    }

    static BiFunction<MetricSubmitCall, PreparedStatement, BoundStatement>
            createStatement = (submit, insert) -> {
        String metricName = String.format("m%d", submit.i);
        BoundStatement insertB = insert.bind()
                .setDouble(0, 1.1)
                .setString(1, "t1")
                .setByte(2, (byte) 0)
                .setString(3, metricName)
                .setTimestamp(4, new Date(submit.start));
        return insertB;
    };


    static BiConsumer<BoundStatement, Session> submitMetric = (insertB, session) -> {
        ResultSetFuture insertFuture = session.executeAsync(insertB);

        Futures.addCallback(insertFuture, new FutureCallback<ResultSet>() {
            @Override public void onSuccess(ResultSet rows) {
                // Not interested in the return value
                done.decrementAndGet();
            }

            @Override public void onFailure(Throwable t) {
//                if(t.getCause() != null && t.getCause() instanceof NoHostAvailableException) {
//                    NoHostAvailableException noHostE = (NoHostAvailableException) t.getCause();
//                    Map<InetSocketAddress, Throwable> hostErrors = noHostE.getErrors();
//                    for (Throwable throwable : hostErrors.values()) {
//                        if (throwable instanceof BusyPoolException) {
//                            System.out.printf("BusyPool!");
//                            try {
//                                Thread.sleep(100);
//                            } catch (InterruptedException e) {
//                                //
//                            }
                errors.incrementAndGet();
                submitMetric.accept(insertB, session);
//                        }
//                    }
//                }
            }
        });
    };


    static BiConsumer<BatchStatement, Session> submitBatch = (insertB, session) -> {
        ResultSetFuture insertFuture = session.executeAsync(insertB);

        Futures.addCallback(insertFuture, new FutureCallback<ResultSet>() {
            @Override public void onSuccess(ResultSet rows) {
                // Not interested in the return value
                batches.decrementAndGet();
            }

            @Override public void onFailure(Throwable t) {
                errors.incrementAndGet();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                submitBatch.accept(insertB, session);
            }
        });
    };

//    @Test
    public void driverTest() throws Exception {

        long start = System.currentTimeMillis();

        int amountOfMetrics = 1_000_000;
        int datapointsPerMetric = 2;
//        done = new AtomicInteger(amountOfMetrics * datapointsPerMetric);

        PreparedStatement insert = session.prepare("UPDATE data_temp_2017071410 " +
                "SET n_value = ? " +
                "WHERE tenant_id = ? AND type = ? AND metric = ? AND time = ? ");

        for (int j = 0; j < datapointsPerMetric; j++) {
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (int i = 0; i < amountOfMetrics; i++) {
                final int jF = j;
                final int iF = i;

                batch.add(createStatement.apply(new DriverTortureTest.MetricSubmitCall(iF, jF, start), insert));
                if(i % 50 == 0) {
                    submitBatch.accept(batch, session);
                    batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                }

//                BoundStatement bs = createStatement.apply(new DriverTortureTest.MetricSubmitCall(iF, jF, start), insert);
//                submitMetric.accept(bs, session);
            }
            submitBatch.accept(batch, session);
        }
        while(batches.get() > 0) {
            System.out.printf("Waiting...\n");
            Thread.sleep(1000);
        }
//        while(done.get() > 0) {
//            System.out.printf("Waiting...");
//            Thread.sleep(1000);
//        }
        System.out.printf("Retries: %d\n", errors.get());
    }
}
