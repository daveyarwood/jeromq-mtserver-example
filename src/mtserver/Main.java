package mtserver;

import java.io.IOException;
import java.net.ServerSocket;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.*;
import org.zeromq.ZMQ.Error;
import org.zeromq.ZMQException;
import org.zeromq.ZThread;

public class Main {
  private static final String BACKEND_ADDRESS = "inproc://workers";
  private static int NUMBER_OF_WORKERS = 5;

  private static int findOpenPort() throws IOException {
    final ServerSocket tmpSocket = new ServerSocket(0, 0);
    try {
      return tmpSocket.getLocalPort();
    } finally {
      tmpSocket.close();
    }
  }

  // Receives parts of a multi-part message from `source` socket and sends them
  // to `destination` socket. Stops when the multi-part message is over, i.e.
  // there are no more messages to receive.
  private static void forwardMessages(Socket source, Socket destination) {
    while (true) {
      byte[] msg = source.recv(0);
      boolean hasMore = source.hasReceiveMore();
      destination.send(msg, hasMore ? ZMQ.SNDMORE : 0);
      if (!hasMore) break;
    }
  }

  private static void workerLog(int id, String msg) {
    System.out.printf("[worker %d] %s\n", id, msg);
  }

  private static void startWorker(ZContext ctx, int id) {
    new Thread() {
      public void run() {
        try {
          workerLog(id, "Reporting for duty!");

          Socket socket = ctx.createSocket(ZMQ.REP);
          socket.connect(BACKEND_ADDRESS);

          while (true) {
            String msg = socket.recvStr(0);
            workerLog(id, "Received message: " + msg);
            // simulate doing work
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(ie);
            }
            workerLog(id, "Sending response...");
            socket.send("got it");
          }
        } catch (ZMQException e) {
          if (e.getErrorCode() != Error.ETERM.getCode())
            throw e;
        }
      }
    }.start();
  }

  public static void main(String[] argv) {
    try {
      ZContext ctx = new ZContext();

      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          System.out.println("Shutting down.");
          ctx.destroy();
        }
      });

      int frontendPort = findOpenPort();
      System.out.printf("Binding frontend DEALER socket on port %d...\n",
                        frontendPort);
      Socket frontend = ctx.createSocket(ZMQ.DEALER);
      frontend.bind("tcp://*:" + frontendPort);

      System.out.printf("Binding backend ROUTER socket at %s...\n",
                        BACKEND_ADDRESS);
      Socket backend = ctx.createSocket(ZMQ.ROUTER);
      backend.bind(BACKEND_ADDRESS);

      System.out.println("Starting workers...");
      for (int i = 0; i < NUMBER_OF_WORKERS; i++) {
        startWorker(ctx, i);
      }

      System.out.println("Proxying requests...");

      Poller poller = ctx.createPoller(2);
      poller.register(frontend, Poller.POLLIN);
      poller.register(backend, Poller.POLLIN);
      while (poller.poll() != -1) {
        if (poller.pollin(0)) forwardMessages(frontend, backend);
        if (poller.pollin(1)) forwardMessages(backend, frontend);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
