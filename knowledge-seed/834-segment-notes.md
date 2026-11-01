# Practice notes on an 834 enrollment file

These notes describe the slices of an 834 file that the practice enrollment application will read later. They are written from public, general knowledge of the file shape. They are not a copy of a licensed implementation guide.

## Envelope

The file opens with an interchange header and a functional-group header, and it closes with the matching trailers. The practice parser checks that the trailer counts agree with the groups and transactions inside. A file whose trailer counts do not match is rejected before any person is stored.

## Transaction

Each enrollment transaction starts with a beginning segment that carries the maintenance intent and the dates for the transaction. The practice plan reads the maintenance type from this area: ADD, CHANGE, or TERM.

## Member loop

Each person is one member loop. The loop names the relationship to the subscriber, and it carries a reference for the member identifier. A subscriber loop uses relationship SELF. A spouse loop uses SPOUSE. A child loop uses CHILD.

The demographic line carries age information the plan uses for the child age limit. The practice rules measure age on the coverage effective date. They do not store a raw birth date in the search index.

## Health coverage

The coverage line names the plan type, HMO or PPO, and the coverage dates. An ADD or CHANGE uses the effective date. A TERM uses the coverage end date. Dates must be real calendar days in CCYYMMDD order.

## What the practice parser ignores

Segments that this practice application does not implement are skipped when they are well formed. They do not by themselves accept or reject the enrollment. A missing member identifier, an unknown relationship, or a bad date still rejects the transaction.

## Rejection reasons the parser may return

The child exceeds the dependent age limit. The retroactive window was exceeded. The relationship code is invalid. The member identifier is already active. The member identifier is required. The date is not CCYYMMDD.
