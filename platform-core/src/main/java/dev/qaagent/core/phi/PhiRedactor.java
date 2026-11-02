package dev.qaagent.core.phi;

import java.util.regex.Pattern;

/**
 * Replaces values that look like personal data with typed tokens.
 * Business words such as plan names and effective-date rules are left as written.
 */
public final class PhiRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z])");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\w)(?:\\+?1[\\s.-]?)?(?:\\(\\d{3}\\)|\\d{3})[\\s.-]\\d{3}[\\s.-]\\d{4}\\b");
    private static final Pattern SSN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{3}\\s\\d{2}\\s\\d{4}\\b");
    private static final Pattern DOB = Pattern.compile(
            "(?i)\\b(?:dob|date of birth|birth date|born on)\\s*[:#-]?\\s*(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");
    private static final Pattern LABELED_MEMBER_ID = Pattern.compile(
            "(?i)\\b(?:member|subscriber)\\s+(?:id|identifier|number)\\s*[:#-]?\\s*[A-Z0-9-]*\\d[A-Z0-9-]*\\b");
    private static final Pattern MEMBER_ID = Pattern.compile(
            "(?i)\\b(?:MBR|MEM)-?\\d{5,}\\b");

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String redacted = EMAIL.matcher(text).replaceAll("[EMAIL]");
        redacted = PHONE.matcher(redacted).replaceAll("[PHONE]");
        redacted = SSN.matcher(redacted).replaceAll("[SSN]");
        redacted = DOB.matcher(redacted).replaceAll("[DOB]");
        redacted = LABELED_MEMBER_ID.matcher(redacted).replaceAll("[MEMBER_ID]");
        redacted = MEMBER_ID.matcher(redacted).replaceAll("[MEMBER_ID]");
        return redacted;
    }
}
