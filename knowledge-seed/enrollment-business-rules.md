# Example Benefit Plan enrollment rules

These rules belong to a fictional plan called the Example Benefit Plan. They are invented for practice. They are not a real insurer's policy and they do not describe a real member.

## Add a spouse

A benefits administrator may submit maintenance type ADD for a spouse of an active subscriber. The relationship code is SPOUSE. The plan stores the spouse as a dependent and leaves the subscriber's own coverage effective date unchanged. The outcome is ACCEPTED when the subscriber is active and the spouse is not already stored.

## Add a child under age 26

A child aged 0 through 25 may be added with relationship CHILD. The child receives the same plan as the subscriber. The child's coverage effective date is the date on the enrollment. Age is measured on the coverage effective date.

## Reject a child aged 26 or older

Relationship CHILD with age 26 or older is rejected. No new member row is stored. The rejection reason says the child exceeds the dependent age limit. A disabled-dependent exception is out of scope for this practice plan.

## Terminate every person on the contract

Maintenance type TERM for the subscriber ends the subscriber and every dependent on the same contract. The coverage end date is the date on the enrollment. A TERM dated in the future is stored and becomes effective on that date.

## Terminate one dependent

A TERM that names one dependent ends only that person. The subscriber and the other dependents stay active. The named person must already be stored. An unknown person is rejected and nothing else changes.

## Retroactive effective dates

An effective date up to 30 days before the submission date is accepted. An effective date older than 30 days is rejected. The rejection reason says the retroactive window was exceeded. Future effective dates are allowed.

## Relationship codes

The only relationship codes this plan accepts are SELF, SPOUSE, and CHILD. Any other code is rejected. No person is stored when the code is unknown. The rejection reason names the invalid relationship code.

## Duplicate member identifier

A second active subscriber must not reuse a member identifier that already belongs to an active subscriber. The second enrollment is rejected. The first subscriber is left unchanged. The rejection reason says the member identifier is already active.

## Address change

An address change updates only the mailing address. The plan, the effective date, and the dependents stay as they were. A missing postal code is rejected and the previous address is kept.

## Plan change during the year

A subscriber may change from HMO to PPO, or from PPO to HMO, with maintenance type CHANGE. The new plan starts on the CHANGE effective date. Claims before that date stay on the old plan. Dependents move with the subscriber unless a dependent was already terminated.

## Maintenance types

ADD creates a person who is not already stored. CHANGE updates an existing enrollment. TERM ends coverage. Any other maintenance type is rejected and no row is written.

## Missing member identifier

An enrollment file with no member identifier on the subscriber is rejected. The reason says the member identifier is required. No partial family is stored.

## Dates

Coverage dates in this plan are calendar dates written CCYYMMDD. A date that is not a real calendar day, or that uses another layout, is rejected. The rejection reason says the date is not CCYYMMDD.
