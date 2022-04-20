package project.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GrpcServer extends Thread {
    int port;
    int threadNumber;
    static int startPort = 8081;

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    public GrpcServer(int port, int threadNumber) {
        this.port = port;
        this.threadNumber = threadNumber;
    }

    public static void main(String[] args) {
        // Stop printing log on Terminal
        logger.info("Logging INFO with Logback");
        logger.error("Logging ERROR with Logback");

        // Get the number of processors
        Runtime runtime = Runtime.getRuntime();
        int numberOfProcessors = runtime.availableProcessors();
        System.out.println("Number of processors is " + numberOfProcessors);

        for(int i = 0; i < numberOfProcessors; i++) {
            new GrpcServer(startPort + i,i).start();
        }
    }

    public void run() {
        Server server = ServerBuilder.forPort(port).addService(new MultServiceImpl(threadNumber)).build();

        try {
            server.start();
            System.out.println("Server " + threadNumber + " started on " + port);
            server.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
