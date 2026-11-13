// Entry form: type in a receipt or bill, or check the fields read from an
// uploaded document, then save it to the backend (POST /api/receipts or
// POST /api/bills). Loaded after app.js so it can reuse showToast().
// app.js opens it prefilled after an upload via window.openEntryForm(...).
(() => {
  const dialog = document.getElementById("entry-dialog");
  const form = document.getElementById("entry-form");
  const receiptFields = document.getElementById("entry-receipt-fields");
  const billFields = document.getElementById("entry-bill-fields");
  const dateLabel = document.getElementById("entry-date-label");
  const errorBox = document.getElementById("entry-error");
  const noteBox = document.getElementById("entry-note");
  const categorySelect = form.elements.category;

  // Extra fields from an uploaded document (OCR text, file name), saved with the entry.
  let extra = {};

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

  function showNote(message) {
    noteBox.textContent = message;
    noteBox.hidden = !message;
  }

  // Guess the bill type from the provider name, e.g. Meralco -> electricity.
  function guessBillType(merchant) {
    const name = (merchant || "").toLowerCase();
    if (/meralco|electric|power/.test(name)) return "electricity";
    if (/water|maynilad/.test(name)) return "water";
    if (/pldt|globe|converge|smart|sky|internet|fiber/.test(name)) return "internet";
    return "other";
  }

  // prefill: fields read from a document (see ExtractedDocument), or nothing for manual entry.
  // note: a message shown at the top of the form.
  async function open(prefill = null, note = "") {
    form.reset();
    extra = {};
    form.elements.date.value = new Date().toLocaleDateString("en-CA"); // today, as yyyy-mm-dd
    showError("");
    try {
      await loadCategories();
    } catch (error) {
      showError("Could not reach the server. Is the backend running?");
    }
    if (prefill) {
      const f = form.elements;
      f.type.value = prefill.type === "BILL" ? "bill" : "receipt";
      if (prefill.merchant) f.merchant.value = prefill.merchant;
      if (prefill.date) f.date.value = prefill.date;
      if (prefill.total != null) f.amount.value = prefill.total;
      if (prefill.category && [...categorySelect.options].some(o => o.value === prefill.category)) {
        f.category.value = prefill.category;
      }
      f.billType.value = guessBillType(prefill.merchant);
      extra = { rawText: prefill.rawText, confidence: prefill.confidence,
                fileName: prefill.fileName, contentType: prefill.contentType };
    }
    updateType();
    showNote(note);
    dialog.showModal();
    form.elements.merchant.focus();
  }

  async function save(event) {
    event.preventDefault();
    if (!form.reportValidity()) return; // browser highlights missing fields

    const f = form.elements;
    const isBill = f.type.value === "bill";
    const url = isBill ? "/api/bills" : "/api/receipts";
    const fields = isBill
      ? { merchant: f.merchant.value.trim(), type: f.billType.value, billingDueDate: f.date.value,
          amountToPay: f.amount.value, paid: f.paid.checked }
      : { merchant: f.merchant.value.trim(), receiptDate: f.date.value,
          amountPaid: f.amount.value, category: f.category.value };
    const body = { ...extra, ...fields };

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
      // Refresh the document list (reloadDocuments is defined in app.js).
      if (typeof reloadDocuments === "function") reloadDocuments().catch(err => console.error(err));
    } catch (error) {
      showError("Could not reach the server. Is the backend running?");
    }
  }

  window.openEntryForm = open;
  document.getElementById("manual-entry-open").addEventListener("click", () => open());
  document.getElementById("entry-close").addEventListener("click", () => dialog.close());
  document.getElementById("entry-cancel").addEventListener("click", () => dialog.close());
  form.elements.type.addEventListener("change", updateType);
  form.addEventListener("submit", save);
})();
