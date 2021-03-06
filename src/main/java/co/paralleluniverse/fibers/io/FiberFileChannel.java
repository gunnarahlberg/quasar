/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (C) 2013, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.fibers.io;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author pron
 */
public class FiberFileChannel implements FiberByteChannel {
    private final AsynchronousFileChannel ac;
    private long position;

    private FiberFileChannel(AsynchronousFileChannel afc) {
        ac = afc;
    }

    public static FiberFileChannel open(ExecutorService ioExecutor, Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return new FiberFileChannel(AsynchronousFileChannel.open(path, options, ioExecutor, attrs));
    }

    public static FiberFileChannel open(Path path, OpenOption... options) throws IOException {
        return new FiberFileChannel(AsynchronousFileChannel.open(path, options));
    }

    public long position() throws IOException {
        return position;
    }

    public FiberFileChannel position(long newPosition) throws IOException {
        this.position = newPosition;
        return this;
    }

    public int read(final ByteBuffer dst, final long position) throws IOException, SuspendExecution {
        return new FiberAsyncIO<Integer>() {
            @Override
            protected void requestAsync(Fiber current, CompletionHandler<Integer, Fiber> completionHandler) {
                ac.read(dst, position, current, completionHandler);
            }
        }.run();
    }

    public int write(final ByteBuffer src, final long position) throws IOException, SuspendExecution {
        return new FiberAsyncIO<Integer>() {
            @Override
            protected void requestAsync(Fiber current, CompletionHandler<Integer, Fiber> completionHandler) {
                ac.write(src, position, current, completionHandler);
            }
        }.run();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException, SuspendExecution {
        final int bytes = read(dst, position);
        position(position + bytes);
        return bytes;
    }

    @Override
    public int write(ByteBuffer src) throws IOException, SuspendExecution {
        final int bytes = write(src, position);
        position(position + bytes);
        return bytes;
    }

    @Override
    public boolean isOpen() {
        return ac.isOpen();
    }

    @Override
    public void close() throws IOException {
        ac.close();
    }
    
    public long size() throws IOException {
        return ac.size();
    }

    public void force(boolean metaData) throws IOException {
        ac.force(metaData);
    }

    public FiberFileChannel truncate(long size) throws IOException {
        ac.truncate(size);
        return this;
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return ac.tryLock(position, size, shared);
    }

    public final FileLock tryLock() throws IOException {
        return ac.tryLock();
    }

    public final FileLock lock() throws IOException, SuspendExecution {
        return new FiberAsyncIO<FileLock>() {
            @Override
            protected void requestAsync(Fiber current, CompletionHandler<FileLock, Fiber> completionHandler) {
                ac.lock(current, completionHandler);
            }
        }.run();
    }

    public FileLock lock(final long position, final long size, final boolean shared) throws IOException, SuspendExecution {
        return new FiberAsyncIO<FileLock>() {
            @Override
            protected void requestAsync(Fiber current, CompletionHandler<FileLock, Fiber> completionHandler) {
                ac.lock(position, size, shared, current, completionHandler);
            }
        }.run();
    }
}
