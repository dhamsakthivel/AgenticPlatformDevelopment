package dev.qaagent.core.phi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhiRedactorTest {

    private final PhiRedactor redactor = new PhiRedactor();

    @Test
    void replacesPersonalValuesWithTypedTokens() {
        String redacted = redactor.redact("""
                Contact pat.example@example.test or (415) 555-0134.
                SSN 123-45-6789 and 123 45 6789.
                Date of birth: 1980-04-02.
                Member ID: MBR-100245.
                Also MEM1234567.
                """);

        assertThat(redacted).contains("[EMAIL]", "[PHONE]", "[SSN]", "[DOB]", "[MEMBER_ID]");
        assertThat(redacted).doesNotContain("123-45-6789", "pat.example@example.test", "415", "1980-04-02", "100245");
    }

    @Test
    void leavesEnrollmentRulesUntouched() {
        String rules = """
                A child aged 0 through 25 is accepted as a dependent.
                Effective dates use CCYYMMDD, for example 2026-01-01.
                Change the plan from HMO to PPO mid-year.
                Story ENR-001 adds a spouse. Retroactive changes over 30 days are rejected.
                """;

        assertThat(redactor.redact(rules)).isEqualTo(rules);
    }
}
