package project.client;

import project.grpc.MultServiceGrpc;
import project.grpc.MultServiceGrpc.MultServiceBlockingStub;
import project.grpc.MatrixRequest;
import project.grpc.MatrixResponse;
import project.client.ClientMatrixService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.math.IntMath.isPowerOfTwo;

@Service
public class GrpcClient {
    @Value("localhost")
    private String serverAddress;

    private int[] stubPorts = {8081,8082,8083,8084,8085,8086,8087,8088};

    private ManagedChannel[] channels;
    private MultServiceBlockingStub[] stubs;
    private BlockingQueue<Integer> stubIndices = new LinkedBlockingQueue<>(stubPorts.length);

    @PostConstruct
    public void init() throws InterruptedException {
        channels = createChannels();
        stubs = createStubs();
    }

    @PreDestroy
    public void destroy() {
        for(ManagedChannel channel : channels) {
            channel.shutdown();
        }
    }

    // Takes the indices of the stubs that have not been used recently and adds them to the back of the queue.
    public int[] takeStubIndices(int num) throws InterruptedException {
        int[] indices = new int[num];
        for(int i = 0; i < num; i++) {
            indices[i] = this.stubIndices.take();
            this.stubIndices.add(indices[i]);
        }
        return indices;
    }

    private MultServiceBlockingStub[] createStubs() {
        MultServiceBlockingStub[] stubs = new MultServiceBlockingStub[stubPorts.length];

        for(int i =0; i < channels.length; i++) {
            stubs[i] = MultServiceGrpc.newBlockingStub(channels[i]);
        }

        for(int i = 0; i < stubPorts.length; i++) {
            stubIndices.add(i);
        }

        return stubs;
    }

    private ManagedChannel[] createChannels() {
        ManagedChannel[] chans = new ManagedChannel[stubPorts.length];
        System.out.println("Connecting to server at: " + serverAddress);

        for(int i =0; i < stubPorts.length; i++) {
            chans[i] = ManagedChannelBuilder.forAddress(serverAddress, stubPorts[i])
                    .keepAliveWithoutCalls(true)
                    .usePlaintext()
                    .build();
        }
        return chans;
    }

    // Add integer matrices
    public int[][] addBlock(int A[][], int B[][], int stubIndex) {
        System.out.println("Calling addBlock on server " + (stubIndex + 1));
        MatrixRequest request = generateRequest(A, B);
        MatrixResponse matrixAddResponse = this.stubs[stubIndex].addBlock(request);
        int[][] summedMatrix = ClientMatrixService.decodeMatrix(matrixAddResponse.getMatrix());
        return summedMatrix;
    }

    // Multiply integer matrices
    public int[][] multiplyBlock(int A[][], int B[][], int stubIndex) {
        System.out.println("Calling multiplyBlock on server " + (stubIndex+1));
        MatrixRequest request = generateRequest(A, B);
        MatrixResponse matrixMultiplyResponse = this.stubs[stubIndex].multiplyBlock(request);
        int[][] multipliedMatrix = ClientMatrixService.decodeMatrix(matrixMultiplyResponse.getMatrix());
        return multipliedMatrix;
    }

    // encode the matrices
    private static MatrixRequest generateRequest(int A[][], int B[][]) {
        String matrixA = ClientMatrixService.encodeMatrix(A);
        String matrixB = ClientMatrixService.encodeMatrix(B);

        MatrixRequest request = MatrixRequest.newBuilder()
                .setMatrixA(matrixA)
                .setMatrixB(matrixB)
                .build();

        return request;
    }
}
