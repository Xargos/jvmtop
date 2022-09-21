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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores method invocations in a thread-safe manner.
 *
 * @author paru
 */
public class MethodStats implements Comparable<MethodStats> {
    private final AtomicLong hits_ = new AtomicLong(0);

    private final String className_;

    private final String methodName_;

    private final int lineNumber;

    /**
     * @param className
     * @param methodName
     * @param lineNumber
     */
    public MethodStats(String className, String methodName, int lineNumber) {
        super();
        className_ = className;
        methodName_ = methodName;
        this.lineNumber = lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodStats that = (MethodStats) o;
        return lineNumber == that.lineNumber && className_.equals(that.className_) && methodName_.equals(that.methodName_);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className_, methodName_, lineNumber);
    }

    @Override
    /**
     * Compares a MethodStats object by its hits
     */
    public int compareTo(MethodStats o) {
        return Long.compare(o.hits_.get(), hits_.get());
    }

    public AtomicLong getHits() {
        return hits_;
    }

    public String getClassName() {
        return className_;
    }

    public String getMethodName() {
        return methodName_;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}