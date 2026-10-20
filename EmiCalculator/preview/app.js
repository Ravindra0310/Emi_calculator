// ==========================================
// EMI Calculator - Interactive Web Preview Logic
// Mirrors Android Kotlin & Jetpack Compose implementation
// ==========================================

// State
let calculationCount = 0;
let lastInterstitialTime = 0;
let lastAppOpenTime = 0;
let isScheduleUnlocked = false;
let pdfEntitlements = 0;
let currentLanguage = "en";
let currentTenureMode = "years"; // 'years' | 'months'
let onboardingIndex = 0;
let activeScreen = "home";

let currentInput = {
    principal: 1000000,
    annualRate: 8.5,
    tenureYears: 5,
    tenureMonths: 60
};

let currentResult = null;
let currentSchedule = [];

// AdPolicy Constants (matching AdPolicy.kt)
const MIN_INTERSTITIAL_INTERVAL_MS = 60000; // 60s
const MIN_APP_OPEN_INTERVAL_MS = 30000;     // 30s

// ==========================================
// Currency Formatter (Indian Numbering Format)
// ==========================================
function formatInr(amount, includeSymbol = true) {
    if (isNaN(amount)) return includeSymbol ? "₹ 0.00" : "0.00";
    
    const parts = Number(amount).toFixed(2).split(".");
    let integerPart = parts[0];
    const decimalPart = parts[1];

    // Indian comma grouping: last 3 digits, then groups of 2
    let lastThree = integerPart.substring(integerPart.length - 3);
    let otherNumbers = integerPart.substring(0, integerPart.length - 3);
    if (otherNumbers !== "") {
        lastThree = "," + lastThree;
    }
    const formattedInteger = otherNumbers.replace(/\B(?=(\d{2})+(?!\d))/g, ",") + lastThree;
    const finalFormatted = formattedInteger + "." + decimalPart;

    return includeSymbol ? `₹ ${finalFormatted}` : finalFormatted;
}

// ==========================================
// EMI Calculator Engine
// ==========================================
function calculateEmi(principal, annualRate, tenureMonths) {
    if (principal <= 0 || tenureMonths <= 0 || annualRate < 0) {
        return null;
    }

    let monthlyEmi = 0;
    let totalPayment = 0;
    let totalInterest = 0;

    if (annualRate === 0) {
        // 0% interest loan
        monthlyEmi = Math.round((principal / tenureMonths) * 100) / 100;
        totalPayment = Math.round(monthlyEmi * tenureMonths * 100) / 100;
        totalInterest = 0;
    } else {
        const monthlyRate = annualRate / (12 * 100);
        const factor = Math.pow(1 + monthlyRate, tenureMonths);
        const rawEmi = principal * monthlyRate * factor / (factor - 1);

        monthlyEmi = Math.round(rawEmi * 100) / 100;
        totalPayment = Math.round(monthlyEmi * tenureMonths * 100) / 100;
        totalInterest = Math.max(0, Math.round((totalPayment - principal) * 100) / 100);
    }

    const principalPct = totalPayment > 0 ? (principal / totalPayment) * 100 : 0;
    const interestPct = 100 - principalPct;

    return {
        principal,
        annualRate,
        tenureMonths,
        monthlyEmi,
        totalInterest,
        totalPayment,
        principalPercentage: principalPct,
        interestPercentage: interestPct
    };
}

// Month-by-month Schedule Generator
function generateSchedule(principal, annualRate, tenureMonths, monthlyEmi) {
    const list = [];
    let opening = principal;
    const monthlyRate = annualRate > 0 ? annualRate / (12 * 100) : 0;

    for (let m = 1; m <= tenureMonths; m++) {
        const interest = annualRate > 0 ? Math.round(opening * monthlyRate * 100) / 100 : 0;
        let principalPaid = Math.round((monthlyEmi - interest) * 100) / 100;
        let closing = Math.round((opening - principalPaid) * 100) / 100;

        if (m === tenureMonths || closing < 0) {
            principalPaid = opening;
            closing = 0;
        }

        const actualEmi = Math.round((principalPaid + interest) * 100) / 100;

        list.push({
            monthIndex: m,
            openingBalance: opening,
            emiAmount: actualEmi,
            principalPaid,
            interestPaid: interest,
            closingBalance: Math.max(0, closing)
        });

        opening = closing;
        if (opening <= 0) break;
    }

    return list;
}

// ==========================================
// UI Event Handlers
// ==========================================
function onPrincipalInputChange(val) {
    const num = parseFloat(val) || 0;
    currentInput.principal = num;
}

function setQuickPrincipal(val) {
    currentInput.principal = val;
    document.getElementById("input-principal").value = val;
    document.querySelectorAll(".quick-chips .chip").forEach(c => c.classList.remove("active"));
    const match = Array.from(document.querySelectorAll(".quick-chips .chip")).find(c => c.textContent.includes(`${val / 100000}L`));
    if (match) match.classList.add("active");
}

function onRateInputChange(val) {
    const num = parseFloat(val) || 0;
    currentInput.annualRate = num;
    document.getElementById("slider-rate").value = num;
}

function onRateSliderChange(val) {
    currentInput.annualRate = parseFloat(val);
    document.getElementById("input-rate").value = val;
}

function setTenureMode(mode) {
    currentTenureMode = mode;
    document.getElementById("tenure-mode-years").classList.toggle("active", mode === "years");
    document.getElementById("tenure-mode-months").classList.toggle("active", mode === "months");
    
    const tenureInput = document.getElementById("input-tenure");
    if (mode === "years") {
        tenureInput.value = currentInput.tenureYears;
    } else {
        tenureInput.value = currentInput.tenureMonths;
    }
}

function onTenureInputChange(val) {
    const num = parseInt(val) || 1;
    if (currentTenureMode === "years") {
        currentInput.tenureYears = num;
        currentInput.tenureMonths = num * 12;
    } else {
        currentInput.tenureMonths = num;
        currentInput.tenureYears = Math.round(num / 12);
    }
}

// Perform Calculation & AdPolicy check
function performCalculateEmi() {
    const p = parseFloat(document.getElementById("input-principal").value) || 0;
    const r = parseFloat(document.getElementById("input-rate").value) || 0;
    const t = parseInt(document.getElementById("input-tenure").value) || 1;
    const months = currentTenureMode === "years" ? t * 12 : t;

    currentResult = calculateEmi(p, r, months);
    if (!currentResult) {
        alert("Please enter valid positive values for calculation.");
        return;
    }

    currentSchedule = generateSchedule(p, r, months, currentResult.monthlyEmi);
    updateResultScreenUI(currentResult);
    populateScheduleCards(currentSchedule);

    // Update calculation count in AdPolicy
    calculationCount++;
    document.getElementById("monitor-calc-count").textContent = calculationCount;

    // Check AdPolicy for Interstitial Ad:
    // Rule: Skip calculation 1. Starting with subsequent, show on every 2nd calculation (2, 4, 6...)
    // AND must obey 60-second cooldown interval!
    const isEverySecond = (calculationCount > 1 && calculationCount % 2 === 0);
    const now = Date.now();
    const intervalOk = (lastInterstitialTime === 0 || now - lastInterstitialTime >= MIN_INTERSTITIAL_INTERVAL_MS);

    if (isEverySecond && intervalOk) {
        showInterstitialAd(() => {
            switchScreen("result");
        });
    } else {
        switchScreen("result");
    }

    updatePolicyMonitor();
}

function updateResultScreenUI(res) {
    document.getElementById("res-monthly-emi").textContent = formatInr(res.monthlyEmi);
    document.getElementById("res-summary-caption").textContent = `For ${res.tenureMonths} Months (${Math.round(res.tenureMonths / 12)} Years) @ ${res.annualRate}% p.a.`;
    document.getElementById("res-principal").textContent = formatInr(res.principal);
    document.getElementById("res-interest").textContent = formatInr(res.totalInterest);
    document.getElementById("res-total-payment").textContent = formatInr(res.totalPayment);

    // Repayment bar percentages
    document.getElementById("bar-principal-fill").style.width = `${res.principalPercentage}%`;
    document.getElementById("bar-interest-fill").style.width = `${res.interestPercentage}%`;

    document.getElementById("leg-principal-pct").textContent = `Principal (${res.principalPercentage.toFixed(1)}%)`;
    document.getElementById("leg-principal-amt").textContent = formatInr(res.principal);

    document.getElementById("leg-interest-pct").textContent = `Interest (${res.interestPercentage.toFixed(1)}%)`;
    document.getElementById("leg-interest-amt").textContent = formatInr(res.totalInterest);
}

function populateScheduleCards(schedule) {
    const container = document.getElementById("schedule-cards-list");
    document.getElementById("sched-months-count").textContent = schedule.length;
    container.innerHTML = "";

    schedule.forEach(m => {
        const card = document.createElement("div");
        card.className = "schedule-card";
        card.innerHTML = `
            <div class="sched-header">
                <span class="sched-badge">Month ${m.monthIndex}</span>
                <span class="sched-emi">${formatInr(m.emiAmount)}</span>
            </div>
            <div class="sched-grid">
                <div>
                    <span class="lbl">Principal Paid: </span>
                    <span class="val">${formatInr(m.principalPaid)}</span>
                </div>
                <div class="text-right">
                    <span class="lbl">Interest: </span>
                    <span class="val interest-color">${formatInr(m.interestPaid)}</span>
                </div>
            </div>
            <div class="sched-closing">
                <span class="lbl">Remaining Balance:</span>
                <span class="val">${formatInr(m.closingBalance)}</span>
            </div>
        `;
        container.appendChild(card);
    });
}

// ==========================================
// Rewarded Ad Unlock (Schedule & PDF)
// ==========================================
function onUnlockScheduleClick() {
    if (isScheduleUnlocked) {
        switchScreen("schedule");
        return;
    }

    // Launch Rewarded Video Ad Modal
    showRewardedVideoAd("Unlocking Amortization Schedule", () => {
        isScheduleUnlocked = true;
        document.getElementById("monitor-schedule-unlocked").textContent = "Unlocked (Active)";
        document.getElementById("btn-unlock-schedule").classList.add("unlocked");
        document.getElementById("unlock-label").textContent = "View Amortization Schedule (Unlocked)";
        switchScreen("schedule");
    });
}

function onExportPdfClick() {
    if (pdfEntitlements > 0) {
        pdfEntitlements--;
        document.getElementById("monitor-pdf-pass").textContent = `${pdfEntitlements} passes`;
        triggerPdfDownload();
    } else {
        showRewardedVideoAd("Unlock 1 PDF Statement Export Pass", () => {
            pdfEntitlements++;
            document.getElementById("monitor-pdf-pass").textContent = `${pdfEntitlements} passes`;
            triggerPdfDownload();
        });
    }
}

function triggerPdfDownload() {
    alert("✓ Generated EMI Statement (PDF) successfully!\n(Matches native PdfDocument export formatted with loan parameters & amortization table)");
}

function onExportCsvClick() {
    if (!currentSchedule || currentSchedule.length === 0) {
        alert("No calculation available to export.");
        return;
    }

    let csvContent = "Month,Opening Balance,Monthly EMI,Principal Paid,Interest Paid,Closing Balance\n";
    currentSchedule.forEach(m => {
        csvContent += `${m.monthIndex},${m.openingBalance},${m.emiAmount},${m.principalPaid},${m.interestPaid},${m.closingBalance}\n`;
    });

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `EMI_Schedule_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

// Schedule Screen Back button (triggers return interstitial if 60s cooldown passed)
function onScheduleBackClick() {
    const now = Date.now();
    const intervalOk = (lastInterstitialTime === 0 || now - lastInterstitialTime >= MIN_INTERSTITIAL_INTERVAL_MS);

    if (intervalOk) {
        showInterstitialAd(() => {
            switchScreen("result");
        });
    } else {
        switchScreen("result");
    }
}

// ==========================================
// AdMob Simulated Modals & Overlays
// ==========================================
let interstitialCallback = null;

function showInterstitialAd(onClosed) {
    interstitialCallback = onClosed;
    lastInterstitialTime = Date.now();
    updatePolicyMonitor();

    const modal = document.getElementById("modal-interstitial");
    modal.classList.add("active");
    
    let count = 3;
    const timerElem = document.getElementById("interstitial-countdown");
    timerElem.textContent = `Ad closes in ${count}s...`;

    const timer = setInterval(() => {
        count--;
        if (count > 0) {
            timerElem.textContent = `Ad closes in ${count}s...`;
        } else {
            clearInterval(timer);
            timerElem.textContent = "Ad ready to skip (✕)";
        }
    }, 1000);
}

function closeInterstitialAd() {
    const modal = document.getElementById("modal-interstitial");
    modal.classList.remove("active");
    if (interstitialCallback) {
        interstitialCallback();
        interstitialCallback = null;
    }
}

// Rewarded Video Ad Simulator
let rewardedCallback = null;
let rewardTimer = null;

function showRewardedVideoAd(title, onRewardEarned) {
    rewardedCallback = onRewardEarned;
    const modal = document.getElementById("modal-rewarded");
    document.getElementById("rewarded-ad-title").textContent = title;
    document.getElementById("btn-claim-reward").style.display = "none";
    document.getElementById("reward-progress-bar").style.width = "0%";
    modal.classList.add("active");

    let progress = 0;
    const timerText = document.getElementById("reward-timer-text");

    rewardTimer = setInterval(() => {
        progress += 20;
        document.getElementById("reward-progress-bar").style.width = `${progress}%`;
        const remaining = Math.max(0, (100 - progress) / 20);
        timerText.textContent = `Reward granted in ${remaining}s...`;

        if (progress >= 100) {
            clearInterval(rewardTimer);
            timerText.textContent = "✓ Reward earned! Tap below to claim.";
            document.getElementById("btn-claim-reward").style.display = "flex";
        }
    }, 800);
}

function completeRewardedAd() {
    const modal = document.getElementById("modal-rewarded");
    modal.classList.remove("active");
    if (rewardedCallback) {
        rewardedCallback();
        rewardedCallback = null;
    }
}

// App Open Ad Simulator
function triggerSimulatedAppOpen() {
    const now = Date.now();
    if (lastAppOpenTime !== 0 && now - lastAppOpenTime < MIN_APP_OPEN_INTERVAL_MS) {
        const remaining = Math.round((MIN_APP_OPEN_INTERVAL_MS - (now - lastAppOpenTime)) / 1000);
        alert(`App Open Ad throttled: 30s interval rule. Try again in ${remaining}s.`);
        return;
    }

    lastAppOpenTime = now;
    showInterstitialAd(() => {
        alert("App Open Ad dismissed. User returned to application.");
    });
}

// UMP Privacy Form Dialog
function openUmpModal() {
    document.getElementById("modal-ump").classList.add("active");
}

function closeUmpModal(consented) {
    document.getElementById("modal-ump").classList.remove("active");
    const status = consented ? "Granted (canRequestAds: true)" : "Denied (canRequestAds: false)";
    document.getElementById("monitor-ump").textContent = status;
    alert(`Google UMP Privacy Preferences Saved: ${consented ? "Consented" : "Non-personalized"}`);
}

// ==========================================
// Onboarding Flow
// ==========================================
function nextOnboardingSlide() {
    if (onboardingIndex < 2) {
        onboardingIndex++;
        updateOnboardingUI();
    } else {
        switchScreen("home");
    }
}

function prevOnboardingSlide() {
    if (onboardingIndex > 0) {
        onboardingIndex--;
        updateOnboardingUI();
    }
}

function updateOnboardingUI() {
    document.querySelectorAll(".onboarding-slide").forEach((s, idx) => {
        s.classList.toggle("active", idx === onboardingIndex);
    });
    document.querySelectorAll(".onb-indicators .dot").forEach((d, idx) => {
        d.classList.toggle("active", idx === onboardingIndex);
    });

    document.getElementById("btn-onb-prev").style.display = onboardingIndex > 0 ? "flex" : "none";
    document.getElementById("btn-onb-next").textContent = onboardingIndex === 2 ? "Get Started ✓" : "Next →";
}

// ==========================================
// Language Switching (English / Hindi)
// ==========================================
const translations = {
    en: {
        app_name: "EMI Calculator",
        calculate_emi: "Calculate EMI",
        loan_amount: "Loan Amount (Principal)",
        interest_rate: "Annual Interest Rate (%)",
        tenure: "Loan Tenure",
        monthly_emi: "Monthly EMI",
        principal: "Principal",
        total_interest: "Total Interest",
        total_amount: "Total Amount",
        amortization_schedule: "Amortization Schedule",
        select_language: "Select Language",
        privacy_settings: "Privacy & Consent",
        settings: "Settings"
    },
    hi: {
        app_name: "ईएमआई कैलकुलेटर",
        calculate_emi: "ईएमआई गणना करें",
        loan_amount: "ऋण राशि (मूलधन)",
        interest_rate: "वार्षिक ब्याज दर (%)",
        tenure: "ऋण अवधि",
        monthly_emi: "मासिक ईएमआई",
        principal: "मूलधन",
        total_interest: "कुल ब्याज",
        total_amount: "कुल देय राशि",
        amortization_schedule: "भुगतान अनुसूची",
        select_language: "भाषा चुनें",
        privacy_settings: "गोपनीयता और सहमति",
        settings: "सेटिंग्स"
    }
};

function onLanguageChanged(lang) {
    currentLanguage = lang;
    const dict = translations[lang] || translations.en;
    document.querySelectorAll("[data-i18n]").forEach(elem => {
        const key = elem.getAttribute("data-i18n");
        if (dict[key]) {
            elem.textContent = dict[key];
        }
    });
}

// ==========================================
// Navigation & Monitors
// ==========================================
function switchScreen(screenKey) {
    activeScreen = screenKey;
    document.querySelectorAll(".screen-view").forEach(v => v.classList.remove("active"));
    const targetView = document.getElementById(`view-${screenKey}`);
    if (targetView) targetView.classList.add("active");

    document.querySelectorAll(".nav-btn").forEach(b => b.classList.remove("active"));
    const targetNav = document.getElementById(`btn-nav-${screenKey}`);
    if (targetNav) targetNav.classList.add("active");
}

function updatePolicyMonitor() {
    const timerElem = document.getElementById("monitor-interstitial-timer");
    const now = Date.now();
    const elapsed = now - lastInterstitialTime;

    if (lastInterstitialTime === 0 || elapsed >= MIN_INTERSTITIAL_INTERVAL_MS) {
        timerElem.textContent = "Ready";
        timerElem.style.color = "#10b981";
    } else {
        const remaining = Math.round((MIN_INTERSTITIAL_INTERVAL_MS - elapsed) / 1000);
        timerElem.textContent = `Cooldown: ${remaining}s`;
        timerElem.style.color = "#f59e0b";
    }
}

function resetAdPolicyState() {
    calculationCount = 0;
    lastInterstitialTime = 0;
    lastAppOpenTime = 0;
    isScheduleUnlocked = false;
    pdfEntitlements = 0;
    document.getElementById("monitor-calc-count").textContent = "0";
    document.getElementById("monitor-schedule-unlocked").textContent = "Locked (Rewarded)";
    document.getElementById("monitor-pdf-pass").textContent = "0 passes";
    document.getElementById("btn-unlock-schedule").classList.remove("unlocked");
    document.getElementById("unlock-label").textContent = "Unlock Full Schedule (Rewarded Ad)";
    updatePolicyMonitor();
    alert("AdPolicy & Session Entitlements Reset.");
}

// Live timer tick for monitor
setInterval(updatePolicyMonitor, 1000);

// Status bar clock
function updateClock() {
    const d = new Date();
    const h = String(d.getHours()).padStart(2, "0");
    const m = String(d.getMinutes()).padStart(2, "0");
    document.getElementById("status-time").textContent = `${h}:${m}`;
}
setInterval(updateClock, 1000);
updateClock();

// Initial run
performCalculateEmi();
