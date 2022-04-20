package project.client;

public class ThrowMatrixException extends Exception {
    public ThrowMatrixException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
