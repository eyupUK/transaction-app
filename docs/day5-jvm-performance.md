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