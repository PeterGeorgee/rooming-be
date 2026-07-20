package com.campkin.api;

import java.util.List;

public class ImportValidationException extends RuntimeException {
    private final List<String> errors;
    public ImportValidationException(List<String> errors) { super("The import file contains " + errors.size() + " error(s)"); this.errors = List.copyOf(errors); }
    public List<String> getErrors() { return errors; }
}
