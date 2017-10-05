# jeromq-mtserver-example

A quick 'n dirty Java example demonstrating [an issue I stumbled
upon][the-issue] in the process of
translating the zguide examples for
[ezzmq](https://github.com/daveyarwood/ezzmq).

## Summary

This code is an implementation of the multithreaded server
([mtserver](http://zguide.zeromq.org/java:mtserver)) zguide example: a single
process that includes a broker (binding ROUTER and DEALER sockets) and, on five
separate threads, workers that connect a REP socket and respond to proxied
requests.

## The issue

Interrupting the process while polling a ROUTER or DEALER socket seems to
intermittently produce an "index out of bounds" stacktrace.

There are 2 separate uncaught exceptions happening in two places in the code --
one is related to ROUTER sockets and the other is related to DEALER sockets.
One is an IndexOutOfBoundsException and the other is an
ArrayIndexOutOfBoundsException. See [the issue][the-issue] for the stacktraces.

## Differences from the zguide example Java code

My code has the same intent and basic structure as the mtserver example code in
the zguide, but with some additions that I feel are necessary in practice:

* Instead of using `ZMQ.proxy`, I am proxying manually by polling the ROUTER and
DEALER sockets and whenever a message is available on one, I receive it and send
it to the other one. This part of the code is essentially the same as the
manual proxying done in the [rrbroker](http://zguide.zeromq.org/java:rrbroker) example.

* Instead of defining a Worker class that extends (and is a thin wrapper
around) Thread, I just make instances of Thread and start them. I don't think
this is effectively any different than the Worker class in the mtserver example,
just pointing it out in case it looks suspect.

* In the worker threads, I catch ZMQException and swallow the exception if the
error code is ETERM. This idea comes from the
[interrupt](http://zguide.zeromq.org/java:interrupt) zguide example; it's a way
to handle Ctrl-C cleanly when there is a worker thread sharing a context with
the main thread and the main thread is interrupted.

* There is a shutdown hook that destroys the context before exiting. This seems to
be where things go haywire -- problems arise (uncaught exceptions, process
hanging) in the course of terminating the context. I could avoid this by not
terminating the context, but from what I understand, it is important to ensure
the context is terminated before exiting, in order to ensure that system
resources (file handles from selectors, etc.) are properly disposed.

## Reproducing the issue

Run `boot run`, wait some arbitrary amount of time, and then interrupt the
process by pressing Ctrl-C. You'll probably have to do it a bunch of times
before you get an "index out of bounds" stacktrace.

Sometimes, it will run and be interrupted successfully.

Other times, it will run successfully, but then hang when you interrupt it.

Other times, it will run successfully, but when you interrupt it, an "index out
of bounds" stacktrace will print and the process hangs.

```
$ boot run
Compiling 1 Java source files...
Binding frontend DEALER socket on port 33825...
Binding backend ROUTER socket at inproc://workers...
Starting workers...
[worker 0] Reporting for duty!
[worker 2] Reporting for duty!
[worker 3] Reporting for duty!
Proxying requests...
[worker 1] Reporting for duty!
[worker 4] Reporting for duty!
^CShutting down.
java.lang.ArrayIndexOutOfBoundsException: -1
        at java.util.ArrayList.elementData(ArrayList.java:418)
        at java.util.ArrayList.get(ArrayList.java:431)
        at java.util.Collections.swap(Collections.java:497)
        at zmq.socket.FQ.terminated(FQ.java:65)
        at zmq.socket.reqrep.Router.xpipeTerminated(Router.java:179)
        at zmq.SocketBase.pipeTerminated(SocketBase.java:1124)
        at zmq.pipe.Pipe.processPipeTermAck(Pipe.java:397)
        at zmq.ZObject.processCommand(ZObject.java:91)
        at zmq.Command.process(Command.java:75)
        at zmq.SocketBase.processCommands(SocketBase.java:937)
        at zmq.SocketBase.inEvent(SocketBase.java:1060)
        at zmq.poll.Poller.run(Poller.java:268)
        at java.lang.Thread.run(Thread.java:745)
```

[the-issue]: http://zguide.zeromq.org/java:mtserver
