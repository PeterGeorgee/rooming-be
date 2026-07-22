package com.campkin.config;
import java.nio.charset.StandardCharsets; import java.security.*; import java.util.*;
public final class AuthTokens { private AuthTokens(){} public static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}} }
