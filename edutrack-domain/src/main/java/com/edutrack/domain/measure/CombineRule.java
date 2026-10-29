package com.edutrack.domain.measure;

/** How the parts of one attempt (results on the same day) become the attempt's value. */
public enum CombineRule {
    /** Each day keeps its last result: single-number measures such as a time or a word count. */
    NONE,
    /** The mean of the parts: an exam scored as the average of its sections (IELTS overall band). */
    AVERAGE,
    /** The total of the parts: an exam scored by adding its sections. */
    SUM
}
