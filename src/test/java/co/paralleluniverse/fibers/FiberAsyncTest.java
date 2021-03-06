/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.fibers;

import co.paralleluniverse.strands.SuspendableRunnable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jsr166e.ForkJoinPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import org.junit.Ignore;

/**
 *
 * @author pron
 */
public class FiberAsyncTest {
    private ForkJoinPool fjPool;

    public FiberAsyncTest() {
        fjPool = new ForkJoinPool(4, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    interface MyCallback {
        void call(String str);

        void fail(RuntimeException e);
    }

    interface Service {
        void registerCallback(MyCallback callback);
    }
    final Service syncService = new Service() {
        @Override
        public void registerCallback(MyCallback callback) {
            callback.call("sync result!");
        }
    };
    final Service badSyncService = new Service() {
        @Override
        public void registerCallback(MyCallback callback) {
            callback.fail(new RuntimeException("sync exception!"));
        }
    };
    final ExecutorService executor = Executors.newFixedThreadPool(1);
    final Service asyncService = new Service() {
        @Override
        public void registerCallback(final MyCallback callback) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(20);
                        callback.call("async result!");
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });

        }
    };
    final Service badAsyncService = new Service() {
        @Override
        public void registerCallback(final MyCallback callback) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(20);
                        callback.fail(new RuntimeException("async exception!"));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });

        }
    };

    static String callService(final Service service) throws SuspendExecution {
        return new MyFiberAsync() {
            @Override
            protected void requestAsync(Fiber current, MyCallback callback) {
                service.registerCallback(callback);
            }
        }.run();
    }

    static abstract class MyFiberAsync extends FiberAsync<String, MyCallback, RuntimeException> implements MyCallback {
        private final Fiber fiber;

        public MyFiberAsync() {
            this.fiber = Fiber.currentFiber();
        }

        @Override
        public void call(String str) {
            super.completed(str, fiber);
        }

        @Override
        public void fail(RuntimeException e) {
            super.failed(e, fiber);
        }
    }

    @Test
    public void testSyncCallback() throws Exception {
        final Fiber fiber = new Fiber(fjPool, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution {
                String res = callService(syncService);
                assertThat(res, equalTo("sync result!"));
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testSyncCallbackException() throws Exception {
        final Fiber fiber = new Fiber(fjPool, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution {
                try {
                    String res = callService(badSyncService);
                    fail();
                } catch (Exception e) {
                    assertThat(e.getMessage(), equalTo("sync exception!"));
                }
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallback() throws Exception {
        final Fiber fiber = new Fiber(fjPool, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution {
                String res = callService(asyncService);
                assertThat(res, equalTo("async result!"));
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallbackException() throws Exception {
        final Fiber fiber = new Fiber(fjPool, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution {
                try {
                    String res = callService(badAsyncService);
                    fail();
                } catch (Exception e) {
                    assertThat(e.getMessage(), equalTo("async exception!"));
                }
            }
        }).start();

        fiber.join();
    }
}
