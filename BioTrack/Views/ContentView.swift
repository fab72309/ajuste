import SwiftUI
import UserNotifications

struct ContentView: View {
    @AppStorage("hasCompletedInitialOnboarding") private var hasCompletedInitialOnboarding: Bool = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showLaunchOverlay =
        !ProcessInfo.processInfo.arguments.contains("-appStoreScreenshots")

    var body: some View {
        ZStack {
            Group {
                if hasCompletedInitialOnboarding {
                    MainAppView()
                } else {
                    OnboardingFlowView(isCompleted: $hasCompletedInitialOnboarding)
                }
            }
            .opacity(showLaunchOverlay ? 0 : 1)
            .allowsHitTesting(!showLaunchOverlay)
            .accessibilityHidden(showLaunchOverlay)

            if showLaunchOverlay {
                LaunchLoadingView()
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 1.02)))
            }
        }
        .task {
            await runLaunchOverlay()
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.28), value: showLaunchOverlay)
    }

    @MainActor
    private func runLaunchOverlay() async {
        guard showLaunchOverlay else { return }
        let delay: UInt64 = reduceMotion ? 600_000_000 : 900_000_000
        try? await Task.sleep(nanoseconds: delay)
        showLaunchOverlay = false
    }
}

struct MainAppView: View {
    @AppStorage("selectedTab") private var selectedTab: Int = 0
    @AppStorage("healthAutoSync") private var healthAutoSync: Bool = true
    @AppStorage("healthImportRangeDays") private var healthImportRangeDays: Int = 30
    @AppStorage("healthDedupePolicy") private var healthDedupePolicyRaw: String = HealthDedupePolicy.replace.rawValue
    @EnvironmentObject var appState: AppState
    @Environment(\.scenePhase) private var scenePhase
    @State private var didInitialHealthSync = false

    var body: some View {
        ZStack {
            Color("Background").ignoresSafeArea()
            TabView(selection: $selectedTab) {
                HomeView()
                    .tag(0)
                    .tabItem { Label(NSLocalizedString("tab.checklist", comment: ""), systemImage: "house") }
                TrackView()
                    .tag(1)
                    .tabItem { Label(NSLocalizedString("tab.track", comment: ""), systemImage: "slider.horizontal.3") }
                StatsView()
                    .tag(2)
                    .tabItem { Label(NSLocalizedString("tab.stats", comment: ""), systemImage: "chart.line.uptrend.xyaxis") }
                ProtocolsView()
                    .tag(3)
                    .tabItem { Label(NSLocalizedString("tab.protocols", comment: ""), systemImage: "target") }
                SupplementsView()
                    .tag(4)
                    .tabItem { Label(NSLocalizedString("tab.supplements", comment: ""), systemImage: "pills") }
            }
            .tint(Color("Primary"))
        }
        .task {
            await MainActor.run { appState.refreshHealthKitStatus() }
            await MainActor.run {
                appState.applyAdaptiveGoalPolicyIfNeeded()
                appState.refreshInsightsAndRecommendations()
            }
            if !didInitialHealthSync {
                didInitialHealthSync = true
                if healthAutoSync, appState.healthKitStatus == .authorized {
                    await appState.syncHealthKit(rangeDays: healthImportRangeDays, dedupePolicy: dedupePolicy)
                }
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task {
                    await MainActor.run { appState.load() }
                    await MainActor.run { appState.refreshHealthKitStatus() }
                    await MainActor.run {
                        appState.applyAdaptiveGoalPolicyIfNeeded()
                        appState.refreshInsightsAndRecommendations()
                    }
                    if healthAutoSync, appState.healthKitStatus == .authorized {
                        await appState.syncHealthKit(rangeDays: healthImportRangeDays, dedupePolicy: dedupePolicy)
                    }
                }
            }
        }
        .onOpenURL { url in
            guard url.scheme == "biotrack" else { return }
            let destination = (url.host ?? "").lowercased()
            let defaults = AppGroup.sharedDefaults()
            if !defaults.bool(forKey: "analytics.widget_added.logged") {
                defaults.set(true, forKey: "analytics.widget_added.logged")
                WidgetEventLogger.log("widget_added")
            }
            switch destination {
            case "home":
                selectedTab = 0
            case "track":
                selectedTab = 1
            case "stats":
                selectedTab = 2
            case "protocols":
                selectedTab = 3
            case "supplements":
                selectedTab = 4
            case "reminders":
                selectedTab = 0
            default:
                selectedTab = 0
            }
            WidgetEventLogger.log("widget_open_app_deeplink", metadata: ["destination": destination])
        }
        .bioTrackNotice(
            isPresented: Binding(
                get: { appState.storeRecoveryNoticeMessage != nil },
                set: { presented in
                    if !presented {
                        appState.dismissStoreRecoveryNotice()
                    }
                }
            ),
            title: "Récupération des données",
            message: appState.storeRecoveryNoticeMessage ?? ""
        )
    }

    private var dedupePolicy: HealthDedupePolicy {
        HealthDedupePolicy(rawValue: healthDedupePolicyRaw) ?? .replace
    }
}

private enum OnboardingStep: Int, CaseIterable {
    case welcome
    case notifications
    case health
}

struct OnboardingFlowView: View {
    @Binding var isCompleted: Bool
    @EnvironmentObject private var appState: AppState

    @State private var step: OnboardingStep = .welcome
    @State private var activeLegalDocument: LegalDocument?
    @State private var notificationStatus: UNAuthorizationStatus = .notDetermined
    @State private var isRequestingNotificationPermission = false
    @State private var isRequestingHealthPermission = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color("Background"),
                    Color("Background"),
                    Color("Primary").opacity(0.10)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                onboardingProgress

                Group {
                    switch step {
                    case .welcome:
                        WelcomeStepView(
                            onContinue: { step = .notifications },
                            onOpenDocument: { activeLegalDocument = $0 }
                        )
                    case .notifications:
                        PermissionStepView(
                            badgeText: String(
                                format: NSLocalizedString("onboarding.step.counter", comment: ""),
                                step.rawValue + 1,
                                OnboardingStep.allCases.count
                            ),
                            title: NSLocalizedString("onboarding.notifications.title", comment: ""),
                            subtitle: NSLocalizedString("onboarding.notifications.subtitle", comment: ""),
                            systemImage: "bell.badge.fill",
                            tint: Color.blue,
                            bullets: [
                                NSLocalizedString("onboarding.notifications.point.1", comment: ""),
                                NSLocalizedString("onboarding.notifications.point.2", comment: ""),
                                NSLocalizedString("onboarding.notifications.point.3", comment: "")
                            ],
                            footnote: NSLocalizedString("onboarding.notifications.footnote", comment: ""),
                            statusText: notificationStatus.localizedBioTrackLabel,
                            statusTint: notificationStatus.localizedBioTrackColor,
                            primaryTitle: notificationPrimaryTitle,
                            secondaryTitle: NSLocalizedString("onboarding.action.later", comment: ""),
                            isLoading: isRequestingNotificationPermission,
                            onPrimary: { Task { await handleNotificationPrimaryAction() } },
                            onSecondary: { step = .health }
                        )
                    case .health:
                        PermissionStepView(
                            badgeText: String(
                                format: NSLocalizedString("onboarding.step.counter", comment: ""),
                                step.rawValue + 1,
                                OnboardingStep.allCases.count
                            ),
                            title: NSLocalizedString("onboarding.health.title", comment: ""),
                            subtitle: NSLocalizedString("onboarding.health.subtitle", comment: ""),
                            systemImage: "heart.text.square.fill",
                            tint: Color.red,
                            bullets: [
                                NSLocalizedString("onboarding.health.point.1", comment: ""),
                                NSLocalizedString("onboarding.health.point.2", comment: ""),
                                NSLocalizedString("onboarding.health.point.3", comment: "")
                            ],
                            footnote: NSLocalizedString("onboarding.health.footnote", comment: ""),
                            statusText: appState.healthKitStatus.localizedBioTrackLabel,
                            statusTint: appState.healthKitStatus.localizedBioTrackColor,
                            primaryTitle: healthPrimaryTitle,
                            secondaryTitle: NSLocalizedString("onboarding.action.later", comment: ""),
                            isLoading: isRequestingHealthPermission,
                            onPrimary: { Task { await handleHealthPrimaryAction() } },
                            onSecondary: { completeOnboarding() }
                        )
                    }
                }
                .animation(.easeInOut(duration: 0.22), value: step)
            }
        }
        .task {
            await refreshPermissionStatuses()
        }
        .onChange(of: step) { _ in
            Task { await refreshPermissionStatuses() }
        }
        .sheet(item: $activeLegalDocument) { document in
            LegalNoticeView(document: document)
        }
    }

    private var onboardingProgress: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(NSLocalizedString("onboarding.brand", comment: ""))
                    .font(.caption.weight(.bold))
                    .foregroundColor(Color.blue.opacity(0.80))
                Spacer()
                Text(
                    String(
                        format: NSLocalizedString("onboarding.step.counter", comment: ""),
                        step.rawValue + 1,
                        OnboardingStep.allCases.count
                    )
                )
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)
            }

            GeometryReader { proxy in
                let width = proxy.size.width / CGFloat(OnboardingStep.allCases.count)
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .fill(Color.primary.opacity(0.08))
                    .overlay(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 999, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.blue.opacity(0.75), Color.blue],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: width * CGFloat(step.rawValue + 1))
                    }
            }
            .frame(height: 8)
        }
        .padding(.horizontal, 24)
        .padding(.top, 20)
    }

    private var notificationPrimaryTitle: String {
        switch notificationStatus {
        case .authorized, .provisional, .ephemeral:
            return NSLocalizedString("onboarding.action.continue", comment: "")
        case .denied:
            return NSLocalizedString("onboarding.action.open_settings", comment: "")
        case .notDetermined:
            return NSLocalizedString("onboarding.notifications.primary", comment: "")
        @unknown default:
            return NSLocalizedString("onboarding.action.continue", comment: "")
        }
    }

    private var healthPrimaryTitle: String {
        switch appState.healthKitStatus {
        case .authorized, .notAvailable:
            return NSLocalizedString("onboarding.action.continue", comment: "")
        case .denied:
            return NSLocalizedString("onboarding.action.open_settings", comment: "")
        case .notDetermined:
            return NSLocalizedString("onboarding.health.primary", comment: "")
        }
    }

    @MainActor
    private func refreshPermissionStatuses() async {
        notificationStatus = await NotificationService.shared.authorizationStatus()
        appState.refreshHealthKitStatus()
    }

    @MainActor
    private func handleNotificationPrimaryAction() async {
        switch notificationStatus {
        case .authorized, .provisional, .ephemeral:
            step = .health
        case .denied:
            NotificationService.shared.openSettings()
        case .notDetermined:
            isRequestingNotificationPermission = true
            _ = await NotificationService.shared.requestPermission()
            notificationStatus = await NotificationService.shared.authorizationStatus()
            isRequestingNotificationPermission = false
            step = .health
        @unknown default:
            step = .health
        }
    }

    @MainActor
    private func handleHealthPrimaryAction() async {
        switch appState.healthKitStatus {
        case .authorized, .notAvailable:
            completeOnboarding()
        case .denied:
            NotificationService.shared.openSettings()
        case .notDetermined:
            isRequestingHealthPermission = true
            await appState.syncHealthKit(rangeDays: 30, dedupePolicy: .replace)
            appState.refreshHealthKitStatus()
            isRequestingHealthPermission = false
            completeOnboarding()
        }
    }

    private func completeOnboarding() {
        isCompleted = true
    }
}

struct WelcomeStepView: View {
    let onContinue: () -> Void
    let onOpenDocument: (LegalDocument) -> Void

    var body: some View {
        GeometryReader { proxy in
            let compact = proxy.size.height < 690
            let emblemSize: CGFloat = compact ? 176 : 244
            let logoSize: CGFloat = compact ? 122 : 176
            let titleSize: CGFloat = compact ? 28 : 32

            ScrollView(showsIndicators: false) {
                VStack(spacing: compact ? 18 : 26) {
                    Spacer(minLength: compact ? 4 : 16)

                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.78))
                            .frame(width: emblemSize, height: emblemSize)
                            .shadow(color: Color.blue.opacity(0.14), radius: compact ? 18 : 28, x: 0, y: compact ? 12 : 18)
                        Circle()
                            .stroke(Color.blue.opacity(0.12), lineWidth: 1)
                            .frame(width: emblemSize, height: emblemSize)
                        Image("OnboardingLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: logoSize, height: logoSize)
                    }

                    VStack(spacing: compact ? 10 : 14) {
                        Text(NSLocalizedString("onboarding.welcome.title", comment: ""))
                            .font(.system(size: titleSize, weight: .bold, design: .rounded))
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(NSLocalizedString("onboarding.welcome.subtitle", comment: ""))
                            .font(compact ? .subheadline : .body)
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 8)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(spacing: compact ? 8 : 12) {
                        WelcomeBenefitRow(
                            systemImage: "checkmark.circle.fill",
                            text: NSLocalizedString("onboarding.welcome.benefit.1", comment: ""),
                            compact: compact
                        )
                        WelcomeBenefitRow(
                            systemImage: "heart.text.square.fill",
                            text: NSLocalizedString("onboarding.welcome.benefit.2", comment: ""),
                            compact: compact
                        )
                        WelcomeBenefitRow(
                            systemImage: "shield.lefthalf.filled",
                            text: NSLocalizedString("onboarding.welcome.benefit.3", comment: ""),
                            compact: compact
                        )
                    }

                    Spacer(minLength: 0)

                    VStack(spacing: compact ? 10 : 14) {
                        Button(action: onContinue) {
                            Text(NSLocalizedString("onboarding.welcome.primary", comment: ""))
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, compact ? 14 : 16)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color.blue)

                        VStack(spacing: compact ? 6 : 10) {
                            Text(NSLocalizedString("onboarding.welcome.legal_intro", comment: ""))
                                .font(.footnote)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)

                            VStack(spacing: compact ? 4 : 8) {
                                LegalLinkButton(title: NSLocalizedString("legal.privacy.title", comment: "")) {
                                    onOpenDocument(.privacy)
                                }
                                LegalLinkButton(title: NSLocalizedString("legal.support.title", comment: "")) {
                                    onOpenDocument(.support)
                                }
                                LegalLinkButton(title: NSLocalizedString("legal.terms.title", comment: "")) {
                                    onOpenDocument(.terms)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, compact ? 8 : 12)
                .padding(.bottom, 18)
                .frame(minHeight: proxy.size.height, alignment: .top)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }
}

struct PermissionStepView: View {
    let badgeText: String
    let title: String
    let subtitle: String
    let systemImage: String
    let tint: Color
    let bullets: [String]
    let footnote: String
    let statusText: String
    let statusTint: Color
    let primaryTitle: String
    let secondaryTitle: String
    let isLoading: Bool
    let onPrimary: () -> Void
    let onSecondary: () -> Void

    var body: some View {
        GeometryReader { proxy in
            let compact = proxy.size.height < 690
            let heroHeight: CGFloat = compact ? 128 : 184
            let heroIconSize: CGFloat = compact ? 42 : 54
            let titleSize: CGFloat = compact ? 24 : 28

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: compact ? 16 : 24) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(badgeText)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(.secondary)

                        ZStack {
                            RoundedRectangle(cornerRadius: 28, style: .continuous)
                                .fill(Color.white.opacity(0.74))
                                .frame(height: heroHeight)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                                        .stroke(Color.primary.opacity(0.06), lineWidth: 1)
                                )
                            Image(systemName: systemImage)
                                .font(.system(size: heroIconSize, weight: .semibold))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [tint.opacity(0.78), tint],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        }

                        Text(title)
                            .font(.system(size: titleSize, weight: .bold, design: .rounded))
                        Text(subtitle)
                            .font(compact ? .subheadline : .body)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)

                        HStack(spacing: 8) {
                            Circle()
                                .fill(statusTint)
                                .frame(width: 10, height: 10)
                            Text(statusText)
                                .font(.footnote.weight(.semibold))
                                .foregroundColor(.secondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(bullets, id: \.self) { bullet in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "checkmark.seal.fill")
                                    .foregroundColor(tint)
                                    .padding(.top, 2)
                                Text(bullet)
                                    .font(compact ? .subheadline : .body)
                                    .foregroundColor(.primary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .padding(compact ? 14 : 18)
                    .background(Color.white.opacity(0.62))
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .stroke(Color.primary.opacity(0.05), lineWidth: 1)
                    )

                    Spacer(minLength: 0)

                    Text(footnote)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(spacing: compact ? 6 : 10) {
                        Button(action: onPrimary) {
                            HStack(spacing: 10) {
                                if isLoading {
                                    ProgressView()
                                        .progressViewStyle(.circular)
                                        .tint(.white)
                                }
                                Text(primaryTitle)
                                    .font(.headline)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, compact ? 14 : 16)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(tint)
                        .disabled(isLoading)

                        Button(action: onSecondary) {
                            Text(secondaryTitle)
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, compact ? 18 : 28)
                .frame(minHeight: proxy.size.height, alignment: .top)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
    }
}

struct LegalNoticeView: View {
    let document: LegalDocument
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    Label {
                        Text(document.title)
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                    } icon: {
                        Image(systemName: document.systemImage)
                            .font(.title2.weight(.semibold))
                            .foregroundColor(document.tint)
                    }

                    ForEach(document.paragraphKeys, id: \.self) { key in
                        Text(NSLocalizedString(key, comment: ""))
                            .font(.body)
                            .foregroundColor(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(spacing: 12) {
                        Link(destination: document.primaryURL) {
                            Label(NSLocalizedString(document.primaryActionKey, comment: ""), systemImage: "arrow.up.right.square")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(document.tint)

                        if let secondaryURL = document.secondaryURL,
                           let secondaryActionKey = document.secondaryActionKey {
                            Link(destination: secondaryURL) {
                                Label(NSLocalizedString(secondaryActionKey, comment: ""), systemImage: "exclamationmark.bubble")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.top, 4)
                }
                .padding(24)
            }
            .background(Color("Background").ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("action.close", comment: "")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

enum LegalDocument: String, Identifiable {
    case privacy
    case support
    case terms

    var id: String { rawValue }

    var title: String {
        switch self {
        case .privacy:
            return NSLocalizedString("legal.privacy.title", comment: "")
        case .support:
            return NSLocalizedString("legal.support.title", comment: "")
        case .terms:
            return NSLocalizedString("legal.terms.title", comment: "")
        }
    }

    var systemImage: String {
        switch self {
        case .privacy:
            return "lock.shield"
        case .support:
            return "questionmark.bubble"
        case .terms:
            return "doc.text"
        }
    }

    var tint: Color {
        switch self {
        case .privacy:
            return .blue
        case .support:
            return .teal
        case .terms:
            return .indigo
        }
    }

    var paragraphKeys: [String] {
        switch self {
        case .privacy:
            return [
                "legal.privacy.summary.1",
                "legal.privacy.summary.2",
                "legal.privacy.summary.3"
            ]
        case .support:
            return [
                "legal.support.summary.1",
                "legal.support.summary.2",
                "legal.support.summary.3"
            ]
        case .terms:
            return [
                "legal.terms.summary.1",
                "legal.terms.summary.2",
                "legal.terms.summary.3",
                "legal.terms.summary.4"
            ]
        }
    }

    var primaryActionKey: String {
        switch self {
        case .privacy:
            return "legal.privacy.open"
        case .support:
            return "legal.support.open"
        case .terms:
            return "legal.terms.open"
        }
    }

    var primaryURL: URL {
        switch self {
        case .privacy:
            return BioTrackLinks.privacyPolicy
        case .support:
            return BioTrackLinks.support
        case .terms:
            return BioTrackLinks.standardEULA
        }
    }

    var secondaryActionKey: String? {
        switch self {
        case .support:
            return "legal.support.email"
        default:
            return nil
        }
    }

    var secondaryURL: URL? {
        switch self {
        case .support:
            return BioTrackLinks.issueTracker
        default:
            return nil
        }
    }
}

enum BioTrackLinks {
    static let privacyPolicy = URL(string: "https://fab72309.github.io/ajuste/privacy-policy.html")!
    static let support = URL(string: "https://fab72309.github.io/ajuste/support.html")!
    static let issueTracker = URL(string: "https://github.com/fab72309/ajuste/issues/new/choose")!
    static let standardEULA = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
}

private struct WelcomeBenefitRow: View {
    let systemImage: String
    let text: String
    var compact: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .foregroundColor(Color.blue)
                .font(compact ? .subheadline : .headline)
                .frame(width: compact ? 22 : 26)
            Text(text)
                .font(compact ? .subheadline : .body)
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
        .padding(.horizontal, compact ? 14 : 16)
        .padding(.vertical, compact ? 10 : 14)
        .background(Color.white.opacity(0.64))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.primary.opacity(0.05), lineWidth: 1)
        )
    }
}

private struct LaunchLoadingView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var glow = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color("Background"),
                    Color("Background"),
                    Color.blue.opacity(0.10)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 20) {
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.82))
                        .frame(width: 198, height: 198)
                        .shadow(color: Color("Secondary").opacity(glow ? 0.18 : 0.08), radius: glow ? 28 : 16, x: 0, y: 12)
                    Circle()
                        .stroke(Color("Primary").opacity(0.14), lineWidth: 1)
                        .frame(width: 198, height: 198)
                    Image("OnboardingLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 140, height: 140)
                        .scaleEffect(glow ? 1.02 : 0.98)
                }

                VStack(spacing: 8) {
                    Text(NSLocalizedString("launch.loading.title", comment: ""))
                        .font(.system(size: 29, weight: .bold, design: .rounded))
                    Text(NSLocalizedString("launch.loading.subtitle", comment: ""))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }

                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Color("Secondary"))
            }
            .padding(.horizontal, 24)
        }
        .task {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                glow = true
            }
        }
    }
}

private struct LegalLinkButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title)
                Image(systemName: "arrow.up.right")
                    .font(.caption.weight(.semibold))
            }
            .font(.footnote.weight(.semibold))
            .foregroundColor(Color("Primary"))
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

extension UNAuthorizationStatus {
    var localizedBioTrackLabel: String {
        switch self {
        case .authorized, .provisional, .ephemeral:
            return NSLocalizedString("status.authorized", comment: "")
        case .denied:
            return NSLocalizedString("status.denied", comment: "")
        case .notDetermined:
            return NSLocalizedString("status.not_configured", comment: "")
        @unknown default:
            return NSLocalizedString("status.unknown", comment: "")
        }
    }

    var localizedBioTrackColor: Color {
        switch self {
        case .authorized, .provisional, .ephemeral:
            return .green
        case .denied:
            return .red
        case .notDetermined:
            return .orange
        @unknown default:
            return .gray
        }
    }
}

extension HealthAuthorizationState {
    var localizedBioTrackLabel: String {
        switch self {
        case .notAvailable:
            return NSLocalizedString("status.not_available", comment: "")
        case .notDetermined:
            return NSLocalizedString("status.not_configured", comment: "")
        case .denied:
            return NSLocalizedString("status.denied", comment: "")
        case .authorized:
            return NSLocalizedString("status.authorized", comment: "")
        }
    }

    var localizedBioTrackColor: Color {
        switch self {
        case .authorized:
            return .green
        case .denied:
            return .red
        case .notAvailable:
            return .gray
        case .notDetermined:
            return .orange
        }
    }
}

#Preview("Content") {
    ContentView().environmentObject(AppState())
}

#Preview("Onboarding") {
    OnboardingFlowView(isCompleted: .constant(false)).environmentObject(AppState())
}
