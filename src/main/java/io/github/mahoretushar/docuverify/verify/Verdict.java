package io.github.mahoretushar.docuverify.verify;

public enum Verdict {
    /** The source states it. */
    SUPPORTED,
    /** The source says the opposite (changed number, negation, wrong entity). */
    CONTRADICTED,
    /** Nothing in the sources backs it up. */
    UNSUPPORTED,
    /** Cites a source number that was never retrieved. */
    INVALID_CITATION
}
