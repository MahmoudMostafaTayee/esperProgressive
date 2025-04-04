package com.espertech.esper.example.IOT.helpers;

public enum ErrorCode {
    SUCCESS(0, "Success"),
    // Clustering error.
    CLUSTERING_HAS_FAILED(100, "Cannot divide by zero"),

    DIVISION_BY_ZERO(1001, "Cannot divide by zero"),
    NEGATIVE_VALUE(1002, "Negative value not allowed"),
    FILE_NOT_FOUND(1003, "File not found"),
    INVALID_INPUT(1004, "Invalid input"),
    UNKNOWN_ERROR(9999, "Unknown error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

