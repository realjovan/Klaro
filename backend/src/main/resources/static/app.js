    const docs = {
      electric: {
        id: "electric", category: "Electricity bill", date: "2026-10-28", sample: true, isSaved: false, reviewed: true,
        title: "Meralco_bill_Oct.pdf", kind: "Electricity bill · Added Oct 28",
        text: "Meralco — Statement of Account\nBilling period: Oct 01–Oct 28, 2026\nTotal amount due: ₱2,486.50\nPayment due: November 18, 2026\nAccount holder: Maria Reyes, Quezon City",
        answer: '<strong>Here’s the short version:</strong> Your electricity bill is <strong>₱2,486.50</strong>, and it needs to be paid by <strong>November 18</strong>.<ul><li>Pay by the due date to avoid a late charge.</li><li>If the amount looks wrong, compare it with your meter reading or contact Meralco.</li></ul>',
        kindLabel: "Electricity bill"
      },
      school: {
        id: "school", category: "School form", date: "2026-10-21", sample: true, isSaved: false, reviewed: false,
        title: "School_enrollment_form.jpg", kind: "School form · Added Oct 21",
        text: "School enrollment form\nLearner name: ____________________\nGrade level: ____________________\nParent/guardian contact number: ____________________\nPlease submit a copy of the learner's birth certificate and latest report card.",
        answer: '<strong>This is a school enrollment form.</strong> It asks for the learner’s name, grade level, and a parent or guardian’s phone number. You also need to attach a birth certificate and the latest report card.<ul><li>Fill in the blank fields.</li><li>Ask the school if photocopies are accepted.</li></ul>',
        kindLabel: "School form"
      },
      medicine: {
        id: "medicine", category: "Medical note", date: "2026-10-17", sample: true, isSaved: false, reviewed: true,
        title: "Reseta_Nanay.jpg", kind: "Medical note · Added Oct 17",
        text: "Medical note\nPatient: ____________________\nMedicine: ____________________\nDosage and schedule: Please follow the instructions provided by your health professional.\nIf you are unsure about a medicine or dose, ask your doctor or pharmacist.",
        answer: '<strong>This looks like a medical note.</strong> The medicine and dose are not readable in this sample, so I can’t safely interpret them. Please confirm the exact medicine and schedule with the doctor or pharmacist before taking anything.',
        kindLabel: "Medical note"
      },
      barangay: {
        id: "barangay", category: "Barangay notice", date: "2026-10-12", sample: true, isSaved: false, reviewed: true,
        title: "Barangay_clearance_requirements.pdf", kind: "Barangay notice · Added Oct 12",
        text: "Barangay clearance requirements\nBring one valid government-issued ID and proof of residency.\nApplicants must be residents of the barangay.\nVisit the barangay hall during office hours to submit your application.\nPlease confirm current fees and requirements with your barangay.",
        answer: '<strong>This notice is about applying for a barangay clearance.</strong> It says to bring a valid ID and proof that you live in the barangay. Apply at the barangay hall during office hours.<ul><li>Confirm the current fee and requirements before you go.</li><li>Bring originals and a photocopy if you have one.</li></ul>',
        kindLabel: "Barangay notice"
      }
    };

    let activeDocument = "electric";
    let documents = Object.values(docs);
    const viewRoutes = { Overview: "overview", "All documents": "documents", Saved: "saved", "Needs review": "review" };
    const routeViews = Object.fromEntries(Object.entries(viewRoutes).map(([view, route]) => [route, view]));
    let activeView = routeViews[window.location.hash.slice(1)] || "Overview";
    if (window.location.hash !== `#${viewRoutes[activeView]}`) {
      window.history.replaceState({ view: activeView }, "", `${window.location.pathname}${window.location.search}#${viewRoutes[activeView]}`);
    }
    let currentObjectUrl = null;
    let appReady;
    let language = "en";
    const fileInput = document.getElementById("file-input");
    const dropZone = document.getElementById("drop-zone");
    const toast = document.getElementById("toast");
    let toastTimer;
    const themeToggle = document.getElementById("theme-toggle");
    const savedTheme = localStorage.getItem("klaro-theme");
    const initialTheme = savedTheme === "light" || savedTheme === "dark"
      ? savedTheme
      : (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");

    function setTheme(theme) {
      document.documentElement.dataset.theme = theme;
      const isDark = theme === "dark";
      themeToggle.setAttribute("aria-label", `Switch to ${isDark ? "light" : "dark"} mode`);
      themeToggle.setAttribute("title", `Switch to ${isDark ? "light" : "dark"} mode`);
      themeToggle.setAttribute("aria-pressed", String(isDark));
      document.querySelector('meta[name="theme-color"]').content = isDark ? "#091426" : "#eef5ff";
    }

    setTheme(initialTheme);
    themeToggle.addEventListener("click", () => {
      const nextTheme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      setTheme(nextTheme);
      localStorage.setItem("klaro-theme", nextTheme);
    });

    function showToast(message) {
      toast.textContent = message;
      toast.classList.add("show");
      window.clearTimeout(toastTimer);
      toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2800);
    }

    const databasePromise = new Promise((resolve, reject) => {
      const request = indexedDB.open("klaro-local-documents", 1);
      request.onupgradeneeded = () => request.result.createObjectStore("documents", { keyPath: "id" });
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });

    function databaseRequest(mode, operation) {
      return databasePromise.then(database => new Promise((resolve, reject) => {
        const transaction = database.transaction("documents", mode);
        const request = operation(transaction.objectStore("documents"));
        let result;
        request.onsuccess = () => { result = request.result; };
        request.onerror = () => reject(request.error);
        transaction.oncomplete = () => resolve(result);
        transaction.onerror = () => reject(transaction.error || new Error("Document storage transaction failed."));
        transaction.onabort = () => reject(transaction.error || new Error("Document storage transaction failed."));
      }));
    }

    const getAllDocuments = () => databaseRequest("readonly", store => store.getAll());
    const saveDocument = documentRecord => databaseRequest("readwrite", store => store.put(documentRecord));
    const removeDocument = id => databaseRequest("readwrite", store => store.delete(id));

    function updateOverview() {
      document.getElementById("stat-documents").textContent = String(documents.length);
      const reviewCount = documents.filter(doc => !doc.reviewed).length;
      const savedCount = documents.filter(doc => doc.isSaved).length;
      document.getElementById("stat-review").textContent = String(reviewCount);
      document.getElementById("stat-saved").textContent = String(savedCount);
      document.getElementById("nav-count").textContent = String(documents.length);
      document.getElementById("review-nav-count").textContent = String(reviewCount);
      document.getElementById("saved-nav-count").textContent = String(savedCount);
    }

    function getVisibleDocuments() {
      const query = document.getElementById("document-search").value.trim().toLocaleLowerCase();
      const category = document.getElementById("category-filter").value;
      return documents
        .filter(doc => activeView !== "Saved" || doc.isSaved)
        .filter(doc => activeView !== "Needs review" || !doc.reviewed)
        .filter(doc => !category || doc.category === category)
        .filter(doc => !query || `${doc.title} ${doc.category} ${doc.text}`.toLocaleLowerCase().includes(query))
        .sort((a, b) => b.date.localeCompare(a.date));
    }

    function renderDocuments() {
      const list = document.getElementById("document-list");
      list.replaceChildren();
      const titles = {
        Overview: ["Your documents", "A little more organized, one paper at a time."],
        "All documents": ["All documents", "Search, sort, and open everything in your local library."],
        Saved: ["Saved documents", "The papers you bookmarked for later."],
        "Needs review": ["Needs review", "Check the text yourself before you rely on it."]
      };
      document.getElementById("list-title").textContent = titles[activeView][0];
      document.getElementById("list-subtitle").textContent = titles[activeView][1];
      const hasFilters = document.getElementById("document-search").value || document.getElementById("category-filter").value;
      const viewAllButton = document.getElementById("view-all");
      viewAllButton.hidden = activeView !== "Overview" && !hasFilters;
      viewAllButton.textContent = activeView === "Overview" && !hasFilters ? "View all" : "Clear filters";
      const matchingDocuments = getVisibleDocuments();
      const visible = activeView === "Overview" && !hasFilters ? matchingDocuments.slice(0, 4) : matchingDocuments;
      if (!visible.length) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        const heading = document.createElement("strong");
        heading.textContent = activeView === "Saved" ? "No saved documents yet" : activeView === "Needs review" ? "All caught up" : "No documents found";
        const caption = document.createElement("span");
        caption.textContent = activeView === "Saved"
          ? "Save a document with the bookmark button to keep it here."
          : activeView === "Needs review"
            ? "You have reviewed every document in your library."
            : "Try another search or category, or add a document to get started.";
        empty.append(heading, caption);
        list.append(empty);
        return;
      }

      visible.forEach(doc => {
        const row = document.createElement("button");
        row.type = "button";
        row.className = `document-row${doc.id === activeDocument ? " selected" : ""}`;
        row.setAttribute("aria-pressed", String(doc.id === activeDocument));
        row.dataset.document = doc.id;
        const icon = document.createElement("span");
        icon.className = `file-icon ${doc.category === "School form" ? "blue" : doc.category === "Medical note" ? "gold" : doc.category === "Barangay notice" ? "green" : ""}`;
        icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><path d="M7 3.75h7.5L19 8.5v11.75H7A2 2 0 0 1 5 18.25v-12a2.5 2.5 0 0 1 2-2.5Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M14 4v5h5M8.5 12.5h7M8.5 16h7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';
        const main = document.createElement("span");
        main.className = "document-main";
        const name = document.createElement("span");
        name.className = "document-name";
        name.textContent = doc.title;
        const meta = document.createElement("span");
        meta.className = "document-meta";
        meta.textContent = `${doc.category} · ${new Date(`${doc.date}T12:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}`;
        main.append(name, meta);
        const status = document.createElement("span");
        status.className = `doc-status${doc.reviewed ? "" : " pending"}`;
        status.textContent = doc.reviewed ? (doc.isSaved ? "Saved" : "Ready") : "Review text";
        row.append(icon, main, status);
        row.addEventListener("click", () => selectDocument(doc.id));
        list.append(row);
      });
    }

    function updateDocumentLabel(doc) {
      document.getElementById("detail-title").textContent = doc ? doc.title : "No document selected";
      document.getElementById("detail-kind").textContent = doc ? `${doc.category} · Added ${new Date(`${doc.date}T12:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" })}` : "Add a document to get started";
      document.getElementById("chat-document").textContent = doc ? doc.title : "No document selected";
      document.getElementById("ocr-text").value = doc ? doc.text : "";
      document.getElementById("ocr-text").readOnly = true;
      document.getElementById("edit-ocr").innerHTML = '<svg viewBox="0 0 24 24" fill="none"><path d="m14 5 5 5M4 20l4.2-.8L19 8.4a2.1 2.1 0 0 0-3-3L5.2 16.2 4 20Z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg> Edit text';
      document.getElementById("edit-ocr").disabled = !doc;
      document.getElementById("doc-category").disabled = !doc;
      if (doc) document.getElementById("doc-category").value = doc.category;
      document.getElementById("save-doc").disabled = !doc;
      document.getElementById("share-doc").disabled = !doc;
      document.getElementById("rename-doc").disabled = !doc;
      document.getElementById("delete-doc").disabled = !doc;
      document.getElementById("save-doc").setAttribute("aria-pressed", String(Boolean(doc && doc.isSaved)));
      document.getElementById("save-doc").title = doc && doc.isSaved ? "Remove from saved" : "Save document";
      document.getElementById("save-doc").setAttribute("aria-label", doc && doc.isSaved ? "Remove from saved" : "Save document");
      document.getElementById("char-count").textContent = `${doc ? doc.text.length : 0} characters`;
      const samplePreview = document.getElementById("sample-preview");
      const filePreview = document.getElementById("file-preview");
      samplePreview.style.display = doc && doc.id === "electric" && doc.sample ? "" : "none";
      filePreview.replaceChildren();
      filePreview.classList.remove("visible");
      if (currentObjectUrl) {
        URL.revokeObjectURL(currentObjectUrl);
        currentObjectUrl = null;
      }
      if (doc && doc.file) {
        currentObjectUrl = URL.createObjectURL(doc.file);
        filePreview.classList.add("visible");
        if (doc.file.type.startsWith("image/")) {
          const image = document.createElement("img");
          image.src = currentObjectUrl;
          image.alt = `Uploaded document: ${doc.title}`;
          filePreview.append(image);
        } else if (doc.file.type === "application/pdf") {
          const frame = document.createElement("iframe");
          frame.src = currentObjectUrl;
          frame.title = `PDF preview: ${doc.title}`;
          filePreview.append(frame);
        } else {
          const textPreview = document.createElement("pre");
          textPreview.className = "empty-state";
          textPreview.textContent = doc.text || "No text has been added.";
          filePreview.append(textPreview);
        }
      } else if (doc && !(doc.id === "electric" && doc.sample)) {
        filePreview.classList.add("visible");
        const textPreview = document.createElement("pre");
        textPreview.className = "empty-state";
        textPreview.textContent = doc.text;
        filePreview.append(textPreview);
      }
    }

    function renderChat(doc) {
      const messages = document.getElementById("chat-messages");
      messages.replaceChildren();
      const row = document.createElement("div");
      row.className = "assistant-row";
      const icon = document.createElement("span");
      icon.className = "mini-assistant";
      icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><path d="M12 3.5v3M12 17.5v3M3.5 12h3M17.5 12h3M6 6l2 2m8 8 2 2m0-12-2 2m-8 8-2 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.8"/></svg>';
      const message = document.createElement("div");
      message.className = "message";
      message.id = "greeting-message";
      message.textContent = doc
        ? (doc.sample ? doc.answer.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").replace(/\s+([.,!?])/g, "$1").trim() : (doc.text ? summarizeText(doc.text) : "This document has no extracted text yet. Lumen OCR is not connected. You can enter text below to use the local helper."))
        : "Choose a document to see its text and ask questions.";
      row.append(icon, message);
      messages.append(row);
      const note = document.createElement("div");
      note.className = "confidence-note";
      note.textContent = "Local demo helper only. It does not use AI; verify important details against the original document.";
      messages.append(note);
      document.getElementById("chat-input").disabled = !doc;
      document.getElementById("suggestions").hidden = !doc;
      document.getElementById("chat-input").placeholder = language === "tl" ? "Magtanong tungkol sa dokumentong ito…" : "Ask about the text in this document…";
    }

    function renderApp() {
      document.querySelectorAll(".nav-item").forEach(nav => {
        const selected = nav.dataset.view === activeView;
        nav.classList.toggle("active", selected);
        if (selected) nav.setAttribute("aria-current", "page");
        else nav.removeAttribute("aria-current");
      });
      document.getElementById("breadcrumb-current").textContent = activeView;
      const isOverview = activeView === "Overview";
      document.querySelector(".welcome").hidden = !isOverview;
      document.getElementById("overview-stats").hidden = !isOverview;
      document.getElementById("page-header").hidden = isOverview;
      document.getElementById("workspace").dataset.view = activeView;
      document.querySelector(".workspace > section").setAttribute("aria-label", isOverview ? "Your recent documents" : `${activeView} documents`);
      const pageContent = {
        "All documents": ["Your document library", "Manage every bill, form, notice, and record saved on this device."],
        Saved: ["Saved for later", "Keep important papers close at hand. Open a document to review its details or remove its bookmark."],
        "Needs review": ["Check these documents", "Review text and correct important names, dates, and amounts before relying on them."]
      };
      if (!isOverview) {
        document.getElementById("page-title").textContent = pageContent[activeView][0];
        document.getElementById("page-description").textContent = pageContent[activeView][1];
        document.getElementById("page-kicker").textContent = activeView === "Needs review" ? "Your attention" : "Your workspace";
      }
      updateOverview();
      renderDocuments();
      const visible = getVisibleDocuments();
      const doc = visible.find(item => item.id === activeDocument) || visible[0] || null;
      activeDocument = doc ? doc.id : null;
      updateDocumentLabel(doc);
      renderChat(doc);
      updateToday();
    }

    function updateToday() {
      const now = new Date();
      document.getElementById("today-label").textContent = now.toLocaleDateString(undefined, {
        weekday: "long", month: "long", day: "numeric"
      });
      const hour = now.getHours();
      document.getElementById("greeting-text").textContent =
        `${hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"}, Maria`;
      document.getElementById("greeting-icon").textContent = hour < 18 ? "☀️" : "🌙";
    }

    function selectDocument(id) {
      const doc = documents.find(item => item.id === id);
      if (!doc) return;
      if (!document.getElementById("ocr-text").readOnly && !window.confirm("Discard your unsaved text changes? Save the correction first to keep them.")) return;
      activeDocument = id;
      renderApp();
      showToast(`${doc.category} opened`);
    }

    // ---- Backend connection ----
    // Receipts and bills are loaded from the API (GET /api/receipts, /api/bills)
    // and converted into the document shape the list and detail panel already use.
    const peso = amount => `₱${Number(amount).toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    const categoryLabel = name => name ? name.charAt(0) + name.slice(1).toLowerCase() : "Other"; // FOOD -> Food

    function receiptToDocument(r) {
      const category = categoryLabel(r.category);
      return {
        id: `receipt-${r.id}`, apiPath: `/api/receipts/${r.id}`,
        title: r.merchant, category, kindLabel: category, date: r.receiptDate || (r.createdAt || "").slice(0, 10),
        text: [`Merchant: ${r.merchant}`, `Date: ${r.receiptDate}`, `Amount paid: ${peso(r.amountPaid)}`, `Category: ${category}`, r.rawText || ""]
          .filter(Boolean).join("\n"),
        reviewed: !r.needsReview, isSaved: false, sample: false
      };
    }

    function billToDocument(b) {
      return {
        id: `bill-${b.id}`, apiPath: `/api/bills/${b.id}`,
        title: b.merchant, category: "Utility bill", kindLabel: "Utility bill", date: b.billingDueDate || (b.createdAt || "").slice(0, 10),
        text: [`Provider: ${b.merchant}`, `Bill type: ${b.type || "—"}`, `Due date: ${b.billingDueDate}`, `Amount due: ${peso(b.amountToPay)}`,
          `Status: ${b.paid ? "Paid" : "Unpaid"}`, b.rawText || ""].filter(Boolean).join("\n"),
        reviewed: !b.needsReview, isSaved: false, sample: false
      };
    }

    // Rebuild the category filter from the categories actually present.
    function updateCategoryFilter() {
      const filter = document.getElementById("category-filter");
      const current = filter.value;
      const names = [...new Set(documents.map(doc => doc.category))].sort();
      filter.replaceChildren(new Option("All categories", ""), ...names.map(name => new Option(name, name)));
      filter.value = names.includes(current) ? current : "";
    }

    // Fetch everything from the backend and redraw. Also called by manual-entry.js after a save.
    async function reloadDocuments() {
      const [receipts, bills] = await Promise.all(["/api/receipts", "/api/bills"].map(async url => {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`${url} returned ${response.status}`);
        return response.json();
      }));
      documents = [...receipts.map(receiptToDocument), ...bills.map(billToDocument)];
      if (!documents.some(doc => doc.id === activeDocument)) activeDocument = documents[0] ? documents[0].id : null;
      updateCategoryFilter();
      renderApp();
    }

    async function initializeApp() {
      try {
        await reloadDocuments();
      } catch (error) {
        console.error("Could not load documents from the backend.", error);
        documents = [];
        renderApp();
        showToast("Could not reach the server. Is the backend running?");
      }
    }

    function summarizeText(text) {
      const lines = text.split(/\n+/).map(line => line.trim()).filter(Boolean);
      const summary = lines.slice(0, 3).join(" · ");
      return summary ? `This document contains: ${summary}` : "No readable text is available yet. Add or correct text below.";
    }

    function inferCategory(fileName) {
      const name = fileName.toLocaleLowerCase();
      if (/school|enroll|report.?card|student/.test(name)) return "School form";
      if (/reseta|medical|medicine|doctor|clinic|health/.test(name)) return "Medical note";
      if (/barangay|clearance|government|permit/.test(name)) return "Barangay notice";
      if (/bill|meralco|electric|receipt|water|internet/.test(name)) return "Electricity bill";
      return "Other";
    }

    function classifyFile(file) {
      const extension = file.name.split(".").pop().toLocaleLowerCase();
      // The types Lumen OCR can read.
      const supported = ["image/png", "image/jpeg", "image/webp", "image/tiff", "application/pdf"].includes(file.type)
        || ["png", "jpg", "jpeg", "webp", "tif", "tiff", "pdf"].includes(extension);
      if (!supported) throw new Error("Choose a photo (PNG, JPEG, WebP, TIFF) or a PDF.");
      if (file.size > 10 * 1024 * 1024) throw new Error("File is too large. The limit is 10 MB.");
    }

    function setView(view, addHistoryEntry) {
      if (!viewRoutes[view]) return;
      activeView = view;
      if (addHistoryEntry && window.location.hash !== `#${viewRoutes[view]}`) {
        window.history.pushState({ view }, "", `#${viewRoutes[view]}`);
      }
      renderApp();
      window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function confirmDiscardTextEdits() {
      return document.getElementById("ocr-text").readOnly ||
        window.confirm("Discard your unsaved text changes? Save the correction first to keep them.");
    }

    function navigateToView(view) {
      if (!confirmDiscardTextEdits()) return;
      setView(view, true);
    }

    document.querySelectorAll(".nav-item").forEach(item => {
      item.addEventListener("click", () => navigateToView(item.dataset.view));
    });

    document.querySelectorAll("[data-stat-view]").forEach(card => {
      card.addEventListener("click", () => navigateToView(card.dataset.statView));
    });

    function openPicker() { fileInput.click(); }
    document.getElementById("upload-top").addEventListener("click", openPicker);
    document.getElementById("page-upload").addEventListener("click", openPicker);
    document.getElementById("choose-file").addEventListener("click", openPicker);
    document.getElementById("document-search").addEventListener("input", renderApp);
    document.getElementById("category-filter").addEventListener("change", renderApp);
    document.getElementById("view-all").addEventListener("click", () => {
      if (activeView === "Overview" && !document.getElementById("document-search").value && !document.getElementById("category-filter").value) {
        navigateToView("All documents");
        return;
      }
      document.getElementById("document-search").value = "";
      document.getElementById("category-filter").value = "";
      renderApp();
    });

    window.addEventListener("popstate", () => {
      const view = routeViews[window.location.hash.slice(1)] || "Overview";
      if (!confirmDiscardTextEdits()) {
        window.history.forward();
        return;
      }
      setView(view, false);
    });

    // Upload flow: send the file to the backend, which reads it with Lumen OCR
    // (POST /api/receipts/extract), then open the entry form prefilled so the
    // user can check the fields before saving. If reading fails, the form opens
    // empty for manual entry. One file at a time.
    async function addFiles(files) {
      await appReady;
      const file = files[0];
      fileInput.value = "";
      if (!file) return;
      try {
        classifyFile(file);
      } catch (error) {
        showToast(error.message);
        return;
      }
      document.body.style.cursor = "progress";
      showToast("Reading your document… this can take a few seconds.");
      try {
        const upload = new FormData();
        upload.append("file", file);
        const response = await fetch("/api/receipts/extract", { method: "POST", body: upload });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
          window.openEntryForm(null, `${data.error || "Could not read this document."} Please enter the details yourself.`);
          return;
        }
        window.openEntryForm({ ...data, fileName: file.name, contentType: file.type },
          "We read these details from your document. Check each one before saving.");
      } catch (error) {
        console.error("Could not read the document.", error);
        window.openEntryForm(null, "Could not reach the server to read this document. Please enter the details yourself.");
      } finally {
        document.body.style.cursor = "";
      }
    }

    fileInput.addEventListener("change", () => {
      if (fileInput.files && fileInput.files.length) addFiles(Array.from(fileInput.files));
    });

    ["dragenter", "dragover"].forEach(eventName => dropZone.addEventListener(eventName, event => {
      event.preventDefault();
      dropZone.classList.add("dragging");
    }));
    ["dragleave", "drop"].forEach(eventName => dropZone.addEventListener(eventName, event => {
      event.preventDefault();
      dropZone.classList.remove("dragging");
    }));
    dropZone.addEventListener("drop", event => {
      if (event.dataTransfer && event.dataTransfer.files.length) {
        addFiles(Array.from(event.dataTransfer.files));
      }
    });

    document.getElementById("edit-ocr").addEventListener("click", async event => {
      const field = document.getElementById("ocr-text");
      if (field.readOnly) {
        field.readOnly = false;
        field.focus();
        event.currentTarget.innerHTML = "Save correction";
      } else {
        const doc = documents.find(item => item.id === activeDocument);
        if (!doc) return;
        doc.text = field.value;
        doc.reviewed = true;
        doc.sample = false;
        try {
          await saveDocument(doc);
          field.readOnly = true;
          renderApp();
          showToast("Corrected text saved on this device.");
        } catch (error) {
          console.error("Could not save the text correction.", error);
          showToast("Could not save your correction. Please try again.");
        }
      }
    });
    function updateCharCount() { document.getElementById("char-count").textContent = `${document.getElementById("ocr-text").value.length} characters`; }
    document.getElementById("ocr-text").addEventListener("input", updateCharCount);

    document.getElementById("doc-category").addEventListener("change", async event => {
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc) return;
      doc.category = event.currentTarget.value;
      doc.kind = `${doc.category} · Added today`;
      doc.sample = false;
      try {
        await saveDocument(doc);
        renderApp();
        showToast("Document category updated.");
      } catch (error) {
        console.error("Could not update the document category.", error);
        showToast("Could not save the category. Please try again.");
      }
    });

    const tagalogSummaries = {
      electric: "Maikling paliwanag: Ang bill mo sa kuryente ay ₱2,486.50, at kailangang bayaran bago ang Nobyembre 18. Magbayad bago ang takdang petsa upang maiwasan ang dagdag-singil.",
      school: "Enrollment form ito ng paaralan. Hinihingi nito ang pangalan ng mag-aaral, baitang, numero ng telepono ng magulang o guardian, birth certificate, at pinakabagong report card.",
      medicine: "Mukhang tala ito tungkol sa gamot. Hindi malinaw dito ang pangalan at dosis ng gamot. Kumpirmahin muna sa doktor o parmasyutiko bago uminom.",
      barangay: "Mga kailangan ito para sa barangay clearance. Magdala ng valid ID at patunay ng tirahan, at kumpirmahin muna sa barangay ang bayad at kasalukuyang requirements."
    };

    document.querySelectorAll("[data-language]").forEach(button => {
      button.addEventListener("click", () => {
        language = button.dataset.language;
        document.querySelectorAll("[data-language]").forEach(option => {
          const active = option === button;
          option.classList.toggle("active", active);
          option.setAttribute("aria-pressed", String(active));
        });
        const doc = documents.find(item => item.id === activeDocument);
        const greeting = document.getElementById("greeting-message");
        if (doc && doc.sample && language === "tl") greeting.textContent = tagalogSummaries[doc.id];
        else if (doc && doc.sample) greeting.textContent = doc.answer.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").replace(/\s+([.,!?])/g, "$1").trim();
        else if (doc) greeting.textContent = language === "tl"
          ? `Nabasa ko ang ${doc.text.split(/\n+/).filter(Boolean).length} linya sa dokumento. Tingnan ang orihinal at tiyakin ang mahahalagang detalye.`
          : summarizeText(doc.text);
        document.getElementById("chat-input").placeholder = language === "tl" ? "Magtanong tungkol sa dokumentong ito…" : "Ask about the text in this document…";
      });
    });

    function answerFromDocument(question, doc) {
      const sourceLines = doc.text.split(/\n+/).map(line => line.trim()).filter(Boolean);
      if (!sourceLines.length) {
        return language === "tl"
          ? "Wala pang text na mababasa sa dokumentong ito. Idagdag o i-paste ang text sa kahon sa kaliwa. Hindi konektado ang Lumen OCR."
          : "There is no text in this document yet. Add or paste text in the box on the left. Lumen OCR is not connected.";
      }
      const tokens = question.toLocaleLowerCase().split(/[^\p{L}\p{N}₱]+/u).filter(token => token.length > 2);
      const matches = sourceLines.filter(line => tokens.some(token => line.toLocaleLowerCase().includes(token)));
      const relevantLines = matches.length ? matches.slice(0, 4) : sourceLines.slice(0, 3);
      if (language === "tl") return `Batay sa text na nasa dokumento: ${relevantLines.join(" · ")}. Pakisuri ang orihinal bago kumilos.`;
      return `The document text ${matches.length ? "that may be relevant" : "available to review"}: ${relevantLines.join(" · ")}. Check the original document before acting.`;
    }

    function askQuestion(question) {
      const cleanQuestion = question.trim();
      if (!cleanQuestion) return;
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc) {
        showToast("Choose a document first.");
        return;
      }
      const messages = document.getElementById("chat-messages");
      const user = document.createElement("div");
      user.className = "user-message";
      user.textContent = cleanQuestion;
      messages.appendChild(user);
      const replyRow = document.createElement("div");
      replyRow.className = "assistant-row";
      const icon = document.createElement("span");
      icon.className = "mini-assistant";
      icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none"><path d="M12 3.5v3M12 17.5v3M3.5 12h3M17.5 12h3M6 6l2 2m8 8 2 2m0-12-2 2m-8 8-2 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.8"/></svg>';
      const message = document.createElement("div");
      message.className = "message";
      message.textContent = answerFromDocument(cleanQuestion, doc);
      const time = document.createElement("div");
      time.className = "message-time";
      time.textContent = "Local text lookup · No AI connected";
      message.appendChild(time);
      replyRow.append(icon, message);
      messages.append(user, replyRow);
      document.getElementById("suggestions").hidden = true;
      document.getElementById("chat-input").value = "";
      messages.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
    document.getElementById("chat-form").addEventListener("submit", event => {
      event.preventDefault();
      askQuestion(document.getElementById("chat-input").value);
    });
    document.querySelectorAll(".suggestion").forEach(button => {
      button.addEventListener("click", () => {
        if (button.textContent === "Explain in Tagalog") {
          document.querySelector('[data-language="tl"]').click();
          return;
        }
        askQuestion(button.textContent);
      });
    });

    document.getElementById("save-doc").addEventListener("click", async () => {
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc) return;
      doc.isSaved = !doc.isSaved;
      try {
        await saveDocument(doc);
        renderApp();
        showToast(doc.isSaved ? "Saved on this device." : "Removed from saved documents.");
      } catch (error) {
        doc.isSaved = !doc.isSaved;
        console.error("Could not update saved status.", error);
        showToast("Could not update saved status. Please try again.");
      }
    });
    document.getElementById("rename-doc").addEventListener("click", async () => {
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc) return;
      const title = window.prompt("Document name", doc.title);
      if (title === null) return;
      const newTitle = title.trim();
      if (!newTitle) {
        showToast("Document name cannot be empty.");
        return;
      }
      doc.title = newTitle.slice(0, 120);
      try {
        await saveDocument(doc);
        renderApp();
        showToast("Document renamed.");
      } catch (error) {
        console.error("Could not rename the document.", error);
        showToast("Could not save the new document name.");
      }
    });
    document.getElementById("delete-doc").addEventListener("click", async () => {
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc || !window.confirm(`Delete “${doc.title}”? This cannot be undone.`)) return;
      try {
        if (doc.apiPath) {
          // Backend record: delete it from the database.
          const response = await fetch(doc.apiPath, { method: "DELETE" });
          if (!response.ok && response.status !== 404) throw new Error(`Delete failed (${response.status})`);
        } else {
          await removeDocument(doc.id);
        }
        documents = documents.filter(item => item.id !== doc.id);
        activeDocument = documents[0] ? documents[0].id : null;
        renderApp();
        showToast("Document deleted.");
      } catch (error) {
        console.error("Could not delete the document.", error);
        showToast("Could not delete this document. Please try again.");
      }
    });
    document.getElementById("share-doc").addEventListener("click", async () => {
      const doc = documents.find(item => item.id === activeDocument);
      if (!doc) return;
      try {
        if (doc.file && navigator.canShare && navigator.canShare({ files: [doc.file] }) && navigator.share) {
          await navigator.share({ title: doc.title, files: [doc.file] });
        } else if (navigator.clipboard) {
          await navigator.clipboard.writeText(`${doc.title}\n\n${doc.text || "No text has been added."}`);
          showToast("Document text copied. The original file stays on this device.");
        } else showToast("Sharing is unavailable in this browser.");
      } catch (error) {
        if (error.name !== "AbortError") showToast("Could not share right now.");
      }
    });
    document.getElementById("help-button").addEventListener("click", () => {
      document.getElementById("settings-dialog").showModal();
      document.getElementById("storage-summary").textContent =
        `${documents.length} document${documents.length === 1 ? "" : "s"} stored in this browser. Files and text are not synced to another device.`;
    });
    document.getElementById("profile-button").addEventListener("click", () => document.getElementById("help-button").click());
    document.getElementById("settings-close").addEventListener("click", () => document.getElementById("settings-dialog").close());
    document.getElementById("settings-done").addEventListener("click", () => document.getElementById("settings-dialog").close());
    document.getElementById("settings-theme").addEventListener("click", () => themeToggle.click());
    document.getElementById("settings-dialog").addEventListener("click", event => {
      if (event.target === event.currentTarget) event.currentTarget.close();
    });
    document.getElementById("clear-library").addEventListener("click", async () => {
      if (!documents.length) {
        showToast("Your local document library is already empty.");
        return;
      }
      if (!window.confirm(`Permanently delete all ${documents.length} local documents and their saved files from this browser? This cannot be undone.`)) return;
      try {
        const database = await databasePromise;
        await new Promise((resolve, reject) => {
          const transaction = database.transaction("documents", "readwrite");
          transaction.objectStore("documents").clear();
          transaction.oncomplete = resolve;
          transaction.onerror = () => reject(transaction.error);
          transaction.onabort = () => reject(transaction.error || new Error("Could not clear local documents."));
        });
        documents = [];
        activeDocument = null;
        renderApp();
        document.getElementById("settings-dialog").close();
        showToast("All local documents were deleted.");
      } catch (error) {
        console.error("Could not clear the local document library.", error);
        showToast("Could not delete the local library. Please try again.");
      }
    });
    document.getElementById("file-input").accept = "image/png,image/jpeg,image/webp,image/tiff,application/pdf,.pdf";
    window.addEventListener("beforeunload", event => {
      if (!document.getElementById("ocr-text").readOnly) {
        event.preventDefault();
        event.returnValue = "";
      }
    });
    appReady = initializeApp();
