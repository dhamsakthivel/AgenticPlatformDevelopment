# Testing standards for the Example Benefit Plan

These standards tell a later test-design agent how to draft cases. They are practice rules, not a client's test policy.

## One case, one behavior

Each test case checks one rule. A case that adds a spouse does not also terminate a child. The title names the behavior, for example "Add a spouse as a dependent" or "Reject a child aged 26 or older."

## Case types

POSITIVE cases follow the rule and expect ACCEPTED. NEGATIVE cases break one rule and expect REJECTED, with the rejection reason in the expected result. BOUNDARY cases sit on a limit: a child aged 25 is accepted, a child aged 26 is rejected, a retroactive date of 30 days is accepted, and a retroactive date of 31 days is rejected.

## Priority

P1 covers reject rules that would store bad coverage if they failed: age limit, duplicate member identifier, missing member identifier, and invalid relationship. P2 covers the ordinary ADD, CHANGE, and TERM paths. P3 covers address-only changes and wording checks.

## Steps

Write steps as an action and an expected result. The action names the maintenance type and the person. The expected result says ACCEPTED or REJECTED and, for a rejection, the reason from the enrollment rules.

## Data

Each case lists the people it needs without using a real person's identifiers. Say "subscriber", "spouse aged 30", or "child aged 26". Dates are described as rules, such as "effective date 10 days before submission" or "a date that is not CCYYMMDD".

## Trace to the story

Every case names the acceptance criterion it covers. A vague story with no criteria should produce a case that records the missing criterion instead of inventing a pass.

## Drafts

Cases written by the agent stay in DRAFT until a person approves them. A draft is not a result and it is not ready to run.
