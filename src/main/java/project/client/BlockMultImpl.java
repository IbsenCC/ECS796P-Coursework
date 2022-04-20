package project.client;

import project.grpc.MultServiceGrpc;
import project.grpc.MultServiceGrpc.MultServiceBlockingStub;
import project.grpc.MatrixRequest;
import project.grpc.MatrixResponse;
import project.client.ClientMatrixService;
import project.client.GrpcClient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.math.IntMath.isPowerOfTwo;

@Service
public class BlockMultImpl implements BlockMult {
    @Autowired
    private GrpcClient grpcClient;

    public String multiplyMatrixFiles(String matrixStringA, String matrixStringB, long deadline) throws ThrowMatrixException, ExecutionException, InterruptedException {
        int[][] A = stringToMatrixArray(matrixStringA);
        int[][] B = stringToMatrixArray(matrixStringB);
        System.out.println("Matrix A is" + ClientMatrixService.encodeMatrix(A));
        System.out.println("Matrix B is" + ClientMatrixService.encodeMatrix(B));

        int[][] multipliedMatrixBlock = multiplyMatrixBlock(A, B, deadline);
        return ClientMatrixService.encodeMatrix(multipliedMatrixBlock);
    }

    private static int[][] stringToMatrixArray(String matrixString) throws ThrowMatrixException {
        // Turn matrix string to lines and columns
        String[] lines = matrixString.trim().split("\n");
        String[] columns = lines[0].trim().split(" ");

        // Init the matrix array
        int[][] matrixArray = new int[lines.length][columns.length];

        if(lines.length != columns.length) {
            throw new ThrowMatrixException("Invalid matrix:\n\n" + matrixString + "\n\nmatrix must be a square matrix", new Error("matrix must be a square matrix"));
        }

        if(!isPowerOfTwo(lines.length) || !isPowerOfTwo(columns.length)){
            throw new ThrowMatrixException("Invalid matrix:\n\n" + matrixString + "\n\nmatrix`s dimensions must be powers of 2", new Error("matrix`s dimensions must be powers of 2"));
        }

        try {
            // Loop through matrix and assign to matrix Array
            for (int i = 0; i < lines.length; i++) {
                String[] matrixValues = lines[i].trim().split(" ");
                for (int j = 0; j < matrixValues.length; j++) {
                    matrixArray[i][j] = Integer.parseInt(matrixValues[j]);
                }
            }
        } catch (NumberFormatException|ArrayIndexOutOfBoundsException e) {
            throw new ThrowMatrixException("Invalid matrix:\n\n" + matrixString, e);
        }

        return matrixArray;
    }

    // Multiplies matrices with addBlock and multiplyBlock
    private int[][] multiplyMatrixBlock(int[][] A, int[][] B, long deadline) throws InterruptedException, ExecutionException {

        // Split matrix blocks into 8 smaller blocks
        HashMap<String, int[][]> blocks = splitBlocks(A, B);

        // Get first gRPC server stub
        int firstStubIndex = grpcClient.takeStubIndices(1)[0];

        // Footprinting function
        long startTime = System.nanoTime();

        // CompletableFuture enables asynchronous calls to the multiplyBlock function
        CompletableFuture<int[][]> A1A2Future = CompletableFuture.supplyAsync(() -> grpcClient.multiplyBlock(blocks.get("A1"), blocks.get("A2"), firstStubIndex));

        // This will wait for the async function to complete before continuing
        int[][] A1A2 = A1A2Future.get();
        long endTime = System.nanoTime();
        long footprint= endTime - startTime;

        int numBlockCalls = 7;
        int numberServer = (int) Math.ceil((float)footprint*(float)numBlockCalls/(float)deadline);
        numberServer = numberServer <= 8 ? numberServer : 8;

        System.out.println("Using "+ numberServer + " servers for rest of calculation");

        // Take the least recently used stub indices for this workload to reduce traffic
        int[] indices = grpcClient.takeStubIndices(numberServer);
        BlockingQueue<Integer> indexQueue = new LinkedBlockingQueue<>((int) numBlockCalls);

        int i = 0;
        while(indexQueue.size() != numBlockCalls) {
            if(indices.length == i) {
                i = 0;
            }
            indexQueue.add(indices[i]);
            i++;
        }

        // Asynchronous calls to the gRPC blocking calls
        CompletableFuture<int[][]> B1C2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("B1"), blocks.get("C2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> A1B2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("A1"), blocks.get("B2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> B1D2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("B1"), blocks.get("D2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> C1A2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("C1"), blocks.get("A2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> D1C2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("D1"), blocks.get("C2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> C1B2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("C1"), blocks.get("B1"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> D1D2 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.multiplyBlock(blocks.get("D1"), blocks.get("D2"), indexQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> A3 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.addBlock(A1A2, B1C2.get(), indexQueue.take());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> B3 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.addBlock(A1B2.get(), B1D2.get(), indexQueue.take());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> C3 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.addBlock(C1A2.get(), D1C2.get(), indexQueue.take());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });

        CompletableFuture<int[][]> D3 = CompletableFuture.supplyAsync(() -> {
            try {
                return grpcClient.addBlock(C1B2.get(), D1D2.get(), indexQueue.take());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });

        // join the remote calculations back together

        int[][] res = joinBlocks(A3.get(), B3.get(), C3.get(), D3.get());

        System.out.println("Finished calculation");
        return res;
    }

    // Joins the blocks back together
    private int[][] joinBlocks(int[][] A3, int[][] B3, int[][] C3, int[][] D3) {
        int MAX = A3.length;
        int bSize = MAX/2;
        int[][] res = new int[MAX][MAX];

        for (int i = 0; i < bSize; i++)
        {
            for (int j = 0; j < bSize; j++)
            {
                res[i][j]=A3[i][j];
            }
        }
        for (int i = 0; i < bSize; i++)
        {
            for (int j = bSize; j < MAX; j++)
            {
                res[i][j]=B3[i][j-bSize];
            }
        }
        for (int i = bSize; i < MAX; i++)
        {
            for (int j = 0; j < bSize; j++)
            {
                res[i][j]=C3[i-bSize][j];
            }
        }
        for (int i = bSize; i < MAX; i++)
        {
            for (int j = bSize; j < MAX; j++)
            {
                res[i][j]=D3[i-bSize][j-bSize];
            }
        }
        return res;
    }

    private HashMap<String,int[][]> splitBlocks(int[][] A, int[][] B) {
        int MAX = A.length;
        int bSize = MAX/2;

        int[][] A1 = new int[MAX][MAX];
        int[][] A2 = new int[MAX][MAX];
        int[][] B1 = new int[MAX][MAX];
        int[][] B2 = new int[MAX][MAX];
        int[][] C1 = new int[MAX][MAX];
        int[][] C2 = new int[MAX][MAX];
        int[][] D1 = new int[MAX][MAX];
        int[][] D2 = new int[MAX][MAX];

        for (int i = 0; i < bSize; i++)
        {
            for (int j = 0; j < bSize; j++)
            {
                A1[i][j]=A[i][j];
                A2[i][j]=B[i][j];
            }
        }
        for (int i = 0; i < bSize; i++)
        {
            for (int j = bSize; j < MAX; j++)
            {
                B1[i][j-bSize]=A[i][j];
                B2[i][j-bSize]=B[i][j];
            }
        }
        for (int i = bSize; i < MAX; i++)
        {
            for (int j = 0; j < bSize; j++)
            {
                C1[i-bSize][j]=A[i][j];
                C2[i-bSize][j]=B[i][j];
            }
        }
        for (int i = bSize; i < MAX; i++)
        {
            for (int j = bSize; j < MAX; j++)
            {
                D1[i-bSize][j-bSize]=A[i][j];
                D2[i-bSize][j-bSize]=B[i][j];
            }
        }

        HashMap<String, int[][]> blocks = new HashMap<>();
        blocks.put("A1", A1);
        blocks.put("A2", A2);
        blocks.put("B1", B2);
        blocks.put("B2", B2);
        blocks.put("C1", C1);
        blocks.put("C2", C2);
        blocks.put("D1", D1);
        blocks.put("D2", D2);

        return blocks;
    }
}