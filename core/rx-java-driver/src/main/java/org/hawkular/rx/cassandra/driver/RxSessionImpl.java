/*
 * Copyright 2014-2017 Red Hat, Inc. and/or its affiliates
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

package org.hawkular.rx.cassandra.driver;

import java.util.function.Function;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import rx.Emitter;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * See {@link RxSession} for documentation of the methods.
 *
 * @author jsanda
 * @author Michael Burman
 */
public class RxSessionImpl implements RxSession {

    private Session session;
    private static int MAXIMUM_INFLIGHT_REQUESTS = 1024;

    public RxSessionImpl(Session session) {
        this.session = session;
    }

    @Override
    public String getLoggedKeyspace() {
        return session.getLoggedKeyspace();
    }

    @Override
    public RxSession init() {
        session.init();
        return this;
    }

    public static Function<Session, Integer> mostInFlightRequests = (session) -> {
        int inFlights = 0;
        for (Host host : session.getState().getConnectedHosts()) {
            inFlights = Math.max(inFlights, session.getState().getInFlightQueries(host));
        }
        return inFlights;
    };

    private Observable<ResultSet> scheduleStatement(Statement st, Scheduler scheduler) {
        Observable<ResultSet> rsObservable = Observable.create(resultSetEmitter -> {
            while (true) {
                if (mostInFlightRequests.apply(session) < MAXIMUM_INFLIGHT_REQUESTS) {
                    ResultSetFuture future = session.executeAsync(st);
                    Futures.addCallback(future, new FutureCallback<ResultSet>() {
                        @Override public void onSuccess(ResultSet rows) {
                            resultSetEmitter.onNext(rows);
                            resultSetEmitter.onCompleted();
                        }

                        @Override public void onFailure(Throwable throwable) {
                            // Retry
                            resultSetEmitter.onError(throwable);
                        }
                    });
                } else {
                    try {
                        Thread.sleep(0, 1);
                    } catch (InterruptedException e) {
                        //
                    }
                }
            }
        }, Emitter.BackpressureMode.ERROR);

        return rsObservable.subscribeOn(scheduler);
    }

    private Observable<PreparedStatement> schedulePrepare(RegularStatement st, Scheduler scheduler) {
        Observable<PreparedStatement> psObservable = Observable.create(psEmitter -> {
            ListenableFuture<PreparedStatement> future = session.prepareAsync(st);
            Futures.addCallback(future, new FutureCallback<PreparedStatement>() {
                @Override public void onSuccess(PreparedStatement ps) {
                    psEmitter.onNext(ps);
                    psEmitter.onCompleted();
                }

                @Override public void onFailure(Throwable throwable) {
                    // Retry
                    psEmitter.onError(throwable);
                }
            });
        }, Emitter.BackpressureMode.ERROR);

        return psObservable.subscribeOn(scheduler);
    }

    @Override
    public Observable<ResultSet> execute(String query) {
        return scheduleStatement(new SimpleStatement(query), Schedulers.computation());
    }

    @Override
    public Observable<Row> executeAndFetch(String query) {
        return execute(query).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(String query, Scheduler scheduler) {
        return scheduleStatement(new SimpleStatement(query), scheduler);
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Scheduler scheduler) {
        return execute(query, scheduler).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<ResultSet> execute(String query, Object... values) {
        return scheduleStatement(new SimpleStatement(query, values), Schedulers.computation());
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Object... values) {
        return execute(query, values).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(String query, Scheduler scheduler, Object... values) {
        return scheduleStatement(new SimpleStatement(query, values), scheduler);
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Scheduler scheduler, Object... values) {
        return execute(query, scheduler, values).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<ResultSet> execute(Statement statement) {
        return scheduleStatement(statement, Schedulers.computation());
    }

    @Override
    public Observable<Row> executeAndFetch(Statement statement) {
        return execute(statement).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(Statement statement, Scheduler scheduler) {
        return scheduleStatement(statement, scheduler);
    }

    @Override
    public Observable<Row> executeAndFetch(Statement statement, Scheduler scheduler) {
        return execute(statement, scheduler).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<PreparedStatement> prepare(String query) {
        return prepare(new SimpleStatement(query));
    }

    @Override
    public Observable<PreparedStatement> prepare(String query, Scheduler scheduler) {
        return prepare(new SimpleStatement(query), scheduler);
    }

    @Override
    public Observable<PreparedStatement> prepare(RegularStatement statement) {
        return schedulePrepare(statement, Schedulers.computation());
    }

    @Override
    public Observable<PreparedStatement> prepare(RegularStatement statement, Scheduler scheduler) {
        return schedulePrepare(statement, scheduler);
    }

    @Override
    public void close() {
        session.close();
    }

    @Override
    public boolean isClosed() {
        return session.isClosed();
    }

    @Override
    public Cluster getCluster() {
        return session.getCluster();
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public Session.State getState() {
        return session.getState();
    }
}
