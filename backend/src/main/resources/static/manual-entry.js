// Manual entry: a form for typing in a receipt or bill and saving it to the
// backend (POST /api/receipts or POST /api/bills). Loaded after app.js so it
// can reuse showToast().
(() => {
  const dialog = document.getElementById("entry-dialog");
  const form = document.getElementById("entry-form");
  const receiptFields = document.getElementById("entry-receipt-fields");
  const billFields = document.getElementById("entry-bill-fields");
  const dateLabel = document.getElementById("entry-date-label");
  const errorBox = document.getElementById("entry-error");
  const categorySelect = form.elements.category;

  // Fill the category dropdown from the backend once, so the list is never hardcoded.
  async function loadCategories() {
    if (categorySelect.options.length) return;
    const response = await fetch("/api/categories");
    if (!response.ok) throw new Error("Could not load categories.");
    for (const name of await response.json()) {
      // FOOD -> "Food" for display; the value sent to the API stays FOOD.
      categorySelect.add(new Option(name.charAt(0) + name.slice(1).toLowerCase(), name));
    }
  }

  // Show the fields for the chosen type. A disabled fieldset is skipped by
  // form validation, so hidden required fields don't block saving.
  function updateType() {
    const isBill = form.elements.type.value === "bill";
    receiptFields.hidden = receiptFields.disabled = isBill;
    billFields.hidden = billFields.disabled = !isBill;
    dateLabel.textContent = isBill ? "Due date" : "Date";
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = !message;
  }

  async function open() {
    form.reset();
    form.elements.date.value = new Date().toLocaleDateString("en-CA"); // today, as yyyy-mm-dd
    updateType();
    showError("");
    try {
      await loadCategories();
    } catch (error) {
      showError("Could not reach the server. Is the backend running?");
    }
    dialog.showModal();
    form.elements.merchant.focus();
  }

  async function save(event) {
    event.preventDefault();
    if (!form.reportValidity()) return; // browser highlights missing fields

    const f = form.elements;
    const isBill = f.type.value === "bill";
    const url = isBill ? "/api/bills" : "/api/receipts";
    const body = isBill
      ? { merchant: f.merchant.value.trim(), type: f.billType.value, billingDueDate: f.date.value,
          amountToPay: f.amount.value, paid: f.paid.checked }
      : { merchant: f.merchant.value.trim(), receiptDate: f.date.value,
          amountPaid: f.amount.value, category: f.category.value };

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      if (!response.ok) {
        // Validation errors come back as {"errors":{"field":"message"}}.
        const data = await response.json().catch(() => ({}));
        const details = data.errors ? Object.entries(data.errors).map(([k, v]) => `${k} ${v}`).join("; ") : "";
        showError(details ? `Please check: ${details}.` : `Could not save (error ${response.status}).`);
        return;
      }
      dialog.close();
      showToast(isBill ? "Bill saved." : "Receipt saved.");
    } catch (error) {
      showError("Could not reach the server. Is the backend running?");
    }
  }

  document.getElementById("manual-entry-open").addEventListener("click", open);
  document.getElementById("entry-close").addEventListener("click", () => dialog.close());
  document.getElementById("entry-cancel").addEventListener("click", () => dialog.close());
  form.elements.type.addEventListener("change", updateType);
  form.addEventListener("submit", save);
})();
