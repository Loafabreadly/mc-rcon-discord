package com.example.mcrcon.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration validation result
 */
@Data
@NoArgsConstructor
public class ValidationResult {
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    
    /**
     * Add an error (makes validation invalid)
     */
    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }
    
    /**
     * Add a warning (doesn't affect validation)
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    /**
     * Get all error messages combined
     */
    public String getErrorSummary() {
        if (errors.isEmpty()) {
            return "No errors";
        }
        return String.join(", ", errors);
    }
    
    /**
     * Get all warning messages combined
     */
    public String getWarningSummary() {
        if (warnings.isEmpty()) {
            return "No warnings";
        }
        return String.join(", ", warnings);
    }
}