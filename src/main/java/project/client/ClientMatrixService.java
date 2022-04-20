package project.client;

import java.util.Arrays;

public class ClientMatrixService {
    // Turn a 2d-array to a matrix
    public static String encodeMatrix(int[][] matrix) {
        return Arrays.deepToString(matrix);
    }

    // Turn a matrix to a 2d-array
    public static int[][] decodeMatrix(String matrixString) {
        return stringToDeep(matrixString);
    }

    public static int[][] stringToDeep(String str) {
        int i, j;
        int n = -1;

        for (i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '[') {
                n++;
            }
        }

        int[][] out = new int[n][n];

        String midstr = str.replaceAll("\\[", "").replaceAll("\\]", "");
        String[] outstr = midstr.split(", ");

        j = -1;
        for (i = 0; i < outstr.length; i++) {
            if (i % n == 0) {
                j++;
            }
            out[j][i % n] = Integer.parseInt(outstr[i]);
        }
        return out;
    }
}