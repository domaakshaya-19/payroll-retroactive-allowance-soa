package com.example.payroll;

/** Permanent validation failures carry only safe diagnostic codes, never payload snippets. */
public class InvalidRuleEventException extends IllegalArgumentException {
    private final String code;
    public InvalidRuleEventException(String code){super(code+" [REDACTED]");this.code=code;}
    public String code(){return code;}
}
