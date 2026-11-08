package com.snovasys.trustvault.service;

public final class VaultExceptions {
    private VaultExceptions() {}
    public static class NotFound extends RuntimeException { public NotFound() { super("Resource not found"); } }
    public static class Forbidden extends RuntimeException { public Forbidden() { super("You are not allowed to perform this action"); } }
    public static class BadRequest extends RuntimeException { public BadRequest(String message) { super(message); } }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
}
