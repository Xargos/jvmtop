package com.jvmtop.profiler;

import sun.tools.attach.HotSpotVirtualMachine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created on 27/03/17.
 *
 * @author tckb
 */
public class MemorySampler {
    public static final Map<Long, String> binaryPrefixes_IEC;
    public static final long _1KB = 1024L;
    public static final Map<Character, String> jniTypeSignatureMap;
    private static final Pattern HIST_PATTERN = Pattern.compile(
            "\\s+(\\d+):{1}\\s+(\\d+)\\s+(\\d+)\\s+(.+)"
    );
    private final HotSpotVirtualMachine hVm;
    private Set<HeapHistogram> currentHist;


    static {
        binaryPrefixes_IEC = new TreeMap<Long, String>();
        binaryPrefixes_IEC.put(1L, "B");
        binaryPrefixes_IEC.put(_1KB, "KiB");
        binaryPrefixes_IEC.put(_1KB * 1024, "MiB");
        binaryPrefixes_IEC.put(_1KB * 1024 * 1024, "GiB");
        binaryPrefixes_IEC.put(_1KB * 1024 * 1024 * 1024, "TiB");

        jniTypeSignatureMap = new HashMap<Character, String>();
        jniTypeSignatureMap.put('L', "class");
        jniTypeSignatureMap.put('[', "array");
        jniTypeSignatureMap.put('B', "boolean");
        jniTypeSignatureMap.put('C', "char");
        jniTypeSignatureMap.put('S', "short");
        jniTypeSignatureMap.put('I', "int");
        jniTypeSignatureMap.put('J', "long");
        jniTypeSignatureMap.put('F', "float");
        jniTypeSignatureMap.put('D', "double");
    }

    public static String[] toHumanForm(long bytes) {
        if (bytes < 1024) {
            return new String[]{String.valueOf(bytes), " B"};
        }
        String[] hForm = new String[]{};
        for (Entry<Long, String> entry : MemorySampler.binaryPrefixes_IEC.entrySet()) {
            Double amount = bytes * 1.d / entry.getKey();
            if (amount.intValue() > 0) {
                hForm = new String[]{
                        String.format("%4.3f", amount), entry.getValue()
                };
            } else {
                break;
            }
        }
        return hForm;
    }

    public static String fromNativeType(String nativeClassType) {
        if (jniTypeSignatureMap.containsKey(nativeClassType.charAt(0))) {
            final String type = jniTypeSignatureMap.get(nativeClassType.charAt(0));
            if (type.equals("array")) {
                return fromNativeType(nativeClassType.substring(1)) + "[]";
            }
            if (type.equals("class")) {
                return nativeClassType.substring(1);
            } else { return type; }

        }
        return nativeClassType;
    }

    public MemorySampler(final HotSpotVirtualMachine hVm) {this.hVm = hVm;}

    public SortedSet<HeapHistogram> getHistogram(boolean updateDeltas) throws IOException {
        SortedSet<HeapHistogram> updatedHist = new TreeSet<HeapHistogram>();
        final Matcher matcher = HIST_PATTERN.matcher("");
        String heapLine;
        final BufferedReader data = new BufferedReader(new InputStreamReader(hVm.heapHisto()));
        while ((heapLine = data.readLine()) != null) {
            matcher.reset(heapLine);
            if (matcher.matches()) {
                updatedHist.add(
                        new HeapHistogram(matcher.group(4),
                                matcher.group(2), matcher.group(3))
                );
            }
        }
        if (updateDeltas && currentHist != null) {
            updateDeltas(updatedHist, currentHist);
        }

        currentHist = updatedHist;
        return updatedHist;
    }

    public Set<HeapHistogram> getHistogram(final int limit, boolean updateDeltas) throws IOException {
        final Set<HeapHistogram> topHeapHist = new TreeSet<HeapHistogram>();
        int cnt = 0;
        for (HeapHistogram histogram : getHistogram(updateDeltas)) {
            topHeapHist.add(histogram);
            if (++cnt > limit) {
                break;
            }
        }
        return topHeapHist;
    }

    // this is computationally expensive!
    protected void updateDeltas(Set<HeapHistogram> updatedSamples, Set<HeapHistogram> original) {
        for (HeapHistogram updatedSample : updatedSamples) {

            innerloop:
            for (HeapHistogram originalSample : original) {
                if (originalSample.className.equals(updatedSample.className)) {
                    double changePer = ((updatedSample.bytes - originalSample.bytes) * 100.d / originalSample.bytes);
                    String sign = changePer > 0 ? "▲" : "▼";
                    changePer = Math.abs(changePer);
                    if (changePer > 0) {
                        updatedSample.delta = changePer;
                        updatedSample.deltaSign = sign;
//                        updatedSample.delta = String.format("%s%-5.3f%%", sign, changePer);
                    }
                    break innerloop;
                }
            }

        }
    }


    public class HeapHistogram implements Comparable<HeapHistogram> {

        public final int count;
        public final int bytes;
        public final String className;
        public final String memory;
        public final String memorySuffix;
        public double delta;
        public String deltaSign;

        private HeapHistogram(final String className, final int count, final int bytes) {
            this.className = MemorySampler.fromNativeType(className);
            this.count = count;
            this.bytes = bytes;
            final String[] strings = MemorySampler.toHumanForm(bytes);
            this.memory = strings[0];
            this.memorySuffix = strings[1];
            this.delta = 0;
            this.deltaSign = "";
        }

        private HeapHistogram(final String className, final String count, final String bytes) {
            this(className, Integer.valueOf(count), Integer.valueOf(bytes));
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            final HeapHistogram that = (HeapHistogram) o;
            return className.equals(that.className);
        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }

        @Override
        public int compareTo(final HeapHistogram o) {
            if (bytes > o.bytes) {
                return -1;
            } else {
                if (bytes < o.bytes) {
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            return className + "(" + count + "/" + memory + ")";
        }

    }


}
