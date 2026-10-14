# Day 5 — JVM Memory and Garbage Collection Investigation

**Date:** 14 October 2026  
**Project:** Java Transaction Platform  
**JVM:** G1 Garbage Collector, `-Xms64m`, `-Xmx128m`  
**Tools:** Java Flight Recorder (JFR), GC logs, `jcmd`

## 1. Objective

Investigate how allocation rate and object retention affect Java heap usage and garbage collection behaviour.

Two controlled workloads were executed: temporary object allocation and intentional object retention.

## 2. Experiment Setup

| Measurement | Temporary | Retained |
|---|---:|---:|
| Arrays created | 10,000 | 1,200 |
| Array size | 32 KiB | 32 KiB |
| Total array payload allocated | 312.5 MiB | 37.5 MiB |
| Arrays intentionally retained | 0 | 1,200 |

The temporary workload did not preserve references to the created arrays, allowing them to become eligible for garbage collection.

The retained workload stored array references in a static collection, keeping them strongly reachable.

## 3. GC Observations

| Metric | Temporary | Retained |
|---|---:|---:|
| JFR GC events | 9 | 4 |
| Young GC events | 9 | 3 |
| Old GC events | 0 | 1 |
| Reserved heap | 128 MiB | 128 MiB |
| Committed heap at exit | 67 MiB | 75 MiB |
| Used heap at exit | 22.4 MiB | 45.3 MiB |

### Temporary Allocation

Nine G1 young-generation collections were recorded.

One collection reported:

`42M -> 4M (67M), 0.152 ms`

This shows substantial memory reclamation during a young-generation GC.

The temporary workload created approximately 312.5 MiB of array payload, yet the heap used at JVM exit was approximately 22.4 MiB.

### Retained Allocation

Three G1 young collections and one G1 old event were recorded.

The young collections included:

- GC(0): 36 MiB → 21 MiB
- GC(1): 34 MiB → 34 MiB
- GC(2): 43 MiB → 43


step 1
```bash
mvn clean verify
mkdir -p target/jvm
```

step 2
```bash
profile() {
mode="$1"

java -Xms64m -Xmx128m \
"-Xlog:gc*:file=target/jvm/${mode}-gc.log:time,uptime,level,tags" \
"-XX:StartFlightRecording=filename=target/jvm/${mode}.jfr,settings=profile,dumponexit=true" \
-cp target/classes \
io.github.eyupuk.transactions.performance.MemoryLab \
"$mode" &

pid=$!

sleep 2
echo "Inspecting JVM process $pid"
jcmd "$pid" GC.heap_info

wait "$pid"
}

profile temporary
profile retained
```
step 3
```aiignore
target/jvm/
├── temporary-gc.log
├── temporary.jfr
├── retained-gc.log
└── retained.jfr
```

step 4
```bash
jfr summary target/jvm/temporary.jfr
jfr summary target/jvm/retained.jfr
```

step 5
```bash
jfr print --events jdk.GarbageCollection \
    target/jvm/temporary.jfr
jfr print --events jdk.GarbageCollection \
    target/jvm/retained.jfr
```

What I want you to investigate
```aiignore
                 Question	                                     What to observe
Which workload allocated more bytes overall?	        Total arrays created × allocation size
Which workload retained more objects?	                retainedData count and heap inspection
Did GC reclaim temporary objects?	                GC events and heap observations
Did retaining objects affect heap usage?	        Compare used heap where reported
Are GC frequency and live heap the same measurement?	Explain the distinction
```


step 6
```aiignore
    Tool	                                Usage
jcmd	                    JVM diagnostics, heap information, thread dumps and JFR control
jfr	                    Summarise and inspect flight recordings
JDK Mission Control	    Graphical analysis of JFR recordings
jstat	                    JVM statistics, including garbage-collection activity
jstack	                    Thread-stack inspection
jmap	                    Heap-related diagnostics
GC logging	            Observe collections, pause times and heap changes
```


step 7
```bash
jcmd -l
```

step 8
```bash
jcmd <PID> GC.heap_info
jcmd <PID> Thread.print
```

GC Logs
```aiignore
tail -n 25 target/jvm/temporary-gc.log
tail -n 25 target/jvm/retained-gc.log
```
1. Comparison of your actual GC results
```
Measurement	                            Temporary	        Retained
JFR GC events	                                9	            4
G1 young GC events	                        9	            3
G1 old GC events	                        0	            1
Longest reported GC pause	                1.74 ms	            2.81 ms
Shortest reported young GC pause	        0.143 ms	    0.474 ms
Total array payload created by the code	        312.5 MiB	    37.5 MiB
Arrays deliberately retained	                0	            1,200
```
