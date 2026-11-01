# Example Benefit Plan glossary

This glossary is invented for the practice enrollment system. The words below are the ones the test agents should use.

## Subscriber

The subscriber is the person who holds the contract. In an 834 enrollment the subscriber is the member whose relationship is SELF. Dependents hang off the subscriber's contract.

## Dependent

A dependent is a spouse or a child stored on the subscriber's contract. A dependent does not have a separate contract in this practice plan.

## Maintenance type

The maintenance type says what the enrollment is trying to do. ADD inserts a person. CHANGE updates a person who is already stored. TERM ends coverage for the named person, or for the whole contract when the subscriber is named.

## Relationship code

The relationship code says how a person is connected to the subscriber. This plan allows SELF, SPOUSE, and CHILD.

## Effective date

The effective date is the day the ADD or CHANGE should start. It is not a date of birth. Retroactive means the effective date is before the day the file was submitted. This plan allows that only within 30 days.

## Coverage end date

The coverage end date is the last day the person is covered on a TERM. People with an end date in the past are not active.

## Member identifier

The member identifier is the plan's own reference for a person. It is not a government identifier. Two active subscribers cannot share one.

## Outcome

ACCEPTED means the enrollment was stored. REJECTED means nothing about that request was stored, and the response includes a reason.

## Plan type

The practice plan offers HMO and PPO. A mid-year CHANGE can move a subscriber from one to the other. The new plan type starts on the CHANGE effective date.

## Module

Enrollment is the only module in this practice system. Searches for enrollment rules should stay in that module.
