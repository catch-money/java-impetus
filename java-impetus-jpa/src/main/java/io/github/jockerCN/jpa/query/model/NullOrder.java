package io.github.jockerCN.jpa.query.model;

/** Placement of null values in an ORDER BY clause. */
public enum NullOrder {
    /** Leave null ordering to the database dialect, as before. */
    DEFAULT,
    FIRST,
    LAST
}
