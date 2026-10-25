package dev.polymodloader.tools.tickprobe;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/**
 * One timed call as a Java Flight Recorder event, committed when the call returns, so a profile can tell the slow calls
 * from the others: the call started {@link #callNanos} before the event's start time. Only with the agent argument
 * {@code jfr=true}; the agent puts this class on the boot class path with {@link TickRecorder}.
 */
@Name("poly.TickProbe.Call")
@Label("Timed call")
@Category("Poly Mod Loader tick probe")
@Description("A call of the method the tick probe times; it began callNanos before this event's start time")
@StackTrace(false)
final class TimedCallEvent extends Event {

    @Label("Call duration")
    @Timespan(Timespan.NANOSECONDS)
    long callNanos;

    @Label("Call index")
    long index;
}
