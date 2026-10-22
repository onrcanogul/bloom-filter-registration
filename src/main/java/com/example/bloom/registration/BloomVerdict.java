package com.example.bloom.registration;

public enum BloomVerdict {
    /** Filter says the username was never added: the database is skipped. */
    DEFINITELY_ABSENT,
    /** Filter says the username might exist: the database must confirm. */
    MIGHT_EXIST,
    /** Filter was bypassed for this lookup. */
    NOT_USED
}
