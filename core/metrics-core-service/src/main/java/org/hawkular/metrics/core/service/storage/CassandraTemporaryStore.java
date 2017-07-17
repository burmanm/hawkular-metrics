///*
// * Copyright 2017 Red Hat, Inc. and/or its affiliates
// * and other contributors as indicated by the @author tags.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *    http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.hawkular.metrics.core.service.storage;
//
//import static com.datastax.driver.core.BatchStatement.Type.UNLOGGED;
//
//import java.nio.ByteBuffer;
//import java.sql.PreparedStatement;
//import java.util.concurrent.Callable;
//import java.util.concurrent.TimeUnit;
//
//import org.hawkular.metrics.core.service.log.CoreLogger;
//import org.hawkular.metrics.core.service.log.CoreLogging;
//import org.hawkular.metrics.model.Metric;
//import org.reactivestreams.Publisher;
//
//import com.datastax.driver.core.BatchStatement;
//import com.datastax.driver.core.BoundStatement;
//import com.datastax.driver.core.ProtocolVersion;
//import com.datastax.driver.core.Session;
//import com.datastax.driver.core.Token;
//import com.datastax.driver.core.TokenRange;
//import com.datastax.driver.core.exceptions.DriverException;
//
//import hu.akarnokd.rxjava.interop.RxJavaInterop;
//import io.reactivex.Completable;
//import io.reactivex.Flowable;
//import io.reactivex.FlowableEmitter;
//import io.reactivex.FlowableOnSubscribe;
//import io.reactivex.FlowableTransformer;
//import io.reactivex.exceptions.Exceptions;
//import io.reactivex.processors.PublishProcessor;
//
///**
// * @author michael
// */
//public class CassandraTemporaryStore {
//
//    private Session session;
//
//    public CassandraTemporaryStore(Session session) {
//        this.session = session;
//    }
//
//    private static final CoreLogger log = CoreLogging.getCoreLogger(CassandraTemporaryStore.class);
//
//    PublishProcessor<Metric<?>> metricProcessor = PublishProcessor.create();
//    PublishProcessor<PreparedStatement> insertProcessor = PublishProcessor.create();
//
//    public Completable insertEntries(Flowable<Metric<?>> metrics) {
//
////        session.getCluster().getMetrics().getTaskSchedulerQueueSize();
//
////        metrics.subscribe(metricProcessor)
//    }
//
//    class SessionConsumer implements FlowableOnSubscribe<PreparedStatement> {
//        @Override
//        public void subscribe(FlowableEmitter<PreparedStatement> flowableEmitter) throws Exception {
//        }
//    }
//
//    /*
//                    .flatMap(batchStatement -> {
//        CompletableFuture<ResultSet> future = new CompletableFuture<>();
//        ResultSetFuture insertFuture = session.executeAsync(batchStatement);
//
//        insertFuture.addListener(() -> future.complete(insertFuture.getUninterruptibly()), Runnable::run);
//
//        return Mono.fromFuture(future);
//    })
//
//    */
//
//    /**
//     * TODO These should be in their own class! And some of them might be incorrect in the future..
//     */
//    private FlowableTransformer<BoundStatement, Integer> applyMicroBatching2() {
//        return tObservable -> tObservable
//                .groupBy(b -> {
//                    ByteBuffer routingKey = b.getRoutingKey(ProtocolVersion.NEWEST_SUPPORTED,
//                            codecRegistry);
//                    Token token = metadata.newToken(routingKey);
//                    for (TokenRange tokenRange : session.getCluster().getMetadata().getTokenRanges()) {
//                        if (tokenRange.contains(token)) {
//                            return tokenRange;
//                        }
//                    }
//                    throw new RuntimeException("Unable to find any Cassandra node to insert token " + token.toString());
//                })
//                .flatMap(g -> g.compose(new BoundBatchStatementTransformer2()))
//                .flatMap(batch ->
//                        RxJavaInterop.toV2Flowable(rxSession.execute(batch))
//                                .compose(applyInsertRetryPolicy2())
//                                .map(resultSet -> batch.size())
//                );
//    }
//
//    /*
//     * Apply our current retry policy to the insert behavior
//     */
//    private <T> FlowableTransformer<T, T> applyInsertRetryPolicy2() {
//        return tObservable -> tObservable
//                .retryWhen(errors -> {
//                    return errors
//                            .zipWith(Flowable.range(1, 2), (t, i) -> {
//                                if (t instanceof DriverException) {
//                                    return i;
//                                }
//                                throw Exceptions.propagate(t);
//                            })
//                            .flatMap(retryCount -> {
//                                long delay = (long) Math.min(Math.pow(2, retryCount) * 1000, 3000);
//                                log.debug("Retrying batch insert in " + delay + " ms");
//                                return Flowable.timer(delay, TimeUnit.MILLISECONDS);
//                            });
//                });
//    }
//
//    public class BoundBatchStatementTransformer2 implements FlowableTransformer<BoundStatement, BatchStatement> {
//        public static final int DEFAULT_BATCH_SIZE = 50;
//
//        /**
//         * Creates {@link BatchStatement.Type#UNLOGGED} batch statements.
//         */
//        public static final Callable<BatchStatement> DEFAULT_BATCH_STATEMENT_FACTORY = () -> new BatchStatement(UNLOGGED);
//
//        private final Callable<BatchStatement> batchStatementFactory;
//        private final int batchSize;
//
//        /**
//         * Creates a new transformer using the {@link #DEFAULT_BATCH_STATEMENT_FACTORY}.
//         */
//        public BoundBatchStatementTransformer2() {
//            this(DEFAULT_BATCH_STATEMENT_FACTORY, DEFAULT_BATCH_SIZE);
//        }
//
//        /**
//         * @param batchStatementFactory function used to initialize a new {@link BatchStatement}
//         * @param batchSize             maximum number of statements in the batch
//         */
//        public BoundBatchStatementTransformer2(Callable<BatchStatement> batchStatementFactory, int batchSize) {
//            this.batchSize = batchSize;
//            this.batchStatementFactory = batchStatementFactory;
//        }
//
//        @Override
//        public Publisher<BatchStatement> apply(Flowable<BoundStatement> statements) {
//            return statements
//                    .window(batchSize)
//                    .flatMapSingle(w -> w.collect(batchStatementFactory, BatchStatement::add));
//        }
//    }
//}
