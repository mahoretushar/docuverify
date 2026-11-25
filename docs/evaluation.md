# Verifier evaluation

Models: llama3.2 (3B) as NLI judge, nomic-embed-text embeddings, via Ollama. Similarity threshold: 0.65. 44 labelled claims over 4 policy documents; every claim is checked against all four documents.

## Similarity only

| Metric | Value |
|---|---|
| Accuracy | 47.7% |
| Hallucination precision (flagged claims that were really wrong) | 77.8% |
| Hallucination recall (wrong claims that were flagged) | 25.0% |
| F1 | 0.38 |
| Contradictions caught | 3 / 16 |
| Unsupported claims caught | 4 / 12 |
| Correct claims wrongly flagged | 2 / 16 |
| Mean time per claim | 21 ms |

## Similarity + NLI judge

| Metric | Value |
|---|---|
| Accuracy | 84.1% |
| Hallucination precision (flagged claims that were really wrong) | 81.8% |
| Hallucination recall (wrong claims that were flagged) | 96.4% |
| F1 | 0.89 |
| Contradictions caught | 15 / 16 |
| Unsupported claims caught | 12 / 12 |
| Correct claims wrongly flagged | 6 / 16 |
| Mean time per claim | 407 ms |

## Per-claim results (similarity + NLI)

| Gold | Verdict | Similarity | Claim |
|---|---|---|---|
| SUPPORTED | SUPPORTED | 0.874 | Travel reimbursement can be claimed up to 30 days after the trip. |
| SUPPORTED | SUPPORTED | 0.667 | The finance head must approve claims over Rs. 50,000. |
| SUPPORTED | UNSUPPORTED **(miss)** | 0.603 | Receipts have to be uploaded in PDF format on the expense portal. |
| SUPPORTED | CONTRADICTED **(miss)** | 0.753 | For trips shorter than 500 km, employees must travel by train. |
| CONTRADICTED | CONTRADICTED | 0.91 | Employees can claim travel reimbursement within 60 days of the trip. |
| CONTRADICTED | UNSUPPORTED | 0.646 | Claims above Rs. 50,000 do not need any approval. |
| CONTRADICTED | UNSUPPORTED | 0.563 | Receipts must be uploaded as JPEG images. |
| CONTRADICTED | CONTRADICTED | 0.766 | Economy class air travel is allowed for trips shorter than 500 km. |
| UNSUPPORTED | CONTRADICTED | 0.759 | Employees receive a daily food allowance of Rs. 1,000 during trips. |
| UNSUPPORTED | CONTRADICTED | 0.652 | Hotel bookings must be made through the company travel desk. |
| UNSUPPORTED | UNSUPPORTED | 0.624 | International travel requires approval from the CEO. |
| SUPPORTED | SUPPORTED | 0.871 | Each full-time employee gets 24 paid annual leave days per year. |
| SUPPORTED | SUPPORTED | 0.843 | Employees may carry up to 8 unused leave days into the next year. |
| SUPPORTED | SUPPORTED | 0.774 | A medical certificate is needed for sick leave longer than 3 consecutive days. |
| SUPPORTED | CONTRADICTED **(miss)** | 0.766 | Leave must be requested at least a week in advance unless it is an emergency. |
| CONTRADICTED | CONTRADICTED | 0.886 | Full-time employees receive 30 days of paid annual leave per year. |
| CONTRADICTED | CONTRADICTED | 0.754 | Unused leave days cannot be carried forward to the next year. |
| CONTRADICTED | CONTRADICTED | 0.772 | A medical certificate is required only for sick leave longer than 10 days. |
| CONTRADICTED | CONTRADICTED | 0.689 | Leave requests can be submitted on the same day without notice. |
| UNSUPPORTED | CONTRADICTED | 0.741 | Employees get 26 weeks of paid maternity leave. |
| UNSUPPORTED | CONTRADICTED | 0.753 | Unused leave is paid out in cash at the end of the year. |
| UNSUPPORTED | CONTRADICTED | 0.839 | Part-time employees receive 12 days of annual leave. |
| SUPPORTED | SUPPORTED | 0.66 | Passwords need a minimum of 12 characters. |
| SUPPORTED | SUPPORTED | 0.679 | Passwords are changed every 90 days. |
| SUPPORTED | UNSUPPORTED **(miss)** | 0.63 | MFA is required for VPN and email. |
| SUPPORTED | CONTRADICTED **(miss)** | 0.724 | A lost device must be reported to the IT helpdesk within one day. |
| CONTRADICTED | CONTRADICTED | 0.7 | Passwords must be at least 8 characters long. |
| CONTRADICTED | UNSUPPORTED | 0.633 | Multi-factor authentication is optional for email access. |
| CONTRADICTED | CONTRADICTED | 0.681 | Company laptops do not need disk encryption. |
| CONTRADICTED | SUPPORTED **(miss)** | 0.677 | Lost devices must be reported within 7 days. |
| UNSUPPORTED | UNSUPPORTED | 0.607 | Employees may not install personal software on company laptops. |
| UNSUPPORTED | UNSUPPORTED | 0.54 | Security awareness training is held every quarter. |
| UNSUPPORTED | UNSUPPORTED | 0.622 | USB storage devices are blocked on all company computers. |
| SUPPORTED | SUPPORTED | 0.913 | Unused products can be returned within 15 days of delivery for a full refund. |
| SUPPORTED | SUPPORTED | 0.76 | Refunds reach the original payment method within 7 working days. |
| SUPPORTED | CONTRADICTED **(miss)** | 0.693 | Gift cards cannot be returned. |
| SUPPORTED | SUPPORTED | 0.791 | Shipping charges are refunded when the product arrives damaged. |
| CONTRADICTED | CONTRADICTED | 0.866 | Customers can return unused products within 30 days of delivery. |
| CONTRADICTED | CONTRADICTED | 0.691 | Refunds are issued as store credit instead of to the original payment method. |
| CONTRADICTED | CONTRADICTED | 0.728 | Digital downloads are eligible for return. |
| CONTRADICTED | CONTRADICTED | 0.726 | Shipping charges are always refunded. |
| UNSUPPORTED | CONTRADICTED | 0.718 | Customers must pay a 10% restocking fee on returns. |
| UNSUPPORTED | CONTRADICTED | 0.692 | Returns can be dropped off at any partner store. |
| UNSUPPORTED | CONTRADICTED | 0.693 | Refund requests are handled by a dedicated support team available 24/7. |

## What the numbers say

1. **Embedding similarity cannot detect contradictions.** A contradicted claim usually scores *higher* than a correct paraphrase because it reuses the source's exact words ("within 60 days of the trip" scored 0.91 against the source). Similarity alone caught 3 of 16 contradictions.
2. **The NLI stage fixes recall.** With llama3.2 as the judge, 15 of 16 contradictions and all 12 unsupported claims were flagged (recall 25.0% to 96.4%, F1 0.38 to 0.89).
3. **The cost is false alarms and latency.** The 3B judge flagged 6 of 16 correct claims. Every one of them needs a small inference step: "a week" = 7 days, "one day" = 24 hours, "MFA" = multi-factor authentication, "gift cards cannot be returned" from a sentence that lists two exclusions. It also adds about 0.4 s per sentence on a laptop.
4. **The similarity threshold was not tuned on this set.** 0.65 was chosen beforehand on separate example sentences; two correct claims fell just under it because each source here is one long chunk, which dilutes similarity.

## Next steps

- Replace the 3B chat model with a dedicated NLI cross-encoder (for example a DeBERTa MNLI model) or a larger judge, and re-run this set.
- Chunk by sentence or small window so similarity is computed against focused evidence.
- Since flagged answers go to human review rather than being deleted, a high-recall, lower-precision setting is the safer default for this use case.
