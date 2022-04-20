package project.client;

import project.grpc.MultServiceGrpc;
import project.grpc.MultServiceGrpc.MultServiceBlockingStub;
import project.grpc.MatrixRequest;
import project.grpc.MatrixResponse;
import project.client.ClientMatrixService;
import project.client.GrpcClient;

import java.util.concurrent.ExecutionException;

public interface BlockMult {
    String multiplyMatrixFiles(String matrixStringA, String matrixStringB, long deadline) throws ThrowMatrixException, ExecutionException, InterruptedException;
}