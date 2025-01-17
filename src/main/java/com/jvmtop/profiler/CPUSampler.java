/**
 * jvmtop - java monitoring for the command-line
 * <p>
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * <p>
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop.profiler;

import com.jvmtop.monitor.VMInfo;

import java.lang.Thread.State;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Experimental and very basic sampling-based CPU-Profiler.
 * <p>
 * It uses package excludes to filter common 3rd party libraries which often
 * distort application problems.
 *
 * @author paru
 */
public class CPUSampler {
    private final ThreadMXBean threadMxBean_;
    private final ConcurrentMap<String, MethodStats> data_ = new ConcurrentHashMap<>();
    private final AtomicLong totalThreadCPUTime_ = new AtomicLong(0);


    //TODO: these exception list should be expanded to the most common 3rd-party library packages
    private final List<String> filter = Arrays
            .asList("org.eclipse.", "org.apache.", "java.", "sun.", "com.sun.", "javax.",
                    "oracle.", "com.trilead.", "org.junit.", "org.mockito.",
                    "org.hibernate.", "com.ibm.", "com.caucho.", "jdk.internal.reflect.", "io.netty.");

    private final ConcurrentMap<Long, Long> threadCPUTime = new ConcurrentHashMap<>();

    private final AtomicLong updateCount_ = new AtomicLong(0);

    /**
     * @param threadMxBean
     */
    public CPUSampler(VMInfo vmInfo) {
        super();
        threadMxBean_ = vmInfo.getThreadMXBean();
    }

    public List<MethodStats> getTop(int limit) {
        ArrayList<MethodStats> statList = new ArrayList<MethodStats>(data_.values());
        Collections.sort(statList);
        return statList.subList(0, Math.min(limit, statList.size()));
    }

    public long getTotal() {
        return totalThreadCPUTime_.get();
    }

    public void update() throws Exception {
        boolean samplesAcquired = false;
        for (ThreadInfo ti : threadMxBean_.dumpAllThreads(false, false)) {
            long cpuTime = threadMxBean_.getThreadCpuTime(ti.getThreadId());
            threadCPUTime.putIfAbsent(ti.getThreadId(), cpuTime);
            Long tCPUTime = threadCPUTime.get(ti.getThreadId());
            long deltaCpuTime = (cpuTime - tCPUTime);
            if (ti.getStackTrace().length > 0 && ti.getThreadState() == State.RUNNABLE) {
                for (StackTraceElement stElement : ti.getStackTrace()) {
                    if (isReallySleeping(stElement)) {
                        break;
                    }
                    if (isFiltered(stElement)) {
                        continue;
                    }
                    String key = stElement.getClassName() + "." + stElement.getMethodName() + ":" + stElement.getLineNumber();
                    data_.putIfAbsent(key, new MethodStats(stElement.getClassName(), stElement.getMethodName(), stElement.getLineNumber()));
                    data_.get(key).getHits().addAndGet(deltaCpuTime);
                    totalThreadCPUTime_.addAndGet(deltaCpuTime);
                    samplesAcquired = true;
                    break;
                }
            }
        }
        if (samplesAcquired) {
            updateCount_.incrementAndGet();
        }
    }

    public Long getUpdateCount() {
        return updateCount_.get();
    }

    private boolean isReallySleeping(StackTraceElement se) {
        return se.getClassName().equals("sun.nio.ch.EPollArrayWrapper") &&
                se.getMethodName().equals("epollWait");
    }

    public boolean isFiltered(StackTraceElement se) {
        for (String filteredPackage : filter) {
            if (se.getClassName().startsWith(filteredPackage)) {
                return true;
            }
        }
        return false;
    }
}

