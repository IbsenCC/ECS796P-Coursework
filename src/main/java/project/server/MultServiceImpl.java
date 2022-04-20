package project.server;

import project.grpc.MultServiceGrpc;
import project.grpc.MatrixRequest;
import project.grpc.MatrixResponse;
import project.server.ServerMatrixService;

import io.grpc.stub.StreamObserver;

import javax.el.MethodNotFoundException;
import java.util.Arrays;

public class MultServiceImpl extends MultServiceGrpc.MultServiceImplBase {
    int threadNumber;

    public MultServiceImpl(int threadNumber) {
        this.threadNumber = threadNumber;
    }

    @Override
        public void addBlock(MatrixRequest request, StreamObserver<MatrixResponse> responseObserver) {
            System.out.println("Running addBlock on server "+ threadNumber);
            requestHandler(request, responseObserver, "add");
        }

    @Override
        public void multiplyBlock(MatrixRequest request, StreamObserver<MatrixResponse> responseObserver) {
            System.out.println("Running multiplyBlock on server " + threadNumber);
            requestHandler(request, responseObserver, "multiply");
        }

    // Response addBlock and multiplyBlock methods
    private void requestHandler(MatrixRequest request, StreamObserver<MatrixResponse> responseObserver, String operation) throws MethodNotFoundException {
        // Decode matrixA and matrixB from the request
        int[][] decodedMatrixA = ServerMatrixService.decodeMatrix(request.getMatrixA());
        int[][] decodedMatrixB = ServerMatrixService.decodeMatrix(request.getMatrixB());
        int[][] result;

        switch(operation) {
            case "add":
                result = addMatrices(decodedMatrixA, decodedMatrixB);
                break;
            case "multiply":
                result = multiplyMatrices(decodedMatrixA, decodedMatrixB);
                break;
            default:
                throw new MethodNotFoundException("No such method: " + operation);
        }

        String encodedMatrix = ServerMatrixService.encodeMatrix(result);

        // Generate and send the matrix response
        MatrixResponse response = MatrixResponse.newBuilder().setMatrix(encodedMatrix).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // Sums two matrices
    private static int[][] addMatrices(int[][] matrixA, int[][]matrixB) {
        int n = matrixA.length;
        int[][] result = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = matrixA[i][j] + matrixB[i][j];
            }
        }
        return result; 
    }

    // Multiply two matrices
    private static int[][] multiplyMatrices(int[][] matrixA, int[][]matrixB) {
        int n = matrixA.length;
        int blockSize = n / 2;
        int[][] result= new int[n][n];

        for(int i = 0; i < blockSize; i++) {
            for(int j = 0; j < blockSize; j++){
                for(int k = 0; k < blockSize; k++){
                    result[i][j] += (matrixA[i][k] * matrixB[k][j]);
                }
            }
        }

        return result;
    }
}
