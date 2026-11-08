package com.snovasys.trustvault.storage;

import java.nio.file.Path;

public record StoredFile(String storageKey, String originalFilename, String contentType, long sizeBytes, Path path) {}
