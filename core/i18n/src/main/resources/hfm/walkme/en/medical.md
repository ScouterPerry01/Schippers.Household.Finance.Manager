# Medical expenses and claims
@about: Add your health plans, record each expense, follow its claims, and get the figures for the tax credit.

## Open Medical claims {#open}
@screen: MEDICAL MedicalTab.PLANS
@done: screen
@manual: medical#overview

In the menu, open **Home and family**, then **Medical claims**. Start with your plans on the **Plans** tab.

## Add a plan {#add-plan}
@screen: MEDICAL MedicalTab.PLANS
@target: medical.addPlan
@manual: medical#plan-form

Click **Add a plan**. Choose the **Kind of plan**, such as **Group health (employer)** or **Dental**, the **Insurer** and the numbers on the plan card.

## Who it covers {#order}
@screen: MEDICAL MedicalTab.PLANS
@manual: medical#coverage-order

For each person, choose **pays first**, **pays second** or **Not covered**. When spouses each have a plan, each plan pays first for its own member and second for the other. Save the plan.

## What it pays {#coverage}
@screen: MEDICAL MedicalTab.PLANS
@target: medical.addCoverage
@manual: medical#coverage-form

Click **Add coverage** for each kind of care in your benefit booklet: the **Kind of care**, the **Percentage paid**, the deductible and the yearly maximum. A plan pays only for the kinds of care it has a line for.

## Record an expense {#expense}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.addExpense
@manual: medical#expense-form

On the **Expenses and claims** tab, click **Add an expense**. Choose the **Person treated** and the **Kind of care**, the date of service and the **Cost**, and keep **Counts for the medical expense tax credit** ticked unless the CRA does not accept it. Click **Save**.

## Send the claim {#send}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.submit
@manual: medical#send-claim

The expense says which plan to claim first, by when, and about how much should come back. Once you have sent the claim, click **Send to** with the plan's name, and enter the **Amount claimed**.

## When the plan pays {#paid}
@screen: MEDICAL MedicalTab.EXPENSES
@target: medical.recordPayment
@manual: medical#record-payment

Click **Record the payment** with what the plan paid, or 0 if it refused. If a second plan covers the person, it is proposed next for what is left.

## The tax credit {#credit}
@screen: REPORTS
@manual: medical#tax-credit

The Medical expenses report under **Reports** gathers what was out of pocket, finds the best 12-month period and says which spouse should claim. The year-end tax package uses the same figures.
