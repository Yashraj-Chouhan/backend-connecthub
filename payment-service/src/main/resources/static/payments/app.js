const apiBase = "/payments";

const state = {
    config: null,
    user: null,
    history: [],
    selectedPlanCode: null,
    customCredits: 150,
    busy: false
};

const dom = {};

document.addEventListener("DOMContentLoaded", async () => {
    bindDom();
    bindEvents();
    prefillUserIdFromUrl();

    try {
        await loadConfig();
        if (dom.userIdInput.value.trim()) {
            await loadAccount();
        }
    } catch (error) {
        setStatus(error.message || "Unable to load payment studio", "error");
    }
});

function bindDom() {
    dom.userIdInput = document.getElementById("userIdInput");
    dom.loadAccountButton = document.getElementById("loadAccountButton");
    dom.refreshHistoryButton = document.getElementById("refreshHistoryButton");
    dom.topUpButton = document.getElementById("topUpButton");
    dom.customSelectButton = document.getElementById("customSelectButton");
    dom.statusBanner = document.getElementById("statusBanner");
    dom.planGrid = document.getElementById("planGrid");
    dom.historyList = document.getElementById("historyList");
    dom.accountSummary = document.getElementById("accountSummary");
    dom.currentCreditsValue = document.getElementById("currentCreditsValue");
    dom.selectedPackNameValue = document.getElementById("selectedPackNameValue");
    dom.selectedPackPriceValue = document.getElementById("selectedPackPriceValue");
    dom.projectedCreditsValue = document.getElementById("projectedCreditsValue");
    dom.customCreditsLabel = document.getElementById("customCreditsLabel");
    dom.customAmountLabel = document.getElementById("customAmountLabel");
    dom.customRateLabel = document.getElementById("customRateLabel");
    dom.creditsRange = document.getElementById("creditsRange");
    dom.creditsNumber = document.getElementById("creditsNumber");
    dom.merchantNameValue = document.getElementById("merchantNameValue");
}

function bindEvents() {
    dom.loadAccountButton.addEventListener("click", loadAccount);
    dom.refreshHistoryButton.addEventListener("click", loadHistory);
    dom.topUpButton.addEventListener("click", startTopUpFlow);
    dom.customSelectButton.addEventListener("click", () => selectPlan("custom"));
    dom.creditsRange.addEventListener("input", () => {
        syncCustomCredits(dom.creditsRange.value);
        if (state.selectedPlanCode === "custom") {
            updateSummary();
        }
    });
    dom.creditsNumber.addEventListener("input", () => {
        syncCustomCredits(dom.creditsNumber.value);
        if (state.selectedPlanCode === "custom") {
            updateSummary();
        }
    });
}

function prefillUserIdFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const userId = params.get("userId");
    if (userId) {
        dom.userIdInput.value = userId;
    }
}

async function loadConfig() {
    setStatus("Loading top-up plans...", "info");
    const response = await fetch(`${apiBase}/config`);
    if (!response.ok) {
        throw new Error("Unable to load payment configuration");
    }

    state.config = await response.json();
    dom.merchantNameValue.textContent = state.config.merchantName || "ConnectHub Credits";

    const recommendedPlan = state.config.plans.find((plan) => plan.recommended) || state.config.plans[0];
    state.selectedPlanCode = recommendedPlan ? recommendedPlan.code : "custom";
    state.customCredits = recommendedPlan ? recommendedPlan.credits : state.config.minCredits;

    setupCustomControls();
    renderPlans();
    syncCustomCredits(state.customCredits);
    updateSummary();
    setStatus(state.config.supportMessage || "Choose a plan or custom credits to continue.", "info");
}

function setupCustomControls() {
    dom.creditsRange.min = String(state.config.minCredits);
    dom.creditsRange.max = String(state.config.maxCredits);
    dom.creditsRange.step = "25";
    dom.creditsRange.value = String(state.customCredits);
    dom.creditsNumber.min = String(state.config.minCredits);
    dom.creditsNumber.max = String(state.config.maxCredits);
    dom.creditsNumber.step = "25";
    dom.creditsNumber.value = String(state.customCredits);
    dom.customRateLabel.textContent = `${formatCurrency(state.config.customCreditRate)} / credit`;
}

function renderPlans() {
    dom.planGrid.innerHTML = "";
    const fragment = document.createDocumentFragment();

    state.config.plans.forEach((plan) => {
        const card = document.createElement("button");
        card.type = "button";
        card.className = buildPlanCardClass(plan);
        card.dataset.planCode = plan.code;
        card.innerHTML = buildPlanCardMarkup(plan);
        card.addEventListener("click", () => selectPlan(plan.code));
        fragment.appendChild(card);
    });

    dom.planGrid.appendChild(fragment);
    syncBusyState();
}

function buildPlanCardClass(plan) {
    const classes = ["plan-card", "fade-in"];
    if (plan.recommended) {
        classes.push("recommended");
    }
    if (state.selectedPlanCode === plan.code) {
        classes.push("selected");
    }
    return classes.join(" ");
}

function buildPlanCardMarkup(plan) {
    const customEquivalent = Number(plan.credits) * Number(state.config.customCreditRate);
    const savings = Math.max(0, customEquivalent - Number(plan.amount));

    return `
        <div class="plan-top">
            <div class="plan-badge">${escapeHtml(plan.badge || "Top-up")}</div>
            <div class="plan-name">${escapeHtml(plan.title)}</div>
            <div class="plan-price">${formatCurrency(plan.amount)}</div>
        </div>
        <div class="plan-details">
            <p>${escapeHtml(plan.description || "")}</p>
        </div>
        <div class="plan-stats">
            <div class="plan-stat">
                <span>Credits</span>
                <strong>${formatNumber(plan.credits)}</strong>
            </div>
            <div class="plan-stat">
                <span>Saved vs custom</span>
                <strong>${savings > 0 ? formatCurrency(savings) : "Flexible"}</strong>
            </div>
        </div>
    `;
}

function selectPlan(planCode) {
    state.selectedPlanCode = planCode;
    updatePlanCardSelection();
    updateSummary();

    if (planCode === "custom") {
        setStatus("Custom credits selected. Review the order preview, then continue to secure payment.", "info");
        dom.customSelectButton.textContent = "Custom amount active";
        return;
    }

    dom.customSelectButton.textContent = "Use custom amount";
    const plan = state.config.plans.find((item) => item.code === planCode);
    if (plan) {
        setStatus(`Selected ${plan.title}. The payment service will create a Razorpay order from this plan.`, "info");
    }
}

function updatePlanCardSelection() {
    [...dom.planGrid.querySelectorAll(".plan-card")].forEach((card) => {
        card.classList.toggle("selected", card.dataset.planCode === state.selectedPlanCode);
    });
}

function syncCustomCredits(value) {
    const credits = clampCredits(value);
    state.customCredits = credits;
    dom.creditsRange.value = String(credits);
    dom.creditsNumber.value = String(credits);
    dom.customCreditsLabel.textContent = formatNumber(credits);
    dom.customAmountLabel.textContent = formatCurrency(credits * Number(state.config.customCreditRate));
}

function clampCredits(value) {
    const parsed = Number(value);
    if (Number.isNaN(parsed)) {
        return Number(state.config.minCredits);
    }

    const rounded = Math.round(parsed / 25) * 25;
    return Math.max(Number(state.config.minCredits), Math.min(Number(state.config.maxCredits), rounded));
}

function updateSummary() {
    const selection = getSelection();
    const currentCredits = state.user?.translationCreditsRemaining ?? null;

    dom.selectedPackNameValue.textContent = selection.title;
    dom.selectedPackPriceValue.textContent = formatCurrency(selection.amount);
    dom.currentCreditsValue.textContent = currentCredits === null ? "--" : formatNumber(currentCredits);
    dom.projectedCreditsValue.textContent = currentCredits === null
        ? formatNumber(selection.credits)
        : formatNumber(currentCredits + selection.credits);
}

function getSelection() {
    if (!state.config) {
        return { code: "custom", title: "Custom Top-up", credits: state.customCredits, amount: 0 };
    }

    if (state.selectedPlanCode === "custom") {
        return {
            code: "custom",
            title: "Custom Top-up",
            credits: state.customCredits,
            amount: state.customCredits * Number(state.config.customCreditRate)
        };
    }

    const plan = state.config.plans.find((item) => item.code === state.selectedPlanCode) || state.config.plans[0];
    return plan || {
        code: "custom",
        title: "Custom Top-up",
        credits: state.customCredits,
        amount: state.customCredits * Number(state.config.customCreditRate)
    };
}

async function loadAccount(options = {}) {
    const silent = Boolean(options.silent);
    const manageBusy = options.manageBusy !== false;
    const userId = dom.userIdInput.value.trim();
    if (!userId) {
        if (!silent) {
            setStatus("Enter a user ID before loading the wallet.", "warning");
        }
        return;
    }

    if (manageBusy) {
        setBusy(true);
    }
    if (!silent) {
        setStatus(`Loading wallet data for ${userId}...`, "info");
    }

    try {
        const response = await fetch(`${apiBase}/users/${encodeURIComponent(userId)}`);
        if (!response.ok) {
            throw new Error((await readErrorMessage(response)) || "Unable to load user account");
        }

        state.user = await response.json();
        renderAccountSummary();
        updateSummary();
        await loadHistory();
        setStatus(`Loaded ${state.user.fullName || state.user.username || userId}.`, "success");
    } catch (error) {
        if (!silent) {
            state.user = null;
            renderAccountSummary();
            updateSummary();
            setStatus(error.message || "Unable to load account", "error");
        }
    } finally {
        if (manageBusy) {
            setBusy(false);
        }
    }
}

function renderAccountSummary() {
    if (!state.user) {
        dom.accountSummary.innerHTML = `
            <p class="empty-state">Load an account to see current credits, profile details, and recent payment activity.</p>
        `;
        return;
    }

    const initials = getInitials(state.user.fullName || state.user.username || state.user.email || "U");
    const status = state.user.onlineStatus || "UNKNOWN";

    dom.accountSummary.innerHTML = `
        <div class="account-card fade-in">
            <div class="account-top">
                <div class="account-avatar">${escapeHtml(initials)}</div>
                <div class="account-name">
                    <strong>${escapeHtml(state.user.fullName || state.user.username || "User")}</strong>
                    <span class="account-line-label">${escapeHtml(state.user.email || state.user.phoneNumber || state.user.userId)}</span>
                </div>
            </div>
            <div class="account-badge">${escapeHtml(status)}</div>
            <div class="account-stats">
                <div class="account-stat">
                    <span>Credits</span>
                    <strong>${formatNumber(state.user.translationCreditsRemaining ?? 0)}</strong>
                </div>
                <div class="account-stat">
                    <span>Preferred language</span>
                    <strong>${escapeHtml(state.user.preferredLanguage || "en")}</strong>
                </div>
                <div class="account-stat">
                    <span>Role</span>
                    <strong>${escapeHtml(state.user.role || "USER")}</strong>
                </div>
                <div class="account-stat">
                    <span>Last seen</span>
                    <strong>${escapeHtml(state.user.lastSeenAt || "Online now")}</strong>
                </div>
            </div>
        </div>
    `;
}

async function loadHistory() {
    const userId = dom.userIdInput.value.trim();
    if (!userId) {
        renderHistory([]);
        return;
    }

    try {
        const response = await fetch(`${apiBase}/history/${encodeURIComponent(userId)}`);
        if (!response.ok) {
            renderHistory([]);
            return;
        }

        state.history = await response.json();
        renderHistory(state.history);
    } catch (error) {
        renderHistory([]);
    }
}

function renderHistory(items) {
    if (!items || !items.length) {
        dom.historyList.innerHTML = `<p class="empty-state">No payment history yet. Once a top-up is completed, the latest activity will appear here.</p>`;
        return;
    }

    dom.historyList.innerHTML = items.slice(0, 8).map((item) => {
        const status = normalizeStatus(item.status);
        const statusClass = historyStatusClass(status);
        const title = item.planName || item.planCode || item.orderId;
        const dateLabel = item.createdAt ? formatDateTime(item.createdAt) : "Pending";
        const amountLabel = item.amount != null ? formatCurrency(item.amount) : "--";
        const creditsLabel = item.credits != null ? formatNumber(item.credits) : "--";
        const details = item.lastError ? `<p class="history-error">${escapeHtml(item.lastError)}</p>` : "";

        return `
            <div class="history-item fade-in">
                <div class="history-item-top">
                    <div class="history-item-title">
                        <strong>${escapeHtml(title)}</strong>
                        <span class="account-line-label">${escapeHtml(item.orderId || "No order id")}</span>
                    </div>
                    <span class="history-badge ${statusClass}">${escapeHtml(status)}</span>
                </div>
                <div class="history-meta">
                    <div class="history-meta-item">
                        <span>Amount</span>
                        <strong>${escapeHtml(amountLabel)}</strong>
                    </div>
                    <div class="history-meta-item">
                        <span>Credits</span>
                        <strong>${escapeHtml(creditsLabel)}</strong>
                    </div>
                    <div class="history-meta-item">
                        <span>Order date</span>
                        <strong>${escapeHtml(dateLabel)}</strong>
                    </div>
                    <div class="history-meta-item">
                        <span>Payment id</span>
                        <strong>${escapeHtml(item.paymentId || "Awaiting payment")}</strong>
                    </div>
                </div>
                ${details}
            </div>
        `;
    }).join("");
}

async function startTopUpFlow() {
    if (!state.config) {
        setStatus("Payment configuration is still loading.", "warning");
        return;
    }

    const userId = dom.userIdInput.value.trim();
    if (!userId) {
        setStatus("Enter your user ID before starting the payment.", "warning");
        return;
    }

    setBusy(true);
    setStatus("Creating your order in the payment service...", "info");

    try {
        const selection = getSelection();
        const payload = {
            userId,
            customerName: state.user?.fullName || state.user?.username,
            customerEmail: state.user?.email
        };

        if (selection.code === "custom") {
            payload.credits = selection.credits;
        } else {
            payload.planCode = selection.code;
        }

        const response = await fetch(`${apiBase}/create-order`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            throw new Error((await readErrorMessage(response)) || "Unable to create a payment order");
        }

        const order = await response.json();
        openRazorpayCheckout(order, selection);
    } catch (error) {
        setBusy(false);
        setStatus(error.message || "Unable to open checkout", "error");
    }
}

function openRazorpayCheckout(order, selection) {
    if (typeof window.Razorpay !== "function") {
        throw new Error("Razorpay Checkout did not load. Refresh the page and try again.");
    }

    let checkoutCompleted = false;
    const razorpay = new window.Razorpay({
        key: order.keyId || state.config?.keyId,
        amount: Number(order.amountInPaise || 0),
        currency: order.currency || "INR",
        name: state.config?.merchantName || "ConnectHub Credits",
        description: selection?.title || order.planName || "ConnectHub Credits",
        order_id: order.orderId,
        handler: async (response) => {
            checkoutCompleted = true;
            try {
                await verifyRazorpayPayment({
                    orderId: response.razorpay_order_id || order.orderId,
                    paymentId: response.razorpay_payment_id,
                    signature: response.razorpay_signature
                });
            } catch (error) {
                setBusy(false);
                setStatus(error.message || "Unable to verify Razorpay payment", "error");
            }
        },
        prefill: {
            name: state.user?.fullName || state.user?.username || "",
            email: state.user?.email || "",
            contact: normalizePhone(state.user?.phoneNumber)
        },
        notes: {
            userId: order.userId || dom.userIdInput.value.trim(),
            planCode: order.planCode || selection?.code || "custom",
            credits: String(order.credits || selection?.credits || 0),
            receipt: order.receipt || ""
        },
        theme: {
            color: "#1f7a8c"
        },
        modal: {
            confirm_close: true,
            ondismiss: () => {
                if (!checkoutCompleted) {
                    setBusy(false);
                    setStatus("Razorpay checkout was closed before payment completed.", "info");
                }
            }
        }
    });

    razorpay.on("payment.failed", (event) => {
        const description = event?.error?.description || event?.error?.reason || "Payment failed. Please try again.";
        setBusy(false);
        setStatus(description, "error");
    });

    setStatus("Order created. Opening Razorpay Checkout. Credits will be added after signature verification and backend confirmation.", "info");
    razorpay.open();
}

async function verifyRazorpayPayment(response) {
    setStatus("Verifying payment in the backend and syncing credits...", "info");

    const payload = {
        orderId: response.orderId,
        paymentId: response.paymentId,
        signature: response.signature
    };

    const verificationResponse = await fetch(`${apiBase}/verify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    const result = await verificationResponse.json().catch(() => ({}));
    if (!verificationResponse.ok) {
        setBusy(false);
        throw new Error(result.message || result.error || "Payment verification failed");
    }

    if (result.updatedUser) {
        state.user = result.updatedUser;
        renderAccountSummary();
    }

    if (result.creditsApplied) {
        setStatus(
            `${result.message || "Payment verified."} ${formatNumber(result.remainingCredits ?? state.user?.translationCreditsRemaining ?? 0)} credits are now in your account.`,
            "success"
        );
    } else {
        setStatus(result.message || "Payment verified, but credit sync is pending.", "warning");
    }

    await loadAccount({ silent: true, manageBusy: false });
    await loadHistory();
    setBusy(false);
}

function setBusy(isBusy) {
    state.busy = isBusy;
    dom.topUpButton.disabled = isBusy;
    dom.loadAccountButton.disabled = isBusy;
    dom.refreshHistoryButton.disabled = isBusy;
    dom.customSelectButton.disabled = isBusy;
    dom.userIdInput.disabled = isBusy;
    dom.creditsRange.disabled = isBusy;
    dom.creditsNumber.disabled = isBusy;
    syncBusyState();
    dom.topUpButton.textContent = isBusy ? "Processing..." : "Continue to secure payment";
}

function syncBusyState() {
    [...dom.planGrid.querySelectorAll(".plan-card")].forEach((card) => {
        card.disabled = state.busy;
    });
}

function setStatus(message, kind = "info") {
    dom.statusBanner.textContent = message;
    dom.statusBanner.className = `status-banner ${kind}`;
}

function getInitials(value) {
    const trimmed = String(value || "").trim();
    if (!trimmed) {
        return "U";
    }

    return trimmed
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0])
        .join("")
        .toUpperCase();
}

function normalizeStatus(status) {
    return String(status || "CREATED").toUpperCase();
}

function historyStatusClass(status) {
    if (status === "CREDITED" || status === "SUCCESS") {
        return "credit";
    }
    if (status === "PAID") {
        return "pending";
    }
    if (status === "FAILED") {
        return "failed";
    }
    return "created";
}

function formatCurrency(value) {
    const numeric = Number(value || 0);
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        minimumFractionDigits: numeric % 1 === 0 ? 0 : 2,
        maximumFractionDigits: 2
    }).format(numeric);
}

function formatNumber(value) {
    return new Intl.NumberFormat("en-IN").format(Number(value || 0));
}

function formatDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value);
    }

    return new Intl.DateTimeFormat("en-IN", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(date);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function normalizePhone(value) {
    const normalized = String(value || "").replace(/[^\d+]/g, "");
    return normalized || "";
}

async function readErrorMessage(response) {
    try {
        const payload = await response.json();
        return payload?.message || payload?.error || payload?.detail || null;
    } catch (error) {
        try {
            return await response.text();
        } catch (textError) {
            return null;
        }
    }
}
